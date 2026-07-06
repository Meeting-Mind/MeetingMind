from dataclasses import dataclass, field
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


def chunk_to_source(chunk: RagChunk) -> RagSource:
    return RagSource(
        sourceId=chunk.sourceId,
        type=chunk.sourceType,
        title=chunk.title,
        speaker=", ".join(chunk.speakerNames) if chunk.speakerNames else None,
        startMs=chunk.startMs,
        endMs=chunk.endMs,
        text=chunk.content,
    )
