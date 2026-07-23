from contextvars import ContextVar, Token
import json
import logging
import re
from time import perf_counter
from typing import Any, Callable
from uuid import uuid4


TRACE_ID_HEADER = "X-Request-ID"
_TRACE_ID = ContextVar[str | None]("meetingmind_trace_id", default=None)
_TRACE_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{8,128}$")
_SAFE_FIELDS = frozenset(
    {
        "endpoint",
        "durationMs",
        "model",
        "sourceCount",
        "unsupported",
        "unsupportedReason",
        "citationFailure",
        "errorType",
        "scope",
        "resultCount",
        "sourceTypeCount",
        "allowedMeetingCount",
        "jobId",
        "generation",
        "attemptCount",
        "chunkCount",
        "activated",
        "failureCode",
        "willRetry",
        "pendingCount",
        "processingCount",
        "failedCount",
        "oldestPendingAgeSeconds",
        "provider",
        "apiStyle",
        "stream",
        "responseFormatMode",
        "totalMs",
        "ttftMs",
        "tokensPerSecond",
        "inputTokens",
        "outputTokens",
        "outputTokenEstimate",
    }
)


def bind_trace_id(candidate: str | None = None) -> Token[str | None]:
    return _TRACE_ID.set(normalize_trace_id(candidate))


def reset_trace_id(token: Token[str | None]) -> None:
    _TRACE_ID.reset(token)


def current_trace_id() -> str:
    trace_id = _TRACE_ID.get()
    if trace_id:
        return trace_id
    trace_id = uuid4().hex
    _TRACE_ID.set(trace_id)
    return trace_id


def normalize_trace_id(candidate: str | None) -> str:
    normalized = candidate.strip() if candidate else ""
    return normalized if _TRACE_ID_PATTERN.fullmatch(normalized) else uuid4().hex


def log_event(logger: logging.Logger, event: str, *, level: int = logging.INFO, **fields: Any) -> None:
    payload = {
        "event": event,
        "traceId": current_trace_id(),
        **{key: value for key, value in fields.items() if key in _SAFE_FIELDS},
    }
    logger.log(level, json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")))


def observe_ai_endpoint(endpoint: str, operation: Callable[[], Any], *, logger: logging.Logger) -> Any:
    started_at = perf_counter()
    try:
        response = operation()
    except Exception as error:
        log_event(
            logger,
            "ai_request_failed",
            level=logging.WARNING,
            endpoint=endpoint,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
        )
        raise

    log_event(logger, "ai_request_completed", **ai_observability_fields(endpoint, response, elapsed_ms(started_at)))
    return response


def ai_observability_fields(endpoint: str, response: Any, duration_ms: int) -> dict[str, Any]:
    source_count = len(getattr(response, "sources", []) or [])
    unsupported = bool(getattr(response, "unsupported", False))
    reason = unsupported_reason(response, source_count) if unsupported else None
    return {
        "endpoint": endpoint,
        "durationMs": duration_ms,
        "model": getattr(response, "model", None),
        "sourceCount": source_count,
        "unsupported": unsupported,
        "unsupportedReason": reason,
        "citationFailure": reason == "UNVERIFIED_OUTPUT",
    }


def unsupported_reason(response: Any, source_count: int) -> str:
    reason = getattr(response, "unsupportedReason", None)
    if reason:
        return reason
    if source_count == 0:
        return "NO_SOURCES"
    if getattr(response, "sourceType", None) == "none":
        return "NO_EVIDENCE"
    return "UNSUPPORTED_RESPONSE"


def elapsed_ms(started_at: float) -> int:
    return max(0, round((perf_counter() - started_at) * 1000))
