import json
import math
import ssl
from numbers import Real
from typing import Protocol
from urllib.parse import urljoin
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .config import get_env
from .provider_url import local_provider_base_url_error

try:
    import certifi
except ImportError:
    certifi = None


class EmbeddingProviderError(RuntimeError):
    pass


class EmbeddingProvider(Protocol):
    provider_id: str
    model: str
    dimension: int
    last_response_model: str | None
    last_response_model_observed: bool

    def embed(self, texts: list[str]) -> list[list[float]]:
        ...


class OpenAICompatibleEmbeddingProvider:
    def __init__(
        self,
        api_key: str,
        *,
        base_url: str,
        model: str,
        dimension: int,
        provider_id: str,
        include_dimensions: bool = True,
    ):
        if not api_key:
            raise EmbeddingProviderError("embedding provider api key is required")
        if not base_url:
            raise EmbeddingProviderError("embedding provider base url is required")
        if not model:
            raise EmbeddingProviderError("embedding provider model is required")
        if provider_id == "local-openai-compatible":
            validate_local_provider_base_url(base_url)
        self._api_key = api_key
        self._base_url = base_url.rstrip("/") + "/"
        self.provider_id = provider_id
        self.model = model
        self.dimension = dimension
        self.include_dimensions = include_dimensions
        self.last_response_model = None
        self.last_response_model_observed = False

    @classmethod
    def openai_from_environment(cls) -> "OpenAICompatibleEmbeddingProvider":
        return cls(
            get_env("OPENAI_API_KEY", "") or "",
            base_url=get_env("OPENAI_BASE_URL", "https://api.openai.com/v1") or "https://api.openai.com/v1",
            model=get_env("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small") or "text-embedding-3-small",
            dimension=dimension_env("OPENAI_EMBEDDING_DIMENSION", "1536"),
            provider_id="openai",
            include_dimensions=parse_bool(get_env("OPENAI_EMBEDDING_INCLUDE_DIMENSIONS", "true")),
        )

    @classmethod
    def local_from_environment(cls) -> "OpenAICompatibleEmbeddingProvider":
        return cls(
            get_env("AI_EMBEDDING_API_KEY", "local-provider") or "local-provider",
            base_url=get_env("AI_EMBEDDING_BASE_URL", "") or "",
            model=get_env("AI_EMBEDDING_MODEL", "") or "",
            dimension=dimension_env("AI_EMBEDDING_DIMENSION", "1536"),
            provider_id="local-openai-compatible",
            include_dimensions=parse_bool(get_env("AI_EMBEDDING_INCLUDE_DIMENSIONS", "false")),
        )

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts or any(not text.strip() for text in texts):
            raise EmbeddingProviderError("embedding input must not be empty")

        body = {
            "model": self.model,
            "input": texts,
            "encoding_format": "float",
        }
        if self.include_dimensions:
            body["dimensions"] = self.dimension
        request = Request(
            urljoin(self._base_url, "embeddings"),
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )

        context = ssl.create_default_context(cafile=certifi.where()) if certifi else None
        try:
            with urlopen(request, timeout=60, context=context) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, ValueError, UnicodeError) as error:
            raise EmbeddingProviderError("embedding provider unavailable") from error

        data = payload.get("data")
        if not isinstance(data, list) or len(data) != len(texts):
            raise EmbeddingProviderError("embedding provider returned an invalid result")
        response_model = payload.get("model")
        self.last_response_model = response_model.strip() if isinstance(response_model, str) and response_model.strip() else None
        self.last_response_model_observed = self.last_response_model is not None

        return parse_embedding_vectors(data, dimension=self.dimension)


class OpenAIEmbeddingProvider(OpenAICompatibleEmbeddingProvider):
    def __init__(self, api_key: str, *, model: str = "text-embedding-3-small", dimension: int = 1536):
        super().__init__(
            api_key,
            base_url="https://api.openai.com/v1",
            model=model,
            dimension=dimension,
            provider_id="openai",
            include_dimensions=True,
        )

    @classmethod
    def from_environment(cls) -> "OpenAIEmbeddingProvider":
        return cls(
            get_env("OPENAI_API_KEY", "") or "",
            model=get_env("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small") or "text-embedding-3-small",
            dimension=dimension_env("OPENAI_EMBEDDING_DIMENSION", "1536"),
        )


def create_embedding_provider() -> EmbeddingProvider:
    provider = get_env("AI_EMBEDDING_PROVIDER", "openai") or "openai"
    normalized = provider.strip().casefold()
    if normalized == "openai":
        return validate_provider_dimension(OpenAICompatibleEmbeddingProvider.openai_from_environment())
    if normalized in ("local", "openai-compatible", "local-openai-compatible"):
        return validate_provider_dimension(OpenAICompatibleEmbeddingProvider.local_from_environment())
    raise EmbeddingProviderError("unsupported embedding provider")


def validate_local_provider_base_url(base_url: str) -> None:
    error = local_provider_base_url_error(base_url, label="local embedding provider base url")
    if error is not None:
        raise EmbeddingProviderError(error)


def validate_provider_dimension(provider: EmbeddingProvider) -> EmbeddingProvider:
    expected_dimension = dimension_env("AI_VECTOR_DIMENSION", "1536")
    if provider.dimension != expected_dimension:
        raise EmbeddingProviderError(
            "embedding provider dimension does not match vector schema dimension; "
            "run schema migration and existing embedding job generation/swap reindex before switching dimensions"
        )
    return provider


def parse_bool(value: str | None) -> bool:
    return str(value or "").strip().casefold() in ("1", "true", "yes", "y", "on")


def dimension_env(key: str, default: str) -> int:
    raw_value = get_env(key, default) or default
    try:
        value = int(raw_value)
    except ValueError as error:
        raise EmbeddingProviderError(f"{key} must be a valid integer") from error
    if value <= 0:
        raise EmbeddingProviderError(f"{key} must be greater than 0")
    return value


def parse_embedding_vectors(data: list[object], *, dimension: int) -> list[list[float]]:
    if any(not isinstance(item, dict) for item in data):
        raise EmbeddingProviderError("embedding provider returned an invalid result")
    ordered = sorted(data, key=lambda item: item.get("index", -1))
    vectors = [item.get("embedding") for item in ordered]
    if any(not isinstance(vector, list) or len(vector) != dimension for vector in vectors):
        raise EmbeddingProviderError("embedding provider returned an invalid dimension")
    for vector in vectors:
        if any(not is_finite_number(value) for value in vector):
            raise EmbeddingProviderError("embedding provider returned an invalid vector value")
    return [[float(value) for value in vector] for vector in vectors]


def is_finite_number(value: object) -> bool:
    return isinstance(value, Real) and not isinstance(value, bool) and math.isfinite(float(value))
