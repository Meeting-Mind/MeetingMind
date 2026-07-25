from collections.abc import Callable
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from hashlib import sha256
import json
import logging
from time import perf_counter
from typing import Any, Protocol

import psycopg
from psycopg import sql
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

from .embedding_provider import EmbeddingProvider, EmbeddingProviderError
from .observability import elapsed_ms, log_event, record_embedding_queue, record_retrieval
from .rag import (
    RagBuildRequest,
    RagChunk,
    RagTextItem,
    RagSearchRequest,
    RagSearchResult,
    TranscriptSegment,
    build_rag_chunks,
    build_text_item_chunks,
)


class RetrievalUnavailableError(RuntimeError):
    pass


@dataclass(frozen=True)
class KnowledgeGraphNode:
    id: str
    source_type: str
    title: str
    source_meeting_id: str | None


@dataclass(frozen=True)
class KnowledgeGraphEdge:
    from_id: str
    to_id: str
    similarity: float


LOGGER = logging.getLogger("meetingmind.ai.retrieval")


@dataclass(frozen=True)
class EmbeddingJob:
    id: str
    space_id: str
    project_knowledge_id: str | None
    meeting_id: str | None
    generation: int
    attempt_count: int
    content_hash: str | None


@dataclass(frozen=True)
class EmbeddingSnapshot:
    chunks: tuple[RagChunk, ...]
    content_hash: str


@dataclass(frozen=True)
class EmbeddingQueueMetrics:
    pending_count: int
    processing_count: int
    failed_count: int
    oldest_pending_age_seconds: int


class EmbeddingJobRepository(Protocol):
    def claim_job(self, lease_seconds: int) -> EmbeddingJob | None:
        ...

    def load_snapshot(self, job: EmbeddingJob) -> EmbeddingSnapshot | None:
        ...

    def complete_job(
        self,
        job: EmbeddingJob,
        snapshot: EmbeddingSnapshot,
        vectors: list[list[float]],
        *,
        model: str,
        dimension: int,
    ) -> bool:
        ...

    def record_failure(
        self,
        job: EmbeddingJob,
        failure_code: str,
        retry_delay_seconds: int | None,
        failure_detail: str | None = None,
    ) -> None:
        ...

    def queue_metrics(self) -> EmbeddingQueueMetrics:
        ...


class PostgresEmbeddingRepository:
    def __init__(self, dsn: str, *, connect: Callable[..., Any] = psycopg.connect):
        if not dsn:
            raise ValueError("AI_DATABASE_URL is required")
        self._dsn = dsn
        self._connect = connect

    def claim_job(self, lease_seconds: int) -> EmbeddingJob | None:
        with self._connect(self._dsn, row_factory=dict_row) as connection:
            row = connection.execute(
                """
                with candidate as (
                    select id
                    from embedding_jobs
                    where attempt_count < 4
                      and (
                          (status = 'PENDING' and next_attempt_at <= now())
                          or (status = 'PROCESSING' and lease_expires_at <= now())
                      )
                    order by next_attempt_at, created_at, id
                    for update skip locked
                    limit 1
                )
                update embedding_jobs job
                set status = 'PROCESSING',
                    attempt_count = job.attempt_count + 1,
                    started_at = coalesce(job.started_at, now()),
                    completed_at = null,
                    failure_code = null,
                    lease_expires_at = now() + (%s * interval '1 second')
                from candidate
                where job.id = candidate.id
                returning job.id, job.space_id, job.project_knowledge_id, job.meeting_id,
                          job.generation, job.attempt_count, job.content_hash
                """,
                (lease_seconds,),
            ).fetchone()
            if row is None:
                return None
            if row["project_knowledge_id"]:
                connection.execute(
                    """
                    update project_knowledge
                    set embedding_status = 'PROCESSING', embedding_job_id = %s, updated_at = now()
                    where id = %s
                    """,
                    (row["id"], row["project_knowledge_id"]),
                )
            return map_embedding_job(row)

    def queue_metrics(self) -> EmbeddingQueueMetrics:
        with self._connect(self._dsn, row_factory=dict_row) as connection:
            row = connection.execute(
                """
                select
                    count(*) filter (where status = 'PENDING') as pending_count,
                    count(*) filter (where status = 'PROCESSING') as processing_count,
                    count(*) filter (where status = 'FAILED') as failed_count,
                    coalesce(
                        extract(epoch from (now() - min(created_at) filter (where status = 'PENDING'))),
                        0
                    )::bigint as oldest_pending_age_seconds
                from embedding_jobs
                """
            ).fetchone()
        metrics = EmbeddingQueueMetrics(
            pending_count=int(row["pending_count"]),
            processing_count=int(row["processing_count"]),
            failed_count=int(row["failed_count"]),
            oldest_pending_age_seconds=max(0, int(row["oldest_pending_age_seconds"])),
        )
        record_embedding_queue(
            pending_count=metrics.pending_count,
            processing_count=metrics.processing_count,
            failed_count=metrics.failed_count,
        )
        return metrics

    def load_snapshot(self, job: EmbeddingJob) -> EmbeddingSnapshot | None:
        with self._connect(self._dsn, row_factory=dict_row) as connection:
            if job.project_knowledge_id:
                chunks = self._load_project_knowledge_chunks(connection, job)
            else:
                chunks = self._load_meeting_chunks(connection, job)
        if not chunks:
            return None
        return EmbeddingSnapshot(chunks=tuple(chunks), content_hash=hash_chunks(chunks))

    def complete_job(
        self,
        job: EmbeddingJob,
        snapshot: EmbeddingSnapshot,
        vectors: list[list[float]],
        *,
        model: str,
        dimension: int,
    ) -> bool:
        if len(snapshot.chunks) != len(vectors):
            raise ValueError("chunk and embedding counts differ")
        if any(len(vector) != dimension for vector in vectors):
            raise ValueError("embedding dimension does not match the configured dimension")

        with self._connect(self._dsn, row_factory=dict_row) as connection:
            current = connection.execute(
                "select * from embedding_jobs where id = %s for update",
                (job.id,),
            ).fetchone()
            if current is None or current["status"] != "PROCESSING":
                return False

            if job.project_knowledge_id:
                latest_row = connection.execute(
                    """
                    select max(generation) as latest_generation
                    from embedding_jobs
                    where project_knowledge_id = %s
                    """,
                    (job.project_knowledge_id,),
                ).fetchone()
                source_column = "project_knowledge_id"
                source_id = job.project_knowledge_id
            else:
                latest_row = connection.execute(
                    """
                    select max(generation) as latest_generation
                    from embedding_jobs
                    where meeting_id = %s
                    """,
                    (job.meeting_id,),
                ).fetchone()
                source_column = "meeting_id"
                source_id = job.meeting_id

            latest_generation = latest_row["latest_generation"]
            if latest_generation != job.generation:
                self._mark_completed(connection, job, model, dimension, snapshot.content_hash)
                return False

            connection.execute(
                sql.SQL("""
                update embedding_chunks
                set is_active = false, replaced_at = now()
                where is_active = true and {} = %s
                """).format(sql.Identifier(source_column)),
                (source_id,),
            )
            for chunk, vector in zip(snapshot.chunks, vectors, strict=True):
                self._insert_chunk(connection, job, chunk, vector)

            self._mark_completed(connection, job, model, dimension, snapshot.content_hash)
            if job.project_knowledge_id:
                connection.execute(
                    """
                    update project_knowledge
                    set embedding_status = 'COMPLETED', embedding_job_id = %s, updated_at = now()
                    where id = %s
                    """,
                    (job.id, job.project_knowledge_id),
                )
            return True

    def record_failure(
        self,
        job: EmbeddingJob,
        failure_code: str,
        retry_delay_seconds: int | None,
        failure_detail: str | None = None,
    ) -> None:
        with self._connect(self._dsn) as connection:
            if retry_delay_seconds is None:
                connection.execute(
                    """
                    update embedding_jobs
                    set status = 'FAILED', failure_code = %s, failure_detail = %s,
                        completed_at = now(), lease_expires_at = null
                    where id = %s and status = 'PROCESSING'
                    """,
                    (failure_code, failure_detail, job.id),
                )
                knowledge_status = "FAILED"
            else:
                # 재시도로 되돌릴 때는 failure_code를 지운다. failure_detail도 함께 지워야
                # check 제약(detail이 있으면 code도 있어야 함)을 만족한다.
                connection.execute(
                    """
                    update embedding_jobs
                    set status = 'PENDING', started_at = null, completed_at = null, failure_code = null,
                        failure_detail = null,
                        next_attempt_at = now() + (%s * interval '1 second'), lease_expires_at = null
                    where id = %s and status = 'PROCESSING'
                    """,
                    (retry_delay_seconds, job.id),
                )
                knowledge_status = "PENDING"
            if job.project_knowledge_id:
                connection.execute(
                    """
                    update project_knowledge
                    set embedding_status = %s, embedding_job_id = %s, updated_at = now()
                    where id = %s
                    """,
                    (knowledge_status, job.id, job.project_knowledge_id),
                )

    def hybrid_search(
        self,
        request: RagSearchRequest,
        query_vector: list[float],
        *,
        candidate_limit: int = 20,
    ) -> list[RagSearchResult]:
        if request.scope == "meeting" and not request.meetingId:
            return []

        scope_sql, scope_params = search_scope_sql(request)
        source_sql = sql.SQL("")
        source_params: list[Any] = []
        if request.sourceTypes:
            source_sql = sql.SQL(" and chunks.source_type = any(%s::varchar[]) ")
            source_params.append(list(request.sourceTypes))

        select_columns = sql.SQL("""
            select chunks.id, chunks.project_id, chunks.meeting_id, chunks.scope,
                   chunks.source_type, chunks.source_id, chunks.title, chunks.speaker_names,
                   chunks.start_ms, chunks.end_ms, chunks.content, chunks.embedding_text,
                   chunks.metadata,
        """)
        eligible_from = sql.SQL("""
            from embedding_chunks chunks
            join embedding_jobs jobs on jobs.id = chunks.embedding_job_id
            left join project_knowledge knowledge on knowledge.id = chunks.project_knowledge_id
            where chunks.is_active = true
              and jobs.status = 'COMPLETED'
              and chunks.embedding is not null
              and (
                  chunks.source_type <> 'projectKnowledge'
                  or (knowledge.status = 'PUBLISHED' and knowledge.deleted_at is null)
              )
              and (
                  chunks.source_type <> 'transcript'
                  or exists (
                      select 1 from meeting_transcripts transcripts
                      where transcripts.meeting_id = chunks.meeting_id
                        and transcripts.status = 'COMPLETED'
                        and transcripts.purged_at is null
                  )
              )
              and
        """)
        vector_query = sql.Composed(
            [
                select_columns,
                sql.SQL("1 - (chunks.embedding <=> %s::vector) as retrieval_score "),
                eligible_from,
                scope_sql,
                source_sql,
                sql.SQL(" order by chunks.embedding <=> %s::vector limit %s"),
            ]
        )
        trigram_query = sql.Composed(
            [
                select_columns,
                sql.SQL("similarity(chunks.embedding_text, %s) as retrieval_score "),
                eligible_from,
                scope_sql,
                source_sql,
                sql.SQL(" and chunks.embedding_text %% %s order by retrieval_score desc, chunks.id limit %s"),
            ]
        )
        vector_value = vector_literal(query_vector)

        try:
            with self._connect(self._dsn, row_factory=dict_row) as connection:
                vector_rows = connection.execute(
                    vector_query,
                    [vector_value, *scope_params, *source_params, vector_value, candidate_limit],
                ).fetchall()
                trigram_rows = connection.execute(
                    trigram_query,
                    [request.query, *scope_params, *source_params, request.query, candidate_limit],
                ).fetchall()
        except psycopg.Error as error:
            raise RetrievalUnavailableError("retrieval database unavailable") from error

        return merge_hybrid_candidates(vector_rows, trigram_rows, request.limit)

    def knowledge_graph(
        self,
        project_id: str,
        allowed_meeting_ids: list[str],
        *,
        similarity_threshold: float = 0.78,
        node_limit: int = 80,
        edge_limit: int = 160,
    ) -> tuple[list[KnowledgeGraphNode], list[KnowledgeGraphEdge]]:
        if not project_id:
            return [], []

        # Nodes are source-level centroids, never individual chunks. The Core-provided
        # meeting allowlist is part of the SQL predicate so unauthorized chunks cannot
        # participate in either edges or cluster labels.
        centroid_cte = """
            with eligible as (
                select
                    case
                        when chunks.project_knowledge_id is not null then 'knowledge:' || chunks.project_knowledge_id
                        else chunks.source_type || ':' || chunks.source_id
                    end as node_id,
                    chunks.source_type,
                    chunks.meeting_id as source_meeting_id,
                    case
                        when chunks.source_type = 'transcript' then
                            'Transcript ' || row_number() over (
                                partition by chunks.meeting_id, chunks.source_type
                                order by chunks.start_ms nulls last, chunks.id
                            )
                        when chunks.source_type = 'meetingSummary' then 'Meeting Summary'
                        else chunks.title
                    end as title,
                    chunks.embedding
                from embedding_chunks chunks
                join embedding_jobs jobs on jobs.id = chunks.embedding_job_id
                left join project_knowledge knowledge on knowledge.id = chunks.project_knowledge_id
                where chunks.space_id = %s
                  and chunks.is_active = true
                  and jobs.status = 'COMPLETED'
                  and chunks.embedding is not null
                  and chunks.source_type in ('projectKnowledge', 'meetingSummary', 'decision', 'actionItem', 'report', 'glossary')
                  and (
                      chunks.source_type <> 'projectKnowledge'
                      or (knowledge.status = 'PUBLISHED' and knowledge.deleted_at is null)
                  )
                  and (
                      chunks.source_type <> 'transcript'
                      or exists (
                          select 1
                          from meeting_transcripts transcripts
                          where transcripts.meeting_id = chunks.meeting_id
                            and transcripts.status = 'COMPLETED'
                            and transcripts.purged_at is null
                      )
                  )
                  and (
                      chunks.project_knowledge_id is not null
                      or chunks.meeting_id = any(%s::varchar[])
                  )
            ), centroids as (
                select node_id, source_type, source_meeting_id, min(title) as title, avg(embedding) as centroid
                from eligible
                group by node_id, source_type, source_meeting_id
                order by node_id
                limit %s
            )
        """
        try:
            with self._connect(self._dsn, row_factory=dict_row) as connection:
                node_rows = connection.execute(
                    centroid_cte + "select node_id, source_type, source_meeting_id, title from centroids order by node_id",
                    (project_id, allowed_meeting_ids, node_limit),
                ).fetchall()
                edge_rows = connection.execute(
                    centroid_cte + """
                        select left_node.node_id as from_id,
                               right_node.node_id as to_id,
                               1 - (left_node.centroid <=> right_node.centroid) as similarity
                        from centroids left_node
                        join centroids right_node on left_node.node_id < right_node.node_id
                        where 1 - (left_node.centroid <=> right_node.centroid) >= %s
                        order by similarity desc, from_id, to_id
                        limit %s
                    """,
                    (project_id, allowed_meeting_ids, node_limit, similarity_threshold, edge_limit),
                ).fetchall()
        except psycopg.Error as error:
            raise RetrievalUnavailableError("knowledge graph database unavailable") from error

        nodes = [
            KnowledgeGraphNode(
                id=str(row["node_id"]),
                source_type=str(row["source_type"]),
                title=str(row["title"] or "Untitled source"),
                source_meeting_id=row["source_meeting_id"],
            )
            for row in node_rows
        ]
        edges = [
            KnowledgeGraphEdge(
                from_id=str(row["from_id"]),
                to_id=str(row["to_id"]),
                similarity=float(row["similarity"]),
            )
            for row in edge_rows
        ]
        return nodes, edges

    def _load_project_knowledge_chunks(self, connection: Any, job: EmbeddingJob) -> list[RagChunk]:
        row = connection.execute(
            """
            select id, space_id, title, content
            from project_knowledge
            where id = %s and space_id = %s and status = 'PUBLISHED' and deleted_at is null
            """,
            (job.project_knowledge_id, job.space_id),
        ).fetchone()
        if row is None:
            return []
        return build_rag_chunks(
            RagBuildRequest(
                projectId=row["space_id"],
                projectKnowledge=(
                    RagTextItem(
                        id=row["id"],
                        sourceType="projectKnowledge",
                        title=row["title"],
                        text=row["content"],
                        metadata={"approvedState": "published"},
                    ),
                ),
            )
        )

    def _load_meeting_chunks(self, connection: Any, job: EmbeddingJob) -> list[RagChunk]:
        meeting = connection.execute(
            "select id, space_id, title from meetings where id = %s and space_id = %s",
            (job.meeting_id, job.space_id),
        ).fetchone()
        if meeting is None:
            return []

        segments = tuple(
            TranscriptSegment(
                id=row["id"],
                meetingId=row["meeting_id"],
                speakerId=row["speaker_id"],
                speakerLabel=row["speaker_label"],
                speakerName=row["speaker_name"],
                startMs=row["start_ms"],
                endMs=row["end_ms"],
                text=row["text"],
                sequence=row["sequence"],
            )
            for row in connection.execute(
                """
                select segments.id, segments.meeting_id, segments.speaker_id,
                       speakers.label as speaker_label,
                       coalesce(speakers.display_name, segments.speaker_name) as speaker_name,
                       segments.start_ms, segments.end_ms, segments.text, segments.sequence
                from transcript_segments segments
                join meeting_speakers speakers on speakers.id = segments.speaker_id
                where segments.meeting_id = %s
                  and exists (
                      select 1 from meeting_transcripts transcripts
                      where transcripts.meeting_id = segments.meeting_id
                        and transcripts.status = 'COMPLETED'
                        and transcripts.purged_at is null
                  )
                order by segments.sequence
                """,
                (job.meeting_id,),
            ).fetchall()
        )

        report = connection.execute(
            """
            select id, title, summary, markdown
            from meeting_reports
            where meeting_id = %s and status = 'CONFIRMED' and is_current = true
            """,
            (job.meeting_id,),
        ).fetchone()
        decisions: tuple[RagTextItem, ...] = ()
        actions: tuple[RagTextItem, ...] = ()
        if report:
            decisions = tuple(
                RagTextItem(
                    id=row["id"], sourceType="decision", title=row["title"], text=row["rationale"] or row["title"]
                )
                for row in connection.execute(
                    """
                    select id, title, rationale
                    from report_decisions
                    where report_id = %s
                    order by decision_order
                    """,
                    (report["id"],),
                ).fetchall()
            )
            actions = tuple(
                RagTextItem(
                    id=row["id"],
                    sourceType="actionItem",
                    title=row["title"],
                    text=" / ".join(
                        value
                        for value in (row["assignee_name"], str(row["due_date"]) if row["due_date"] else None)
                        if value
                    ) or row["title"],
                )
                for row in connection.execute(
                    """
                    select id, title, assignee_name, due_date
                    from report_action_items
                    where report_id = %s
                    order by item_order
                    """,
                    (report["id"],),
                ).fetchall()
            )

        request = RagBuildRequest(
            projectId=meeting["space_id"],
            meetingId=meeting["id"],
            meetingTitle=meeting["title"],
            transcriptSegments=segments,
            decisions=decisions,
            actions=actions,
        )
        chunks = build_rag_chunks(request)
        if report:
            report_text = report["markdown"] or report["summary"]
            chunks.extend(
                build_text_item_chunks(
                    request,
                    (
                        RagTextItem(
                            id=report["id"],
                            sourceType="report",
                            title=report["title"],
                            text=report_text,
                            metadata={"approvedState": "confirmed"},
                        ),
                    ),
                    source_type="report",
                    scope="meeting",
                )
            )
        return chunks

    def _insert_chunk(
        self,
        connection: Any,
        job: EmbeddingJob,
        chunk: RagChunk,
        vector: list[float],
    ) -> None:
        chunk_id = sha256(f"{job.id}:{chunk.chunkId}".encode()).hexdigest()
        connection.execute(
            """
            insert into embedding_chunks (
                id, space_id, project_id, meeting_id, project_knowledge_id, scope, source_type,
                source_id, title, speaker_names, start_ms, end_ms, content, embedding_text,
                metadata, embedding, embedding_job_id, generation, is_active
            ) values (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s::vector, %s, %s, true
            )
            """,
            (
                chunk_id,
                job.space_id,
                chunk.projectId,
                chunk.meetingId,
                job.project_knowledge_id,
                chunk.scope,
                chunk.sourceType,
                chunk.sourceId,
                chunk.title or chunk.sourceType,
                Jsonb(list(chunk.speakerNames)),
                chunk.startMs,
                chunk.endMs,
                chunk.content,
                chunk.embeddingText,
                Jsonb(chunk.metadata),
                vector_literal(vector),
                job.id,
                job.generation,
            ),
        )
        for order, segment_id in enumerate(chunk.sourceSegmentIds):
            link_id = sha256(f"{chunk_id}:{segment_id}".encode()).hexdigest()
            connection.execute(
                """
                insert into chunk_source_segments (id, chunk_id, segment_id, segment_order)
                values (%s, %s, %s, %s)
                """,
                (link_id, chunk_id, segment_id, order),
            )

    def _mark_completed(
        self,
        connection: Any,
        job: EmbeddingJob,
        model: str,
        dimension: int,
        content_hash: str,
    ) -> None:
        connection.execute(
            """
            update embedding_jobs
            set status = 'COMPLETED', model = %s, dimension = %s, content_hash = %s,
                completed_at = now(), failure_code = null, lease_expires_at = null
            where id = %s and status = 'PROCESSING'
            """,
            (model, dimension, content_hash, job.id),
        )


def map_embedding_job(row: dict[str, Any]) -> EmbeddingJob:
    return EmbeddingJob(
        id=row["id"],
        space_id=row["space_id"],
        project_knowledge_id=row["project_knowledge_id"],
        meeting_id=row["meeting_id"],
        generation=row["generation"],
        attempt_count=row["attempt_count"],
        content_hash=row["content_hash"],
    )


def hash_chunks(chunks: list[RagChunk]) -> str:
    payload = json.dumps(
        [asdict(chunk) for chunk in chunks],
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return sha256(payload.encode("utf-8")).hexdigest()


def vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(format(value, ".12g") for value in vector) + "]"


class PostgresRagRetriever:
    def __init__(self, repository: PostgresEmbeddingRepository, provider: EmbeddingProvider):
        self._repository = repository
        self._provider = provider

    def search(self, request: RagSearchRequest) -> list[RagSearchResult]:
        started_at = perf_counter()
        if not request.query.strip():
            self._log_search("ai_retrieval_completed", request, started_at, result_count=0, source_type_count=0)
            return []
        try:
            vectors = self._provider.embed([request.query])
            if len(vectors) != 1 or len(vectors[0]) != self._provider.dimension:
                raise RetrievalUnavailableError("query embedding dimension mismatch")
            results = self._repository.hybrid_search(request, vectors[0])
        except (EmbeddingProviderError, RetrievalUnavailableError) as error:
            self._log_search("ai_retrieval_failed", request, started_at, error_type=type(error).__name__)
            record_retrieval(request.scope, elapsed_ms(started_at), None, outcome="failed")
            if isinstance(error, RetrievalUnavailableError):
                raise
            raise RetrievalUnavailableError("query embedding unavailable") from error
        except Exception as error:
            self._log_search("ai_retrieval_failed", request, started_at, error_type=type(error).__name__)
            record_retrieval(request.scope, elapsed_ms(started_at), None, outcome="failed")
            raise

        self._log_search(
            "ai_retrieval_completed",
            request,
            started_at,
            result_count=len(results),
            source_type_count=len({result.chunk.sourceType for result in results}),
        )
        record_retrieval(request.scope, elapsed_ms(started_at), len(results), outcome="completed")
        return results

    @staticmethod
    def _log_search(
        event: str,
        request: RagSearchRequest,
        started_at: float,
        *,
        result_count: int | None = None,
        source_type_count: int | None = None,
        error_type: str | None = None,
    ) -> None:
        level = logging.WARNING if error_type else logging.INFO
        log_event(
            LOGGER,
            event,
            level=level,
            scope=request.scope,
            durationMs=elapsed_ms(started_at),
            resultCount=result_count,
            sourceTypeCount=source_type_count,
            allowedMeetingCount=len(request.allowedMeetingIds),
            errorType=error_type,
        )


def search_scope_sql(request: RagSearchRequest) -> tuple[sql.Composed, list[Any]]:
    if request.scope == "meeting":
        return (
            sql.SQL("chunks.space_id = %s and chunks.scope = 'meeting' and chunks.meeting_id = %s"),
            [request.projectId, request.meetingId],
        )
    return (
        sql.SQL("""
            chunks.space_id = %s
            and (
                (chunks.scope = 'project' and chunks.source_type = 'projectKnowledge')
                or (chunks.scope = 'meeting' and chunks.meeting_id = any(%s::varchar[]))
            )
        """),
        [request.projectId, list(request.allowedMeetingIds)],
    )


def merge_hybrid_candidates(
    vector_rows: list[dict[str, Any]],
    trigram_rows: list[dict[str, Any]],
    limit: int,
) -> list[RagSearchResult]:
    candidates: dict[str, dict[str, Any]] = {}
    for rank, row in enumerate(vector_rows, start=1):
        candidate = candidates.setdefault(row["id"], {"row": row, "rrf": 0.0, "relevance": 0.0})
        candidate["rrf"] += 1.0 / (60 + rank)
        candidate["relevance"] = max(candidate["relevance"], normalized_score(row["retrieval_score"]))
    for rank, row in enumerate(trigram_rows, start=1):
        candidate = candidates.setdefault(row["id"], {"row": row, "rrf": 0.0, "relevance": 0.0})
        candidate["rrf"] += 1.0 / (60 + rank)
        candidate["relevance"] = max(candidate["relevance"], normalized_score(row["retrieval_score"]))

    ranked = sorted(
        candidates.values(),
        key=lambda item: (-item["rrf"], -item["relevance"], item["row"]["id"]),
    )
    return [
        RagSearchResult(chunk=row_to_rag_chunk(item["row"]), score=item["relevance"])
        for item in ranked[: max(0, limit)]
    ]


def row_to_rag_chunk(row: dict[str, Any]) -> RagChunk:
    return RagChunk(
        chunkId=row["id"],
        scope=row["scope"],
        projectId=row["project_id"],
        meetingId=row["meeting_id"],
        sourceType=row["source_type"],
        sourceId=row["source_id"],
        title=row["title"],
        speakerNames=tuple(row["speaker_names"] or []),
        startMs=row["start_ms"],
        endMs=row["end_ms"],
        content=row["content"],
        embeddingText=row["embedding_text"],
        metadata={str(key): str(value) for key, value in (row["metadata"] or {}).items()},
    )


def normalized_score(value: Any) -> float:
    if not isinstance(value, (int, float)):
        return 0.0
    return min(1.0, max(0.0, float(value)))
