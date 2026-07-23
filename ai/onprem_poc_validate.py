import json
import os
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


RESULT_SCHEMA_VERSION = 2
REQUIRED_SCENARIOS = {
    "text_provider_probe",
    "embedding_provider_probe",
    "retrieval_latency_probe",
    "meeting_ai",
    "project_ai",
    "report",
    "task",
    "meeting_ai_unsupported",
    "project_ai_permission_guard",
}
GENERATION_SCENARIOS = (
    "text_provider_probe",
    "meeting_ai",
    "project_ai",
    "report",
    "task",
)
PLACEHOLDER_MODELS = {
    "local-model",
    "local-llm-model",
    "local-text",
    "local-embedding-model",
    "local-embedding",
    "model",
    "test-model",
}
SENSITIVE_RESULT_KEYS = {
    "apikey",
    "api_key",
    "token",
    "servicetoken",
    "service_token",
    "internalservicetoken",
    "internal_service_token",
    "bearertoken",
    "bearer_token",
    "baseurl",
    "base_url",
    "textbaseurl",
    "text_base_url",
    "embeddingbaseurl",
    "embedding_base_url",
    "databaseurl",
    "database_url",
    "dsn",
    "connectionstring",
    "connection_string",
}


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: python onprem_poc_validate.py <result-json>")
    result = load_result(Path(sys.argv[1]))
    failures = validate_result(result)
    if failures:
        print(json.dumps({"ok": False, "failures": failures}, ensure_ascii=False))
        raise SystemExit(1)
    print(json.dumps({"ok": True, "failures": []}, ensure_ascii=False))


def load_result(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"invalid result json: {type(error).__name__}") from error
    if not isinstance(payload, dict):
        raise SystemExit("invalid result json: root must be an object")
    return payload


def validate_result(result: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    validate_no_sensitive_result_fields(result, failures)
    if result.get("preflightOnly") is True:
        failures.append("preflight-only result cannot be used for final on-prem validation")
    run = object_field(result, "run", failures)
    config = object_field(result, "config", failures)
    summary = object_field(result, "summary", failures)
    metrics = list_field(result, "metrics", failures)
    if failures:
        return failures

    validate_run_metadata(run, failures)

    if summary.get("ok") is not True:
        failures.append("summary.ok must be true")
    if summary.get("scenarioCount") != len(REQUIRED_SCENARIOS):
        failures.append(f"summary.scenarioCount must be {len(REQUIRED_SCENARIOS)}")
    if summary.get("failedScenarios") != []:
        failures.append("summary.failedScenarios must be empty")
    if summary.get("citationSuccessRate") != 1.0:
        failures.append("citationSuccessRate must be 1.0")
    if summary.get("jsonParsingSuccessRate") != 1.0:
        failures.append("jsonParsingSuccessRate must be 1.0")
    if summary.get("unsupportedGuardPassed") is not True:
        failures.append("unsupportedGuardPassed must be true")
    if summary.get("permissionGuardPassed") is not True:
        failures.append("permissionGuardPassed must be true")
    if summary.get("retrievalRequired") is not True:
        failures.append("retrievalRequired must be true for final on-prem validation")
    if summary.get("retrievalRequirementPassed") is not True:
        failures.append("retrievalRequirementPassed must be true")
    if summary.get("retrievalLatencyMeasured") is not True:
        failures.append("retrievalLatencyMeasured must be true")
    if summary.get("hallucinationDetected") is not False:
        failures.append("hallucinationDetected must be false")
    if len(metrics) != len(REQUIRED_SCENARIOS):
        failures.append(f"metrics must contain exactly {len(REQUIRED_SCENARIOS)} scenarios")

    if config.get("textProvider") != "local-openai-compatible":
        failures.append("textProvider must be local-openai-compatible")
    if config.get("textBaseUrlConfigured") is not True:
        failures.append("textBaseUrlConfigured must be true")
    if config.get("textBaseUrlLocalCompatible") is not True:
        failures.append("textBaseUrlLocalCompatible must be true")
    if not non_empty_string(config.get("textModel")):
        failures.append("textModel must be a non-empty string")
    elif is_placeholder_model(config.get("textModel")):
        failures.append("textModel must be replaced with the actual local model name")
    if config.get("textApiStyle") != "chat-completions":
        failures.append("textApiStyle must be chat-completions for TTFT validation")
    if config.get("textStream") is not True:
        failures.append("textStream must be true for TTFT validation")
    if not isinstance(config.get("textStreamOptionsIncludeUsage"), bool):
        failures.append("textStreamOptionsIncludeUsage must be boolean")
    if config.get("textResponseFormatMode") not in ("json_schema", "json_object", "none"):
        failures.append("textResponseFormatMode must be json_schema, json_object, or none")
    if config.get("embeddingProvider") != "local-openai-compatible":
        failures.append("embeddingProvider must be local-openai-compatible")
    if config.get("embeddingBaseUrlConfigured") is not True:
        failures.append("embeddingBaseUrlConfigured must be true")
    if config.get("embeddingBaseUrlLocalCompatible") is not True:
        failures.append("embeddingBaseUrlLocalCompatible must be true")
    if not non_empty_string(config.get("embeddingModel")):
        failures.append("embeddingModel must be a non-empty string")
    elif is_placeholder_model(config.get("embeddingModel")):
        failures.append("embeddingModel must be replaced with the actual local embedding model name")
    if config.get("databaseConfigured") is not True:
        failures.append("databaseConfigured must be true")
    if config.get("internalServiceTokenConfigured") is not True:
        failures.append("internalServiceTokenConfigured must be true")
    if config.get("retrievalRequired") is not True:
        failures.append("config.retrievalRequired must be true for final on-prem validation")
    if summary.get("retrievalRequired") != config.get("retrievalRequired"):
        failures.append("summary.retrievalRequired must match config.retrievalRequired")
    if summary.get("retrievalRequirementPassed") != (
        summary.get("retrievalLatencyMeasured") is True or config.get("retrievalRequired") is not True
    ):
        failures.append("summary.retrievalRequirementPassed must match retrieval requirement")
    if not non_empty_string(config.get("ragProjectId")):
        failures.append("ragProjectId must be a non-empty string")
    if not isinstance(config.get("allowedMeetingCount"), int) or config.get("allowedMeetingCount") <= 0:
        failures.append("allowedMeetingCount must be greater than 0")
    if not isinstance(config.get("embeddingDimension"), int) or config.get("embeddingDimension") <= 0:
        failures.append("embeddingDimension must be greater than 0")
    if not isinstance(config.get("embeddingIncludeDimensions"), bool):
        failures.append("embeddingIncludeDimensions must be boolean")
    if not isinstance(config.get("vectorDimension"), int) or config.get("vectorDimension") <= 0:
        failures.append("vectorDimension must be greater than 0")
    if config.get("embeddingDimension") != config.get("vectorDimension"):
        failures.append("embeddingDimension must match vectorDimension")

    metric_by_name = metrics_by_scenario(metrics, failures)
    missing_scenarios = sorted(REQUIRED_SCENARIOS - set(metric_by_name))
    for scenario in missing_scenarios:
        failures.append(f"missing scenario: {scenario}")
    for scenario, metric in metric_by_name.items():
        if scenario in REQUIRED_SCENARIOS and metric.get("ok") is not True:
            failures.append(f"{scenario}.ok must be true")
    for scenario in REQUIRED_SCENARIOS:
        require_non_negative_number(metric_by_name, scenario, "durationMs", failures)
    validate_summary_consistency(summary, metrics, metric_by_name, failures)
    require_run_duration_covers_summary(run, summary, failures)

    for scenario in GENERATION_SCENARIOS:
        require_generation_metrics(
            metric_by_name,
            scenario,
            failures,
            expected_text_model=config.get("textModel"),
            expected_response_format_mode=config.get("textResponseFormatMode"),
        )
        require_metric_at_most_metric(
            metric_by_name,
            scenario,
            "providerTotalMs",
            "durationMs",
            failures,
        )
    require_positive_number(metric_by_name, "embedding_provider_probe", "itemCount", failures)
    embedding_probe = metric_by_name.get("embedding_provider_probe", {})
    if embedding_probe.get("provider") != "local-openai-compatible":
        failures.append("embedding_provider_probe.provider must be local-openai-compatible")
    if (
        isinstance(config.get("embeddingDimension"), int)
        and embedding_probe.get("itemCount") != config.get("embeddingDimension")
    ):
        failures.append("embedding_provider_probe.itemCount must match embeddingDimension")
    if (
        isinstance(config.get("vectorDimension"), int)
        and embedding_probe.get("itemCount") != config.get("vectorDimension")
    ):
        failures.append("embedding_provider_probe.itemCount must match vectorDimension")
    if embedding_probe.get("modelObserved") is not True:
        failures.append("embedding_provider_probe.modelObserved must be true")
    embedding_probe_model = embedding_probe.get("model")
    if not non_empty_string(embedding_probe_model):
        failures.append("embedding_provider_probe.model must be a non-empty string")
    elif is_placeholder_model(embedding_probe_model):
        failures.append("embedding_provider_probe.model must be the actual local embedding model name")
    elif embedding_probe_model != config.get("embeddingModel"):
        failures.append("embedding_provider_probe.model must match embeddingModel")
    require_number(metric_by_name, "retrieval_latency_probe", "retrievalLatencyMs", failures)
    require_metric_at_most_metric(
        metric_by_name,
        "retrieval_latency_probe",
        "retrievalLatencyMs",
        "durationMs",
        failures,
    )
    require_positive_number(metric_by_name, "retrieval_latency_probe", "sourceCount", failures)
    for scenario in ("meeting_ai", "project_ai", "report", "task"):
        require_positive_number(metric_by_name, scenario, "sourceCount", failures)
        if metric_by_name.get(scenario, {}).get("hallucinationDetected") is not False:
            failures.append(f"{scenario}.hallucinationDetected must be false")
    for scenario in ("report", "task"):
        require_positive_number(metric_by_name, scenario, "itemCount", failures)

    unsupported = metric_by_name.get("meeting_ai_unsupported", {})
    if unsupported.get("unsupported") is not True:
        failures.append("meeting_ai_unsupported.unsupported must be true")
    if unsupported.get("sourceCount") != 0:
        failures.append("meeting_ai_unsupported.sourceCount must be 0")

    permission_guard = metric_by_name.get("project_ai_permission_guard", {})
    if permission_guard.get("errorType") != "HTTPException":
        failures.append("project_ai_permission_guard.errorType must be HTTPException")
    if permission_guard.get("statusCode") != 403:
        failures.append("project_ai_permission_guard.statusCode must be 403")

    apply_thresholds(summary, metric_by_name, failures)
    return failures


def validate_run_metadata(run: dict[str, Any], failures: list[str]) -> None:
    if run.get("resultSchemaVersion") != RESULT_SCHEMA_VERSION:
        failures.append(f"run.resultSchemaVersion must be {RESULT_SCHEMA_VERSION}")
    if run.get("preflightOnly") is not False:
        failures.append("run.preflightOnly must be false")
    if not isinstance(run.get("durationMs"), (int, float)) or run.get("durationMs") < 0:
        failures.append("run.durationMs must be numeric and non-negative")
    started_at = parse_utc_timestamp(run.get("startedAt"))
    completed_at = parse_utc_timestamp(run.get("completedAt"))
    if started_at is None:
        failures.append("run.startedAt must be an ISO-8601 UTC timestamp")
    if completed_at is None:
        failures.append("run.completedAt must be an ISO-8601 UTC timestamp")
    if started_at is not None and completed_at is not None and completed_at < started_at:
        failures.append("run.completedAt must be greater than or equal to run.startedAt")
    min_started_at_raw = os.getenv("ONPREM_POC_MIN_STARTED_AT")
    if min_started_at_raw:
        min_started_at = parse_utc_timestamp(min_started_at_raw)
        if min_started_at is None:
            failures.append("ONPREM_POC_MIN_STARTED_AT must be an ISO-8601 UTC timestamp")
        elif started_at is not None and started_at < min_started_at:
            failures.append("run.startedAt must be greater than or equal to ONPREM_POC_MIN_STARTED_AT")


def require_run_duration_covers_summary(
    run: dict[str, Any],
    summary: dict[str, Any],
    failures: list[str],
) -> None:
    duration_ms = run.get("durationMs")
    max_duration_ms = summary.get("maxDurationMs")
    if (
        isinstance(duration_ms, (int, float))
        and isinstance(max_duration_ms, (int, float))
        and duration_ms < max_duration_ms
    ):
        failures.append("run.durationMs must be greater than or equal to summary.maxDurationMs")


def parse_utc_timestamp(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    normalized = value.strip()
    if normalized.endswith("Z"):
        normalized = normalized[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return None
    return parsed.astimezone(UTC)


def object_field(result: dict[str, Any], key: str, failures: list[str]) -> dict[str, Any]:
    value = result.get(key)
    if not isinstance(value, dict):
        failures.append(f"{key} must be an object")
        return {}
    return value


def list_field(result: dict[str, Any], key: str, failures: list[str]) -> list[Any]:
    value = result.get(key)
    if not isinstance(value, list):
        failures.append(f"{key} must be a list")
        return []
    return value


def non_empty_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def is_placeholder_model(value: Any) -> bool:
    return isinstance(value, str) and value.strip().casefold() in PLACEHOLDER_MODELS


def validate_no_sensitive_result_fields(value: Any, failures: list[str], path: str = "$") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if isinstance(key, str) and normalize_result_key(key) in SENSITIVE_RESULT_KEYS:
                failures.append(f"result must not include sensitive field: {child_path}")
            validate_no_sensitive_result_fields(child, failures, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            validate_no_sensitive_result_fields(child, failures, f"{path}[{index}]")


def normalize_result_key(key: str) -> str:
    return key.replace("-", "_").strip().casefold()


def metrics_by_scenario(metrics: list[Any], failures: list[str]) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for item in metrics:
        if not isinstance(item, dict):
            failures.append("metric item must be an object")
            continue
        scenario = item.get("scenario")
        if not isinstance(scenario, str) or not scenario:
            failures.append("metric scenario must be a non-empty string")
            continue
        if scenario not in REQUIRED_SCENARIOS:
            failures.append(f"unexpected scenario: {scenario}")
        if scenario in indexed:
            failures.append(f"duplicate scenario: {scenario}")
        indexed[scenario] = item
    return indexed


def require_number(
    metric_by_name: dict[str, dict[str, Any]],
    scenario: str,
    field: str,
    failures: list[str],
) -> None:
    value = metric_by_name.get(scenario, {}).get(field)
    if not isinstance(value, (int, float)):
        failures.append(f"{scenario}.{field} must be numeric")


def require_non_negative_number(
    metric_by_name: dict[str, dict[str, Any]],
    scenario: str,
    field: str,
    failures: list[str],
) -> None:
    value = metric_by_name.get(scenario, {}).get(field)
    if not isinstance(value, (int, float)) or value < 0:
        failures.append(f"{scenario}.{field} must be numeric and non-negative")


def require_positive_number(
    metric_by_name: dict[str, dict[str, Any]],
    scenario: str,
    field: str,
    failures: list[str],
) -> None:
    value = metric_by_name.get(scenario, {}).get(field)
    if not isinstance(value, (int, float)) or value <= 0:
        failures.append(f"{scenario}.{field} must be greater than 0")


def require_metric_at_most_metric(
    metric_by_name: dict[str, dict[str, Any]],
    scenario: str,
    field: str,
    maximum_field: str,
    failures: list[str],
) -> None:
    metric = metric_by_name.get(scenario, {})
    value = metric.get(field)
    maximum = metric.get(maximum_field)
    if isinstance(value, (int, float)) and isinstance(maximum, (int, float)) and value > maximum:
        failures.append(f"{scenario}.{field} must be <= {maximum_field}")


def require_generation_metrics(
    metric_by_name: dict[str, dict[str, Any]],
    scenario: str,
    failures: list[str],
    *,
    expected_text_model: Any = None,
    expected_response_format_mode: Any = None,
) -> None:
    metric = metric_by_name.get(scenario, {})
    if metric.get("provider") != "local-openai-compatible":
        failures.append(f"{scenario}.provider must be local-openai-compatible")
    if metric.get("apiStyle") != "chat-completions":
        failures.append(f"{scenario}.apiStyle must be chat-completions")
    if metric.get("stream") is not True:
        failures.append(f"{scenario}.stream must be true")
    if (
        non_empty_string(expected_response_format_mode)
        and metric.get("responseFormatMode") != expected_response_format_mode
    ):
        failures.append(f"{scenario}.responseFormatMode must match textResponseFormatMode")
    require_positive_number(metric_by_name, scenario, "providerTotalMs", failures)
    require_positive_number(metric_by_name, scenario, "tokensPerSecond", failures)
    require_non_negative_number(metric_by_name, scenario, "ttftMs", failures)
    model = metric.get("model")
    if metric.get("modelObserved") is not True:
        failures.append(f"{scenario}.modelObserved must be true")
    if not non_empty_string(model):
        failures.append(f"{scenario}.model must be a non-empty string")
    elif is_placeholder_model(model):
        failures.append(f"{scenario}.model must be the actual local model name")
    elif non_empty_string(expected_text_model) and model != expected_text_model:
        failures.append(f"{scenario}.model must match textModel")


def validate_summary_consistency(
    summary: dict[str, Any],
    metrics: list[Any],
    metric_by_name: dict[str, dict[str, Any]],
    failures: list[str],
) -> None:
    metric_objects = [metric for metric in metrics if isinstance(metric, dict)]
    failed_scenarios = [
        metric.get("scenario")
        for metric in metric_objects
        if isinstance(metric.get("scenario"), str) and metric.get("ok") is not True
    ]
    if summary.get("scenarioCount") != len(metric_objects):
        failures.append("summary.scenarioCount must match metrics length")
    if summary.get("failedScenarios") != failed_scenarios:
        failures.append("summary.failedScenarios must match failed metric scenarios")

    citation_metrics = [
        metric_by_name[name]
        for name in ("meeting_ai", "project_ai", "report", "task")
        if name in metric_by_name
    ]
    if summary.get("citationSuccessRate") != success_rate(citation_metrics):
        failures.append("summary.citationSuccessRate must match citation scenario metrics")

    parsing_metrics = [
        metric_by_name[name]
        for name in GENERATION_SCENARIOS
        if name in metric_by_name
    ]
    if summary.get("jsonParsingSuccessRate") != success_rate(parsing_metrics):
        failures.append("summary.jsonParsingSuccessRate must match generation scenario metrics")

    unsupported = metric_by_name.get("meeting_ai_unsupported")
    if summary.get("unsupportedGuardPassed") is not bool(unsupported and unsupported.get("ok") is True):
        failures.append("summary.unsupportedGuardPassed must match meeting_ai_unsupported")

    permission_guard = metric_by_name.get("project_ai_permission_guard")
    if summary.get("permissionGuardPassed") is not bool(permission_guard and permission_guard.get("ok") is True):
        failures.append("summary.permissionGuardPassed must match project_ai_permission_guard")

    retrieval_latencies = [
        metric.get("retrievalLatencyMs")
        for metric in metric_objects
        if isinstance(metric.get("retrievalLatencyMs"), (int, float))
    ]
    if summary.get("retrievalLatencyMeasured") is not bool(retrieval_latencies):
        failures.append("summary.retrievalLatencyMeasured must match retrieval metrics")
    expected_max_retrieval = max(retrieval_latencies) if retrieval_latencies else None
    if summary.get("maxRetrievalLatencyMs") != expected_max_retrieval:
        failures.append("summary.maxRetrievalLatencyMs must match retrieval metrics")

    hallucination_detected = any(
        metric.get("hallucinationDetected") is True
        for metric in metric_objects
    )
    if summary.get("hallucinationDetected") is not hallucination_detected:
        failures.append("summary.hallucinationDetected must match scenario metrics")

    durations = [
        metric.get("durationMs")
        for metric in metric_objects
        if isinstance(metric.get("durationMs"), (int, float))
    ]
    if summary.get("maxDurationMs") != (max(durations) if durations else 0):
        failures.append("summary.maxDurationMs must match scenario durations")


def success_rate(metrics: list[dict[str, Any]]) -> float:
    if not metrics:
        return 0.0
    return round(sum(1 for metric in metrics if metric.get("ok") is True) / len(metrics), 4)


def apply_thresholds(
    summary: dict[str, Any],
    metric_by_name: dict[str, dict[str, Any]],
    failures: list[str],
) -> None:
    max_ttft_ms = optional_float_env("ONPREM_POC_MAX_TTFT_MS")
    if max_ttft_ms is not None:
        for scenario in GENERATION_SCENARIOS:
            require_at_most(
                metric_by_name.get(scenario, {}).get("ttftMs"),
                max_ttft_ms,
                f"{scenario}.ttftMs",
                failures,
            )

    max_total_ms = optional_float_env("ONPREM_POC_MAX_TOTAL_MS")
    if max_total_ms is not None:
        require_at_most(summary.get("maxDurationMs"), max_total_ms, "summary.maxDurationMs", failures)

    max_retrieval_ms = optional_float_env("ONPREM_POC_MAX_RETRIEVAL_MS")
    if max_retrieval_ms is not None:
        require_at_most(
            summary.get("maxRetrievalLatencyMs"),
            max_retrieval_ms,
            "summary.maxRetrievalLatencyMs",
            failures,
        )

    min_tokens_per_second = optional_float_env("ONPREM_POC_MIN_TOKENS_PER_SECOND")
    if min_tokens_per_second is not None:
        for scenario in GENERATION_SCENARIOS:
            require_at_least(
                metric_by_name.get(scenario, {}).get("tokensPerSecond"),
                min_tokens_per_second,
                f"{scenario}.tokensPerSecond",
                failures,
            )


def optional_float_env(key: str) -> float | None:
    value = os.getenv(key)
    if value is None or not value.strip():
        return None
    try:
        return float(value)
    except ValueError:
        raise SystemExit(f"{key} must be numeric")


def require_at_most(value: Any, threshold: float, label: str, failures: list[str]) -> None:
    if not isinstance(value, (int, float)) or value > threshold:
        failures.append(f"{label} must be <= {threshold:g}")


def require_at_least(value: Any, threshold: float, label: str, failures: list[str]) -> None:
    if not isinstance(value, (int, float)) or value < threshold:
        failures.append(f"{label} must be >= {threshold:g}")


if __name__ == "__main__":
    main()
