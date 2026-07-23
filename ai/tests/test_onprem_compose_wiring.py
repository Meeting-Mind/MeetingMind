import unittest
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover - optional local tooling guard
    yaml = None


REPO_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = REPO_ROOT / "compose.local.yml"


@unittest.skipIf(yaml is None, "PyYAML is required to inspect compose.local.yml")
class OnPremComposeWiringTest(unittest.TestCase):
    def test_ai_services_share_database_embedding_provider_and_vector_dimension_env(self):
        compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))
        services = compose["services"]
        ai_env = services["meetingmind-ai"]["environment"]
        worker_env = services["meetingmind-ai-worker"]["environment"]

        for key in (
            "AI_DATABASE_URL",
            "AI_EMBEDDING_PROVIDER",
            "AI_EMBEDDING_BASE_URL",
            "AI_EMBEDDING_API_KEY",
            "AI_EMBEDDING_MODEL",
            "AI_EMBEDDING_DIMENSION",
            "AI_EMBEDDING_INCLUDE_DIMENSIONS",
            "AI_VECTOR_DIMENSION",
            "OPENAI_API_KEY",
            "OPEN_AI_KEY",
            "OPENAI_BASE_URL",
            "OPENAI_EMBEDDING_MODEL",
            "OPENAI_EMBEDDING_DIMENSION",
            "OPENAI_EMBEDDING_INCLUDE_DIMENSIONS",
        ):
            self.assertEqual(worker_env[key], ai_env[key], key)

    def test_ai_server_exposes_internal_token_text_provider_env_and_safe_healthcheck(self):
        compose = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))
        ai_service = compose["services"]["meetingmind-ai"]
        ai_env = ai_service["environment"]

        self.assertIn("AI_INTERNAL_SERVICE_TOKEN", ai_env)
        for key in (
            "AI_TEXT_PROVIDER",
            "AI_TEXT_BASE_URL",
            "AI_TEXT_API_KEY",
            "AI_TEXT_MODEL",
            "AI_TEXT_API_STYLE",
            "AI_TEXT_STREAM",
            "AI_TEXT_STREAM_OPTIONS_INCLUDE_USAGE",
            "AI_TEXT_RESPONSE_FORMAT_MODE",
        ):
            self.assertIn(key, ai_env)

        healthcheck = ai_service["healthcheck"]["test"]
        self.assertIn("/health", " ".join(str(part) for part in healthcheck))
        self.assertNotIn("AI_INTERNAL_SERVICE_TOKEN", str(healthcheck))


if __name__ == "__main__":
    unittest.main()
