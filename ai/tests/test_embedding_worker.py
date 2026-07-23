import unittest
import json
from unittest.mock import patch

from app.embedding_provider import (
    EmbeddingProviderError,
    OpenAICompatibleEmbeddingProvider,
    OpenAIEmbeddingProvider,
    create_embedding_provider,
)
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
    provider_id = "fake"
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
            {"model": "test-model", "data": [{"index": 0, "embedding": [0.1, 0.2, 0.3]}]}
        ).encode()
        response.__enter__.return_value = response

        provider = OpenAIEmbeddingProvider("test-key", model="test-model", dimension=3)
        with patch("app.embedding_provider.urlopen", return_value=response) as urlopen:
            vectors = provider.embed(["회의"])

        request = urlopen.call_args.args[0]
        body = json.loads(request.data.decode())
        self.assertEqual(body["model"], "test-model")
        self.assertEqual(body["dimensions"], 3)
        self.assertEqual(body["input"], ["회의"])
        self.assertEqual(vectors, [[0.1, 0.2, 0.3]])
        self.assertEqual(provider.last_response_model, "test-model")
        self.assertTrue(provider.last_response_model_observed)

    def test_factory_uses_openai_provider_by_default(self):
        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "openai",
                "OPENAI_API_KEY": "test-key",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "OPENAI_EMBEDDING_MODEL": "text-embedding-test",
                "OPENAI_EMBEDDING_DIMENSION": "3",
                "AI_VECTOR_DIMENSION": "3",
            }.get(key, default)

            provider = create_embedding_provider()

        self.assertIsInstance(provider, OpenAICompatibleEmbeddingProvider)
        self.assertEqual(provider.provider_id, "openai")
        self.assertEqual(provider.model, "text-embedding-test")
        self.assertEqual(provider.dimension, 3)
        self.assertTrue(provider.include_dimensions)

    def test_factory_uses_local_openai_compatible_provider(self):
        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_API_KEY": "local-token",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_VECTOR_DIMENSION": "1024",
            }.get(key, default)

            provider = create_embedding_provider()

        self.assertIsInstance(provider, OpenAICompatibleEmbeddingProvider)
        self.assertEqual(provider.provider_id, "local-openai-compatible")
        self.assertEqual(provider.model, "local-embedding")
        self.assertEqual(provider.dimension, 1024)
        self.assertFalse(provider.include_dimensions)

    def test_local_embedding_factory_rejects_openai_or_invalid_base_url(self):
        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_API_KEY": "local-token",
                "AI_EMBEDDING_BASE_URL": "https://api.openai.com/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_VECTOR_DIMENSION": "1024",
            }.get(key, default)

            with self.assertRaises(EmbeddingProviderError) as openai_url:
                create_embedding_provider()

        self.assertIn("must not point to api.openai.com", str(openai_url.exception))

        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_API_KEY": "local-token",
                "AI_EMBEDDING_BASE_URL": "/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_VECTOR_DIMENSION": "1024",
            }.get(key, default)

            with self.assertRaises(EmbeddingProviderError) as invalid_url:
                create_embedding_provider()

        self.assertIn("absolute http(s) URL", str(invalid_url.exception))

        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_API_KEY": "local-token",
                "AI_EMBEDDING_BASE_URL": "https://embedding.internal:8001/v1?api_key=secret",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_VECTOR_DIMENSION": "1024",
            }.get(key, default)

            with self.assertRaises(EmbeddingProviderError) as query_url:
                create_embedding_provider()

        self.assertIn("must not include query or fragment", str(query_url.exception))

    def test_local_embedding_provider_can_opt_into_dimensions_parameter(self):
        response = unittest.mock.MagicMock()
        response.read.return_value = json.dumps(
            {"data": [{"index": 0, "embedding": [0.1, 0.2, 0.3]}]}
        ).encode()
        response.__enter__.return_value = response

        provider = OpenAICompatibleEmbeddingProvider(
            "local-token",
            base_url="http://embedding.internal:8001/v1",
            model="local-embedding",
            dimension=3,
            provider_id="local-openai-compatible",
            include_dimensions=False,
        )
        with patch("app.embedding_provider.urlopen", return_value=response) as urlopen:
            provider.embed(["회의"])

        body = json.loads(urlopen.call_args.args[0].data.decode())
        self.assertNotIn("dimensions", body)

        provider_with_dimensions = OpenAICompatibleEmbeddingProvider(
            "local-token",
            base_url="http://embedding.internal:8001/v1",
            model="local-embedding",
            dimension=3,
            provider_id="local-openai-compatible",
            include_dimensions=True,
        )
        with patch("app.embedding_provider.urlopen", return_value=response) as urlopen_with_dimensions:
            provider_with_dimensions.embed(["회의"])

        body_with_dimensions = json.loads(urlopen_with_dimensions.call_args.args[0].data.decode())
        self.assertEqual(body_with_dimensions["dimensions"], 3)

    def test_rejects_malformed_embedding_response_items(self):
        provider = OpenAICompatibleEmbeddingProvider(
            "local-token",
            base_url="http://embedding.internal:8001/v1",
            model="local-embedding",
            dimension=3,
            provider_id="local-openai-compatible",
            include_dimensions=False,
        )
        invalid_item_response = unittest.mock.MagicMock()
        invalid_item_response.read.return_value = json.dumps({"data": ["not-an-object"]}).encode()
        invalid_item_response.__enter__.return_value = invalid_item_response

        with (
            patch("app.embedding_provider.urlopen", return_value=invalid_item_response),
            self.assertRaises(EmbeddingProviderError) as invalid_item,
        ):
            provider.embed(["회의"])

        self.assertIn("invalid result", str(invalid_item.exception))

        non_numeric_response = unittest.mock.MagicMock()
        non_numeric_response.read.return_value = json.dumps(
            {"data": [{"index": 0, "embedding": [0.1, "bad", 0.3]}]}
        ).encode()
        non_numeric_response.__enter__.return_value = non_numeric_response

        with (
            patch("app.embedding_provider.urlopen", return_value=non_numeric_response),
            self.assertRaises(EmbeddingProviderError) as non_numeric,
        ):
            provider.embed(["회의"])

        self.assertIn("invalid vector value", str(non_numeric.exception))

    def test_factory_rejects_dimension_mismatch_before_retrieval_or_worker_runs(self):
        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_API_KEY": "local-token",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_VECTOR_DIMENSION": "1536",
            }.get(key, default)

            with self.assertRaises(EmbeddingProviderError) as raised:
                create_embedding_provider()

        message = str(raised.exception)
        self.assertIn("embedding provider dimension does not match vector schema dimension", message)
        self.assertIn("embedding job generation/swap reindex", message)

    def test_factory_rejects_invalid_dimension_environment_values(self):
        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_API_KEY": "local-token",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "not-a-number",
                "AI_VECTOR_DIMENSION": "1536",
            }.get(key, default)

            with self.assertRaises(EmbeddingProviderError) as invalid:
                create_embedding_provider()

        self.assertIn("AI_EMBEDDING_DIMENSION must be a valid integer", str(invalid.exception))

        with patch("app.embedding_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_EMBEDDING_PROVIDER": "openai",
                "OPENAI_API_KEY": "test-key",
                "OPENAI_BASE_URL": "https://api.openai.com/v1",
                "OPENAI_EMBEDDING_MODEL": "text-embedding-test",
                "OPENAI_EMBEDDING_DIMENSION": "3",
                "AI_VECTOR_DIMENSION": "0",
            }.get(key, default)

            with self.assertRaises(EmbeddingProviderError) as zero:
                create_embedding_provider()

        self.assertIn("AI_VECTOR_DIMENSION must be greater than 0", str(zero.exception))


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
