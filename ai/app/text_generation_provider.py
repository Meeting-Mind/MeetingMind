import json
import ssl
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from time import perf_counter
from collections.abc import Iterator
from typing import Any, Literal, Protocol
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen

from .config import get_env
from .provider_url import local_provider_base_url_error

try:
    import certifi
except ImportError:
    certifi = None


DEFAULT_TIMEOUT_SECONDS = 30
REPORT_TIMEOUT_SECONDS = 60
_METRIC_SINK = ContextVar[list["TextGenerationMetrics"] | None]("meetingmind_text_generation_metrics", default=None)


class TextGenerationProviderError(RuntimeError):
    pass


@dataclass(frozen=True)
class TextGenerationMetrics:
    provider: str
    apiStyle: str
    stream: bool
    totalMs: int
    modelObserved: bool = False
    responseFormatMode: str | None = None
    ttftMs: int | None = None
    tokensPerSecond: float | None = None
    inputTokens: int | None = None
    outputTokens: int | None = None
    outputTokenEstimate: int | None = None


@dataclass(frozen=True)
class TextGenerationResult:
    text: str
    model: str
    metrics: TextGenerationMetrics


class TextGenerationProvider(Protocol):
    model: str

    def generate(
        self,
        developer_content: str,
        user_content: str,
        *,
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
        response_format: dict[str, Any] | None = None,
    ) -> TextGenerationResult:
        ...


class OpenAICompatibleTextGenerationProvider:
    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        provider_id: str,
        api_style: Literal["responses", "chat-completions"] = "responses",
        stream: bool = False,
        response_format_mode: Literal["json_schema", "json_object", "none"] = "json_schema",
        include_stream_options: bool = False,
    ):
        if not base_url:
            raise TextGenerationProviderError("text provider base url is required")
        if not api_key:
            raise TextGenerationProviderError("text provider api key is required")
        if not model:
            raise TextGenerationProviderError("text provider model is required")
        if provider_id == "local-openai-compatible":
            validate_local_provider_base_url(base_url)
        self.base_url = base_url.rstrip("/") + "/"
        self.api_key = api_key
        self.model = model
        self.provider_id = provider_id
        self.api_style = api_style
        self.stream = stream
        self.response_format_mode = response_format_mode
        self.include_stream_options = include_stream_options

    @classmethod
    def openai_from_environment(cls) -> "OpenAICompatibleTextGenerationProvider":
        return cls(
            base_url=get_env("OPENAI_BASE_URL", "https://api.openai.com/v1") or "https://api.openai.com/v1",
            api_key=get_env("OPENAI_API_KEY", "") or "",
            model=get_env("OPENAI_MODEL", "gpt-4.1-mini") or "gpt-4.1-mini",
            provider_id="openai",
            api_style="responses",
            stream=False,
            response_format_mode="json_schema",
            include_stream_options=False,
        )

    @classmethod
    def local_from_environment(cls) -> "OpenAICompatibleTextGenerationProvider":
        api_style = get_env("AI_TEXT_API_STYLE", "responses") or "responses"
        if api_style not in ("responses", "chat-completions"):
            raise TextGenerationProviderError("AI_TEXT_API_STYLE must be responses or chat-completions")
        response_format_mode = get_env("AI_TEXT_RESPONSE_FORMAT_MODE", "json_schema") or "json_schema"
        if response_format_mode not in ("json_schema", "json_object", "none"):
            raise TextGenerationProviderError("AI_TEXT_RESPONSE_FORMAT_MODE must be json_schema, json_object, or none")
        return cls(
            base_url=get_env("AI_TEXT_BASE_URL", "") or "",
            api_key=get_env("AI_TEXT_API_KEY", "local-provider") or "local-provider",
            model=get_env("AI_TEXT_MODEL", "") or "",
            provider_id="local-openai-compatible",
            api_style=api_style,
            stream=parse_bool(get_env("AI_TEXT_STREAM", "false")),
            response_format_mode=response_format_mode,
            include_stream_options=parse_bool(get_env("AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE", "false")),
        )

    def generate(
        self,
        developer_content: str,
        user_content: str,
        *,
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
        response_format: dict[str, Any] | None = None,
    ) -> TextGenerationResult:
        started_at = perf_counter()
        ttft_ms: int | None = None
        if self.api_style == "chat-completions":
            request_body = build_chat_completions_request(
                self.model,
                developer_content,
                user_content,
                response_format=response_format,
                stream=self.stream,
                response_format_mode=self.response_format_mode,
                include_stream_options=self.include_stream_options,
            )
            if self.stream:
                response_data, text, ttft_ms = self._post_streaming_chat_completion(
                    request_body,
                    started_at=started_at,
                    timeout_seconds=timeout_seconds,
                )
            else:
                response_data = self._post_json(
                    "chat/completions",
                    request_body,
                    timeout_seconds=timeout_seconds,
                )
                text = extract_chat_completion_text(response_data)
        else:
            response_data = self._post_json(
                "responses",
                build_responses_request(
                    self.model,
                    developer_content,
                    user_content,
                    response_format=response_format,
                ),
                timeout_seconds=timeout_seconds,
            )
            text = extract_responses_output_text(response_data)

        total_ms = max(1, int((perf_counter() - started_at) * 1000))
        input_tokens, output_tokens = extract_usage_tokens(response_data)
        output_token_estimate = estimate_output_tokens(text)
        measured_output_tokens = output_tokens or output_token_estimate
        tokens_per_second = (
            round(measured_output_tokens / (total_ms / 1000), 2)
            if measured_output_tokens is not None and total_ms > 0
            else None
        )
        response_model = extract_response_model(response_data)
        metrics = TextGenerationMetrics(
                provider=self.provider_id,
                apiStyle=self.api_style,
                stream=self.stream,
                totalMs=total_ms,
                modelObserved=response_model is not None,
                responseFormatMode=self.response_format_mode if response_format is not None else None,
                ttftMs=ttft_ms,
                inputTokens=input_tokens,
                outputTokens=output_tokens,
                outputTokenEstimate=output_token_estimate if output_tokens is None else None,
                tokensPerSecond=tokens_per_second,
        )
        record_text_generation_metrics(metrics)
        return TextGenerationResult(
            text=text,
            model=response_model or self.model,
            metrics=metrics,
        )

    def _post_json(
        self,
        path: str,
        body: dict[str, Any],
        *,
        timeout_seconds: int,
    ) -> dict[str, Any]:
        request = Request(
            urljoin(self.base_url, path),
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=timeout_seconds, context=ssl_context()) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, ValueError, UnicodeError) as error:
            raise TextGenerationProviderError("text provider unavailable") from error
        if not isinstance(payload, dict):
            raise TextGenerationProviderError("text provider returned an invalid result")
        return payload

    def _post_streaming_chat_completion(
        self,
        body: dict[str, Any],
        *,
        started_at: float,
        timeout_seconds: int,
    ) -> tuple[dict[str, Any], str, int | None]:
        request = Request(
            urljoin(self.base_url, "chat/completions"),
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
            },
            method="POST",
        )
        text_parts: list[str] = []
        usage: dict[str, Any] | None = None
        response_model: str | None = None
        ttft_ms: int | None = None
        try:
            with urlopen(request, timeout=timeout_seconds, context=ssl_context()) as response:
                for raw_line in response:
                    line = raw_line.decode("utf-8").strip()
                    if not line or not line.startswith("data:"):
                        continue
                    data = line.removeprefix("data:").strip()
                    if data == "[DONE]":
                        break
                    chunk = json.loads(data)
                    if not isinstance(chunk, dict):
                        raise TextGenerationProviderError("text provider returned an invalid response")
                    chunk_model = chunk.get("model")
                    if response_model is None and isinstance(chunk_model, str) and chunk_model.strip():
                        response_model = chunk_model.strip()
                    usage_payload = chunk.get("usage")
                    if isinstance(usage_payload, dict):
                        usage = usage_payload
                    delta = extract_chat_completion_delta(chunk)
                    if delta:
                        if ttft_ms is None:
                            ttft_ms = int((perf_counter() - started_at) * 1000)
                        text_parts.append(delta)
        except (HTTPError, URLError, TimeoutError, ValueError, UnicodeError) as error:
            raise TextGenerationProviderError("text provider unavailable") from error

        text = "".join(text_parts).strip()
        if not text:
            raise TextGenerationProviderError("text provider returned an empty response")
        response_data: dict[str, Any] = {"choices": [{"message": {"content": text}}]}
        if response_model is not None:
            response_data["model"] = response_model
        if usage:
            response_data["usage"] = usage
        return response_data, text, ttft_ms


def get_text_generation_provider() -> TextGenerationProvider:
    provider = get_env("AI_TEXT_PROVIDER", "openai") or "openai"
    normalized = provider.strip().casefold()
    if normalized == "openai":
        return OpenAICompatibleTextGenerationProvider.openai_from_environment()
    if normalized in ("local", "openai-compatible", "local-openai-compatible"):
        return OpenAICompatibleTextGenerationProvider.local_from_environment()
    raise TextGenerationProviderError("unsupported text provider")


def validate_local_provider_base_url(base_url: str) -> None:
    error = local_provider_base_url_error(base_url, label="local text provider base url")
    if error is not None:
        raise TextGenerationProviderError(error)


@contextmanager
def collect_text_generation_metrics() -> Iterator[list[TextGenerationMetrics]]:
    metrics: list[TextGenerationMetrics] = []
    token = _METRIC_SINK.set(metrics)
    try:
        yield metrics
    finally:
        _METRIC_SINK.reset(token)


def record_text_generation_metrics(metrics: TextGenerationMetrics) -> None:
    sink = _METRIC_SINK.get()
    if sink is not None:
        sink.append(metrics)


def build_responses_request(
    model: str,
    developer_content: str,
    user_content: str,
    *,
    response_format: dict[str, Any] | None = None,
) -> dict[str, Any]:
    request_body: dict[str, Any] = {
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
    if response_format is not None:
        request_body["text"] = {"format": response_format}
    return request_body


def build_chat_completions_request(
    model: str,
    developer_content: str,
    user_content: str,
    *,
    response_format: dict[str, Any] | None = None,
    stream: bool = False,
    response_format_mode: Literal["json_schema", "json_object", "none"] = "json_schema",
    include_stream_options: bool = False,
) -> dict[str, Any]:
    request_body: dict[str, Any] = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": developer_content,
            },
            {
                "role": "user",
                "content": user_content,
            },
        ],
    }
    if response_format is not None:
        chat_response_format = chat_completions_response_format(response_format, mode=response_format_mode)
        if chat_response_format is not None:
            request_body["response_format"] = chat_response_format
    if stream:
        request_body["stream"] = True
        if include_stream_options:
            request_body["stream_options"] = {"include_usage": True}
    return request_body


def chat_completions_response_format(
    response_format: dict[str, Any],
    *,
    mode: Literal["json_schema", "json_object", "none"] = "json_schema",
) -> dict[str, Any] | None:
    if mode == "none":
        return None
    if mode == "json_object":
        return {"type": "json_object"}
    if response_format.get("type") != "json_schema":
        return response_format
    return {
        "type": "json_schema",
        "json_schema": {
            "name": response_format.get("name"),
            "strict": response_format.get("strict", True),
            "schema": response_format.get("schema"),
        },
    }


def extract_responses_output_text(response_data: dict[str, Any]) -> str:
    output_items = response_data.get("output", [])
    for item in output_items:
        if not isinstance(item, dict) or item.get("type") != "message":
            continue
        for content in item.get("content", []):
            if isinstance(content, dict) and content.get("type") == "output_text":
                text = str(content.get("text", "")).strip()
                if text:
                    return text
    raise TextGenerationProviderError("text provider returned an empty response")


def extract_chat_completion_text(response_data: dict[str, Any]) -> str:
    choices = response_data.get("choices", [])
    if not isinstance(choices, list) or not choices:
        raise TextGenerationProviderError("text provider returned an empty response")
    message = choices[0].get("message") if isinstance(choices[0], dict) else None
    if not isinstance(message, dict):
        raise TextGenerationProviderError("text provider returned an invalid response")
    content = message.get("content")
    if isinstance(content, str) and content.strip():
        return content.strip()
    raise TextGenerationProviderError("text provider returned an empty response")


def extract_chat_completion_delta(chunk: dict[str, Any]) -> str:
    choices = chunk.get("choices", [])
    if not isinstance(choices, list) or not choices:
        return ""
    choice = choices[0]
    if not isinstance(choice, dict):
        return ""
    delta = choice.get("delta")
    if not isinstance(delta, dict):
        return ""
    content = delta.get("content")
    return content if isinstance(content, str) else ""


def extract_usage_tokens(response_data: dict[str, Any]) -> tuple[int | None, int | None]:
    usage = response_data.get("usage")
    if not isinstance(usage, dict):
        return None, None
    input_tokens = usage.get("input_tokens", usage.get("prompt_tokens"))
    output_tokens = usage.get("output_tokens", usage.get("completion_tokens"))
    return (
        input_tokens if isinstance(input_tokens, int) else None,
        output_tokens if isinstance(output_tokens, int) else None,
    )


def extract_response_model(response_data: dict[str, Any]) -> str | None:
    model = response_data.get("model")
    if isinstance(model, str) and model.strip():
        return model.strip()
    return None


def estimate_output_tokens(text: str) -> int | None:
    normalized = text.strip()
    if not normalized:
        return None
    return max(1, round(len(normalized) / 4))


def parse_bool(value: str | None) -> bool:
    return (value or "").strip().casefold() in {"1", "true", "yes", "on"}


def ssl_context() -> ssl.SSLContext | None:
    if certifi is None:
        return None
    return ssl.create_default_context(cafile=certifi.where())
