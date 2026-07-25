from contextvars import ContextVar, Token
import json
import logging
import re
from time import perf_counter
from typing import Any, Callable
from uuid import uuid4
from prometheus_client import Counter, Gauge, Histogram, generate_latest, CONTENT_TYPE_LATEST


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
        # 예외의 정규화된 타입 이름만 담는다. 메시지는 담지 않는다(`failure_detail_for` 참고).
        "failureDetail",
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

AI_REQUEST_DURATION = Histogram(
    "meetingmind_ai_request_duration_ms",
    "AI endpoint duration in milliseconds",
    ["endpoint"],
    buckets=(25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000, 60000),
)
AI_REQUEST_TOTAL = Counter(
    "meetingmind_ai_requests_total",
    "AI endpoint request count",
    ["endpoint", "outcome"],
)
AI_REQUEST_SOURCES = Histogram(
    "meetingmind_ai_request_source_count",
    "Number of sources used by AI responses",
    ["endpoint"],
    buckets=(0, 1, 2, 3, 5, 8, 13, 21, 34, 55),
)
AI_PROVIDER_TOTAL = Counter(
    "meetingmind_ai_provider_requests_total",
    "AI provider request count",
    ["provider", "api_style", "stream", "outcome"],
)
AI_PROVIDER_DURATION = Histogram(
    "meetingmind_ai_provider_total_ms",
    "AI provider total processing time in milliseconds",
    ["provider", "api_style", "stream"],
    buckets=(50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000, 60000),
)
AI_PROVIDER_TOKENS = Counter(
    "meetingmind_ai_provider_tokens_total",
    "AI provider token usage",
    ["provider", "api_style", "stream", "direction"],
)
AI_RAG_RETRIEVAL_DURATION = Histogram(
    "meetingmind_ai_rag_retrieval_duration_ms",
    "RAG retrieval duration in milliseconds",
    ["scope", "outcome"],
    buckets=(10, 25, 50, 100, 250, 500, 1000, 2500, 5000),
)
AI_RAG_RETRIEVAL_RESULTS = Histogram(
    "meetingmind_ai_rag_retrieval_result_count",
    "RAG retrieval result count",
    ["scope"],
    buckets=(0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89),
)
AI_EMBEDDING_QUEUE = Gauge(
    "meetingmind_ai_embedding_queue",
    "Embedding queue snapshot",
    ["status"],
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
        duration_ms = elapsed_ms(started_at)
        AI_REQUEST_TOTAL.labels(endpoint=endpoint, outcome="failed").inc()
        AI_REQUEST_DURATION.labels(endpoint=endpoint).observe(duration_ms)
        log_event(
            logger,
            "ai_request_failed",
            level=logging.WARNING,
            endpoint=endpoint,
            durationMs=duration_ms,
            errorType=type(error).__name__,
        )
        raise

    duration_ms = elapsed_ms(started_at)
    fields = ai_observability_fields(endpoint, response, duration_ms)
    AI_REQUEST_TOTAL.labels(endpoint=endpoint, outcome="completed").inc()
    AI_REQUEST_DURATION.labels(endpoint=endpoint).observe(duration_ms)
    AI_REQUEST_SOURCES.labels(endpoint=endpoint).observe(fields["sourceCount"])
    log_event(logger, "ai_request_completed", **fields)
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


def record_provider_completed(
    *,
    provider: str | None,
    api_style: str | None,
    stream: bool | None,
    total_ms: int | None,
    input_tokens: int | None,
    output_tokens: int | None,
    output_token_estimate: int | None,
) -> None:
    provider_value = provider or "unknown"
    api_style_value = api_style or "unknown"
    stream_value = "true" if bool(stream) else "false"
    AI_PROVIDER_TOTAL.labels(
        provider=provider_value,
        api_style=api_style_value,
        stream=stream_value,
        outcome="completed",
    ).inc()
    if total_ms is not None:
        AI_PROVIDER_DURATION.labels(
            provider=provider_value,
            api_style=api_style_value,
            stream=stream_value,
        ).observe(total_ms)
    if input_tokens is not None:
        AI_PROVIDER_TOKENS.labels(
            provider=provider_value,
            api_style=api_style_value,
            stream=stream_value,
            direction="input",
        ).inc(input_tokens)
    if output_tokens is not None:
        AI_PROVIDER_TOKENS.labels(
            provider=provider_value,
            api_style=api_style_value,
            stream=stream_value,
            direction="output",
        ).inc(output_tokens)
    elif output_token_estimate is not None:
        AI_PROVIDER_TOKENS.labels(
            provider=provider_value,
            api_style=api_style_value,
            stream=stream_value,
            direction="output_estimate",
        ).inc(output_token_estimate)


def record_provider_failed(*, provider: str | None, api_style: str | None, stream: bool | None) -> None:
    AI_PROVIDER_TOTAL.labels(
        provider=provider or "unknown",
        api_style=api_style or "unknown",
        stream="true" if bool(stream) else "false",
        outcome="failed",
    ).inc()


def record_retrieval(scope: str, duration_ms: int, result_count: int | None, *, outcome: str) -> None:
    AI_RAG_RETRIEVAL_DURATION.labels(scope=scope, outcome=outcome).observe(duration_ms)
    if outcome == "completed" and result_count is not None:
        AI_RAG_RETRIEVAL_RESULTS.labels(scope=scope).observe(result_count)


def record_embedding_queue(*, pending_count: int, processing_count: int, failed_count: int) -> None:
    AI_EMBEDDING_QUEUE.labels(status="pending").set(pending_count)
    AI_EMBEDDING_QUEUE.labels(status="processing").set(processing_count)
    AI_EMBEDDING_QUEUE.labels(status="failed").set(failed_count)


def prometheus_payload() -> tuple[bytes, str]:
    return generate_latest(), CONTENT_TYPE_LATEST
