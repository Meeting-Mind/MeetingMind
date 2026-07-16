import os
import json
import unittest
from uuid import uuid4

import psycopg

from app.embedding_worker import EmbeddingWorker
from app.rag import RagChunk, RagSearchRequest, RagSearchResult
from app.repository import PostgresEmbeddingRepository, PostgresRagRetriever


class DeterministicEmbeddingProvider:
    model = "test-embedding"
    dimension = 1536

    def embed(self, texts):
        return [[0.01] * self.dimension for _ in texts]


class RetrievalObservabilityTest(unittest.TestCase):
    def test_search_log_excludes_query_content(self):
        class FakeRepository:
            def hybrid_search(self, request, vector):
                return [
                    RagSearchResult(
                        chunk=RagChunk(
                            chunkId="chunk-1",
                            scope="meeting",
                            projectId=request.projectId,
                            meetingId=request.meetingId,
                            sourceType="transcript",
                            sourceId="segment-1",
                            content="검색 결과 원문",
                            embeddingText="검색 결과 원문",
                        ),
                        score=0.8,
                    )
                ]

        request = RagSearchRequest(
            query="로그에 남으면 안 되는 민감한 질문",
            scope="meeting",
            projectId="space-1",
            meetingId="meeting-1",
            sourceTypes=("transcript",),
        )

        with self.assertLogs("meetingmind.ai.retrieval", level="INFO") as logs:
            PostgresRagRetriever(FakeRepository(), DeterministicEmbeddingProvider()).search(request)

        log_text = "\n".join(logs.output)
        self.assertNotIn(request.query, log_text)
        self.assertNotIn("검색 결과 원문", log_text)
        payload = json.loads(logs.output[0].split("INFO:meetingmind.ai.retrieval:", 1)[1])
        self.assertEqual(payload["event"], "ai_retrieval_completed")
        self.assertEqual(payload["resultCount"], 1)
        self.assertEqual(payload["sourceTypeCount"], 1)


@unittest.skipUnless(os.getenv("AI_TEST_DATABASE_URL"), "AI_TEST_DATABASE_URL is not configured")
class PostgresEmbeddingRepositoryIntegrationTest(unittest.TestCase):
    def test_claims_jobs_and_swaps_only_the_latest_generation(self):
        dsn = os.environ["AI_TEST_DATABASE_URL"]
        suffix = uuid4().hex[:12]
        user_id = f"user-{suffix}"
        space_id = f"space-{suffix}"
        meeting_id = f"meeting-{suffix}"

        with psycopg.connect(dsn) as connection:
            connection.execute(
                "insert into users (id, email, display_name) values (%s, %s, 'Embedding User')",
                (user_id, f"{suffix}@meetingmind.test"),
            )
            connection.execute(
                "insert into spaces (id, name, created_by) values (%s, 'Embedding Space', %s)",
                (space_id, user_id),
            )
            connection.execute(
                """
                insert into meetings (id, space_id, title, scheduled_at)
                values (%s, %s, 'Embedding Meeting', now())
                """,
                (meeting_id, space_id),
            )
            connection.execute(
                """
                insert into meeting_speakers (id, meeting_id, label, display_name)
                values (%s, %s, 'S1', '발화자')
                """,
                (f"speaker-{suffix}", meeting_id),
            )
            for sequence in range(4):
                connection.execute(
                    """
                    insert into transcript_segments (
                        id, meeting_id, speaker_id, speaker_label, speaker_name,
                        start_ms, end_ms, text, source, sequence
                    ) values (%s, %s, %s, 'S1', '발화자', %s, %s, %s, 'stt', %s)
                    """,
                    (
                        f"segment-{suffix}-{sequence}",
                        meeting_id,
                        f"speaker-{suffix}",
                        sequence * 1000,
                        (sequence + 1) * 1000,
                        f"권한 기반 검색 데이터 {sequence}",
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

        repository = PostgresEmbeddingRepository(dsn)
        worker = EmbeddingWorker(repository, DeterministicEmbeddingProvider())
        self.assertTrue(worker.run_once())
        self.assertEqual(active_generations(dsn, meeting_id), [1])

        with psycopg.connect(dsn) as connection:
            insert_job(connection, f"job-{suffix}-2", space_id, meeting_id, 2)
        self.assertTrue(worker.run_once())
        self.assertEqual(active_generations(dsn, meeting_id), [2])

        with psycopg.connect(dsn) as connection:
            insert_job(connection, f"job-{suffix}-3", space_id, meeting_id, 3)
            insert_job(connection, f"job-{suffix}-4", space_id, meeting_id, 4)
        self.assertTrue(worker.run_once())
        self.assertEqual(active_generations(dsn, meeting_id), [2])
        self.assertTrue(worker.run_once())
        self.assertEqual(active_generations(dsn, meeting_id), [4])

        retriever = PostgresRagRetriever(repository, DeterministicEmbeddingProvider())
        meeting_results = retriever.search(
            RagSearchRequest(
                query="권한 기반 검색",
                scope="meeting",
                projectId=space_id,
                meetingId=meeting_id,
                sourceTypes=("transcript",),
                limit=5,
            )
        )
        self.assertEqual(len(meeting_results), 1)
        self.assertEqual(meeting_results[0].chunk.meetingId, meeting_id)
        self.assertGreater(meeting_results[0].score, 0)

        project_results = retriever.search(
            RagSearchRequest(
                query="권한 기반 검색",
                scope="project",
                projectId=space_id,
                allowedMeetingIds=(meeting_id,),
                sourceTypes=("transcript", "projectKnowledge"),
                limit=8,
            )
        )
        self.assertEqual([result.chunk.meetingId for result in project_results], [meeting_id])
        self.assertEqual(
            retriever.search(
                RagSearchRequest(
                    query="권한 기반 검색",
                    scope="project",
                    projectId=space_id,
                    allowedMeetingIds=(),
                    sourceTypes=("transcript",),
                    limit=8,
                )
            ),
            [],
        )
        self.assertEqual(
            retriever.search(
                RagSearchRequest(
                    query="권한 기반 검색",
                    scope="meeting",
                    projectId="another-space",
                    meetingId=meeting_id,
                    sourceTypes=("transcript",),
                    limit=5,
                )
            ),
            [],
        )

        with psycopg.connect(dsn) as connection:
            statuses = connection.execute(
                """
                select generation, status from embedding_jobs
                where meeting_id = %s order by generation
                """,
                (meeting_id,),
            ).fetchall()
            self.assertEqual(statuses, [(1, "COMPLETED"), (2, "COMPLETED"), (3, "COMPLETED"), (4, "COMPLETED")])
            self.assertEqual(
                connection.execute(
                    """
                    select count(*) from chunk_source_segments links
                    join embedding_chunks chunks on chunks.id = links.chunk_id
                    where chunks.meeting_id = %s and chunks.generation = 4
                    """,
                    (meeting_id,),
                ).fetchone()[0],
                4,
            )
            self.assertEqual(
                connection.execute(
                    "select distinct vector_dims(embedding) from embedding_chunks where meeting_id = %s",
                    (meeting_id,),
                ).fetchall(),
                [(1536,)],
            )

        with psycopg.connect(dsn) as connection:
            connection.execute(
                "update meeting_transcripts set purged_at = now() where meeting_id = %s",
                (meeting_id,),
            )

        self.assertEqual(retriever.search(
            RagSearchRequest(
                query="권한 기반 검색",
                scope="meeting",
                projectId=space_id,
                meetingId=meeting_id,
                sourceTypes=("transcript",),
                limit=5,
            )
        ), [])
        self.assertEqual(active_generations(dsn, meeting_id), [])
        with psycopg.connect(dsn) as connection:
            self.assertEqual(
                connection.execute(
                    """
                    select count(*) from chunk_source_segments links
                    join embedding_chunks chunks on chunks.id = links.chunk_id
                    where chunks.meeting_id = %s
                    """,
                    (meeting_id,),
                ).fetchone()[0],
                0,
            )


def insert_job(connection, job_id: str, space_id: str, meeting_id: str, generation: int) -> None:
    connection.execute(
        """
        insert into embedding_jobs (
            id, space_id, meeting_id, generation, trigger_reason, next_attempt_at
        ) values (%s, %s, %s, %s, 'FULL_REINDEX', now())
        """,
        (job_id, space_id, meeting_id, generation),
    )


def active_generations(dsn: str, meeting_id: str) -> list[int]:
    with psycopg.connect(dsn) as connection:
        return [
            row[0]
            for row in connection.execute(
                """
                select distinct generation from embedding_chunks
                where meeting_id = %s and is_active = true order by generation
                """,
                (meeting_id,),
            ).fetchall()
        ]


if __name__ == "__main__":
    unittest.main()
