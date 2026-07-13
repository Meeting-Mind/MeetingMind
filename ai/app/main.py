import json
import logging
import os
from pathlib import Path
import ssl
from time import perf_counter
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .rag import (
    InMemoryRagRetriever,
    RagBuildRequest,
    RagChunk,
    RagSourceType,
    RagTextItem,
    RagSearchRequest,
    TranscriptSegment,
    build_rag_chunks,
    chunk_to_source,
)

try:
    import certifi
except ImportError:
    certifi = None


APP_ROOT = Path(__file__).resolve().parent.parent
DOTENV_CANDIDATES = [
    APP_ROOT / ".env",
    APP_ROOT.parent / ".env",
    APP_ROOT.parent / "backend" / ".env",
]
ENV_ALIASES = {
    "OPENAI_API_KEY": ("OPEN_AI_KEY",),
}
LOGGER = logging.getLogger("meetingmind.ai")


class TranscriptRow(BaseModel):
    time: str
    speaker: str
    text: str


class NamedItem(BaseModel):
    title: str
    meta: str


class GlossaryItem(BaseModel):
    term: str
    definition: str
    sourceId: str | None = None


class AiSource(BaseModel):
    sourceId: str
    type: str
    title: str | None = None
    speaker: str | None = None
    time: str | None = None
    startMs: int | None = None
    endMs: int | None = None
    text: str


class MeetingAiAskRequest(BaseModel):
    question: str = Field(min_length=1)
    transcript: list[TranscriptRow] = Field(default_factory=list)
    decisions: list[NamedItem] = Field(default_factory=list)
    actions: list[NamedItem] = Field(default_factory=list)


class MeetingAiAskResponse(BaseModel):
    answer: str
    model: str


class ExplainTermRequest(BaseModel):
    projectId: str | None = None
    meetingId: str | None = None
    meetingTitle: str | None = None
    term: str = Field(min_length=1)
    selectedText: str | None = None
    glossary: list[GlossaryItem] = Field(default_factory=list)
    transcript: list[TranscriptRow] = Field(default_factory=list)


class ExplainTermResponse(BaseModel):
    term: str
    explanation: str
    sourceType: str
    sources: list[AiSource] = Field(default_factory=list)
    unsupported: bool = False
    model: str


class MeetingAiChatRequest(BaseModel):
    projectId: str | None = None
    meetingId: str
    meetingTitle: str | None = None
    question: str = Field(min_length=1)
    transcript: list[TranscriptRow] = Field(default_factory=list)
    decisions: list[NamedItem] = Field(default_factory=list)
    actions: list[NamedItem] = Field(default_factory=list)


class MeetingAiChatResponse(BaseModel):
    answer: str
    sources: list[AiSource] = Field(default_factory=list)
    unsupported: bool = False
    model: str


class BackendMeetingAiSource(BaseModel):
    sourceId: str = Field(min_length=1)
    type: RagSourceType
    meetingId: str = Field(min_length=1)
    title: str | None = None
    speaker: str | None = None
    time: str | None = None
    startMs: int | None = None
    endMs: int | None = None
    text: str = Field(min_length=1)


class BackendMeetingAiChatRequest(BaseModel):
    projectId: str
    meetingId: str = Field(min_length=1)
    meetingTitle: str | None = None
    question: str = Field(min_length=1)
    sources: list[BackendMeetingAiSource] = Field(default_factory=list)


class ProjectKnowledgeItem(BaseModel):
    sourceId: str | None = None
    title: str
    text: str


class ProjectMeetingContext(BaseModel):
    meetingId: str
    title: str | None = None
    summary: str | None = None


class ProjectAiChatRequest(BaseModel):
    projectId: str
    question: str = Field(min_length=1)
    projectKnowledge: list[ProjectKnowledgeItem] = Field(default_factory=list)
    meetings: list[ProjectMeetingContext] = Field(default_factory=list)


class ProjectAiChatResponse(BaseModel):
    answer: str
    sources: list[AiSource] = Field(default_factory=list)
    unsupported: bool = False
    model: str


class GenerateReportRequest(BaseModel):
    projectId: str | None = None
    meetingId: str
    title: str | None = None
    transcript: list[TranscriptRow] = Field(default_factory=list)
    decisions: list[NamedItem] = Field(default_factory=list)
    actions: list[NamedItem] = Field(default_factory=list)
    format: str = "markdown"


class ReportDecision(BaseModel):
    title: str
    rationale: str | None = None
    sourceIds: list[str] = Field(default_factory=list)


class ReportActionItem(BaseModel):
    title: str
    assignee: str | None = None
    dueDate: str | None = None
    sourceIds: list[str] = Field(default_factory=list)
    confirmationState: str = "candidate"


class GenerateReportResponse(BaseModel):
    summary: str
    decisions: list[ReportDecision] = Field(default_factory=list)
    actionItems: list[ReportActionItem] = Field(default_factory=list)
    markdown: str
    sources: list[AiSource] = Field(default_factory=list)
    unsupported: bool = False
    model: str


class ParticipantItem(BaseModel):
    name: str
    role: str | None = None


class ExtractTasksRequest(BaseModel):
    projectId: str | None = None
    meetingId: str
    title: str | None = None
    transcript: list[TranscriptRow] = Field(default_factory=list)
    summary: str | None = None
    participants: list[ParticipantItem] = Field(default_factory=list)


class TaskCandidate(BaseModel):
    title: str
    assignee: str | None = None
    dueDate: str | None = None
    sourceIds: list[str] = Field(default_factory=list)
    confirmationState: str = "candidate"


class ExtractTasksResponse(BaseModel):
    tasks: list[TaskCandidate] = Field(default_factory=list)
    sources: list[AiSource] = Field(default_factory=list)
    unsupported: bool = False
    model: str


def observe_ai_endpoint(endpoint: str, operation: Any) -> Any:
    started_at = perf_counter()
    try:
        response = operation()
    except Exception as error:
        duration_ms = elapsed_ms(started_at)
        LOGGER.warning(
            "ai_request_failed %s",
            json.dumps(
                {
                    "endpoint": endpoint,
                    "durationMs": duration_ms,
                    "errorType": type(error).__name__,
                },
                ensure_ascii=False,
                sort_keys=True,
            ),
        )
        raise

    LOGGER.info(
        "ai_request_completed %s",
        json.dumps(
            ai_observability_fields(endpoint, response, elapsed_ms(started_at)),
            ensure_ascii=False,
            sort_keys=True,
        ),
    )
    return response


def ai_observability_fields(endpoint: str, response: Any, duration_ms: int) -> dict[str, Any]:
    source_count = len(getattr(response, "sources", []) or [])
    unsupported = bool(getattr(response, "unsupported", False))
    return {
        "endpoint": endpoint,
        "durationMs": duration_ms,
        "model": getattr(response, "model", None),
        "sourceCount": source_count,
        "unsupported": unsupported,
        "unsupportedReason": unsupported_reason(response, source_count) if unsupported else None,
    }


def unsupported_reason(response: Any, source_count: int) -> str:
    if source_count == 0:
        return "NO_SOURCES"
    if getattr(response, "sourceType", None) == "none":
        return "NO_EVIDENCE"
    return "UNSUPPORTED_RESPONSE"


def elapsed_ms(started_at: float) -> int:
    return max(0, round((perf_counter() - started_at) * 1000))


def load_dotenv() -> dict[str, str]:
    values: dict[str, str] = {}

    for candidate in DOTENV_CANDIDATES:
        if not candidate.exists():
            continue

        for raw_line in candidate.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue

            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("\"'")

    return values


ENV_CACHE = load_dotenv()


def get_env(key: str, default: str | None = None) -> str | None:
    value = os.getenv(key) or ENV_CACHE.get(key)
    if value:
        return value

    for alias in ENV_ALIASES.get(key, ()):
        value = os.getenv(alias) or ENV_CACHE.get(alias)
        if value:
            return value

    return default


def require_env(key: str) -> str:
    value = get_env(key)
    if not value:
        raise HTTPException(status_code=500, detail=f"{key} is not configured.")
    return value


def build_context_block(payload: MeetingAiAskRequest) -> str:
    transcript_lines = "\n".join(
        f"- {row.time} {row.speaker}: {row.text}" for row in payload.transcript[:12]
    )
    decision_lines = "\n".join(
        f"- {item.title} ({item.meta})" for item in payload.decisions[:8]
    )
    action_lines = "\n".join(
        f"- {item.title} ({item.meta})" for item in payload.actions[:8]
    )

    return (
        "[회의 STT 발췌]\n"
        f"{transcript_lines or '- 없음'}\n\n"
        "[결정사항]\n"
        f"{decision_lines or '- 없음'}\n\n"
        "[Action Items]\n"
        f"{action_lines or '- 없음'}"
    )


def extract_output_text(response_data: dict[str, Any]) -> str:
    output_items = response_data.get("output", [])

    for item in output_items:
        if item.get("type") != "message":
            continue

        for content in item.get("content", []):
            if content.get("type") == "output_text":
                text = content.get("text", "").strip()
                if text:
                    return text

    raise HTTPException(status_code=502, detail="OpenAI response did not contain output text.")


def call_openai_text(developer_content: str, user_content: str) -> tuple[str, str]:
    api_key = require_env("OPENAI_API_KEY")
    model = get_env("OPENAI_MODEL", "gpt-4.1-mini") or "gpt-4.1-mini"

    request_body = {
        "model": model,
        "input": [
            {
                "role": "developer",
                "content": developer_content,
            },
            {
                "role": "user",
                "content": user_content,
            },
        ],
    }

    request = Request(
        "https://api.openai.com/v1/responses",
        data=json.dumps(request_body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urlopen(request, timeout=60, context=openai_ssl_context()) as response:
            response_data = json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="ignore")
        raise HTTPException(status_code=502, detail=f"OpenAI API error: {detail}") from error
    except URLError as error:
        raise HTTPException(status_code=502, detail=f"OpenAI API connection failed: {error.reason}") from error

    return extract_output_text(response_data), model


def openai_ssl_context() -> ssl.SSLContext | None:
    if certifi is None:
        return None
    return ssl.create_default_context(cafile=certifi.where())


def call_openai(payload: MeetingAiAskRequest) -> MeetingAiAskResponse:
    text, model = call_openai_text(
        developer_content=(
            "너는 MeetingMind의 회의 분석 Assistant다. "
            "반드시 제공된 회의 맥락만 근거로 답하고, 없는 내용은 추정하지 말고 "
            "'제공된 회의 맥락에서는 확인되지 않습니다'라고 말해라. "
            "답변은 한국어로 간결하게 작성하고, 가능하면 근거가 된 시간이나 항목을 함께 적어라."
        ),
        user_content=(
            f"{build_context_block(payload)}\n\n"
            f"[사용자 질문]\n{payload.question}"
        ),
    )
    return MeetingAiAskResponse(answer=text, model=model)


def normalize_term(value: str) -> str:
    return value.strip().casefold()


def build_transcript_sources(term: str, rows: list[TranscriptRow]) -> list[AiSource]:
    chunks = build_rag_chunks(
        RagBuildRequest(
            projectId="prototype-project",
            meetingId="prototype-meeting",
            transcriptSegments=transcript_rows_to_segments(rows),
        )
    )
    retriever = InMemoryRagRetriever(chunks)
    results = retriever.search(
        RagSearchRequest(
            query=term,
            scope="meeting",
            projectId="prototype-project",
            meetingId="prototype-meeting",
            sourceTypes=("transcript",),
            limit=4,
        )
    )
    return [rag_source_to_ai_source(chunk_to_source(result.chunk)) for result in results]


def build_rag_transcript_sources(payload: ExplainTermRequest) -> list[AiSource]:
    project_id = payload.projectId or payload.meetingId or "prototype-project"
    meeting_id = payload.meetingId or "prototype-meeting"
    chunks = build_rag_chunks(
        RagBuildRequest(
            projectId=project_id,
            meetingId=meeting_id,
            meetingTitle=payload.meetingTitle or meeting_id,
            transcriptSegments=transcript_rows_to_segments(payload.transcript, meeting_id=meeting_id),
        )
    )
    retriever = InMemoryRagRetriever(chunks)
    results = retriever.search(
        RagSearchRequest(
            query=payload.term,
            scope="meeting",
            projectId=project_id,
            meetingId=meeting_id,
            sourceTypes=("transcript",),
            limit=4,
        )
    )
    return [rag_source_to_ai_source(chunk_to_source(result.chunk)) for result in results]


def build_meeting_rag_chunks(
    *,
    project_id: str,
    meeting_id: str,
    meeting_title: str | None,
    transcript: list[TranscriptRow],
    decisions: list[NamedItem],
    actions: list[NamedItem],
) -> list[RagChunk]:
    return build_rag_chunks(
        RagBuildRequest(
            projectId=project_id,
            meetingId=meeting_id,
            meetingTitle=meeting_title or meeting_id,
            transcriptSegments=transcript_rows_to_segments(transcript, meeting_id=meeting_id),
            decisions=named_items_to_rag_items(decisions, source_type="decision"),
            actions=named_items_to_rag_items(actions, source_type="actionItem"),
        )
    )


def named_items_to_rag_items(
    items: list[NamedItem],
    *,
    source_type: RagSourceType,
) -> tuple[RagTextItem, ...]:
    source_id_prefix = "action" if source_type == "actionItem" else source_type
    return tuple(
        RagTextItem(
            id=f"{source_id_prefix}-{index + 1:03d}",
            sourceType=source_type,
            title=item.title,
            text=item.title,
            meta=item.meta,
        )
        for index, item in enumerate(items)
    )


def build_meeting_chat_sources(payload: MeetingAiChatRequest) -> list[AiSource]:
    project_id = payload.projectId or payload.meetingId
    chunks = build_meeting_rag_chunks(
        project_id=project_id,
        meeting_id=payload.meetingId,
        meeting_title=payload.meetingTitle,
        transcript=payload.transcript,
        decisions=payload.decisions,
        actions=payload.actions,
    )
    retriever = InMemoryRagRetriever(chunks)
    results = retriever.search(
        RagSearchRequest(
            query=payload.question,
            scope="meeting",
            projectId=project_id,
            meetingId=payload.meetingId,
            sourceTypes=("transcript", "decision", "actionItem"),
            limit=5,
        )
    )
    return [rag_source_to_ai_source(chunk_to_source(result.chunk)) for result in results]


def build_backend_meeting_chat_sources(payload: BackendMeetingAiChatRequest) -> list[AiSource]:
    validate_backend_meeting_sources(payload)
    chunks = backend_sources_to_rag_chunks(payload)
    retriever = InMemoryRagRetriever(chunks)
    results = retriever.search(
        RagSearchRequest(
            query=payload.question,
            scope="meeting",
            projectId=payload.projectId,
            meetingId=payload.meetingId,
            sourceTypes=("transcript", "decision", "actionItem", "report"),
            limit=5,
        )
    )
    return [rag_source_to_ai_source(chunk_to_source(result.chunk)) for result in results]


def validate_backend_meeting_sources(payload: BackendMeetingAiChatRequest) -> None:
    forbidden_source = next(
        (source for source in payload.sources if source.meetingId != payload.meetingId),
        None,
    )
    if forbidden_source is None:
        return

    raise HTTPException(
        status_code=403,
        detail={
            "code": "AI_CONTEXT_FORBIDDEN",
            "message": "AI context source meetingId must match request meetingId.",
        },
    )


def backend_sources_to_rag_chunks(payload: BackendMeetingAiChatRequest) -> list[RagChunk]:
    chunks: list[RagChunk] = []
    for index, source in enumerate(payload.sources, start=1):
        chunks.append(
            RagChunk(
                chunkId=f"{payload.meetingId}:{source.type}:{index:04d}",
                scope="meeting",
                projectId=payload.projectId,
                meetingId=payload.meetingId,
                sourceType=source.type,
                sourceId=source.sourceId,
                title=source.title or payload.meetingTitle or source.type,
                speakerNames=(source.speaker,) if source.speaker else (),
                startMs=source.startMs,
                endMs=source.endMs,
                content=source.text,
                embeddingText=format_backend_source_embedding(payload, source),
                metadata=backend_source_metadata(source),
            )
        )
    return chunks


def format_backend_source_embedding(
    payload: BackendMeetingAiChatRequest,
    source: BackendMeetingAiSource,
) -> str:
    lines = [
        f"회의: {source.title or payload.meetingTitle or payload.meetingId}",
        "범위: meeting",
        f"출처: {source.type}",
    ]
    if source.time:
        lines.append(f"시간: {source.time}")
    if source.speaker:
        lines.append(f"발화자: {source.speaker}")
    lines.extend(["내용:", source.text])
    return "\n".join(lines)


def backend_source_metadata(source: BackendMeetingAiSource) -> dict[str, str]:
    metadata = {
        "visibility": "already_filtered",
        "createdFrom": source.type,
    }
    if source.time:
        metadata["timeRange"] = source.time
    return metadata


def build_report_chunks(payload: GenerateReportRequest) -> list[RagChunk]:
    project_id = payload.projectId or payload.meetingId
    return build_meeting_rag_chunks(
        project_id=project_id,
        meeting_id=payload.meetingId,
        meeting_title=payload.title,
        transcript=payload.transcript,
        decisions=payload.decisions,
        actions=payload.actions,
    )


def build_report_sources(payload: GenerateReportRequest) -> list[AiSource]:
    chunks = build_report_chunks(payload)
    return [rag_source_to_ai_source(chunk_to_source(chunk)) for chunk in chunks]


def build_task_extraction_chunks(payload: ExtractTasksRequest) -> list[RagChunk]:
    project_id = payload.projectId or payload.meetingId
    summary_items: tuple[RagTextItem, ...] = ()
    if payload.summary and payload.summary.strip():
        summary_items = (
            RagTextItem(
                id="meeting-summary-001",
                sourceType="meetingSummary",
                title=payload.title or payload.meetingId,
                meetingId=payload.meetingId,
                text=payload.summary.strip(),
                metadata={"recordType": "meeting"},
            ),
        )

    return build_rag_chunks(
        RagBuildRequest(
            projectId=project_id,
            meetingId=payload.meetingId,
            meetingTitle=payload.title or payload.meetingId,
            transcriptSegments=transcript_rows_to_segments(payload.transcript, meeting_id=payload.meetingId),
            meetingSummaries=summary_items,
        )
    )


def build_task_extraction_sources(payload: ExtractTasksRequest) -> list[AiSource]:
    chunks = build_task_extraction_chunks(payload)
    return [rag_source_to_ai_source(chunk_to_source(chunk)) for chunk in chunks]


def build_project_rag_chunks(payload: ProjectAiChatRequest) -> list[RagChunk]:
    return build_rag_chunks(
        RagBuildRequest(
            projectId=payload.projectId,
            projectKnowledge=project_knowledge_to_rag_items(payload.projectKnowledge),
            meetingSummaries=meeting_context_to_summary_items(payload.meetings),
        )
    )


def project_knowledge_to_rag_items(items: list[ProjectKnowledgeItem]) -> tuple[RagTextItem, ...]:
    return tuple(
        RagTextItem(
            id=item.sourceId or f"knowledge-{index + 1:03d}",
            sourceType="projectKnowledge",
            title=item.title,
            text=item.text,
            metadata={"recordType": "official"},
        )
        for index, item in enumerate(items)
    )


def meeting_context_to_summary_items(items: list[ProjectMeetingContext]) -> tuple[RagTextItem, ...]:
    summaries: list[RagTextItem] = []
    for index, item in enumerate(items):
        summary = (item.summary or "").strip()
        if not summary:
            continue

        summaries.append(
            RagTextItem(
                id=f"meeting-summary-{index + 1:03d}",
                sourceType="meetingSummary",
                title=item.title or item.meetingId,
                meetingId=item.meetingId,
                text=summary,
                metadata={"recordType": "meeting"},
            )
        )
    return tuple(summaries)


def build_project_chat_sources(payload: ProjectAiChatRequest) -> list[AiSource]:
    chunks = build_project_rag_chunks(payload)
    retriever = InMemoryRagRetriever(chunks)
    allowed_meeting_ids = tuple(meeting.meetingId for meeting in payload.meetings)
    results = retriever.search(
        RagSearchRequest(
            query=payload.question,
            scope="project",
            projectId=payload.projectId,
            allowedMeetingIds=allowed_meeting_ids,
            sourceTypes=("projectKnowledge", "meetingSummary"),
            limit=6,
        )
    )
    return [rag_source_to_ai_source(chunk_to_source(result.chunk)) for result in results]


def transcript_rows_to_segments(
    rows: list[TranscriptRow],
    *,
    meeting_id: str | None = None,
) -> tuple[TranscriptSegment, ...]:
    segments: list[TranscriptSegment] = []
    for index, row in enumerate(rows):
        time_ms = parse_time_to_ms(row.time)
        segments.append(
            TranscriptSegment(
                id=f"segment-{index + 1:03d}",
                meetingId=meeting_id,
                speakerName=row.speaker,
                startMs=time_ms,
                endMs=time_ms,
                text=row.text,
                sequence=index + 1,
            )
        )
    return tuple(segments)


def rag_source_to_ai_source(source: Any) -> AiSource:
    return AiSource(
        sourceId=source.sourceId,
        type=source.type,
        title=source.title,
        speaker=source.speaker,
        time=source.time,
        startMs=source.startMs,
        endMs=source.endMs,
        text=source.text,
    )


def parse_time_to_ms(value: str) -> int | None:
    parts = value.strip().split(":")
    if len(parts) != 3:
        return None

    try:
        hours, minutes, seconds = (int(part) for part in parts)
    except ValueError:
        return None

    return ((hours * 60 * 60) + (minutes * 60) + seconds) * 1000


def explain_term(payload: ExplainTermRequest) -> ExplainTermResponse:
    term = payload.term.strip()

    for item in payload.glossary:
        if normalize_term(item.term) != normalize_term(term):
            continue

        return ExplainTermResponse(
            term=term,
            explanation=item.definition,
            sourceType="glossary",
            sources=[
                AiSource(
                    sourceId=item.sourceId or f"glossary-{normalize_term(item.term)}",
                    type="glossary",
                    title="Domain Dictionary",
                    text=item.definition,
                )
            ],
            model="local-glossary",
        )

    sources = build_rag_transcript_sources(payload)
    selected_text = (payload.selectedText or "").strip()
    if selected_text and not sources:
        sources.append(
            AiSource(
                sourceId="selected-text",
                type="transcript",
                text=selected_text,
            )
        )

    if not sources:
        return ExplainTermResponse(
            term=term,
            explanation="제공된 회의 맥락에서는 이 용어의 의미를 확인할 수 없습니다.",
            sourceType="none",
            unsupported=True,
            model="context-only",
        )

    context_lines = "\n".join(
        f"- {source.time or source.sourceId} {source.speaker or ''}: {source.text}".strip()
        for source in sources
    )
    text, model = call_openai_text(
        developer_content=(
            "너는 MeetingMind의 회의 중 용어 설명 Assistant다. "
            "반드시 제공된 회의 발화만 근거로 해당 용어가 이 회의에서 어떤 의미로 쓰였는지 설명해라. "
            "일반 지식이 필요하더라도 회의 맥락과 충돌하지 않는 범위에서만 짧게 보충해라. "
            "근거가 부족하면 확인할 수 없다고 답해라. 답변은 한국어 2문장 이내로 작성해라."
        ),
        user_content=(
            f"[용어]\n{term}\n\n"
            f"[선택 문장]\n{selected_text or '- 없음'}\n\n"
            f"[회의 발화 근거]\n{context_lines}"
        ),
    )

    return ExplainTermResponse(
        term=term,
        explanation=text,
        sourceType="transcript",
        sources=sources,
        model=model,
    )


def meeting_chat(payload: MeetingAiChatRequest) -> MeetingAiChatResponse:
    sources = build_meeting_chat_sources(payload)
    return answer_meeting_chat(payload.meetingId, payload.question, sources)


def backend_meeting_chat(payload: BackendMeetingAiChatRequest) -> MeetingAiChatResponse:
    sources = build_backend_meeting_chat_sources(payload)
    return answer_meeting_chat(payload.meetingId, payload.question, sources)


def answer_meeting_chat(
    meeting_id: str,
    question: str,
    sources: list[AiSource],
) -> MeetingAiChatResponse:
    if not sources:
        return MeetingAiChatResponse(
            answer="제공된 회의 맥락에서는 답변 근거를 찾을 수 없습니다.",
            sources=[],
            unsupported=True,
            model="context-only",
        )

    context_lines = "\n".join(
        f"- [{source.type}] {source.title or source.sourceId} "
        f"{source.time or ''} {source.speaker or ''}: {source.text}".strip()
        for source in sources
    )
    text, model = call_openai_text(
        developer_content=(
            "너는 MeetingMind의 회의별 챗봇이다. "
            "반드시 제공된 단일 회의 근거만 사용해서 답해라. "
            "프로젝트 전체 지식이나 다른 회의 내용은 추정하지 마라. "
            "근거가 부족하면 제공된 회의 맥락에서는 확인되지 않는다고 답해라. "
            "답변은 한국어로 간결하게 작성하고, 관련 근거를 함께 언급해라."
        ),
        user_content=(
            f"[회의 ID]\n{meeting_id}\n\n"
            f"[사용자 질문]\n{question}\n\n"
            f"[검색된 회의 근거]\n{context_lines}"
        ),
    )

    return MeetingAiChatResponse(
        answer=text,
        sources=sources,
        model=model,
    )


def as_provider_unavailable(error: HTTPException) -> HTTPException:
    if error.status_code not in (500, 502, 503):
        return error
    return HTTPException(
        status_code=503,
        detail={
            "code": "AI_PROVIDER_UNAVAILABLE",
            "message": "AI provider 응답을 받을 수 없습니다.",
        },
    )


def generate_report(payload: GenerateReportRequest) -> GenerateReportResponse:
    sources = build_report_sources(payload)
    if not sources:
        return GenerateReportResponse(
            summary="제공된 회의 맥락에서는 보고서를 생성할 근거를 찾을 수 없습니다.",
            decisions=[],
            actionItems=[],
            markdown="## 요약\n제공된 회의 맥락에서는 보고서를 생성할 근거를 찾을 수 없습니다.",
            sources=[],
            unsupported=True,
            model="context-only",
        )

    context_lines = "\n".join(
        f"- sourceId={source.sourceId} type={source.type} title={source.title or '-'} "
        f"time={source.time or '-'} speaker={source.speaker or '-'}\n{source.text}"
        for source in sources[:12]
    )
    text, model = call_openai_text(
        developer_content=(
            "너는 MeetingMind의 회의 보고서 생성 Assistant다. "
            "반드시 제공된 회의 근거만 사용해서 요약, 결정사항, 액션아이템, markdown 보고서 초안을 만들어라. "
            "각 결정사항과 액션아이템에는 근거가 된 sourceIds를 포함해라. "
            "응답은 반드시 JSON 객체만 반환하고, key는 summary, decisions, actionItems, markdown을 사용해라. "
            "decisions 항목은 title, rationale, sourceIds를 포함하고, "
            "actionItems 항목은 title, assignee, dueDate, sourceIds, confirmationState를 포함해라. "
            "confirmationState는 candidate로 둔다."
        ),
        user_content=(
            f"[회의 ID]\n{payload.meetingId}\n\n"
            f"[회의 제목]\n{payload.title or payload.meetingId}\n\n"
            f"[출력 형식]\n{payload.format}\n\n"
            f"[회의 근거]\n{context_lines}"
        ),
    )

    return parse_report_response(text, model=model, sources=sources)


def parse_report_response(
    value: str,
    *,
    model: str,
    sources: list[AiSource],
) -> GenerateReportResponse:
    source_ids = [source.sourceId for source in sources]
    try:
        data = extract_json_object(value)
        summary = str(data.get("summary") or "").strip()
        markdown = str(data.get("markdown") or "").strip()
        decisions = [
            ReportDecision(
                title=str(item.get("title") or "").strip(),
                rationale=optional_str(item.get("rationale")),
                sourceIds=filter_source_ids(item.get("sourceIds"), source_ids),
            )
            for item in data.get("decisions", [])
            if isinstance(item, dict) and str(item.get("title") or "").strip()
        ]
        action_items = [
            ReportActionItem(
                title=str(item.get("title") or "").strip(),
                assignee=optional_str(item.get("assignee")),
                dueDate=optional_str(item.get("dueDate")),
                sourceIds=filter_source_ids(item.get("sourceIds"), source_ids),
                confirmationState="candidate",
            )
            for item in data.get("actionItems", [])
            if isinstance(item, dict) and str(item.get("title") or "").strip()
        ]

        if summary and markdown:
            return GenerateReportResponse(
                summary=summary,
                decisions=decisions,
                actionItems=action_items,
                markdown=markdown,
                sources=sources,
                model=model,
            )
    except (TypeError, ValueError, json.JSONDecodeError):
        pass

    fallback_summary = first_non_empty_line(value) or "회의 보고서 초안이 생성되었습니다."
    return GenerateReportResponse(
        summary=fallback_summary,
        decisions=[],
        actionItems=[],
        markdown=value.strip() or f"## 요약\n{fallback_summary}",
        sources=sources,
        model=model,
    )


def extract_tasks(payload: ExtractTasksRequest) -> ExtractTasksResponse:
    sources = build_task_extraction_sources(payload)
    if not sources:
        return ExtractTasksResponse(
            tasks=[],
            sources=[],
            unsupported=True,
            model="context-only",
        )

    participant_lines = "\n".join(
        f"- {participant.name} ({participant.role or 'role-unknown'})"
        for participant in payload.participants
    )
    context_lines = "\n".join(
        f"- sourceId={source.sourceId} type={source.type} title={source.title or '-'} "
        f"time={source.time or '-'} speaker={source.speaker or '-'}\n{source.text}"
        for source in sources[:12]
    )
    text, model = call_openai_text(
        developer_content=(
            "너는 MeetingMind의 회의 종료 태스크 후보 추출 Assistant다. "
            "반드시 제공된 회의 근거에서 실제 할 일 후보만 추출해라. "
            "저장 확정이 아니라 후보 생성 단계이므로 모든 confirmationState는 candidate로 둔다. "
            "각 태스크에는 title, assignee, dueDate, sourceIds, confirmationState를 포함해라. "
            "assignee와 dueDate가 근거에 없으면 null로 둔다. "
            "응답은 반드시 JSON 객체만 반환하고 key는 tasks를 사용해라."
        ),
        user_content=(
            f"[회의 ID]\n{payload.meetingId}\n\n"
            f"[회의 제목]\n{payload.title or payload.meetingId}\n\n"
            f"[참석자]\n{participant_lines or '- 없음'}\n\n"
            f"[회의 근거]\n{context_lines}"
        ),
    )

    return parse_task_candidates_response(text, model=model, sources=sources)


def parse_task_candidates_response(
    value: str,
    *,
    model: str,
    sources: list[AiSource],
) -> ExtractTasksResponse:
    source_ids = [source.sourceId for source in sources]
    try:
        data = extract_json_object(value)
        tasks = [
            TaskCandidate(
                title=str(item.get("title") or "").strip(),
                assignee=optional_str(item.get("assignee")),
                dueDate=optional_str(item.get("dueDate")),
                sourceIds=filter_source_ids(item.get("sourceIds"), source_ids),
                confirmationState="candidate",
            )
            for item in data.get("tasks", [])
            if isinstance(item, dict) and str(item.get("title") or "").strip()
        ]
        return ExtractTasksResponse(
            tasks=tasks,
            sources=sources,
            unsupported=False,
            model=model,
        )
    except (TypeError, ValueError, json.JSONDecodeError):
        return ExtractTasksResponse(
            tasks=[],
            sources=sources,
            unsupported=True,
            model=model,
        )


def extract_json_object(value: str) -> dict[str, Any]:
    stripped = value.strip()
    if stripped.startswith("```"):
        stripped = stripped.strip("`")
        if stripped.startswith("json"):
            stripped = stripped[4:].strip()

    start = stripped.find("{")
    end = stripped.rfind("}")
    if start == -1 or end == -1 or end < start:
        raise ValueError("JSON object not found")

    data = json.loads(stripped[start : end + 1])
    if not isinstance(data, dict):
        raise ValueError("JSON root is not an object")
    return data


def optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def filter_source_ids(value: Any, allowed_source_ids: list[str]) -> list[str]:
    if not isinstance(value, list):
        return []

    allowed = set(allowed_source_ids)
    return [str(item) for item in value if str(item) in allowed]


def first_non_empty_line(value: str) -> str | None:
    for line in value.splitlines():
        stripped = line.strip().lstrip("#").strip()
        if stripped:
            return stripped
    return None


def project_chat(payload: ProjectAiChatRequest) -> ProjectAiChatResponse:
    sources = build_project_chat_sources(payload)
    if not sources:
        return ProjectAiChatResponse(
            answer="제공된 프로젝트 맥락에서는 답변 근거를 찾을 수 없습니다.",
            sources=[],
            unsupported=True,
            model="context-only",
        )

    context_lines = "\n".join(
        f"- [{source.type}] {source.title or source.sourceId}: {source.text}".strip()
        for source in sources
    )
    text, model = call_openai_text(
        developer_content=(
            "너는 MeetingMind의 프로젝트별 챗봇이다. "
            "반드시 제공된 프로젝트 지식과 접근 허용된 회의 요약만 근거로 답해라. "
            "공식 프로젝트 지식과 회의 기록 출처를 구분해서 다뤄라. "
            "제공되지 않은 회의나 권한 밖 데이터를 추정하지 마라. "
            "근거가 부족하면 제공된 프로젝트 맥락에서는 확인되지 않는다고 답해라. "
            "답변은 한국어로 간결하게 작성해라."
        ),
        user_content=(
            f"[프로젝트 ID]\n{payload.projectId}\n\n"
            f"[사용자 질문]\n{payload.question}\n\n"
            f"[검색된 프로젝트 근거]\n{context_lines}"
        ),
    )

    return ProjectAiChatResponse(
        answer=text,
        sources=sources,
        model=model,
    )


app = FastAPI(title="MeetingMind AI Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(RequestValidationError)
def validation_exception_handler(_request: Any, _exception: RequestValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=400,
        content={
            "code": "INVALID_REQUEST",
            "message": "요청값이 잘못되었습니다.",
            "fieldErrors": [],
        },
    )


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "ok": True,
        "openai_configured": bool(get_env("OPENAI_API_KEY")),
        "model": get_env("OPENAI_MODEL", "gpt-4.1-mini"),
    }


@app.post("/api/meeting-ai/ask", response_model=MeetingAiAskResponse)
def meeting_ai_ask(payload: MeetingAiAskRequest) -> MeetingAiAskResponse:
    return observe_ai_endpoint("meeting-ai.ask", lambda: call_openai(payload))


@app.post("/api/meeting-ai/explain-term", response_model=ExplainTermResponse)
def meeting_ai_explain_term(payload: ExplainTermRequest) -> ExplainTermResponse:
    return observe_ai_endpoint("meeting-ai.explain-term", lambda: explain_term(payload))


@app.post("/api/meeting-ai/chat", response_model=MeetingAiChatResponse)
def meeting_ai_chat(payload: MeetingAiChatRequest) -> MeetingAiChatResponse:
    return observe_ai_endpoint("meeting-ai.chat", lambda: meeting_chat(payload))


@app.post("/api/internal/meeting-ai/chat", response_model=MeetingAiChatResponse)
def backend_meeting_ai_chat(payload: BackendMeetingAiChatRequest) -> MeetingAiChatResponse:
    try:
        return observe_ai_endpoint("meeting-ai.chat.internal", lambda: backend_meeting_chat(payload))
    except HTTPException as error:
        raise as_provider_unavailable(error) from error


@app.post("/api/meeting-ai/generate-report", response_model=GenerateReportResponse)
def meeting_ai_generate_report(payload: GenerateReportRequest) -> GenerateReportResponse:
    return observe_ai_endpoint("meeting-ai.generate-report", lambda: generate_report(payload))


@app.post("/api/meeting-ai/extract-tasks", response_model=ExtractTasksResponse)
def meeting_ai_extract_tasks(payload: ExtractTasksRequest) -> ExtractTasksResponse:
    return observe_ai_endpoint("meeting-ai.extract-tasks", lambda: extract_tasks(payload))


@app.post("/api/project-ai/chat", response_model=ProjectAiChatResponse)
def project_ai_chat(payload: ProjectAiChatRequest) -> ProjectAiChatResponse:
    return observe_ai_endpoint("project-ai.chat", lambda: project_chat(payload))
