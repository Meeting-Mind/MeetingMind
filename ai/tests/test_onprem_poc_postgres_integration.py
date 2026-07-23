import os
import threading
import time
import unittest
from http.server import ThreadingHTTPServer
from unittest.mock import patch
from uuid import uuid4

import psycopg

import onprem_poc_smoke
import onprem_poc_validate
from app.embedding_provider import create_embedding_provider
from app.embedding_worker import EmbeddingWorker
from app.rag import RagSearchRequest
from app.repository import PostgresEmbeddingRepository, PostgresRagRetriever
from tests.test_onprem_poc_http_smoke import OpenAICompatibleMockHandler
from tests.test_openai_rag_integration import (
    assert_empty_embedding_queue,
    delete_fixture,
    embedding_job_statuses,
    insert_fixture,
)


def can_run_onprem_postgres_integration() -> bool:
    return (
        os.getenv("RUN_ONPREM_POC_POSTGRES_INTEGRATION") == "true"
        and bool(os.getenv("AI_TEST_DATABASE_URL"))
    )


@unittest.skipUnless(
    can_run_onprem_postgres_integration(),
    "RUN_ONPREM_POC_POSTGRES_INTEGRATION=true and AI_TEST_DATABASE_URL are required",
)
class OnPremPocPostgresIntegrationTest(unittest.TestCase):
    def test_local_openai_compatible_embedding_indexes_pgvector_and_retrieves_with_scope(self):
        dsn = os.environ["AI_TEST_DATABASE_URL"]
        suffix = uuid4().hex[:12]
        user_id = f"onprem-rag-user-{suffix}"
        space_id = f"onprem-rag-space-{suffix}"
        allowed_meeting_id = f"onprem-rag-allowed-{suffix}"
        restricted_meeting_id = f"onprem-rag-restricted-{suffix}"

        try:
            server = ThreadingHTTPServer(("127.0.0.1", 0), OpenAICompatibleMockHandler)
        except PermissionError as error:
            raise unittest.SkipTest("local socket binding is not permitted in this sandbox") from error

        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}/v1"

        try:
            with patch.dict(
                os.environ,
                {
                    "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                    "AI_EMBEDDING_BASE_URL": base_url,
                    "AI_EMBEDDING_API_KEY": "local-token",
                    "AI_EMBEDDING_MODEL": "mock-local-embedding",
                    "AI_EMBEDDING_DIMENSION": "1536",
                    "AI_EMBEDDING_INCLUDE_DIMENSIONS": "false",
                    "AI_VECTOR_DIMENSION": "1536",
                    "AI_DATABASE_URL": dsn,
                    "ONPREM_POC_RAG_QUERY": "다온오로라 출시 일정",
                    "ONPREM_POC_PROJECT_ID": space_id,
                    "ONPREM_POC_ALLOWED_MEETING_IDS": allowed_meeting_id,
                    "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                },
                clear=False,
            ):
                assert_empty_embedding_queue(dsn)
                insert_fixture(dsn, user_id, space_id, allowed_meeting_id, restricted_meeting_id, suffix)

                provider = create_embedding_provider()
                self.assertEqual(provider.provider_id, "local-openai-compatible")
                self.assertEqual(provider.dimension, 1536)

                repository = PostgresEmbeddingRepository(dsn)
                worker = EmbeddingWorker(repository, provider)
                self.assertTrue(worker.run_once())
                self.assertTrue(worker.run_once())
                self.assertFalse(worker.run_once())
                self.assertEqual(embedding_job_statuses(dsn, space_id), ["COMPLETED", "COMPLETED"])

                with psycopg.connect(dsn) as connection:
                    dimensions = connection.execute(
                        """
                        select distinct vector_dims(embedding)
                        from embedding_chunks
                        where space_id = %s and is_active = true
                        """,
                        (space_id,),
                    ).fetchall()
                self.assertEqual(dimensions, [(1536,)])

                retrieval_metric = onprem_poc_smoke.run_retrieval_latency_probe()
                self.assertTrue(retrieval_metric.ok, retrieval_metric)
                self.assertIsNotNone(retrieval_metric.retrievalLatencyMs)
                self.assertGreater(retrieval_metric.sourceCount or 0, 0)

                retriever = PostgresRagRetriever(repository, provider)
                request = RagSearchRequest(
                    query="다온오로라 출시 일정",
                    scope="project",
                    projectId=space_id,
                    allowedMeetingIds=(allowed_meeting_id,),
                    sourceTypes=("transcript",),
                    limit=5,
                )
                started_at = time.perf_counter()
                allowed_results = retriever.search(request)
                retrieval_ms = round((time.perf_counter() - started_at) * 1000)
                self.assertTrue(allowed_results)
                self.assertLess(retrieval_ms, 1000)
                self.assertTrue(all(result.chunk.meetingId == allowed_meeting_id for result in allowed_results))
                self.assertEqual(
                    retriever.search(
                        RagSearchRequest(
                            query="다온오로라 출시 일정",
                            scope="project",
                            projectId=space_id,
                            allowedMeetingIds=(),
                            sourceTypes=("transcript",),
                            limit=5,
                        )
                    ),
                    [],
                )
        finally:
            server.shutdown()
            server.server_close()
            delete_fixture(dsn, user_id, space_id)

    def test_smoke_runner_validates_local_providers_with_real_pgvector_retrieval(self):
        dsn = os.environ["AI_TEST_DATABASE_URL"]
        suffix = uuid4().hex[:12]
        user_id = f"onprem-smoke-user-{suffix}"
        space_id = f"onprem-smoke-space-{suffix}"
        allowed_meeting_id = f"onprem-smoke-allowed-{suffix}"
        restricted_meeting_id = f"onprem-smoke-restricted-{suffix}"

        try:
            server = ThreadingHTTPServer(("127.0.0.1", 0), OpenAICompatibleMockHandler)
        except PermissionError as error:
            raise unittest.SkipTest("local socket binding is not permitted in this sandbox") from error

        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{server.server_port}/v1"

        try:
            with patch.dict(
                os.environ,
                {
                    "RUN_ONPREM_AI_POC_SMOKE": "true",
                    "AI_TEXT_PROVIDER": "local-openai-compatible",
                    "AI_TEXT_BASE_URL": base_url,
                    "AI_TEXT_API_KEY": "local-token",
                    "AI_TEXT_MODEL": "mock-local-llm",
                    "AI_TEXT_API_STYLE": "chat-completions",
                    "AI_TEXT_STREAM": "true",
                    "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                    "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                    "AI_EMBEDDING_BASE_URL": base_url,
                    "AI_EMBEDDING_API_KEY": "local-token",
                    "AI_EMBEDDING_MODEL": "mock-local-embedding",
                    "AI_EMBEDDING_DIMENSION": "1536",
                    "AI_EMBEDDING_INCLUDE_DIMENSIONS": "false",
                    "AI_VECTOR_DIMENSION": "1536",
                    "AI_DATABASE_URL": dsn,
                    "ONPREM_POC_RAG_QUERY": "다온오로라 출시 일정",
                    "ONPREM_POC_PROJECT_ID": space_id,
                    "ONPREM_POC_ALLOWED_MEETING_IDS": allowed_meeting_id,
                    "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                },
                clear=False,
            ):
                assert_empty_embedding_queue(dsn)
                insert_fixture(dsn, user_id, space_id, allowed_meeting_id, restricted_meeting_id, suffix)

                provider = create_embedding_provider()
                repository = PostgresEmbeddingRepository(dsn)
                worker = EmbeddingWorker(repository, provider)
                self.assertTrue(worker.run_once())
                self.assertTrue(worker.run_once())
                self.assertFalse(worker.run_once())

                metrics = [
                    onprem_poc_smoke.run_provider_probe(),
                    onprem_poc_smoke.run_embedding_probe(),
                    onprem_poc_smoke.run_retrieval_latency_probe(),
                    onprem_poc_smoke.run_scenario("meeting_ai", onprem_poc_smoke.meeting_ai_scenario),
                    onprem_poc_smoke.run_scenario("project_ai", onprem_poc_smoke.project_ai_scenario),
                    onprem_poc_smoke.run_scenario("report", onprem_poc_smoke.report_scenario),
                    onprem_poc_smoke.run_scenario("task", onprem_poc_smoke.task_scenario),
                    onprem_poc_smoke.run_expected_unsupported(
                        "meeting_ai_unsupported",
                        onprem_poc_smoke.meeting_ai_unsupported_scenario,
                    ),
                    onprem_poc_smoke.run_permission_guard(),
                ]
                summary = onprem_poc_smoke.summarize(metrics)
                result = {
                    "config": onprem_poc_smoke.asdict(onprem_poc_smoke.smoke_config()),
                    "summary": onprem_poc_smoke.asdict(summary),
                    "metrics": [onprem_poc_smoke.asdict(metric) for metric in metrics],
                }

                self.assertTrue(summary.ok)
                self.assertTrue(summary.retrievalLatencyMeasured)
                self.assertEqual(onprem_poc_validate.validate_result(result), [])
                retrieval = next(metric for metric in metrics if metric.scenario == "retrieval_latency_probe")
                self.assertGreater(retrieval.sourceCount or 0, 0)
                self.assertLess(retrieval.retrievalLatencyMs or 0, 1000)
        finally:
            server.shutdown()
            server.server_close()
            delete_fixture(dsn, user_id, space_id)


if __name__ == "__main__":
    unittest.main()
