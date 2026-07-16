import os
import time
import unittest
from uuid import uuid4

import psycopg

from app.embedding_worker import EmbeddingWorker
from app.rag import RagSearchRequest
from app.repository import PostgresEmbeddingRepository, PostgresRagRetriever


class DeterministicEmbeddingProvider:
    model = "test-embedding"
    dimension = 1536

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [[0.01] * self.dimension for _ in texts]


@unittest.skipUnless(os.getenv("AI_TEST_DATABASE_URL"), "AI_TEST_DATABASE_URL is not configured")
class SttRagPerformanceIntegrationTest(unittest.TestCase):
    def test_completed_transcript_is_retrievable_within_local_p95_target(self):
        dsn = os.environ["AI_TEST_DATABASE_URL"]
        suffix = uuid4().hex[:12]
        user_id = f"stt-rag-user-{suffix}"
        space_id = f"stt-rag-space-{suffix}"
        meeting_id = f"stt-rag-meeting-{suffix}"
        speaker_id = f"stt-rag-speaker-{suffix}"

        with psycopg.connect(dsn) as connection:
            connection.execute(
                "insert into users (id, email, display_name) values (%s, %s, 'STT RAG User')",
                (user_id, f"{suffix}@meetingmind.test"),
            )
            connection.execute(
                "insert into spaces (id, name, created_by) values (%s, 'STT RAG Space', %s)",
                (space_id, user_id),
            )
            connection.execute(
                """
                insert into meetings (id, space_id, title, scheduled_at)
                values (%s, %s, 'STT RAG Meeting', now())
                """,
                (meeting_id, space_id),
            )
            connection.execute(
                """
                insert into meeting_speakers (id, meeting_id, label, display_name)
                values (%s, %s, 'S1', '발화자')
                """,
                (speaker_id, meeting_id),
            )
            for sequence in range(200):
                connection.execute(
                    """
                    insert into transcript_segments (
                        id, meeting_id, speaker_id, speaker_label, speaker_name,
                        start_ms, end_ms, text, source, sequence
                    ) values (%s, %s, %s, 'S1', '발화자', %s, %s, %s, 'stt', %s)
                    """,
                    (
                        f"stt-rag-segment-{suffix}-{sequence}",
                        meeting_id,
                        speaker_id,
                        sequence * 1_000,
                        (sequence + 1) * 1_000,
                        f"{sequence}번째 STT 다이얼로그입니다. 권한 기반 RAG 정합성을 검증합니다.",
                        sequence,
                    ),
                )
            connection.execute(
                """
                insert into meeting_transcripts (meeting_id, status, started_at, completed_at)
                values (%s, 'COMPLETED', now(), now())
                """,
                (meeting_id,),
            )
            job_count = connection.execute(
                """
                select count(*) from embedding_jobs
                where meeting_id = %s and trigger_reason = 'TRANSCRIPT_COMPLETED'
                """,
                (meeting_id,),
            ).fetchone()[0]
            self.assertEqual(job_count, 1)

        repository = PostgresEmbeddingRepository(dsn)
        provider = DeterministicEmbeddingProvider()
        worker = EmbeddingWorker(repository, provider)
        self.assertTrue(worker.run_once())

        retriever = PostgresRagRetriever(repository, provider)
        request = RagSearchRequest(
            query="권한 기반 RAG 정합성",
            scope="meeting",
            projectId=space_id,
            meetingId=meeting_id,
            sourceTypes=("transcript",),
            limit=5,
        )
        latencies_seconds: list[float] = []
        for _ in range(100):
            started_at = time.perf_counter()
            results = retriever.search(request)
            latencies_seconds.append(time.perf_counter() - started_at)
            self.assertGreater(len(results), 0)
            self.assertTrue(all(result.chunk.meetingId == meeting_id for result in results))
            self.assertTrue(all(result.chunk.projectId == space_id for result in results))

        p95_seconds = sorted(latencies_seconds)[94]
        print(f"local deterministic STT RAG retrieval p95: {p95_seconds * 1_000:.2f} ms")
        self.assertLess(
            p95_seconds,
            1.0,
            f"local deterministic retrieval p95 exceeded 1 second: {p95_seconds:.3f}s",
        )


if __name__ == "__main__":
    unittest.main()
