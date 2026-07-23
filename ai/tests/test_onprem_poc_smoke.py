import os
import tempfile
import unittest
from dataclasses import asdict, dataclass, field
from pathlib import Path
from unittest.mock import patch

from fastapi import HTTPException

import onprem_poc_smoke
from onprem_poc_validate import SENSITIVE_RESULT_KEYS, normalize_result_key


def assert_no_sensitive_result_keys(test_case, value, path="$"):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            test_case.assertNotIn(normalize_result_key(str(key)), SENSITIVE_RESULT_KEYS, child_path)
            assert_no_sensitive_result_keys(test_case, child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            assert_no_sensitive_result_keys(test_case, child, f"{path}[{index}]")


@dataclass(frozen=True)
class FakeTextMetrics:
    provider: str = "fake-provider"
    apiStyle: str = "chat-completions"
    stream: bool = True
    responseFormatMode: str = "json_schema"
    totalMs: int = 11
    ttftMs: int = 3
    tokensPerSecond: float = 12.5
    inputTokens: int = 5
    outputTokens: int = 2
    outputTokenEstimate: int | None = None


@dataclass(frozen=True)
class FakeTextResult:
    text: str = '{"supported":true,"answer":"ok","sourceIds":["smoke-source"]}'
    model: str = "fake-text-model"
    metrics: FakeTextMetrics = field(default_factory=FakeTextMetrics)


class FakeTextProvider:
    def __init__(self, text='{"supported":true,"answer":"ok","sourceIds":["smoke-source"]}'):
        self.text = text
        self.calls = []

    def generate(self, *args, **kwargs):
        self.calls.append((args, kwargs))
        result = FakeTextResult(text=self.text)
        from app.text_generation_provider import record_text_generation_metrics

        record_text_generation_metrics(result.metrics)
        return result


class FakeEmbeddingProvider:
    provider_id = "fake-embedding-provider"
    model = "fake-embedding-model"
    dimension = 3

    def embed(self, texts):
        return [[0.1, 0.2, 0.3] for _ in texts]


class FakeResponse:
    def __init__(self, *, unsupported=False, sources=None, model="fake-model", tasks=None):
        self.unsupported = unsupported
        self.sources = sources or []
        self.model = model
        self.tasks = tasks or []


class OnPremPocSmokeTest(unittest.TestCase):
    def test_requires_explicit_opt_in(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(SystemExit) as raised:
            onprem_poc_smoke.require_opt_in()

        self.assertIn("RUN_ONPREM_AI_POC_SMOKE=true", str(raised.exception))

    def test_final_smoke_requires_local_text_and_embedding_providers(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "openai",
                "AI_EMBEDDING_PROVIDER": "openai",
                "OPENAI_API_KEY": "openai-key",
                "OPENAI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("AI_TEXT_PROVIDER must be local-openai-compatible", message)
        self.assertIn("AI_EMBEDDING_PROVIDER must be local-openai-compatible", message)

    def test_local_provider_opt_in_requires_base_url(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        self.assertIn("AI_TEXT_BASE_URL", str(raised.exception))

    def test_local_provider_opt_in_requires_embedding_base_url(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        self.assertIn("AI_EMBEDDING_BASE_URL", str(raised.exception))

    def test_local_provider_opt_in_rejects_public_openai_or_invalid_base_urls(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "https://api.openai.com/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("AI_TEXT_BASE_URL must not point to api.openai.com", message)
        self.assertIn("AI_EMBEDDING_BASE_URL must be an absolute http(s) URL", message)

    def test_local_provider_opt_in_rejects_base_urls_with_credentials_or_query(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "https://token:secret@llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "https://embedding.internal:8001/v1?api_key=secret",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("AI_TEXT_BASE_URL must not include userinfo credentials", message)
        self.assertIn("AI_EMBEDDING_BASE_URL must not include query or fragment", message)
        self.assertNotIn("token:secret", message)
        self.assertNotIn("api_key=secret", message)

    def test_local_provider_opt_in_requires_embedding_dimension_match(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "768",
                "AI_VECTOR_DIMENSION": "1536",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("embedding dimension must match AI_VECTOR_DIMENSION", message)
        self.assertIn("embedding job generation/swap reindex", message)

    def test_local_provider_opt_in_requires_positive_embedding_and_vector_dimensions(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "0",
                "AI_VECTOR_DIMENSION": "-1",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("embedding dimension must be greater than 0", message)
        self.assertIn("AI_VECTOR_DIMENSION must be greater than 0", message)

    def test_local_provider_opt_in_accepts_complete_local_config(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                "ONPREM_POC_PROJECT_ID": "space-1",
                "ONPREM_POC_ALLOWED_MEETING_IDS": "meeting-1",
            }.get(key, default)

            onprem_poc_smoke.require_opt_in()

    def test_final_local_provider_opt_in_requires_streaming_chat_completions_for_ttft(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "responses",
                "AI_TEXT_STREAM": "false",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("AI_TEXT_API_STYLE must be chat-completions", message)
        self.assertIn("AI_TEXT_STREAM=true is required", message)

    def test_final_local_provider_opt_in_requires_internal_service_token(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        self.assertIn("AI_INTERNAL_SERVICE_TOKEN is required", str(raised.exception))

    def test_retrieval_required_preflight_requires_project_and_allowed_meeting_scope(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                "ONPREM_POC_PROJECT_ID": "",
                "ONPREM_POC_ALLOWED_MEETING_IDS": "",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("ONPREM_POC_PROJECT_ID is required", message)
        self.assertIn("ONPREM_POC_ALLOWED_MEETING_IDS must contain at least one meeting id", message)

    def test_retrieval_required_preflight_requires_explicit_project_and_meeting_scope(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct-awq",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "bge-m3",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("ONPREM_POC_PROJECT_ID is required", message)
        self.assertIn("ONPREM_POC_ALLOWED_MEETING_IDS must contain at least one meeting id", message)

    def test_final_local_provider_opt_in_rejects_placeholder_model_names(self):
        with (
            patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
            patch("onprem_poc_smoke.get_env") as get_env,
            self.assertRaises(SystemExit) as raised,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "local-llm-model",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding-model",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)
            onprem_poc_smoke.require_opt_in()

        message = str(raised.exception)
        self.assertIn("AI_TEXT_MODEL must be replaced with the actual local model name", message)
        self.assertIn("AI_EMBEDDING_MODEL must be replaced with the actual local embedding model name", message)

    def test_preflight_only_writes_config_without_provider_calls(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            result_path = str(Path(tmpdir) / "preflight.json")
            env = {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "local-text",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_schema",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "AI_DATABASE_URL": "postgresql://meetingmind",
                "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                "ONPREM_POC_PREFLIGHT_ONLY": "true",
                "ONPREM_POC_RESULT_PATH": result_path,
                "ONPREM_POC_PROJECT_ID": "space-1",
                "ONPREM_POC_ALLOWED_MEETING_IDS": "meeting-1,meeting-2",
            }
            with (
                patch.dict(os.environ, {"RUN_ONPREM_AI_POC_SMOKE": "true"}, clear=True),
                patch("onprem_poc_smoke.get_env", side_effect=lambda key, default=None: env.get(key, default)),
                patch("onprem_poc_smoke.run_provider_probe") as provider_probe,
                patch("onprem_poc_smoke.run_embedding_probe") as embedding_probe,
                patch("onprem_poc_smoke.run_retrieval_latency_probe") as retrieval_probe,
                patch("builtins.print"),
            ):
                onprem_poc_smoke.main()

            payload = Path(result_path).read_text(encoding="utf-8")

        self.assertIn('"preflightOnly": true', payload)
        self.assertIn('"run": {', payload)
        self.assertIn('"resultSchemaVersion": 2', payload)
        self.assertIn('"preflightOnly": true', payload)
        self.assertIn('"internalServiceTokenConfigured": true', payload)
        self.assertNotIn("service-secret", payload)
        provider_probe.assert_not_called()
        embedding_probe.assert_not_called()
        retrieval_probe.assert_not_called()

    def test_provider_probe_uses_structured_response_format(self):
        provider = FakeTextProvider()

        with patch("onprem_poc_smoke.get_text_generation_provider", return_value=provider):
            metric = onprem_poc_smoke.run_provider_probe()

        self.assertTrue(metric.ok)
        self.assertEqual(metric.model, "fake-text-model")
        self.assertEqual(metric.provider, "fake-provider")
        self.assertEqual(metric.apiStyle, "chat-completions")
        self.assertTrue(metric.stream)
        self.assertEqual(metric.responseFormatMode, "json_schema")
        self.assertEqual(metric.providerTotalMs, 11)
        self.assertEqual(metric.ttftMs, 3)
        self.assertEqual(metric.tokensPerSecond, 12.5)
        kwargs = provider.calls[0][1]
        self.assertEqual(kwargs["response_format"]["type"], "json_schema")
        self.assertEqual(kwargs["response_format"]["name"], "meetingmind_onprem_smoke")

    def test_provider_probe_requires_parseable_grounded_json_shape(self):
        malformed_provider = FakeTextProvider('{"supported":true')
        wrong_shape_provider = FakeTextProvider('{"supported":true,"answer":"","sourceIds":[]}')

        with patch("onprem_poc_smoke.get_text_generation_provider", return_value=malformed_provider):
            malformed = onprem_poc_smoke.run_provider_probe()
        with patch("onprem_poc_smoke.get_text_generation_provider", return_value=wrong_shape_provider):
            wrong_shape = onprem_poc_smoke.run_provider_probe()

        self.assertFalse(malformed.ok)
        self.assertFalse(wrong_shape.ok)

    def test_smoke_config_excludes_secrets_and_reports_runtime_shape(self):
        with patch("onprem_poc_smoke.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_API_KEY": "secret-text-token",
                "AI_TEXT_MODEL": "local-text",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_object",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_API_KEY": "secret-embedding-token",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_EMBEDDING_INCLUDE_DIMENSIONS": "false",
                "AI_VECTOR_DIMENSION": "1024",
                "AI_DATABASE_URL": "postgresql://secret-db",
                "AI_INTERNAL_SERVICE_TOKEN": "secret-service-token",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
                "ONPREM_POC_PROJECT_ID": "space-1",
                "ONPREM_POC_ALLOWED_MEETING_IDS": "meeting-1,meeting-2",
            }.get(key, default)
            config = onprem_poc_smoke.smoke_config()

        self.assertEqual(config.textProvider, "local-openai-compatible")
        self.assertTrue(config.textBaseUrlConfigured)
        self.assertTrue(config.textBaseUrlLocalCompatible)
        self.assertEqual(config.textModel, "local-text")
        self.assertEqual(config.textApiStyle, "chat-completions")
        self.assertTrue(config.textStream)
        self.assertTrue(config.textStreamOptionsIncludeUsage)
        self.assertEqual(config.textResponseFormatMode, "json_object")
        self.assertEqual(config.embeddingProvider, "local-openai-compatible")
        self.assertTrue(config.embeddingBaseUrlConfigured)
        self.assertTrue(config.embeddingBaseUrlLocalCompatible)
        self.assertEqual(config.embeddingDimension, 1024)
        self.assertFalse(config.embeddingIncludeDimensions)
        self.assertEqual(config.vectorDimension, 1024)
        self.assertTrue(config.databaseConfigured)
        self.assertTrue(config.internalServiceTokenConfigured)
        self.assertTrue(config.retrievalRequired)
        self.assertEqual(config.allowedMeetingCount, 2)
        self.assertNotIn("secret", str(config))
        assert_no_sensitive_result_keys(self, asdict(config))

    def test_smoke_config_canonicalizes_local_provider_aliases(self):
        with patch("onprem_poc_smoke.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "local-text",
                "AI_EMBEDDING_PROVIDER": "openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
            }.get(key, default)

            config = onprem_poc_smoke.smoke_config()

        self.assertEqual(config.textProvider, "local-openai-compatible")
        self.assertEqual(config.embeddingProvider, "local-openai-compatible")

    def test_smoke_config_reports_effective_openai_provider_shape(self):
        with patch("onprem_poc_smoke.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "openai",
                "AI_TEXT_MODEL": "ignored-local-text",
                "AI_TEXT_API_STYLE": "chat-completions",
                "AI_TEXT_STREAM": "true",
                "AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE": "true",
                "AI_TEXT_RESPONSE_FORMAT_MODE": "json_object",
                "OPENAI_MODEL": "openai-text",
                "AI_EMBEDDING_PROVIDER": "openai",
                "AI_EMBEDDING_MODEL": "ignored-local-embedding",
                "AI_EMBEDDING_DIMENSION": "1024",
                "AI_EMBEDDING_INCLUDE_DIMENSIONS": "false",
                "OPENAI_EMBEDDING_MODEL": "openai-embedding",
                "OPENAI_EMBEDDING_DIMENSION": "1536",
                "OPENAI_EMBEDDING_INCLUDE_DIMENSIONS": "true",
                "AI_VECTOR_DIMENSION": "1536",
            }.get(key, default)
            config = onprem_poc_smoke.smoke_config()

        self.assertEqual(config.textProvider, "openai")
        self.assertFalse(config.textBaseUrlLocalCompatible)
        self.assertEqual(config.textModel, "openai-text")
        self.assertEqual(config.textApiStyle, "responses")
        self.assertFalse(config.textStream)
        self.assertFalse(config.textStreamOptionsIncludeUsage)
        self.assertEqual(config.textResponseFormatMode, "json_schema")
        self.assertEqual(config.embeddingProvider, "openai")
        self.assertFalse(config.embeddingBaseUrlLocalCompatible)
        self.assertEqual(config.embeddingModel, "openai-embedding")
        self.assertEqual(config.embeddingDimension, 1536)
        self.assertTrue(config.embeddingIncludeDimensions)

    def test_smoke_config_does_not_invent_retrieval_scope_defaults(self):
        with patch("onprem_poc_smoke.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_MODEL": "local-text",
                "AI_EMBEDDING_PROVIDER": "local-openai-compatible",
                "AI_EMBEDDING_BASE_URL": "http://embedding.internal:8001/v1",
                "AI_EMBEDDING_MODEL": "local-embedding",
                "AI_EMBEDDING_DIMENSION": "1536",
                "AI_VECTOR_DIMENSION": "1536",
                "ONPREM_POC_REQUIRE_RETRIEVAL": "true",
            }.get(key, default)

            config = onprem_poc_smoke.smoke_config()

        self.assertTrue(config.retrievalRequired)
        self.assertIsNone(config.ragProjectId)
        self.assertEqual(config.allowedMeetingCount, 0)

    def test_write_result_if_requested_writes_json_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target = Path(tmpdir) / "nested" / "onprem-result.json"
            with patch("onprem_poc_smoke.get_env", return_value=str(target)):
                onprem_poc_smoke.write_result_if_requested({"summary": {"ok": True}})

            payload = target.read_text(encoding="utf-8")

        self.assertIn('"ok": true', payload)

    def test_embedding_probe_reports_dimension(self):
        with patch("onprem_poc_smoke.create_embedding_provider", return_value=FakeEmbeddingProvider()):
            metric = onprem_poc_smoke.run_embedding_probe()

        self.assertTrue(metric.ok)
        self.assertEqual(metric.provider, "fake-embedding-provider")
        self.assertEqual(metric.itemCount, 3)
        self.assertEqual(metric.model, "fake-embedding-model")

    def test_retrieval_latency_probe_skips_without_database_url(self):
        with patch("onprem_poc_smoke.get_env", return_value=None):
            metric = onprem_poc_smoke.run_retrieval_latency_probe()

        self.assertTrue(metric.ok)
        self.assertIsNone(metric.retrievalLatencyMs)
        self.assertEqual(metric.errorType, "SKIPPED_NO_AI_DATABASE_URL")

    def test_retrieval_latency_probe_measures_postgres_search(self):
        with (
            patch("onprem_poc_smoke.get_env") as get_env,
            patch("onprem_poc_smoke.search_postgres_sources", return_value=[object(), object()]),
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_DATABASE_URL": "postgresql://example",
                "ONPREM_POC_RAG_QUERY": "출시 일정",
                "ONPREM_POC_PROJECT_ID": "space-1",
                "ONPREM_POC_ALLOWED_MEETING_IDS": "meeting-1, meeting-2",
            }.get(key, default)
            metric = onprem_poc_smoke.run_retrieval_latency_probe()

        self.assertTrue(metric.ok)
        self.assertEqual(metric.sourceCount, 2)
        self.assertIsNotNone(metric.retrievalLatencyMs)

    def test_expected_unsupported_requires_no_sources(self):
        metric = onprem_poc_smoke.run_expected_unsupported(
            "unsupported",
            lambda: FakeResponse(unsupported=True, sources=[]),
        )
        with_sources = onprem_poc_smoke.run_expected_unsupported(
            "unsupported",
            lambda: FakeResponse(unsupported=True, sources=[object()]),
        )

        self.assertTrue(metric.ok)
        self.assertFalse(with_sources.ok)

    def test_permission_guard_accepts_only_forbidden_scope_error(self):
        with patch("onprem_poc_smoke.backend_project_chat", side_effect=HTTPException(status_code=403)):
            forbidden = onprem_poc_smoke.run_permission_guard()
        with patch("onprem_poc_smoke.backend_project_chat", return_value=object()):
            accepted = onprem_poc_smoke.run_permission_guard()

        self.assertTrue(forbidden.ok)
        self.assertEqual(forbidden.statusCode, 403)
        self.assertFalse(accepted.ok)

    def test_summary_reports_safety_and_quality_rates(self):
        metrics = [
            onprem_poc_smoke.SmokeMetric("text_provider_probe", True, 10),
            onprem_poc_smoke.SmokeMetric("embedding_provider_probe", True, 8),
            onprem_poc_smoke.SmokeMetric("retrieval_latency_probe", True, 7, retrievalLatencyMs=6),
            onprem_poc_smoke.SmokeMetric("meeting_ai", True, 20, sourceCount=1),
            onprem_poc_smoke.SmokeMetric("project_ai", True, 25, sourceCount=1),
            onprem_poc_smoke.SmokeMetric("report", True, 30, sourceCount=2, itemCount=2),
            onprem_poc_smoke.SmokeMetric("task", True, 18, sourceCount=1, itemCount=1),
            onprem_poc_smoke.SmokeMetric("meeting_ai_unsupported", True, 3, unsupported=True, sourceCount=0),
            onprem_poc_smoke.SmokeMetric(
                "project_ai_permission_guard",
                True,
                1,
                errorType="HTTPException",
                statusCode=403,
            ),
        ]

        summary = onprem_poc_smoke.summarize(metrics)

        self.assertTrue(summary.ok)
        self.assertEqual(summary.scenarioCount, 9)
        self.assertEqual(summary.failedScenarios, [])
        self.assertEqual(summary.citationSuccessRate, 1.0)
        self.assertEqual(summary.jsonParsingSuccessRate, 1.0)
        self.assertTrue(summary.unsupportedGuardPassed)
        self.assertTrue(summary.permissionGuardPassed)
        self.assertTrue(summary.retrievalLatencyMeasured)
        self.assertFalse(summary.retrievalRequired)
        self.assertTrue(summary.retrievalRequirementPassed)
        self.assertEqual(summary.maxRetrievalLatencyMs, 6)
        self.assertFalse(summary.hallucinationDetected)
        self.assertEqual(summary.maxDurationMs, 30)

    def test_summary_lists_failed_scenarios(self):
        metrics = [
            onprem_poc_smoke.SmokeMetric("text_provider_probe", True, 10),
            onprem_poc_smoke.SmokeMetric("meeting_ai", False, 20, errorType="HTTPException"),
        ]

        summary = onprem_poc_smoke.summarize(metrics)

        self.assertFalse(summary.ok)
        self.assertEqual(summary.failedScenarios, ["meeting_ai"])
        self.assertEqual(summary.citationSuccessRate, 0.0)

    def test_summary_reports_hallucination_proxy(self):
        metrics = [
            onprem_poc_smoke.SmokeMetric("meeting_ai", True, 20, sourceCount=0, hallucinationDetected=True),
        ]

        summary = onprem_poc_smoke.summarize(metrics)

        self.assertTrue(summary.hallucinationDetected)
        self.assertFalse(summary.ok)

    def test_summary_can_require_retrieval_latency_measurement(self):
        metrics = [
            onprem_poc_smoke.SmokeMetric(
                "retrieval_latency_probe",
                True,
                1,
                errorType="SKIPPED_NO_AI_DATABASE_URL",
            ),
        ]

        with patch.dict(os.environ, {"ONPREM_POC_REQUIRE_RETRIEVAL": "true"}, clear=False):
            summary = onprem_poc_smoke.summarize(metrics)

        self.assertTrue(summary.retrievalRequired)
        self.assertFalse(summary.retrievalLatencyMeasured)
        self.assertFalse(summary.retrievalRequirementPassed)
        self.assertFalse(summary.ok)


if __name__ == "__main__":
    unittest.main()
