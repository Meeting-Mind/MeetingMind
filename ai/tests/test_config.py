import os
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from app import config
from app.config import get_env


class ConfigTest(unittest.TestCase):
    def test_primary_environment_value_takes_priority(self):
        with patch.dict(os.environ, {"OPENAI_API_KEY": "primary-key"}, clear=True):
            with patch("app.config.ENV_CACHE", {"OPENAI_API_KEY": "dotenv-key"}):
                self.assertEqual(get_env("OPENAI_API_KEY"), "primary-key")

    def test_open_ai_key_alias_is_supported_for_worker_and_api(self):
        with patch.dict(os.environ, {"OPEN_AI_KEY": "alias-key"}, clear=True):
            with patch("app.config.ENV_CACHE", {}):
                self.assertEqual(get_env("OPENAI_API_KEY"), "alias-key")

    def test_process_environment_alias_wins_over_dotenv_primary_key(self):
        with patch.dict(os.environ, {"OPEN_AI_KEY": "process-alias"}, clear=True):
            with patch("app.config.ENV_CACHE", {"OPENAI_API_KEY": "dotenv-primary"}):
                self.assertEqual(get_env("OPENAI_API_KEY"), "process-alias")

    def test_dotenv_candidates_keep_ai_then_root_then_backend_precedence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ai_env = root / "ai.env"
            root_env = root / "root.env"
            backend_env = root / "backend.env"
            ai_env.write_text("OPENAI_API_KEY=ai-key\nAI_DATABASE_URL=postgresql://ai\n", encoding="utf-8")
            root_env.write_text("OPENAI_API_KEY=root-key\n", encoding="utf-8")
            backend_env.write_text("OPENAI_API_KEY=backend-key\nAI_DATABASE_URL=postgresql://backend\n", encoding="utf-8")

            with patch.object(config, "DOTENV_CANDIDATES", (ai_env, root_env, backend_env)):
                values = config.load_dotenv()

        self.assertEqual(values["OPENAI_API_KEY"], "ai-key")
        self.assertEqual(values["AI_DATABASE_URL"], "postgresql://ai")


if __name__ == "__main__":
    unittest.main()
