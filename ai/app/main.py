import json
import os
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


APP_ROOT = Path(__file__).resolve().parent.parent
DOTENV_CANDIDATES = [APP_ROOT / ".env", APP_ROOT.parent / "backend" / ".env"]


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
    meetingId: str | None = None
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
    return os.getenv(key) or ENV_CACHE.get(key) or default


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
        with urlopen(request, timeout=60) as response:
            response_data = json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="ignore")
        raise HTTPException(status_code=502, detail=f"OpenAI API error: {detail}") from error
    except URLError as error:
        raise HTTPException(status_code=502, detail=f"OpenAI API connection failed: {error.reason}") from error

    return extract_output_text(response_data), model


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
    normalized = normalize_term(term)
    sources: list[AiSource] = []

    for index, row in enumerate(rows):
        if normalized not in normalize_term(row.text):
            continue

        sources.append(
            AiSource(
                sourceId=f"segment-{index + 1:03d}",
                type="transcript",
                speaker=row.speaker,
                time=row.time,
                text=row.text,
            )
        )

    return sources[:4]


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

    sources = build_transcript_sources(term, payload.transcript)
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


app = FastAPI(title="MeetingMind AI Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
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
    return call_openai(payload)


@app.post("/api/meeting-ai/explain-term", response_model=ExplainTermResponse)
def meeting_ai_explain_term(payload: ExplainTermRequest) -> ExplainTermResponse:
    return explain_term(payload)
