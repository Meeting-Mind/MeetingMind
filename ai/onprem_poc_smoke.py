import json
import os
import time
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable

from fastapi import HTTPException

from app.config import get_env
from app.embedding_provider import create_embedding_provider
from app.main import (
    BackendExtractTasksRequest,
    BackendGenerateReportRequest,
    BackendMeetingAiChatRequest,
    BackendMeetingAiSource,
    BackendProjectAiChatRequest,
    BackendProjectAiSource,
    ParticipantItem,
    RagSearchRequest,
    backend_extract_tasks,
    backend_generate_report,
    backend_meeting_chat,
    backend_project_chat,
    search_postgres_sources,
)
from app.grounding import GROUNDED_ANSWER_SCHEMA, strict_json_schema_format
from app.provider_url import local_provider_base_url_error, local_provider_base_url_is_compatible
from app.text_generation_provider import collect_text_generation_metrics, get_text_generation_provider


PLACEHOLDER_MODELS = {
    "local-model",
    "local-llm-model",
    "local-text",
    "local-embedding-model",
    "local-embedding",
    "model",
    "test-model",
}
RESULT_SCHEMA_VERSION = 2


@dataclass(frozen=True)
class SmokeRun:
    resultSchemaVersion: int
    startedAt: str
    completedAt: str
    durationMs: int
    preflightOnly: bool


@dataclass(frozen=True)
class SmokeMetric:
    scenario: str
    ok: bool
    durationMs: int
    unsupported: bool | None = None
    sourceCount: int | None = None
    itemCount: int | None = None
    model: str | None = None
    modelObserved: bool | None = None
    provider: str | None = None
    apiStyle: str | None = None
    stream: bool | None = None
    responseFormatMode: str | None = None
    providerTotalMs: int | None = None
    ttftMs: int | None = None
    tokensPerSecond: float | None = None
    inputTokens: int | None = None
    outputTokens: int | None = None
    outputTokenEstimate: int | None = None
    retrievalLatencyMs: int | None = None
    hallucinationDetected: bool | None = None
    errorType: str | None = None
    statusCode: int | None = None


@dataclass(frozen=True)
class SmokeSummary:
    ok: bool
    scenarioCount: int
    failedScenarios: list[str]
    citationSuccessRate: float
    jsonParsingSuccessRate: float
    unsupportedGuardPassed: bool
    permissionGuardPassed: bool
    retrievalLatencyMeasured: bool
    retrievalRequired: bool
    retrievalRequirementPassed: bool
    maxRetrievalLatencyMs: int | None
    hallucinationDetected: bool
    maxDurationMs: int


@dataclass(frozen=True)
class SmokeConfig:
    textProvider: str | None
    textBaseUrlConfigured: bool
    textBaseUrlLocalCompatible: bool
    textModel: str | None
    textApiStyle: str | None
    textStream: bool
    textStreamOptionsIncludeUsage: bool
    textResponseFormatMode: str | None
    embeddingProvider: str | None
    embeddingBaseUrlConfigured: bool
    embeddingBaseUrlLocalCompatible: bool
    embeddingModel: str | None
    embeddingDimension: int | None
    embeddingIncludeDimensions: bool
    vectorDimension: int | None
    databaseConfigured: bool
    internalServiceTokenConfigured: bool
    retrievalRequired: bool
    ragProjectId: str | None
    allowedMeetingCount: int


def main() -> None:
    started_at = time.perf_counter()
    started_wall_time = utc_now_iso()
    require_opt_in()
    if parse_bool(get_env("ONPREM_POC_PREFLIGHT_ONLY", "false")):
        output = {
            "ok": True,
            "preflightOnly": True,
            "run": asdict(smoke_run(started_at, started_wall_time, preflight_only=True)),
            "config": asdict(smoke_config()),
        }
        write_result_if_requested(output)
        print(json.dumps(output, ensure_ascii=False))
        return
    metrics = [
        run_provider_probe(),
        run_embedding_probe(),
        run_retrieval_latency_probe(),
        run_scenario("meeting_ai", meeting_ai_scenario),
        run_scenario("project_ai", project_ai_scenario),
        run_scenario("report", report_scenario),
        run_scenario("task", task_scenario),
        run_expected_unsupported("meeting_ai_unsupported", meeting_ai_unsupported_scenario),
        run_permission_guard(),
    ]
    summary = summarize(metrics)
    output = {
        "run": asdict(smoke_run(started_at, started_wall_time, preflight_only=False)),
        "config": asdict(smoke_config()),
        "summary": asdict(summary),
        "metrics": [asdict(metric) for metric in metrics],
    }
    write_result_if_requested(output)
    print(json.dumps(output, ensure_ascii=False))
    if not summary.ok:
        raise SystemExit(1)


def require_opt_in() -> None:
    if os.getenv("RUN_ONPREM_AI_POC_SMOKE") != "true":
        raise SystemExit("RUN_ONPREM_AI_POC_SMOKE=true is required")
    failures = validate_smoke_configuration()
    if failures:
        raise SystemExit("; ".join(failures))


def smoke_run(started_at: float, started_wall_time: str, *, preflight_only: bool) -> SmokeRun:
    return SmokeRun(
        resultSchemaVersion=RESULT_SCHEMA_VERSION,
        startedAt=started_wall_time,
        completedAt=utc_now_iso(),
        durationMs=elapsed_ms(started_at),
        preflightOnly=preflight_only,
    )


def utc_now_iso() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


def validate_smoke_configuration() -> list[str]:
    config = smoke_config()
    failures: list[str] = []
    text_provider = normalize_provider(config.textProvider)
    embedding_provider = normalize_provider(config.embeddingProvider)
    final_smoke = not parse_bool(get_env("ONPREM_POC_PREFLIGHT_ONLY", "false"))

    if text_provider not in ("openai", "local-openai-compatible"):
        failures.append("AI_TEXT_PROVIDER must be openai or local-openai-compatible")
    if embedding_provider not in ("openai", "local-openai-compatible"):
        failures.append("AI_EMBEDDING_PROVIDER must be openai or local-openai-compatible")
    if final_smoke and text_provider != "local-openai-compatible":
        failures.append("AI_TEXT_PROVIDER must be local-openai-compatible for final on-prem smoke")
    if final_smoke and embedding_provider != "local-openai-compatible":
        failures.append("AI_EMBEDDING_PROVIDER must be local-openai-compatible for final on-prem smoke")

    if text_provider == "openai" and not get_env("OPENAI_API_KEY"):
        failures.append("OPENAI_API_KEY is required for AI_TEXT_PROVIDER=openai")
    if text_provider == "local-openai-compatible":
        if not config.textBaseUrlConfigured:
            failures.append("AI_TEXT_BASE_URL is required for local text provider smoke")
        else:
            failures.extend(validate_local_provider_base_url("AI_TEXT_BASE_URL", get_env("AI_TEXT_BASE_URL")))
        if not config.textModel:
            failures.append("AI_TEXT_MODEL is required for local text provider smoke")
        elif final_smoke and is_placeholder_model(config.textModel):
            failures.append("AI_TEXT_MODEL must be replaced with the actual local model name")
        if config.textApiStyle not in ("responses", "chat-completions"):
            failures.append("AI_TEXT_API_STYLE must be responses or chat-completions")
        if config.textResponseFormatMode not in ("json_schema", "json_object", "none"):
            failures.append("AI_TEXT_RESPONSE_FORMAT_MODE must be json_schema, json_object, or none")
        if final_smoke and config.textApiStyle != "chat-completions":
            failures.append("AI_TEXT_API_STYLE must be chat-completions for final on-prem smoke TTFT validation")
        if final_smoke and not config.textStream:
            failures.append("AI_TEXT_STREAM=true is required for final on-prem smoke TTFT validation")

    if embedding_provider == "openai" and not get_env("OPENAI_API_KEY"):
        failures.append("OPENAI_API_KEY is required for AI_EMBEDDING_PROVIDER=openai")
    if embedding_provider == "local-openai-compatible":
        if not config.embeddingBaseUrlConfigured:
            failures.append("AI_EMBEDDING_BASE_URL is required for local embedding provider smoke")
        else:
            failures.extend(validate_local_provider_base_url("AI_EMBEDDING_BASE_URL", get_env("AI_EMBEDDING_BASE_URL")))
        if not config.embeddingModel:
            failures.append("AI_EMBEDDING_MODEL is required for local embedding provider smoke")
        elif final_smoke and is_placeholder_model(config.embeddingModel):
            failures.append("AI_EMBEDDING_MODEL must be replaced with the actual local embedding model name")

    if config.embeddingDimension is None:
        failures.append("embedding dimension must be a valid integer")
    elif config.embeddingDimension <= 0:
        failures.append("embedding dimension must be greater than 0")
    if config.vectorDimension is None:
        failures.append("AI_VECTOR_DIMENSION must be a valid integer")
    elif config.vectorDimension <= 0:
        failures.append("AI_VECTOR_DIMENSION must be greater than 0")
    if (
        config.embeddingDimension is not None
        and config.vectorDimension is not None
        and config.embeddingDimension > 0
        and config.vectorDimension > 0
        and config.embeddingDimension != config.vectorDimension
    ):
        failures.append(
            "embedding dimension must match AI_VECTOR_DIMENSION; "
            "run schema migration and existing embedding job generation/swap reindex before switching dimensions"
        )
    retrieval_project_id = get_env("ONPREM_POC_PROJECT_ID")
    retrieval_allowed_meeting_ids = parse_allowed_meeting_ids(get_env("ONPREM_POC_ALLOWED_MEETING_IDS"))
    if config.retrievalRequired and not config.databaseConfigured:
        failures.append("AI_DATABASE_URL is required when ONPREM_POC_REQUIRE_RETRIEVAL=true")
    if config.retrievalRequired and not retrieval_project_id:
        failures.append("ONPREM_POC_PROJECT_ID is required when ONPREM_POC_REQUIRE_RETRIEVAL=true")
    if config.retrievalRequired and len(retrieval_allowed_meeting_ids) <= 0:
        failures.append("ONPREM_POC_ALLOWED_MEETING_IDS must contain at least one meeting id when retrieval is required")
    if final_smoke and not config.internalServiceTokenConfigured:
        failures.append("AI_INTERNAL_SERVICE_TOKEN is required for final on-prem smoke")
    return failures


def smoke_config() -> SmokeConfig:
    text_provider = normalize_provider(get_env("AI_TEXT_PROVIDER", "openai"))
    embedding_provider = normalize_provider(get_env("AI_EMBEDDING_PROVIDER", "openai"))
    text_model = (
        get_env("OPENAI_MODEL", "gpt-4.1-mini")
        if text_provider == "openai"
        else get_env("AI_TEXT_MODEL", "")
    )
    text_api_style = "responses" if text_provider == "openai" else get_env("AI_TEXT_API_STYLE", "responses") or "responses"
    text_stream = False if text_provider == "openai" else parse_bool(get_env("AI_TEXT_STREAM", "false"))
    text_stream_options_include_usage = (
        False if text_provider == "openai" else parse_bool(get_env("AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE", "false"))
    )
    text_response_format_mode = (
        "json_schema"
        if text_provider == "openai"
        else get_env("AI_TEXT_RESPONSE_FORMAT_MODE", "json_schema") or "json_schema"
    )
    embedding_model = (
        get_env("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")
        if embedding_provider == "openai"
        else get_env("AI_EMBEDDING_MODEL", "")
    )
    embedding_dimension = parse_optional_int(
        get_env("OPENAI_EMBEDDING_DIMENSION", "1536")
        if embedding_provider == "openai"
        else get_env("AI_EMBEDDING_DIMENSION")
    )
    embedding_include_dimensions = (
        parse_bool(get_env("OPENAI_EMBEDDING_INCLUDE_DIMENSIONS", "true"))
        if embedding_provider == "openai"
        else parse_bool(get_env("AI_EMBEDDING_INCLUDE_DIMENSIONS", "false"))
    )
    return SmokeConfig(
        textProvider=text_provider,
        textBaseUrlConfigured=bool(get_env("AI_TEXT_BASE_URL")),
        textBaseUrlLocalCompatible=local_provider_base_url_is_compatible(get_env("AI_TEXT_BASE_URL")),
        textModel=text_model,
        textApiStyle=text_api_style,
        textStream=text_stream,
        textStreamOptionsIncludeUsage=text_stream_options_include_usage,
        textResponseFormatMode=text_response_format_mode,
        embeddingProvider=embedding_provider,
        embeddingBaseUrlConfigured=bool(get_env("AI_EMBEDDING_BASE_URL")),
        embeddingBaseUrlLocalCompatible=local_provider_base_url_is_compatible(get_env("AI_EMBEDDING_BASE_URL")),
        embeddingModel=embedding_model,
        embeddingDimension=embedding_dimension,
        embeddingIncludeDimensions=embedding_include_dimensions,
        vectorDimension=parse_optional_int(get_env("AI_VECTOR_DIMENSION", "1536")),
        databaseConfigured=bool(get_env("AI_DATABASE_URL")),
        internalServiceTokenConfigured=bool(get_env("AI_INTERNAL_SERVICE_TOKEN")),
        retrievalRequired=parse_bool(get_env("ONPREM_POC_REQUIRE_RETRIEVAL", "false")),
        ragProjectId=get_env("ONPREM_POC_PROJECT_ID"),
        allowedMeetingCount=len(parse_allowed_meeting_ids(get_env("ONPREM_POC_ALLOWED_MEETING_IDS"))),
    )


def normalize_provider(provider: str | None) -> str:
    normalized = (provider or "").strip().casefold()
    if normalized in ("local", "openai-compatible"):
        return "local-openai-compatible"
    return normalized


def is_placeholder_model(value: str | None) -> bool:
    return str(value or "").strip().casefold() in PLACEHOLDER_MODELS


def validate_local_provider_base_url(env_key: str, value: str | None) -> list[str]:
    error = local_provider_base_url_error(value, label=env_key)
    return [] if error is None else [error]


def write_result_if_requested(output: dict[str, object]) -> None:
    result_path = get_env("ONPREM_POC_RESULT_PATH")
    if not result_path:
        return
    target = Path(result_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run_provider_probe() -> SmokeMetric:
    started_at = time.perf_counter()
    try:
        with collect_text_generation_metrics() as metrics:
            result = get_text_generation_provider().generate(
                "너는 MeetingMind provider smoke test다. JSON만 반환한다.",
                '반드시 {"supported":true,"answer":"ok","sourceIds":["smoke-source"]} 만 반환해라.',
                timeout_seconds=30,
                response_format=strict_json_schema_format("meetingmind_onprem_smoke", GROUNDED_ANSWER_SCHEMA),
            )
        ok = provider_probe_response_is_valid(result.text) and bool(result.model)
        return with_provider_metrics(SmokeMetric(
            scenario="text_provider_probe",
            ok=bool(ok),
            durationMs=elapsed_ms(started_at),
            model=result.model,
        ), metrics)
    except Exception as error:
        return SmokeMetric(
            scenario="text_provider_probe",
            ok=False,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
        )


def provider_probe_response_is_valid(text: str) -> bool:
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return False
    if not isinstance(payload, dict):
        return False
    source_ids = payload.get("sourceIds")
    return (
        payload.get("supported") is True
        and isinstance(payload.get("answer"), str)
        and bool(payload.get("answer").strip())
        and isinstance(source_ids, list)
        and source_ids == ["smoke-source"]
    )


def run_embedding_probe() -> SmokeMetric:
    started_at = time.perf_counter()
    try:
        provider = create_embedding_provider()
        vector = provider.embed(["MeetingMind 온프레 embedding smoke"])[0]
        return SmokeMetric(
            scenario="embedding_provider_probe",
            ok=len(vector) == provider.dimension,
            durationMs=elapsed_ms(started_at),
            itemCount=len(vector),
            model=getattr(provider, "last_response_model", None) or provider.model,
            modelObserved=getattr(provider, "last_response_model_observed", False),
            provider=getattr(provider, "provider_id", None),
        )
    except Exception as error:
        return SmokeMetric(
            scenario="embedding_provider_probe",
            ok=False,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
        )


def run_retrieval_latency_probe() -> SmokeMetric:
    started_at = time.perf_counter()
    if not get_env("AI_DATABASE_URL"):
        return SmokeMetric(
            scenario="retrieval_latency_probe",
            ok=True,
            durationMs=elapsed_ms(started_at),
            errorType="SKIPPED_NO_AI_DATABASE_URL",
        )

    retrieval_started_at = time.perf_counter()
    try:
        sources = search_postgres_sources(
            RagSearchRequest(
                query=get_env("ONPREM_POC_RAG_QUERY", "온프레 AI PoC 출시 일정과 QA 마감") or "",
                scope="project",
                projectId=get_env("ONPREM_POC_PROJECT_ID", "smoke-space") or "",
                allowedMeetingIds=parse_allowed_meeting_ids(get_env("ONPREM_POC_ALLOWED_MEETING_IDS", "smoke-meeting")),
                sourceTypes=(
                    "projectKnowledge",
                    "transcript",
                    "meetingSummary",
                    "decision",
                    "actionItem",
                    "report",
                ),
                limit=8,
            )
        )
        return SmokeMetric(
            scenario="retrieval_latency_probe",
            ok=True,
            durationMs=elapsed_ms(started_at),
            sourceCount=len(sources),
            retrievalLatencyMs=elapsed_ms(retrieval_started_at),
        )
    except Exception as error:
        return SmokeMetric(
            scenario="retrieval_latency_probe",
            ok=False,
            durationMs=elapsed_ms(started_at),
            retrievalLatencyMs=elapsed_ms(retrieval_started_at),
            errorType=type(error).__name__,
        )


def run_scenario(name: str, scenario: Callable[[], object]) -> SmokeMetric:
    started_at = time.perf_counter()
    try:
        with collect_text_generation_metrics() as metrics:
            response = scenario()
        return with_provider_metrics(SmokeMetric(
            scenario=name,
            ok=not bool(getattr(response, "unsupported", False)),
            durationMs=elapsed_ms(started_at),
            unsupported=bool(getattr(response, "unsupported", False)),
            sourceCount=len(getattr(response, "sources", []) or []),
            itemCount=response_item_count(response),
            model=getattr(response, "model", None),
            hallucinationDetected=detect_hallucination_proxy(name, response),
        ), metrics)
    except Exception as error:
        return SmokeMetric(
            scenario=name,
            ok=False,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
        )


def run_expected_unsupported(name: str, scenario: Callable[[], object]) -> SmokeMetric:
    started_at = time.perf_counter()
    try:
        with collect_text_generation_metrics() as metrics:
            response = scenario()
        unsupported = bool(getattr(response, "unsupported", False))
        return with_provider_metrics(SmokeMetric(
            scenario=name,
            ok=unsupported and len(getattr(response, "sources", []) or []) == 0,
            durationMs=elapsed_ms(started_at),
            unsupported=unsupported,
            sourceCount=len(getattr(response, "sources", []) or []),
            model=getattr(response, "model", None),
            hallucinationDetected=not unsupported or len(getattr(response, "sources", []) or []) > 0,
        ), metrics)
    except Exception as error:
        return SmokeMetric(
            scenario=name,
            ok=False,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
        )


def run_permission_guard() -> SmokeMetric:
    started_at = time.perf_counter()
    try:
        backend_project_chat(
            BackendProjectAiChatRequest(
                projectId="smoke-space",
                question="허용되지 않은 회의 내용을 알려 주세요.",
                allowedMeetingIds=["smoke-meeting"],
                history=[],
                sources=[
                    BackendProjectAiSource(
                        sourceId="forbidden-segment-001",
                        type="transcript",
                        projectId="smoke-space",
                        meetingId="forbidden-meeting",
                        title="권한 밖 회의",
                        text="이 source는 allowedMeetingIds 밖에 있으므로 AI 컨텍스트에 들어가면 안 된다.",
                    )
                ],
            )
        )
    except HTTPException as error:
        return SmokeMetric(
            scenario="project_ai_permission_guard",
            ok=error.status_code == 403,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
            statusCode=error.status_code,
        )
    except Exception as error:
        return SmokeMetric(
            scenario="project_ai_permission_guard",
            ok=False,
            durationMs=elapsed_ms(started_at),
            errorType=type(error).__name__,
        )
    return SmokeMetric(
        scenario="project_ai_permission_guard",
        ok=False,
        durationMs=elapsed_ms(started_at),
    )


def meeting_ai_scenario() -> object:
    return backend_meeting_chat(
        BackendMeetingAiChatRequest(
            projectId="smoke-space",
            meetingId="smoke-meeting",
            meetingTitle="온프레 AI PoC 회의",
            question="모바일 앱 QA 완료",
            sources=meeting_sources(),
        )
    )


def meeting_ai_unsupported_scenario() -> object:
    return backend_meeting_chat(
        BackendMeetingAiChatRequest(
            projectId="smoke-space",
            meetingId="smoke-meeting",
            meetingTitle="온프레 AI PoC 회의",
            question="해외 지사 예산은 얼마인가요?",
            sources=[
                BackendMeetingAiSource(
                    sourceId="irrelevant-segment-001",
                    type="transcript",
                    projectId="smoke-space",
                    meetingId="smoke-meeting",
                    title="온프레 AI PoC 회의",
                    speaker="민지",
                    time="00:03:00",
                    text="회의실 정리와 다음 회의실 예약 방법을 안내했습니다.",
                )
            ],
        )
    )


def project_ai_scenario() -> object:
    return backend_project_chat(
        BackendProjectAiChatRequest(
            projectId="smoke-space",
            question="오로라 프로젝트 출시 목표와 모바일 앱 QA 완료 일정을 요약해 주세요.",
            allowedMeetingIds=["smoke-meeting"],
            history=[],
            sources=[
                BackendProjectAiSource(
                    sourceId="knowledge-001",
                    type="projectKnowledge",
                    projectId="smoke-space",
                    title="출시 정책",
                    text="오로라 프로젝트는 2026년 9월 18일 출시를 목표로 한다.",
                ),
                BackendProjectAiSource(
                    sourceId="meeting-summary-001",
                    type="meetingSummary",
                    projectId="smoke-space",
                    meetingId="smoke-meeting",
                    title="온프레 AI PoC 회의",
                    text="모바일 앱 QA는 2026년 9월 12일까지 완료하고 출시 공지는 9월 17일 예약한다.",
                ),
            ],
        )
    )


def report_scenario() -> object:
    return backend_generate_report(
        BackendGenerateReportRequest(
            projectId="smoke-space",
            meetingId="smoke-meeting",
            title="온프레 AI PoC 회의",
            sources=meeting_sources(),
        )
    )


def task_scenario() -> object:
    return backend_extract_tasks(
        BackendExtractTasksRequest(
            projectId="smoke-space",
            meetingId="smoke-meeting",
            title="온프레 AI PoC 회의",
            participants=[
                ParticipantItem(name="민지", role="QA"),
                ParticipantItem(name="태훈", role="운영"),
            ],
            sources=meeting_sources(),
        )
    )


def meeting_sources() -> list[BackendMeetingAiSource]:
    return [
        BackendMeetingAiSource(
            sourceId="segment-001",
            type="transcript",
            projectId="smoke-space",
            meetingId="smoke-meeting",
            title="온프레 AI PoC 회의",
            speaker="민지",
            time="00:01:00",
            text="오로라 서비스 출시일은 2026년 9월 18일로 확정했습니다.",
        ),
        BackendMeetingAiSource(
            sourceId="segment-002",
            type="transcript",
            projectId="smoke-space",
            meetingId="smoke-meeting",
            title="온프레 AI PoC 회의",
            speaker="태훈",
            time="00:02:00",
            text="모바일 앱 QA는 2026년 9월 12일까지 완료하고 운영 대시보드는 태훈이 점검합니다.",
        ),
        BackendMeetingAiSource(
            sourceId="decision-001",
            type="decision",
            projectId="smoke-space",
            meetingId="smoke-meeting",
            title="출시 일정",
            text="출시일은 2026년 9월 18일, 공지 예약일은 2026년 9월 17일로 결정했습니다.",
        ),
        BackendMeetingAiSource(
            sourceId="action-001",
            type="actionItem",
            projectId="smoke-space",
            meetingId="smoke-meeting",
            title="QA 마감",
            text="민지는 2026년 9월 12일까지 모바일 앱 QA를 완료합니다.",
        ),
    ]


def response_item_count(response: object) -> int | None:
    if hasattr(response, "tasks"):
        return len(getattr(response, "tasks") or [])
    if hasattr(response, "decisions") or hasattr(response, "actionItems"):
        return len(getattr(response, "decisions", []) or []) + len(getattr(response, "actionItems", []) or [])
    return None


def summarize(metrics: list[SmokeMetric]) -> SmokeSummary:
    metric_by_name = {metric.scenario: metric for metric in metrics}
    failed = [metric.scenario for metric in metrics if not metric.ok]
    citation_scenarios = [
        metric_by_name[name]
        for name in ("meeting_ai", "project_ai", "report", "task")
        if name in metric_by_name
    ]
    parsing_scenarios = [
        metric_by_name[name]
        for name in ("text_provider_probe", "meeting_ai", "project_ai", "report", "task")
        if name in metric_by_name
    ]
    unsupported_guard = metric_by_name.get("meeting_ai_unsupported")
    permission_guard = metric_by_name.get("project_ai_permission_guard")
    retrieval_metrics = [
        metric
        for metric in metrics
        if metric.retrievalLatencyMs is not None
    ]
    retrieval_measured = bool(retrieval_metrics)
    retrieval_required = parse_bool(get_env("ONPREM_POC_REQUIRE_RETRIEVAL", "false"))
    hallucination_detected = any(
        bool(metric.hallucinationDetected)
        for metric in metrics
        if metric.hallucinationDetected is not None
    )
    retrieval_requirement_passed = retrieval_measured or not retrieval_required
    return SmokeSummary(
        ok=not failed and retrieval_requirement_passed and not hallucination_detected,
        scenarioCount=len(metrics),
        failedScenarios=failed,
        citationSuccessRate=success_rate(citation_scenarios),
        jsonParsingSuccessRate=success_rate(parsing_scenarios),
        unsupportedGuardPassed=bool(unsupported_guard and unsupported_guard.ok),
        permissionGuardPassed=bool(permission_guard and permission_guard.ok),
        retrievalLatencyMeasured=retrieval_measured,
        retrievalRequired=retrieval_required,
        retrievalRequirementPassed=retrieval_requirement_passed,
        maxRetrievalLatencyMs=max((metric.retrievalLatencyMs for metric in retrieval_metrics), default=None),
        hallucinationDetected=hallucination_detected,
        maxDurationMs=max((metric.durationMs for metric in metrics), default=0),
    )


def success_rate(metrics: list[SmokeMetric]) -> float:
    if not metrics:
        return 0.0
    return round(sum(1 for metric in metrics if metric.ok) / len(metrics), 4)


def with_provider_metrics(metric: SmokeMetric, metrics: list[object]) -> SmokeMetric:
    if not metrics:
        return metric
    provider_metric = metrics[-1]
    return SmokeMetric(
        scenario=metric.scenario,
        ok=metric.ok,
        durationMs=metric.durationMs,
        unsupported=metric.unsupported,
        sourceCount=metric.sourceCount,
        itemCount=metric.itemCount,
        model=metric.model,
        modelObserved=getattr(provider_metric, "modelObserved", None),
        provider=getattr(provider_metric, "provider", None),
        apiStyle=getattr(provider_metric, "apiStyle", None),
        stream=getattr(provider_metric, "stream", None),
        responseFormatMode=getattr(provider_metric, "responseFormatMode", None),
        providerTotalMs=getattr(provider_metric, "totalMs", None),
        ttftMs=getattr(provider_metric, "ttftMs", None),
        tokensPerSecond=getattr(provider_metric, "tokensPerSecond", None),
        inputTokens=getattr(provider_metric, "inputTokens", None),
        outputTokens=getattr(provider_metric, "outputTokens", None),
        outputTokenEstimate=getattr(provider_metric, "outputTokenEstimate", None),
        retrievalLatencyMs=metric.retrievalLatencyMs,
        hallucinationDetected=metric.hallucinationDetected,
        errorType=metric.errorType,
        statusCode=metric.statusCode,
    )


def detect_hallucination_proxy(name: str, response: object) -> bool:
    if name not in ("meeting_ai", "project_ai", "report", "task"):
        return False
    if bool(getattr(response, "unsupported", False)):
        return True
    if hasattr(response, "sources") and len(getattr(response, "sources") or []) == 0:
        return True
    if hasattr(response, "tasks") and len(getattr(response, "tasks") or []) == 0:
        return True
    if (hasattr(response, "decisions") or hasattr(response, "actionItems")) and response_item_count(response) == 0:
        return True
    return False


def elapsed_ms(started_at: float) -> int:
    return max(0, round((time.perf_counter() - started_at) * 1000))


def parse_bool(value: str | None) -> bool:
    return str(value or "").strip().casefold() in ("1", "true", "yes", "y", "on")


def parse_optional_int(value: str | None) -> int | None:
    try:
        return int(value) if value is not None and str(value).strip() else None
    except ValueError:
        return None


def parse_allowed_meeting_ids(value: str | None) -> tuple[str, ...]:
    return tuple(meeting_id.strip() for meeting_id in (value or "").split(",") if meeting_id.strip())


if __name__ == "__main__":
    main()
