import unittest
from pathlib import Path


APP_DIR = Path(__file__).resolve().parents[1] / "app"
ALLOWED_HTTP_PROVIDER_FILES = {
    APP_DIR / "text_generation_provider.py",
    APP_DIR / "embedding_provider.py",
}
DIRECT_HTTP_MARKERS = (
    "urlopen",
    "requests.",
    "httpx.",
    "/responses",
    "/chat/completions",
    "/embeddings",
)


class ProviderBoundaryTest(unittest.TestCase):
    def test_direct_provider_http_calls_stay_inside_provider_modules(self):
        leaks: list[str] = []
        for path in sorted(APP_DIR.rglob("*.py")):
            if path in ALLOWED_HTTP_PROVIDER_FILES:
                continue
            text = path.read_text(encoding="utf-8")
            for marker in DIRECT_HTTP_MARKERS:
                if marker in text:
                    leaks.append(f"{path.relative_to(APP_DIR)} contains {marker}")

        self.assertEqual(
            [],
            leaks,
            "AI service/router/RAG code must use TextGenerationProvider or EmbeddingProvider, "
            "not direct provider HTTP calls.",
        )


if __name__ == "__main__":
    unittest.main()
