import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND_SRC = REPO_ROOT / "frontend" / "src"
FORBIDDEN_FRONTEND_AI_MARKERS = (
    "VITE_AI_API_BASE_URL",
    "/api/internal/",
    "localhost:8000",
    "meetingmind-ai:8000",
    "AI_TEXT_PROVIDER",
    "AI_EMBEDDING_PROVIDER",
)
SCANNED_SUFFIXES = {
    ".ts",
    ".tsx",
    ".js",
    ".jsx",
    ".css",
}


class OnPremFrontendBoundaryTest(unittest.TestCase):
    def test_frontend_does_not_call_ai_server_or_internal_ai_endpoints_directly(self):
        leaks: list[str] = []
        for path in sorted(FRONTEND_SRC.rglob("*")):
            if not path.is_file() or path.suffix not in SCANNED_SUFFIXES:
                continue
            text = path.read_text(encoding="utf-8")
            for marker in FORBIDDEN_FRONTEND_AI_MARKERS:
                if marker in text:
                    leaks.append(f"{path.relative_to(REPO_ROOT)} contains {marker}")

        self.assertEqual(
            [],
            leaks,
            "Frontend must continue to use Backend API contracts; "
            "on-prem provider selection belongs behind Spring Backend and FastAPI AI Server.",
        )


if __name__ == "__main__":
    unittest.main()
