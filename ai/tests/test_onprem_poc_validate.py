import unittest
from unittest.mock import patch

import onprem_poc_validate


class OnPremPocValidateTest(unittest.TestCase):
    def test_accepts_complete_local_onprem_result(self):
        failures = onprem_poc_validate.validate_result(valid_result())

        self.assertEqual(failures, [])

    def test_rejects_preflight_only_result(self):
        result = {
            "ok": True,
            "preflightOnly": True,
            "run": {
                "resultSchemaVersion": 2,
                "startedAt": "2026-07-22T03:00:00Z",
                "completedAt": "2026-07-22T03:00:01Z",
                "durationMs": 1,
                "preflightOnly": True,
            },
            "config": valid_result()["config"],
        }

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("preflight-only result cannot be used for final on-prem validation", failures)

    def test_rejects_missing_or_invalid_run_metadata(self):
        missing = valid_result()
        del missing["run"]

        missing_failures = onprem_poc_validate.validate_result(missing)

        self.assertIn("run must be an object", missing_failures)

        invalid = valid_result()
        invalid["run"] = {
            "resultSchemaVersion": 0,
            "startedAt": "not-a-time",
            "completedAt": "2026-07-22T02:59:59Z",
            "durationMs": -1,
            "preflightOnly": True,
        }

        failures = onprem_poc_validate.validate_result(invalid)

        self.assertIn("run.resultSchemaVersion must be 2", failures)
        self.assertIn("run.preflightOnly must be false", failures)
        self.assertIn("run.durationMs must be numeric and non-negative", failures)
        self.assertIn("run.startedAt must be an ISO-8601 UTC timestamp", failures)

    def test_rejects_stale_v1_result_schema(self):
        result = valid_result()
        result["run"]["resultSchemaVersion"] = 1

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("run.resultSchemaVersion must be 2", failures)

    def test_rejects_sensitive_fields_in_result_json(self):
        result = valid_result()
        result["config"]["textBaseUrl"] = "http://llm.internal:8000/v1"
        result["metrics"][0]["apiKey"] = "secret-provider-token"
        result["metrics"][1]["database_url"] = "postgresql://meetingmind:secret@db/meetingmind"

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("result must not include sensitive field: $.config.textBaseUrl", failures)
        self.assertIn("result must not include sensitive field: $.metrics[0].apiKey", failures)
        self.assertIn("result must not include sensitive field: $.metrics[1].database_url", failures)

    def test_rejects_run_completed_before_started(self):
        result = valid_result()
        result["run"]["startedAt"] = "2026-07-22T03:00:01Z"
        result["run"]["completedAt"] = "2026-07-22T03:00:00Z"

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("run.completedAt must be greater than or equal to run.startedAt", failures)

    def test_rejects_result_started_before_wrapper_start_boundary(self):
        with patch.dict(
            "os.environ",
            {"ONPREM_POC_MIN_STARTED_AT": "2026-07-22T03:00:01Z"},
            clear=False,
        ):
            failures = onprem_poc_validate.validate_result(valid_result())

        self.assertIn("run.startedAt must be greater than or equal to ONPREM_POC_MIN_STARTED_AT", failures)

    def test_rejects_invalid_wrapper_start_boundary(self):
        with patch.dict(
            "os.environ",
            {"ONPREM_POC_MIN_STARTED_AT": "not-a-time"},
            clear=False,
        ):
            failures = onprem_poc_validate.validate_result(valid_result())

        self.assertIn("ONPREM_POC_MIN_STARTED_AT must be an ISO-8601 UTC timestamp", failures)

    def test_rejects_run_duration_shorter_than_longest_scenario(self):
        result = valid_result()
        result["run"]["durationMs"] = 119

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("run.durationMs must be greater than or equal to summary.maxDurationMs", failures)

    def test_rejects_openai_provider_and_missing_retrieval_measurement(self):
        result = valid_result()
        result["config"]["textProvider"] = "openai"
        result["config"]["retrievalRequired"] = False
        result["summary"]["retrievalLatencyMeasured"] = False
        result["summary"]["retrievalRequirementPassed"] = False
        result["metrics"][2]["retrievalLatencyMs"] = None

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("textProvider must be local-openai-compatible", failures)
        self.assertIn("config.retrievalRequired must be true for final on-prem validation", failures)
        self.assertIn("retrievalLatencyMeasured must be true", failures)
        self.assertIn("retrievalRequirementPassed must be true", failures)
        self.assertIn("retrieval_latency_probe.retrievalLatencyMs must be numeric", failures)

    def test_rejects_inconsistent_summary_scenario_count_and_failures(self):
        result = valid_result()
        result["summary"]["scenarioCount"] = 8
        result["summary"]["failedScenarios"] = ["meeting_ai"]

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("summary.scenarioCount must be 9", failures)
        self.assertIn("summary.failedScenarios must be empty", failures)
        self.assertIn("summary.scenarioCount must match metrics length", failures)
        self.assertIn("summary.failedScenarios must match failed metric scenarios", failures)

    def test_rejects_summary_values_that_do_not_match_metrics(self):
        result = valid_result()
        result["summary"]["citationSuccessRate"] = 0.75
        result["summary"]["jsonParsingSuccessRate"] = 0.8
        result["summary"]["unsupportedGuardPassed"] = False
        result["summary"]["permissionGuardPassed"] = False
        result["summary"]["retrievalLatencyMeasured"] = False
        result["summary"]["maxRetrievalLatencyMs"] = 99
        result["summary"]["maxDurationMs"] = 999

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("citationSuccessRate must be 1.0", failures)
        self.assertIn("jsonParsingSuccessRate must be 1.0", failures)
        self.assertIn("unsupportedGuardPassed must be true", failures)
        self.assertIn("permissionGuardPassed must be true", failures)
        self.assertIn("retrievalLatencyMeasured must be true", failures)
        self.assertIn("summary.citationSuccessRate must match citation scenario metrics", failures)
        self.assertIn("summary.jsonParsingSuccessRate must match generation scenario metrics", failures)
        self.assertIn("summary.unsupportedGuardPassed must match meeting_ai_unsupported", failures)
        self.assertIn("summary.permissionGuardPassed must match project_ai_permission_guard", failures)
        self.assertIn("summary.retrievalLatencyMeasured must match retrieval metrics", failures)
        self.assertIn("summary.maxRetrievalLatencyMs must match retrieval metrics", failures)
        self.assertIn("summary.maxDurationMs must match scenario durations", failures)

    def test_rejects_missing_or_invalid_metric_duration(self):
        result = valid_result()
        del result["metrics"][0]["durationMs"]
        result["metrics"][8]["durationMs"] = -1

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("text_provider_probe.durationMs must be numeric and non-negative", failures)
        self.assertIn("project_ai_permission_guard.durationMs must be numeric and non-negative", failures)

    def test_rejects_duplicate_unexpected_or_extra_metric_scenarios(self):
        result = valid_result()
        result["metrics"].append({"scenario": "unexpected_probe", "ok": True})
        result["metrics"].append(dict(result["metrics"][0]))

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("metrics must contain exactly 9 scenarios", failures)
        self.assertIn("unexpected scenario: unexpected_probe", failures)
        self.assertIn("duplicate scenario: text_provider_probe", failures)

    def test_rejects_incomplete_local_provider_config(self):
        result = valid_result()
        result["config"]["textBaseUrlConfigured"] = False
        result["config"]["textBaseUrlLocalCompatible"] = False
        result["config"]["textModel"] = ""
        result["config"]["textStreamOptionsIncludeUsage"] = "false"
        result["config"]["textResponseFormatMode"] = "xml"
        result["config"]["embeddingBaseUrlConfigured"] = False
        result["config"]["embeddingBaseUrlLocalCompatible"] = False
        result["config"]["embeddingModel"] = ""
        result["config"]["embeddingIncludeDimensions"] = "false"
        result["config"]["internalServiceTokenConfigured"] = False
        result["config"]["ragProjectId"] = ""
        result["config"]["allowedMeetingCount"] = 0

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("textBaseUrlConfigured must be true", failures)
        self.assertIn("textBaseUrlLocalCompatible must be true", failures)
        self.assertIn("textModel must be a non-empty string", failures)
        self.assertIn("textStreamOptionsIncludeUsage must be boolean", failures)
        self.assertIn("textResponseFormatMode must be json_schema, json_object, or none", failures)
        self.assertIn("embeddingBaseUrlConfigured must be true", failures)
        self.assertIn("embeddingBaseUrlLocalCompatible must be true", failures)
        self.assertIn("embeddingModel must be a non-empty string", failures)
        self.assertIn("embeddingIncludeDimensions must be boolean", failures)
        self.assertIn("internalServiceTokenConfigured must be true", failures)
        self.assertIn("ragProjectId must be a non-empty string", failures)
        self.assertIn("allowedMeetingCount must be greater than 0", failures)

    def test_rejects_summary_retrieval_requirement_inconsistent_with_config(self):
        result = valid_result()
        result["summary"]["retrievalRequired"] = False
        result["summary"]["retrievalRequirementPassed"] = False

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("retrievalRequired must be true for final on-prem validation", failures)
        self.assertIn("retrievalRequirementPassed must be true", failures)
        self.assertIn("summary.retrievalRequired must match config.retrievalRequired", failures)
        self.assertIn("summary.retrievalRequirementPassed must match retrieval requirement", failures)

    def test_rejects_template_placeholder_models_for_final_validation(self):
        result = valid_result()
        result["config"]["textModel"] = "local-model"
        result["config"]["embeddingModel"] = "local-embedding-model"
        result["metrics"][0]["model"] = "local-model"
        result["metrics"][0]["modelObserved"] = True
        result["metrics"][1]["model"] = "local-embedding-model"
        result["metrics"][1]["modelObserved"] = True

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("textModel must be replaced with the actual local model name", failures)
        self.assertIn("embeddingModel must be replaced with the actual local embedding model name", failures)
        self.assertIn("text_provider_probe.model must be the actual local model name", failures)
        self.assertIn("embedding_provider_probe.model must be the actual local embedding model name", failures)

    def test_rejects_text_probe_without_observed_response_model(self):
        result = valid_result()
        result["metrics"][0]["modelObserved"] = False
        result["metrics"][3]["modelObserved"] = False

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("text_provider_probe.modelObserved must be true", failures)
        self.assertIn("meeting_ai.modelObserved must be true", failures)

    def test_rejects_embedding_probe_without_observed_response_model(self):
        result = valid_result()
        result["metrics"][1]["modelObserved"] = False

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("embedding_provider_probe.modelObserved must be true", failures)

    def test_rejects_probe_model_mismatch(self):
        result = valid_result()
        result["metrics"][0]["model"] = "other-text-model"
        result["metrics"][1]["model"] = "other-embedding-model"
        result["metrics"][4]["model"] = "other-text-model"

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("text_provider_probe.model must match textModel", failures)
        self.assertIn("embedding_provider_probe.model must match embeddingModel", failures)
        self.assertIn("project_ai.model must match textModel", failures)

    def test_rejects_hallucination_and_missing_required_scenario(self):
        result = valid_result()
        result["summary"]["hallucinationDetected"] = True
        result["metrics"] = [metric for metric in result["metrics"] if metric["scenario"] != "task"]

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("hallucinationDetected must be false", failures)
        self.assertIn("missing scenario: task", failures)

    def test_rejects_non_streaming_text_mode_for_final_ttft_validation(self):
        result = valid_result()
        result["config"]["textApiStyle"] = "responses"
        result["config"]["textStream"] = False
        result["metrics"][0]["apiStyle"] = "responses"
        result["metrics"][0]["stream"] = False

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("textApiStyle must be chat-completions for TTFT validation", failures)
        self.assertIn("textStream must be true for TTFT validation", failures)
        self.assertIn("text_provider_probe.apiStyle must be chat-completions", failures)
        self.assertIn("text_provider_probe.stream must be true", failures)

    def test_rejects_missing_generation_metrics_for_core_scenarios(self):
        result = valid_result()
        del result["metrics"][3]["providerTotalMs"]
        result["metrics"][4]["stream"] = False
        del result["metrics"][5]["ttftMs"]
        result["metrics"][6]["tokensPerSecond"] = 0
        result["metrics"][6]["responseFormatMode"] = "json_object"
        result["metrics"][0]["ttftMs"] = -1

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("meeting_ai.providerTotalMs must be greater than 0", failures)
        self.assertIn("project_ai.stream must be true", failures)
        self.assertIn("report.ttftMs must be numeric and non-negative", failures)
        self.assertIn("text_provider_probe.ttftMs must be numeric and non-negative", failures)
        self.assertIn("task.tokensPerSecond must be greater than 0", failures)
        self.assertIn("task.responseFormatMode must match textResponseFormatMode", failures)

    def test_rejects_metrics_that_exceed_scenario_duration(self):
        result = valid_result()
        result["metrics"][2]["retrievalLatencyMs"] = 23
        result["metrics"][3]["providerTotalMs"] = 81

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("retrieval_latency_probe.retrievalLatencyMs must be <= durationMs", failures)
        self.assertIn("meeting_ai.providerTotalMs must be <= durationMs", failures)

    def test_rejects_embedding_probe_dimension_mismatch(self):
        result = valid_result()
        result["metrics"][1]["itemCount"] = 1024

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("embedding_provider_probe.itemCount must match embeddingDimension", failures)
        self.assertIn("embedding_provider_probe.itemCount must match vectorDimension", failures)

    def test_rejects_non_positive_config_dimensions(self):
        result = valid_result()
        result["config"]["embeddingDimension"] = 0
        result["config"]["vectorDimension"] = -1

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("embeddingDimension must be greater than 0", failures)
        self.assertIn("vectorDimension must be greater than 0", failures)
        self.assertIn("embeddingDimension must match vectorDimension", failures)

    def test_rejects_embedding_probe_from_non_local_provider(self):
        result = valid_result()
        result["metrics"][1]["provider"] = "openai"

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("embedding_provider_probe.provider must be local-openai-compatible", failures)

    def test_rejects_retrieval_probe_without_sources(self):
        result = valid_result()
        result["metrics"][2]["sourceCount"] = 0

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("retrieval_latency_probe.sourceCount must be greater than 0", failures)

    def test_rejects_report_or_task_without_generated_items(self):
        result = valid_result()
        result["metrics"][5]["itemCount"] = 0
        result["metrics"][6]["itemCount"] = 0

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("report.itemCount must be greater than 0", failures)
        self.assertIn("task.itemCount must be greater than 0", failures)

    def test_rejects_permission_guard_without_forbidden_status(self):
        result = valid_result()
        result["metrics"][-1]["statusCode"] = 401
        result["metrics"][-1]["errorType"] = "RuntimeError"

        failures = onprem_poc_validate.validate_result(result)

        self.assertIn("project_ai_permission_guard.errorType must be HTTPException", failures)
        self.assertIn("project_ai_permission_guard.statusCode must be 403", failures)

    def test_accepts_configured_performance_thresholds_when_result_is_within_bounds(self):
        with patch.dict(
            "os.environ",
            {
                "ONPREM_POC_MAX_TTFT_MS": "10",
                "ONPREM_POC_MAX_TOTAL_MS": "500",
                "ONPREM_POC_MAX_RETRIEVAL_MS": "50",
                "ONPREM_POC_MIN_TOKENS_PER_SECOND": "10",
            },
            clear=False,
        ):
            failures = onprem_poc_validate.validate_result(valid_result())

        self.assertEqual(failures, [])

    def test_rejects_configured_performance_threshold_violations(self):
        with patch.dict(
            "os.environ",
            {
                "ONPREM_POC_MAX_TTFT_MS": "4",
                "ONPREM_POC_MAX_TOTAL_MS": "119",
                "ONPREM_POC_MAX_RETRIEVAL_MS": "20",
                "ONPREM_POC_MIN_TOKENS_PER_SECOND": "21",
            },
            clear=False,
        ):
            failures = onprem_poc_validate.validate_result(valid_result())

        self.assertIn("text_provider_probe.ttftMs must be <= 4", failures)
        self.assertIn("meeting_ai.ttftMs must be <= 4", failures)
        self.assertIn("project_ai.ttftMs must be <= 4", failures)
        self.assertIn("report.ttftMs must be <= 4", failures)
        self.assertIn("task.ttftMs must be <= 4", failures)
        self.assertIn("summary.maxDurationMs must be <= 119", failures)
        self.assertIn("summary.maxRetrievalLatencyMs must be <= 20", failures)
        self.assertIn("text_provider_probe.tokensPerSecond must be >= 21", failures)
        self.assertIn("meeting_ai.tokensPerSecond must be >= 21", failures)
        self.assertIn("project_ai.tokensPerSecond must be >= 21", failures)
        self.assertIn("report.tokensPerSecond must be >= 21", failures)
        self.assertIn("task.tokensPerSecond must be >= 21", failures)


def valid_result():
    return {
        "run": {
            "resultSchemaVersion": 2,
            "startedAt": "2026-07-22T03:00:00Z",
            "completedAt": "2026-07-22T03:00:01Z",
            "durationMs": 120,
            "preflightOnly": False,
        },
        "config": {
            "textProvider": "local-openai-compatible",
            "textBaseUrlConfigured": True,
            "textBaseUrlLocalCompatible": True,
            "textModel": "qwen2.5-14b-instruct-awq",
            "textApiStyle": "chat-completions",
            "textStream": True,
            "textStreamOptionsIncludeUsage": False,
            "textResponseFormatMode": "json_schema",
            "embeddingProvider": "local-openai-compatible",
            "embeddingBaseUrlConfigured": True,
            "embeddingBaseUrlLocalCompatible": True,
            "embeddingModel": "bge-m3",
            "embeddingDimension": 1536,
            "embeddingIncludeDimensions": False,
            "vectorDimension": 1536,
            "databaseConfigured": True,
            "internalServiceTokenConfigured": True,
            "retrievalRequired": True,
            "ragProjectId": "smoke-space",
            "allowedMeetingCount": 1,
        },
        "summary": {
            "ok": True,
            "scenarioCount": 9,
            "failedScenarios": [],
            "citationSuccessRate": 1.0,
            "jsonParsingSuccessRate": 1.0,
            "unsupportedGuardPassed": True,
            "permissionGuardPassed": True,
            "retrievalLatencyMeasured": True,
            "retrievalRequired": True,
            "retrievalRequirementPassed": True,
            "maxRetrievalLatencyMs": 21,
            "hallucinationDetected": False,
            "maxDurationMs": 120,
        },
        "metrics": [
            {
                "scenario": "text_provider_probe",
                "ok": True,
                "durationMs": 30,
                "provider": "local-openai-compatible",
                "apiStyle": "chat-completions",
                "stream": True,
                "responseFormatMode": "json_schema",
                "model": "qwen2.5-14b-instruct-awq",
                "modelObserved": True,
                "providerTotalMs": 28,
                "ttftMs": 5,
                "tokensPerSecond": 20.0,
            },
            {
                "scenario": "embedding_provider_probe",
                "ok": True,
                "durationMs": 15,
                "provider": "local-openai-compatible",
                "itemCount": 1536,
                "model": "bge-m3",
                "modelObserved": True,
            },
            {
                "scenario": "retrieval_latency_probe",
                "ok": True,
                "durationMs": 22,
                "sourceCount": 4,
                "retrievalLatencyMs": 21,
            },
            {
                "scenario": "meeting_ai",
                "ok": True,
                "durationMs": 80,
                "provider": "local-openai-compatible",
                "apiStyle": "chat-completions",
                "stream": True,
                "responseFormatMode": "json_schema",
                "model": "qwen2.5-14b-instruct-awq",
                "modelObserved": True,
                "providerTotalMs": 78,
                "ttftMs": 7,
                "tokensPerSecond": 18.0,
                "sourceCount": 1,
                "hallucinationDetected": False,
            },
            {
                "scenario": "project_ai",
                "ok": True,
                "durationMs": 90,
                "provider": "local-openai-compatible",
                "apiStyle": "chat-completions",
                "stream": True,
                "responseFormatMode": "json_schema",
                "model": "qwen2.5-14b-instruct-awq",
                "modelObserved": True,
                "providerTotalMs": 88,
                "ttftMs": 8,
                "tokensPerSecond": 17.0,
                "sourceCount": 1,
                "hallucinationDetected": False,
            },
            {
                "scenario": "report",
                "ok": True,
                "durationMs": 120,
                "provider": "local-openai-compatible",
                "apiStyle": "chat-completions",
                "stream": True,
                "responseFormatMode": "json_schema",
                "model": "qwen2.5-14b-instruct-awq",
                "modelObserved": True,
                "providerTotalMs": 118,
                "ttftMs": 9,
                "tokensPerSecond": 16.0,
                "sourceCount": 2,
                "itemCount": 2,
                "hallucinationDetected": False,
            },
            {
                "scenario": "task",
                "ok": True,
                "durationMs": 70,
                "provider": "local-openai-compatible",
                "apiStyle": "chat-completions",
                "stream": True,
                "responseFormatMode": "json_schema",
                "model": "qwen2.5-14b-instruct-awq",
                "modelObserved": True,
                "providerTotalMs": 68,
                "ttftMs": 6,
                "tokensPerSecond": 19.0,
                "sourceCount": 1,
                "itemCount": 1,
                "hallucinationDetected": False,
            },
            {
                "scenario": "meeting_ai_unsupported",
                "ok": True,
                "durationMs": 35,
                "provider": "local-openai-compatible",
                "apiStyle": "chat-completions",
                "stream": True,
                "providerTotalMs": 33,
                "ttftMs": 5,
                "tokensPerSecond": 21.0,
                "unsupported": True,
                "sourceCount": 0,
            },
            {
                "scenario": "project_ai_permission_guard",
                "ok": True,
                "durationMs": 3,
                "errorType": "HTTPException",
                "statusCode": 403,
            },
        ],
    }


if __name__ == "__main__":
    unittest.main()
