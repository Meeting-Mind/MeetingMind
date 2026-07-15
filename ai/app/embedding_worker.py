import logging
import os
import time
from time import perf_counter

from .embedding_provider import EmbeddingProvider, EmbeddingProviderError, OpenAIEmbeddingProvider
from .observability import bind_trace_id, elapsed_ms, log_event, reset_trace_id
from .repository import EmbeddingJob, EmbeddingJobRepository, PostgresEmbeddingRepository


LOGGER = logging.getLogger("meetingmind.ai.embedding")
RETRY_DELAYS_SECONDS = (60, 300, 900)
LEASE_SECONDS = 300


class EmbeddingWorker:
    def __init__(self, repository: EmbeddingJobRepository, provider: EmbeddingProvider):
        self._repository = repository
        self._provider = provider

    def run_once(self) -> bool:
        job = self._repository.claim_job(LEASE_SECONDS)
        if job is None:
            return False

        trace_token = bind_trace_id(job.id)
        started_at = perf_counter()
        try:
            try:
                snapshot = self._repository.load_snapshot(job)
                if snapshot is None or not snapshot.chunks:
                    raise ValueError("source has no indexable content")
                if job.content_hash and job.content_hash != snapshot.content_hash:
                    raise SourceChangedError()

                vectors = self._provider.embed([chunk.embeddingText for chunk in snapshot.chunks])
                activated = self._repository.complete_job(
                    job,
                    snapshot,
                    vectors,
                    model=self._provider.model,
                    dimension=self._provider.dimension,
                )
                log_event(
                    LOGGER,
                    "embedding_job_completed",
                    jobId=job.id,
                    generation=job.generation,
                    attemptCount=job.attempt_count,
                    chunkCount=len(snapshot.chunks),
                    activated=activated,
                    durationMs=elapsed_ms(started_at),
                )
            except Exception as error:
                failure_code = normalize_failure_code(error)
                retry_delay = retry_delay_for(job, error)
                self._repository.record_failure(job, failure_code, retry_delay)
                log_event(
                    LOGGER,
                    "embedding_job_failed",
                    level=logging.WARNING,
                    jobId=job.id,
                    generation=job.generation,
                    attemptCount=job.attempt_count,
                    failureCode=failure_code,
                    willRetry=retry_delay is not None,
                    durationMs=elapsed_ms(started_at),
                )
        finally:
            reset_trace_id(trace_token)
        self.observe_queue()
        return True

    def observe_queue(self) -> None:
        try:
            metrics = self._repository.queue_metrics()
        except Exception as error:
            log_event(
                LOGGER,
                "embedding_queue_unavailable",
                level=logging.WARNING,
                errorType=type(error).__name__,
            )
            return
        log_event(
            LOGGER,
            "embedding_queue_snapshot",
            pendingCount=metrics.pending_count,
            processingCount=metrics.processing_count,
            failedCount=metrics.failed_count,
            oldestPendingAgeSeconds=metrics.oldest_pending_age_seconds,
        )


class SourceChangedError(RuntimeError):
    pass


def retry_delay_for(job: EmbeddingJob, error: Exception | None = None) -> int | None:
    if isinstance(error, (SourceChangedError, ValueError)) or job.attempt_count >= 4:
        return None
    return RETRY_DELAYS_SECONDS[job.attempt_count - 1]


def normalize_failure_code(error: Exception) -> str:
    if isinstance(error, SourceChangedError):
        return "SOURCE_CHANGED"
    if isinstance(error, EmbeddingProviderError):
        return "PROVIDER_UNAVAILABLE"
    if isinstance(error, ValueError):
        return "INVALID_SOURCE"
    return "INTERNAL_ERROR"


def main() -> None:
    logging.basicConfig(level=os.getenv("AI_LOG_LEVEL", "INFO"))
    repository = PostgresEmbeddingRepository(os.getenv("AI_DATABASE_URL", ""))
    provider = OpenAIEmbeddingProvider.from_environment()
    poll_seconds = max(1, int(os.getenv("AI_EMBEDDING_POLL_SECONDS", "5")))
    observation_seconds = max(5, int(os.getenv("AI_QUEUE_OBSERVATION_SECONDS", "60")))
    worker = EmbeddingWorker(repository, provider)
    next_observation_at = 0.0

    while True:
        if worker.run_once():
            next_observation_at = time.monotonic() + observation_seconds
        else:
            now = time.monotonic()
            if now >= next_observation_at:
                worker.observe_queue()
                next_observation_at = now + observation_seconds
            time.sleep(poll_seconds)


if __name__ == "__main__":
    main()
