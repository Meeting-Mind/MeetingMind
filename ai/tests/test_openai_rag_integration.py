import os
import time
import unittest
from uuid import uuid4

import psycopg

from app.config import get_env
from app.embedding_provider import OpenAIEmbeddingProvider
from app.embedding_worker import EmbeddingWorker
from app.rag import RagSearchRequest
from app.repository import PostgresEmbeddingRepository, PostgresRagRetriever


def can_run_openai_rag_integration() -> bool:
    return (
        os.getenv("RUN_OPENAI_RAG_INTEGRATION") == "true"
        and bool(get_env("OPENAI_API_KEY"))
        and bool(get_env("AI_TEST_DATABASE_URL"))
    )


@unittest.skipUnless(
    can_run_openai_rag_integration(),
    "RUN_OPENAI_RAG_INTEGRATION=true, OPENAI_API_KEY, and AI_TEST_DATABASE_URL are required",
)
class OpenAiRagIntegrationTest(unittest.TestCase):
    def test_korean_stt_is_embedded_and_scope_is_enforced(self):
        dsn = get_env("AI_TEST_DATABASE_URL") or ""
        suffix = uuid4().hex[:12]
        user_id = f"openai-rag-user-{suffix}"
        space_id = f"openai-rag-space-{suffix}"
        allowed_meeting_id = f"openai-rag-allowed-{suffix}"
        restricted_meeting_id = f"openai-rag-restricted-{suffix}"
        provider = OpenAIEmbeddingProvider.from_environment()
        self.assertEqual(provider.dimension, 1536, "PostgreSQL vector schema is fixed at 1536 dimensions")

        try:
            assert_empty_embedding_queue(dsn)
            insert_fixture(dsn, user_id, space_id, allowed_meeting_id, restricted_meeting_id, suffix)
            repository = PostgresEmbeddingRepository(dsn)
            worker = EmbeddingWorker(repository, provider)
            self.assertTrue(worker.run_once())
            self.assertTrue(worker.run_once())
            self.assertFalse(worker.run_once())
            self.assertEqual(
                embedding_job_statuses(dsn, space_id),
                ["COMPLETED", "COMPLETED"],
                "actual provider jobs must complete before retrieval is evaluated",
            )

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

            retriever = PostgresRagRetriever(repository, provider)
            query = "다온오로라 출시 일정은 언제인가요"
            allowed_results = retriever.search(
                RagSearchRequest(
                    query=query,
                    scope="project",
                    projectId=space_id,
                    allowedMeetingIds=(allowed_meeting_id,),
                    sourceTypes=("transcript",),
                    limit=5,
                )
            )
            self.assertTrue(allowed_results)
            self.assertTrue(all(result.chunk.meetingId == allowed_meeting_id for result in allowed_results))
            self.assertTrue(any("다온오로라" in result.chunk.content for result in allowed_results))

            self.assertEqual(
                retriever.search(
                    RagSearchRequest(
                        query=query,
                        scope="project",
                        projectId=space_id,
                        allowedMeetingIds=(),
                        sourceTypes=("transcript",),
                        limit=5,
                    )
                ),
                [],
            )
            self.assertEqual(
                retriever.search(
                    RagSearchRequest(
                        query=query,
                        scope="meeting",
                        projectId="other-space",
                        meetingId=allowed_meeting_id,
                        sourceTypes=("transcript",),
                        limit=5,
                    )
                ),
                [],
            )

            query_vector = provider.embed([query])[0]
            latencies = []
            request = RagSearchRequest(
                query=query,
                scope="project",
                projectId=space_id,
                allowedMeetingIds=(allowed_meeting_id,),
                sourceTypes=("transcript",),
                limit=5,
            )
            for _ in range(100):
                started_at = time.perf_counter()
                results = repository.hybrid_search(request, query_vector)
                latencies.append(time.perf_counter() - started_at)
                self.assertTrue(results)
                self.assertTrue(all(result.chunk.meetingId == allowed_meeting_id for result in results))

            p95_seconds = sorted(latencies)[94]
            print(f"OpenAI-backed PostgreSQL retrieval p95: {p95_seconds * 1_000:.2f} ms")
            self.assertLess(p95_seconds, 1.0, f"retrieval p95 exceeded 1 second: {p95_seconds:.3f}s")
        finally:
            delete_fixture(dsn, user_id, space_id)


def insert_fixture(
    dsn: str,
    user_id: str,
    space_id: str,
    allowed_meeting_id: str,
    restricted_meeting_id: str,
    suffix: str,
) -> None:
    meetings = (
        (
            allowed_meeting_id,
            "출시 일정 검토 회의",
            (
                "다온오로라 출시일은 9월 18일로 확정했습니다.",
                "모바일 앱 QA는 9월 12일까지 완료합니다.",
                "출시 공지는 9월 17일 오후에 예약합니다.",
                "담당자는 배포 후 장애 대응 채널을 열어둡니다.",
            ),
        ),
        (
            restricted_meeting_id,
            "접근 제한 회의",
            (
                "비공개 전략의 코드명은 다온오로라 비공개 계획입니다.",
                "접근 제한 회의의 세부 일정은 외부에 공유하지 않습니다.",
                "참여자만 비공개 전략 문서를 열람할 수 있습니다.",
            ),
        ),
    )
    with psycopg.connect(dsn) as connection:
        connection.execute(
            "insert into users (id, email, display_name) values (%s, %s, 'OpenAI RAG User')",
            (user_id, f"{suffix}@meetingmind.test"),
        )
        connection.execute(
            "insert into spaces (id, name, created_by) values (%s, 'OpenAI RAG Space', %s)",
            (space_id, user_id),
        )
        for meeting_id, title, segments in meetings:
            speaker_id = f"speaker-{meeting_id}"
            connection.execute(
                """
                insert into meetings (id, space_id, title, scheduled_at, scheduled_end_at)
                values (%s, %s, %s, now(), now() + interval '1 hour')
                """,
                (meeting_id, space_id, title),
            )
            connection.execute(
                """
                insert into meeting_speakers (id, meeting_id, label, display_name)
                values (%s, %s, 'S1', '발화자')
                """,
                (speaker_id, meeting_id),
            )
            for sequence, text in enumerate(segments):
                connection.execute(
                    """
                    insert into transcript_segments (
                        id, meeting_id, speaker_id, speaker_label, speaker_name,
                        start_ms, end_ms, text, source, sequence
                    ) values (%s, %s, %s, 'S1', '발화자', %s, %s, %s, 'stt', %s)
                    """,
                    (
                        f"segment-{meeting_id}-{sequence}",
                        meeting_id,
                        speaker_id,
                        sequence * 1_000,
                        (sequence + 1) * 1_000,
                        text,
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


def assert_empty_embedding_queue(dsn: str) -> None:
    with psycopg.connect(dsn) as connection:
        queued_jobs = connection.execute("select count(*) from embedding_jobs").fetchone()[0]
    if queued_jobs:
        raise AssertionError(
            f"AI_TEST_DATABASE_URL must point to an empty evaluation database; found {queued_jobs} embedding jobs"
        )


def embedding_job_statuses(dsn: str, space_id: str) -> list[str]:
    with psycopg.connect(dsn) as connection:
        return [
            row[0]
            for row in connection.execute(
                "select status from embedding_jobs where space_id = %s order by created_at, id",
                (space_id,),
            ).fetchall()
        ]


def delete_fixture(dsn: str, user_id: str, space_id: str) -> None:
    with psycopg.connect(dsn) as connection:
        connection.execute(
            """
            delete from chunk_source_segments
            where chunk_id in (select id from embedding_chunks where space_id = %s)
            """,
            (space_id,),
        )
        connection.execute("delete from embedding_chunks where space_id = %s", (space_id,))
        connection.execute("delete from embedding_jobs where space_id = %s", (space_id,))
        connection.execute("delete from meeting_transcripts where meeting_id in (select id from meetings where space_id = %s)", (space_id,))
        connection.execute("delete from transcript_segments where meeting_id in (select id from meetings where space_id = %s)", (space_id,))
        connection.execute("delete from meeting_speakers where meeting_id in (select id from meetings where space_id = %s)", (space_id,))
        connection.execute("delete from meetings where space_id = %s", (space_id,))
        connection.execute("delete from spaces where id = %s", (space_id,))
        connection.execute("delete from users where id = %s", (user_id,))


if __name__ == "__main__":
    unittest.main()
