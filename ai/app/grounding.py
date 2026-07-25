import json
from collections.abc import Iterable
from dataclasses import dataclass
from typing import Literal


UnsupportedReason = Literal[
    "NO_EVIDENCE",
    "LOW_RELEVANCE",
    "MODEL_UNSUPPORTED",
    "UNVERIFIED_OUTPUT",
]

DEFAULT_RELEVANCE_THRESHOLD = 0.3

GROUNDED_ANSWER_SCHEMA = {
    "type": "object",
    "properties": {
        "supported": {"type": "boolean"},
        "answer": {"type": "string"},
        "sourceIds": {
            "type": "array",
            "items": {"type": "string"},
        },
    },
    "required": ["supported", "answer", "sourceIds"],
    "additionalProperties": False,
}

# `markdown`은 더 이상 모델이 만들지 않는다. 구조화 데이터를 다시 쓴 것이라 토큰을
# 두 배로 쓰고 둘이 어긋날 수 있었다. 서버가 구조화 데이터로 조립한다.
# `summary`는 문장 배열이며 문장마다 근거를 강제한다 — 근거 없는 문장은 지어낸 문장이다.
# 자세한 근거는 `contracts/report-format.md`에 있다.
REPORT_SCHEMA = {
    "type": "object",
    "properties": {
        "supported": {"type": "boolean"},
        "summary": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "text": {"type": "string"},
                    "sourceIds": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                },
                "required": ["text", "sourceIds"],
                "additionalProperties": False,
            },
        },
        "decisions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "rationale": {"type": ["string", "null"]},
                    "sourceIds": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                },
                "required": ["title", "rationale", "sourceIds"],
                "additionalProperties": False,
            },
        },
        "actionItems": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "assignee": {"type": ["string", "null"]},
                    "dueDate": {"type": ["string", "null"]},
                    "sourceIds": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                    "confirmationState": {
                        "type": "string",
                        "enum": ["candidate"],
                    },
                },
                "required": [
                    "title",
                    "assignee",
                    "dueDate",
                    "sourceIds",
                    "confirmationState",
                ],
                "additionalProperties": False,
            },
        },
    },
    "required": ["supported", "summary", "decisions", "actionItems"],
    "additionalProperties": False,
}

TASK_CANDIDATES_SCHEMA = {
    "type": "object",
    "properties": {
        "supported": {"type": "boolean"},
        "tasks": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "assignee": {"type": ["string", "null"]},
                    "dueDate": {"type": ["string", "null"]},
                    "sourceIds": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                    "confirmationState": {
                        "type": "string",
                        "enum": ["candidate"],
                    },
                },
                "required": [
                    "title",
                    "assignee",
                    "dueDate",
                    "sourceIds",
                    "confirmationState",
                ],
                "additionalProperties": False,
            },
        },
    },
    "required": ["supported", "tasks"],
    "additionalProperties": False,
}


def strict_json_schema_format(name: str, schema: dict[str, object]) -> dict[str, object]:
    return {
        "type": "json_schema",
        "name": name,
        "schema": schema,
        "strict": True,
    }


@dataclass(frozen=True)
class EvidenceDecision:
    supported: bool
    reason: UnsupportedReason | None = None


@dataclass(frozen=True)
class GroundedAnswer:
    supported: bool
    answer: str
    source_ids: tuple[str, ...]
    reason: UnsupportedReason | None = None


class MalformedGroundedOutput(ValueError):
    pass


def evaluate_evidence(
    relevance_scores: Iterable[float | None],
    *,
    threshold: float = DEFAULT_RELEVANCE_THRESHOLD,
) -> EvidenceDecision:
    scores = tuple(relevance_scores)
    if not scores:
        return EvidenceDecision(supported=False, reason="NO_EVIDENCE")

    if any(score is None for score in scores):
        return EvidenceDecision(supported=True)

    measured_scores = tuple(score for score in scores if score is not None)
    if measured_scores and max(measured_scores) < threshold:
        return EvidenceDecision(supported=False, reason="LOW_RELEVANCE")

    return EvidenceDecision(supported=True)


def parse_grounded_answer(value: str, allowed_source_ids: Iterable[str]) -> GroundedAnswer:
    data = extract_json_object(value)
    supported = data.get("supported")
    if not isinstance(supported, bool):
        raise MalformedGroundedOutput("supported must be a boolean")

    if not supported:
        return GroundedAnswer(
            supported=False,
            answer="",
            source_ids=(),
            reason="MODEL_UNSUPPORTED",
        )

    answer = data.get("answer")
    source_ids = data.get("sourceIds")
    if not isinstance(answer, str) or not answer.strip() or not isinstance(source_ids, list):
        return unverified_answer()

    allowed = set(allowed_source_ids)
    normalized_ids = tuple(dict.fromkeys(str(source_id) for source_id in source_ids))
    if not normalized_ids or any(source_id not in allowed for source_id in normalized_ids):
        return unverified_answer()

    return GroundedAnswer(
        supported=True,
        answer=answer.strip(),
        source_ids=normalized_ids,
    )


def extract_json_object(value: str) -> dict[str, object]:
    stripped = value.strip()
    if stripped.startswith("```"):
        stripped = stripped.strip("`")
        if stripped.startswith("json"):
            stripped = stripped[4:].strip()

    start = stripped.find("{")
    end = stripped.rfind("}")
    if start == -1 or end == -1 or end < start:
        raise MalformedGroundedOutput("JSON object not found")

    try:
        data = json.loads(stripped[start : end + 1])
    except json.JSONDecodeError as error:
        raise MalformedGroundedOutput("invalid JSON object") from error

    if not isinstance(data, dict):
        raise MalformedGroundedOutput("JSON root must be an object")
    return data


def unverified_answer() -> GroundedAnswer:
    return GroundedAnswer(
        supported=False,
        answer="",
        source_ids=(),
        reason="UNVERIFIED_OUTPUT",
    )
