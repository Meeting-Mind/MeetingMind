import unittest
import json
from unittest.mock import patch

from app.embedding_provider import EmbeddingProviderError, OpenAIEmbeddingProvider
from app.embedding_worker import EmbeddingWorker, retry_delay_for
from app.rag import RagChunk
from app.repository import EmbeddingJob, EmbeddingQueueMetrics, EmbeddingSnapshot


class FakeRepository:
    def __init__(self, job: EmbeddingJob | None, snapshot: EmbeddingSnapshot | None):
        self.job = job
        self.snapshot = snapshot
        self.completed = None
        self.failure = None
        self.metrics = EmbeddingQueueMetrics(2, 1, 0, 15)

    def claim_job(self, lease_seconds: int):
        self.lease_seconds = lease_seconds
        job, self.job = self.job, None
        return job

    def load_snapshot(self, job):
        return self.snapshot

    def complete_job(self, job, snapshot, vectors, *, model, dimension):
        self.completed = (job, snapshot, vectors, model, dimension)
        return True

    def record_failure(self, job, failure_code, retry_delay_seconds):
        self.failure = (job, failure_code, retry_delay_seconds)

    def queue_metrics(self):
        return self.metrics


class FakeProvider:
    model = "test-embedding"
    dimension = 3

    def __init__(self, *, error: Exception | None = None):
        self.error = error
        self.inputs = None

    def embed(self, texts):
        self.inputs = texts
        if self.error:
            raise self.error
        return [[0.1, 0.2, 0.3] for _ in texts]


class EmbeddingWorkerTest(unittest.TestCase):
    def test_completes_claimed_job_with_generated_vectors(self):
        job = embedding_job(attempt_count=1)
        snapshot = embedding_snapshot()
        repository = FakeRepository(job, snapshot)
        provider = FakeProvider()

        processed = EmbeddingWorker(repository, provider).run_once()

        self.assertTrue(processed)
        self.assertEqual(provider.inputs, [snapshot.chunks[0].embeddingText])
        self.assertEqual(repository.completed[3:], (provider.model, provider.dimension))
        self.assertIsNone(repository.failure)

    def test_does_not_call_provider_when_source_hash_changed(self):
        job = embedding_job(attempt_count=1, content_hash="0" * 64)
        repository = FakeRepository(job, embedding_snapshot())
        provider = FakeProvider()

        EmbeddingWorker(repository, provider).run_once()

        self.assertIsNone(provider.inputs)
        self.assertEqual(repository.failure[1:], ("SOURCE_CHANGED", None))

    def test_provider_failure_is_final_after_three_retries(self):
        job = embedding_job(attempt_count=4)
        repository = FakeRepository(job, embedding_snapshot())
        provider = FakeProvider(error=EmbeddingProviderError("secret provider detail"))

        with self.assertLogs("meetingmind.ai.embedding", level="WARNING") as logs:
            EmbeddingWorker(repository, provider).run_once()

        self.assertEqual(repository.failure[1:], ("PROVIDER_UNAVAILABLE", None))
        self.assertNotIn("secret provider detail", "\n".join(logs.output))

    def test_logs_safe_queue_metrics_after_processing(self):
        repository = FakeRepository(embedding_job(attempt_count=1), embedding_snapshot())

        with self.assertLogs("meetingmind.ai.embedding", level="INFO") as logs:
            EmbeddingWorker(repository, FakeProvider()).run_once()

        payloads = [json.loads(message.split("INFO:meetingmind.ai.embedding:", 1)[1]) for message in logs.output]
        queue_payload = next(payload for payload in payloads if payload["event"] == "embedding_queue_snapshot")
        self.assertEqual(queue_payload["pendingCount"], 2)
        self.assertEqual(queue_payload["processingCount"], 1)
        self.assertEqual(queue_payload["oldestPendingAgeSeconds"], 15)

    def test_returns_false_without_claimable_job(self):
        repository = FakeRepository(None, None)

        self.assertFalse(EmbeddingWorker(repository, FakeProvider()).run_once())

    def test_retry_schedule_is_one_five_fifteen_minutes_then_final(self):
        self.assertEqual(retry_delay_for(embedding_job(attempt_count=1)), 60)
        self.assertEqual(retry_delay_for(embedding_job(attempt_count=2)), 300)
        self.assertEqual(retry_delay_for(embedding_job(attempt_count=3)), 900)
        self.assertIsNone(retry_delay_for(embedding_job(attempt_count=4)))


class OpenAIEmbeddingProviderTest(unittest.TestCase):
    def test_requests_configured_model_and_dimension(self):
        response = unittest.mock.MagicMock()
        response.read.return_value = json.dumps(
            {"data": [{"index": 0, "embedding": [0.1, 0.2, 0.3]}]}
        ).encode()
        response.__enter__.return_value = response

        with patch("app.embedding_provider.urlopen", return_value=response) as urlopen:
            vectors = OpenAIEmbeddingProvider("test-key", model="test-model", dimension=3).embed(["회의"])

        request = urlopen.call_args.args[0]
        body = json.loads(request.data.decode())
        self.assertEqual(body["model"], "test-model")
        self.assertEqual(body["dimensions"], 3)
        self.assertEqual(body["input"], ["회의"])
        self.assertEqual(vectors, [[0.1, 0.2, 0.3]])


def embedding_job(*, attempt_count: int, content_hash: str | None = None) -> EmbeddingJob:
    return EmbeddingJob(
        id="job-1",
        space_id="space-1",
        project_knowledge_id=None,
        meeting_id="meeting-1",
        generation=1,
        attempt_count=attempt_count,
        content_hash=content_hash,
    )


def embedding_snapshot() -> EmbeddingSnapshot:
    return EmbeddingSnapshot(
        chunks=(
            RagChunk(
                chunkId="meeting-1:transcript:0001",
                scope="meeting",
                projectId="space-1",
                meetingId="meeting-1",
                sourceType="transcript",
                sourceId="segment-1",
                content="회의 데이터",
                embeddingText="회의: 데이터",
            ),
        ),
        content_hash="1" * 64,
    )


if __name__ == "__main__":
    unittest.main()
