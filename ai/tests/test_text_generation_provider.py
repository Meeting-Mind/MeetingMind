import json
import unittest
from unittest.mock import MagicMock, patch

from app.text_generation_provider import (
    OpenAICompatibleTextGenerationProvider,
    TextGenerationProviderError,
    build_chat_completions_request,
    collect_text_generation_metrics,
    get_text_generation_provider,
)


class TextGenerationProviderTest(unittest.TestCase):
    def test_openai_factory_uses_responses_api_and_records_model_metrics(self):
        response = MagicMock()
        response.read.return_value = json.dumps(
            {
                "model": "gpt-test",
                "output": [
                    {
                        "type": "message",
                        "content": [{"type": "output_text", "text": "openai ok"}],
                    }
                ],
                "usage": {"input_tokens": 7, "output_tokens": 3},
            }
        ).encode("utf-8")
        response_context = MagicMock()
        response_context.__enter__.return_value = response

        with (
            patch("app.text_generation_provider.get_env") as get_env,
            patch("app.text_generation_provider.ssl_context", return_value=None),
            patch("app.text_generation_provider.urlopen", return_value=response_context) as urlopen,
            collect_text_generation_metrics() as metrics,
        ):
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "openai",
                "OPENAI_API_KEY": "test-key",
                "OPENAI_BASE_URL": "https://api.openai.test/v1",
                "OPENAI_MODEL": "gpt-test",
            }.get(key, default)

            result = get_text_generation_provider().generate("developer", "user")

        self.assertEqual(result.text, "openai ok")
        self.assertEqual(result.model, "gpt-test")
        self.assertEqual(len(metrics), 1)
        self.assertEqual(metrics[0].provider, "openai")
        self.assertEqual(metrics[0].apiStyle, "responses")
        self.assertFalse(metrics[0].stream)
        self.assertTrue(metrics[0].modelObserved)
        self.assertEqual(metrics[0].inputTokens, 7)
        self.assertEqual(metrics[0].outputTokens, 3)

        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "https://api.openai.test/v1/responses")
        body = json.loads(request.data.decode("utf-8"))
        self.assertEqual(body["model"], "gpt-test")
        self.assertEqual(body["input"][0]["role"], "developer")
        self.assertEqual(body["input"][1]["role"], "user")

    def test_local_alias_factory_selects_local_openai_compatible_provider(self):
        with patch("app.text_generation_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_API_KEY": "local-token",
                "AI_TEXT_MODEL": "local-model",
                "AI_TEXT_API_STYLE": "chat-completions",
            }.get(key, default)

            provider = get_text_generation_provider()

        self.assertIsInstance(provider, OpenAICompatibleTextGenerationProvider)
        self.assertEqual(provider.provider_id, "local-openai-compatible")
        self.assertEqual(provider.base_url, "http://llm.internal:8000/v1/")
        self.assertEqual(provider.model, "local-model")
        self.assertEqual(provider.api_style, "chat-completions")

    def test_factory_rejects_unsupported_text_provider(self):
        with (
            patch("app.text_generation_provider.get_env", return_value="custom-provider"),
            self.assertRaises(TextGenerationProviderError),
        ):
            get_text_generation_provider()

    def test_local_factory_requires_base_url_and_model_before_provider_call(self):
        with patch("app.text_generation_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "",
                "AI_TEXT_API_KEY": "local-token",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct",
                "AI_TEXT_API_STYLE": "chat-completions",
            }.get(key, default)

            with self.assertRaises(TextGenerationProviderError) as missing_base_url:
                get_text_generation_provider()

        self.assertIn("base url is required", str(missing_base_url.exception))

        with patch("app.text_generation_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "http://llm.internal:8000/v1",
                "AI_TEXT_API_KEY": "local-token",
                "AI_TEXT_MODEL": "",
                "AI_TEXT_API_STYLE": "chat-completions",
            }.get(key, default)

            with self.assertRaises(TextGenerationProviderError) as missing_model:
                get_text_generation_provider()

        self.assertIn("model is required", str(missing_model.exception))

    def test_local_factory_rejects_openai_or_invalid_base_url(self):
        with patch("app.text_generation_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "https://api.openai.com/v1",
                "AI_TEXT_API_KEY": "local-token",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct",
                "AI_TEXT_API_STYLE": "chat-completions",
            }.get(key, default)

            with self.assertRaises(TextGenerationProviderError) as openai_url:
                get_text_generation_provider()

        self.assertIn("must not point to api.openai.com", str(openai_url.exception))

        with patch("app.text_generation_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "/v1",
                "AI_TEXT_API_KEY": "local-token",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct",
                "AI_TEXT_API_STYLE": "chat-completions",
            }.get(key, default)

            with self.assertRaises(TextGenerationProviderError) as invalid_url:
                get_text_generation_provider()

        self.assertIn("absolute http(s) URL", str(invalid_url.exception))

        with patch("app.text_generation_provider.get_env") as get_env:
            get_env.side_effect = lambda key, default=None: {
                "AI_TEXT_PROVIDER": "local-openai-compatible",
                "AI_TEXT_BASE_URL": "https://token:secret@llm.internal:8000/v1",
                "AI_TEXT_API_KEY": "local-token",
                "AI_TEXT_MODEL": "qwen2.5-14b-instruct",
                "AI_TEXT_API_STYLE": "chat-completions",
            }.get(key, default)

            with self.assertRaises(TextGenerationProviderError) as credential_url:
                get_text_generation_provider()

        self.assertIn("must not include userinfo credentials", str(credential_url.exception))

    def test_chat_completions_request_preserves_existing_prompt_roles_and_json_schema(self):
        response_format = {
            "type": "json_schema",
            "name": "grounded_answer",
            "strict": True,
            "schema": {"type": "object", "required": ["supported"]},
        }

        body = build_chat_completions_request(
            "local-model",
            "developer prompt",
            "user prompt",
            response_format=response_format,
            stream=True,
            response_format_mode="json_schema",
            include_stream_options=True,
        )

        self.assertEqual(body["model"], "local-model")
        self.assertEqual(body["messages"][0], {"role": "system", "content": "developer prompt"})
        self.assertEqual(body["messages"][1], {"role": "user", "content": "user prompt"})
        self.assertTrue(body["stream"])
        self.assertEqual(body["stream_options"], {"include_usage": True})
        self.assertEqual(body["response_format"]["type"], "json_schema")
        self.assertEqual(body["response_format"]["json_schema"]["name"], "grounded_answer")
        self.assertEqual(body["response_format"]["json_schema"]["schema"], response_format["schema"])

    def test_chat_completions_response_format_modes_are_env_switchable(self):
        response_format = {
            "type": "json_schema",
            "name": "grounded_answer",
            "strict": True,
            "schema": {"type": "object"},
        }

        json_object_body = build_chat_completions_request(
            "local-model",
            "developer prompt",
            "user prompt",
            response_format=response_format,
            response_format_mode="json_object",
        )
        none_body = build_chat_completions_request(
            "local-model",
            "developer prompt",
            "user prompt",
            response_format=response_format,
            response_format_mode="none",
        )

        self.assertEqual(json_object_body["response_format"], {"type": "json_object"})
        self.assertNotIn("response_format", none_body)

    def test_streaming_chat_completion_accepts_sse_comments_and_role_only_chunks(self):
        response = MagicMock()
        response.__iter__.return_value = iter(
            [
                b": keep-alive\n\n",
                b"event: ping\n\n",
                b'data: {"model":"qwen2.5-14b-instruct","choices":[{"delta":{"role":"assistant"}}]}\n\n',
                b'data: {"model":"qwen2.5-14b-instruct","choices":[{"delta":{"content":"local"}}]}\n\n',
                b'data: {"choices":[{"delta":{"content":" ok"}}]}\n\n',
                b'data: {"choices":[],"usage":{"prompt_tokens":5,"completion_tokens":2}}\n\n',
                b"data: [DONE]\n\n",
            ]
        )
        response_context = MagicMock()
        response_context.__enter__.return_value = response
        provider = OpenAICompatibleTextGenerationProvider(
            base_url="http://llm.internal:8000/v1",
            api_key="local-token",
            model="qwen2.5-14b-instruct",
            provider_id="local-openai-compatible",
            api_style="chat-completions",
            stream=True,
        )

        with (
            patch("app.text_generation_provider.ssl_context", return_value=None),
            patch("app.text_generation_provider.urlopen", return_value=response_context) as urlopen,
            collect_text_generation_metrics() as metrics,
        ):
            result = provider.generate("developer", "user")

        self.assertEqual(result.text, "local ok")
        self.assertEqual(result.model, "qwen2.5-14b-instruct")
        self.assertEqual(metrics[0].provider, "local-openai-compatible")
        self.assertTrue(metrics[0].stream)
        self.assertTrue(metrics[0].modelObserved)
        self.assertEqual(metrics[0].inputTokens, 5)
        self.assertEqual(metrics[0].outputTokens, 2)
        self.assertIsNotNone(metrics[0].ttftMs)
        self.assertIsNotNone(metrics[0].tokensPerSecond)
        self.assertEqual(urlopen.call_args.args[0].headers["Accept"], "text/event-stream")

    def test_streaming_chat_completion_rejects_non_object_data_chunk(self):
        response = MagicMock()
        response.__iter__.return_value = iter(
            [
                b'data: ["not", "an", "object"]\n\n',
                b"data: [DONE]\n\n",
            ]
        )
        response_context = MagicMock()
        response_context.__enter__.return_value = response
        provider = OpenAICompatibleTextGenerationProvider(
            base_url="http://llm.internal:8000/v1",
            api_key="local-token",
            model="qwen2.5-14b-instruct",
            provider_id="local-openai-compatible",
            api_style="chat-completions",
            stream=True,
        )

        with (
            patch("app.text_generation_provider.ssl_context", return_value=None),
            patch("app.text_generation_provider.urlopen", return_value=response_context),
            self.assertRaises(TextGenerationProviderError) as raised,
        ):
            provider.generate("developer", "user")

        self.assertIn("invalid response", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
