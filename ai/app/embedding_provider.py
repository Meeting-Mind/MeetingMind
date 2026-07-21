import json
import ssl
from typing import Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .config import get_env

try:
    import certifi
except ImportError:
    certifi = None


class EmbeddingProviderError(RuntimeError):
    pass


class EmbeddingProvider(Protocol):
    model: str
    dimension: int

    def embed(self, texts: list[str]) -> list[list[float]]:
        ...


class OpenAIEmbeddingProvider:
    def __init__(self, api_key: str, *, model: str = "text-embedding-3-small", dimension: int = 1536):
        if not api_key:
            raise EmbeddingProviderError("OPENAI_API_KEY is required")
        self._api_key = api_key
        self.model = model
        self.dimension = dimension

    @classmethod
    def from_environment(cls) -> "OpenAIEmbeddingProvider":
        return cls(
            get_env("OPENAI_API_KEY", "") or "",
            model=get_env("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small") or "text-embedding-3-small",
            dimension=int(get_env("OPENAI_EMBEDDING_DIMENSION", "1536") or "1536"),
        )

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts or any(not text.strip() for text in texts):
            raise EmbeddingProviderError("embedding input must not be empty")

        body = {
            "model": self.model,
            "input": texts,
            "dimensions": self.dimension,
            "encoding_format": "float",
        }
        request = Request(
            "https://api.openai.com/v1/embeddings",
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

        ordered = sorted(data, key=lambda item: item.get("index", -1))
        vectors = [item.get("embedding") for item in ordered]
        if any(not isinstance(vector, list) or len(vector) != self.dimension for vector in vectors):
            raise EmbeddingProviderError("embedding provider returned an invalid dimension")
        return vectors
