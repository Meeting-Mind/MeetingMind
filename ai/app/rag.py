from collections.abc import Iterable
from dataclasses import dataclass, field
import re
from typing import Literal, Protocol


RagScope = Literal["meeting", "project"]
RagSourceType = Literal[
    "transcript",
    "meetingSummary",
    "decision",
    "actionItem",
    "report",
    "projectKnowledge",
    "glossary",
]


@dataclass(frozen=True)
class RagSource:
    sourceId: str
    type: RagSourceType
    text: str
    title: str | None = None
    speaker: str | None = None
    time: str | None = None
    startMs: int | None = None
    endMs: int | None = None


@dataclass(frozen=True)
class RagChunk:
    chunkId: str
    scope: RagScope
    projectId: str
    sourceType: RagSourceType
    sourceId: str
    content: str
    embeddingText: str
    meetingId: str | None = None
    sourceSegmentIds: tuple[str, ...] = ()
    title: str | None = None
    speakerNames: tuple[str, ...] = ()
    startMs: int | None = None
    endMs: int | None = None
    metadata: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class TranscriptSegment:
    text: str
    id: str | None = None
    meetingId: str | None = None
    speakerId: str | None = None
    speakerLabel: str | None = None
    speakerName: str | None = None
    startMs: int | None = None
    endMs: int | None = None
    sequence: int = 0


@dataclass(frozen=True)
class RagTextItem:
    text: str
    sourceType: RagSourceType
    id: str | None = None
    title: str | None = None
    meetingId: str | None = None
    meta: str | None = None
    metadata: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class RagBuildRequest:
    projectId: str
    meetingId: str | None = None
    meetingTitle: str | None = None
    transcriptSegments: tuple[TranscriptSegment, ...] = ()
    meetingSummaries: tuple[RagTextItem, ...] = ()
    decisions: tuple[RagTextItem, ...] = ()
    actions: tuple[RagTextItem, ...] = ()
    projectKnowledge: tuple[RagTextItem, ...] = ()
    language: str = "ko"
    visibility: str = "already_filtered"


@dataclass(frozen=True)
class RagSearchRequest:
    query: str
    scope: RagScope
    projectId: str
    meetingId: str | None = None
    allowedMeetingIds: tuple[str, ...] = ()
    sourceTypes: tuple[RagSourceType, ...] = ()
    limit: int = 5


@dataclass(frozen=True)
class RagSearchResult:
    chunk: RagChunk
    score: float


class RagRetriever(Protocol):
    def search(self, request: RagSearchRequest) -> list[RagSearchResult]:
        ...


class InMemoryRagRetriever:
    def __init__(self, chunks: Iterable[RagChunk]):
        self._chunks = tuple(chunks)

    def search(self, request: RagSearchRequest) -> list[RagSearchResult]:
        query_tokens = tokenize_for_search(request.query)
        if not query_tokens:
            return []

        results: list[RagSearchResult] = []
        for chunk in self._chunks:
            if not chunk_matches_request(chunk, request):
                continue

            score = score_chunk(chunk, query_tokens)
            if score <= 0:
                continue

            results.append(RagSearchResult(chunk=chunk, score=score))

        results.sort(key=lambda result: (-result.score, result.chunk.chunkId))
        return results[: max(0, request.limit)]


def chunk_to_source(chunk: RagChunk) -> RagSource:
    return RagSource(
        sourceId=chunk.sourceId,
        type=chunk.sourceType,
        title=chunk.title,
        speaker=", ".join(chunk.speakerNames) if chunk.speakerNames else None,
        time=chunk.metadata.get("timeRange"),
        startMs=chunk.startMs,
        endMs=chunk.endMs,
        text=chunk.content,
    )


def build_rag_chunks(request: RagBuildRequest) -> list[RagChunk]:
    chunks: list[RagChunk] = []
    chunks.extend(build_transcript_chunks(request))
    chunks.extend(
        build_text_item_chunks(
            request,
            request.meetingSummaries,
            source_type="meetingSummary",
            scope="project",
        )
    )
    chunks.extend(build_text_item_chunks(request, request.decisions, source_type="decision", scope="meeting"))
    chunks.extend(build_text_item_chunks(request, request.actions, source_type="actionItem", scope="meeting"))
    chunks.extend(
        build_text_item_chunks(
            request,
            request.projectKnowledge,
            source_type="projectKnowledge",
            scope="project",
        )
    )
    return chunks


def build_transcript_chunks(
    request: RagBuildRequest,
    *,
    window_size: int = 4,
    min_window_size: int = 3,
) -> list[RagChunk]:
    if not request.meetingId:
        return []

    segments = sorted(
        (segment for segment in request.transcriptSegments if segment.text.strip()),
        key=lambda segment: segment.sequence,
    )
    if not segments:
        return []

    chunk_size = max(1, min(window_size, 8))
    min_size = max(1, min(min_window_size, chunk_size))
    windows = window_segments(segments, chunk_size=chunk_size, min_size=min_size)
    chunks: list[RagChunk] = []

    for index, window in enumerate(windows, start=1):
        segment_ids = tuple(
            segment_id(segment, ((index - 1) * chunk_size) + offset)
            for offset, segment in enumerate(window, start=1)
        )
        speaker_names = unique_values(speaker_name(segment) for segment in window)
        start_ms = first_int(segment.startMs for segment in window)
        end_ms = last_int(segment.endMs for segment in window)
        content = "\n".join(
            f"{speaker_name(segment)}: {segment.text.strip()}"
            for segment in window
            if segment.text.strip()
        )
        chunk_id = f"{request.meetingId}:transcript:{index:04d}"
        title = request.meetingTitle or request.meetingId
        metadata = base_metadata(request)
        metadata.update(
            {
                "createdFrom": "stt",
                "segmentCount": str(len(window)),
            }
        )
        if start_ms is not None or end_ms is not None:
            metadata["timeRange"] = format_time_range(start_ms, end_ms)

        chunks.append(
            RagChunk(
                chunkId=chunk_id,
                scope="meeting",
                projectId=request.projectId,
                meetingId=request.meetingId,
                sourceType="transcript",
                sourceId=segment_ids[0],
                sourceSegmentIds=segment_ids,
                title=title,
                speakerNames=speaker_names,
                startMs=start_ms,
                endMs=end_ms,
                content=content,
                embeddingText=format_embedding_text(
                    title=title,
                    scope="meeting",
                    source_type="transcript",
                    content=content,
                    speaker_names=speaker_names,
                    start_ms=start_ms,
                    end_ms=end_ms,
                ),
                metadata=metadata,
            )
        )

    return chunks


def build_text_item_chunks(
    request: RagBuildRequest,
    items: tuple[RagTextItem, ...],
    *,
    source_type: RagSourceType,
    scope: RagScope,
) -> list[RagChunk]:
    chunks: list[RagChunk] = []

    for index, item in enumerate(items, start=1):
        if item.sourceType != source_type or not item.text.strip():
            continue

        meeting_id = item.meetingId or request.meetingId
        if scope == "meeting" and not meeting_id:
            continue

        source_id = item.id or f"{source_type}-{index:03d}"
        title = item.title or request.meetingTitle or source_type
        content = item.text.strip()
        if item.meta:
            content = f"{content}\n메타: {item.meta.strip()}"

        metadata = base_metadata(request)
        metadata.update({"createdFrom": source_type})
        metadata.update(item.metadata)

        chunks.append(
            RagChunk(
                chunkId=chunk_id_for(scope, request.projectId, meeting_id, source_type, index),
                scope=scope,
                projectId=request.projectId,
                meetingId=meeting_id if source_type != "projectKnowledge" else None,
                sourceType=source_type,
                sourceId=source_id,
                title=title,
                content=content,
                embeddingText=format_embedding_text(
                    title=title,
                    scope=scope,
                    source_type=source_type,
                    content=content,
                ),
                metadata=metadata,
            )
        )

    return chunks


def window_segments(
    segments: list[TranscriptSegment],
    *,
    chunk_size: int,
    min_size: int,
) -> list[list[TranscriptSegment]]:
    if len(segments) <= chunk_size:
        return [segments]

    windows: list[list[TranscriptSegment]] = []
    index = 0
    while index < len(segments):
        window = segments[index : index + chunk_size]
        if len(window) < min_size and windows:
            windows[-1].extend(window)
            break

        windows.append(window)
        index += chunk_size

    return windows


def format_embedding_text(
    *,
    title: str,
    scope: RagScope,
    source_type: RagSourceType,
    content: str,
    speaker_names: tuple[str, ...] = (),
    start_ms: int | None = None,
    end_ms: int | None = None,
) -> str:
    lines = [
        f"회의: {title}",
        f"범위: {scope}",
        f"출처: {source_type}",
    ]
    if start_ms is not None or end_ms is not None:
        lines.append(f"시간: {format_time_range(start_ms, end_ms)}")
    if speaker_names:
        lines.append(f"발화자: {', '.join(speaker_names)}")
    lines.extend(["내용:", content])
    return "\n".join(lines)


def base_metadata(request: RagBuildRequest) -> dict[str, str]:
    return {
        "language": request.language,
        "visibility": request.visibility,
    }


def chunk_id_for(
    scope: RagScope,
    project_id: str,
    meeting_id: str | None,
    source_type: RagSourceType,
    index: int,
) -> str:
    owner = meeting_id if scope == "meeting" and meeting_id else project_id
    return f"{owner}:{source_type}:{index:04d}"


def segment_id(segment: TranscriptSegment, fallback_sequence: int) -> str:
    if segment.id:
        return segment.id
    sequence = segment.sequence or fallback_sequence
    return f"segment-{sequence:03d}"


def speaker_name(segment: TranscriptSegment) -> str:
    return segment.speakerName or segment.speakerLabel or segment.speakerId or "unknown"


def unique_values(values: Iterable[str]) -> tuple[str, ...]:
    unique: list[str] = []
    for value in values:
        if not isinstance(value, str):
            continue
        stripped = value.strip()
        if stripped and stripped not in unique:
            unique.append(stripped)
    return tuple(unique)


def first_int(values: Iterable[int | None]) -> int | None:
    for value in values:
        if isinstance(value, int):
            return value
    return None


def last_int(values: Iterable[int | None]) -> int | None:
    last: int | None = None
    for value in values:
        if isinstance(value, int):
            last = value
    return last


def format_time_range(start_ms: int | None, end_ms: int | None) -> str:
    if start_ms is None and end_ms is None:
        return ""
    if start_ms is None:
        return f"-{format_ms(end_ms)}"
    if end_ms is None:
        return f"{format_ms(start_ms)}-"
    return f"{format_ms(start_ms)}-{format_ms(end_ms)}"


def format_ms(value: int | None) -> str:
    if value is None:
        return ""

    total_seconds = max(0, value // 1000)
    hours, remainder = divmod(total_seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}"


def chunk_matches_request(chunk: RagChunk, request: RagSearchRequest) -> bool:
    if chunk.projectId != request.projectId:
        return False
    if chunk.scope != request.scope:
        return False
    if request.sourceTypes and chunk.sourceType not in request.sourceTypes:
        return False

    if request.scope == "meeting":
        return bool(request.meetingId and chunk.meetingId == request.meetingId)

    if chunk.sourceType == "projectKnowledge":
        return True

    allowed_meeting_ids = set(request.allowedMeetingIds)
    return bool(chunk.meetingId and chunk.meetingId in allowed_meeting_ids)


def score_chunk(chunk: RagChunk, query_tokens: tuple[str, ...]) -> float:
    haystack = searchable_text(chunk)
    haystack_tokens = tokenize_for_search(haystack)
    if not haystack_tokens:
        return 0

    token_set = set(haystack_tokens)
    score = 0.0
    for token in query_tokens:
        if token in token_set:
            score += 2.0
        elif token in haystack:
            score += 1.0
        elif any(token_part in token for token_part in token_set if len(token_part) >= 2):
            score += 1.0

    if normalize_search_text(" ".join(query_tokens)) in haystack:
        score += 3.0

    if score <= 0:
        return 0
    if chunk.sourceType == "projectKnowledge":
        score += 0.2
    if chunk.sourceType == "glossary":
        score += 0.3

    return score


def searchable_text(chunk: RagChunk) -> str:
    parts = [
        chunk.title or "",
        chunk.sourceType,
        chunk.content,
        chunk.embeddingText,
        " ".join(chunk.speakerNames),
        " ".join(chunk.metadata.values()),
    ]
    return normalize_search_text("\n".join(parts))


def tokenize_for_search(value: str) -> tuple[str, ...]:
    normalized = normalize_search_text(value)
    tokens = re.findall(r"[0-9a-z가-힣]+", normalized)
    return tuple(token for token in tokens if len(token) >= 2)


def normalize_search_text(value: str) -> str:
    return value.casefold()
