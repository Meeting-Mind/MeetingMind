import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


AI_DIR = Path(__file__).resolve().parents[1]
RUN_SCRIPT = AI_DIR / "onprem_poc_run.sh"
ONPREM_ENV_EXAMPLE = AI_DIR / "onprem.env.example"


class OnPremPocRunScriptTest(unittest.TestCase):
    def test_repository_env_example_supports_preflight_only_wrapper(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            result_path = Path(tmpdir) / "template-preflight.json"
            env = os.environ.copy()
            env["PYTHON"] = sys.executable
            env["ONPREM_POC_PREFLIGHT_ONLY"] = "true"
            env["ONPREM_POC_RESULT_PATH"] = str(result_path)

            completed = subprocess.run(
                ["bash", str(RUN_SCRIPT), str(ONPREM_ENV_EXAMPLE)],
                cwd=AI_DIR,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(completed.returncode, 0, completed.stderr)
            payload = json.loads(result_path.read_text(encoding="utf-8"))
            self.assertTrue(payload["preflightOnly"])
            self.assertEqual(payload["config"]["textProvider"], "local-openai-compatible")
            self.assertEqual(payload["config"]["embeddingProvider"], "local-openai-compatible")
            self.assertTrue(payload["config"]["textBaseUrlLocalCompatible"])
            self.assertTrue(payload["config"]["embeddingBaseUrlLocalCompatible"])
            self.assertNotIn("local-provider-token", completed.stdout)
            self.assertNotIn("local-only-ai-service-token", completed.stdout)

    def test_repository_env_example_final_wrapper_fails_until_models_are_replaced(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            result_path = Path(tmpdir) / "template-final.json"
            env = os.environ.copy()
            env["PYTHON"] = sys.executable
            env["ONPREM_POC_PREFLIGHT_ONLY"] = "false"
            env["ONPREM_POC_RESULT_PATH"] = str(result_path)

            completed = subprocess.run(
                ["bash", str(RUN_SCRIPT), str(ONPREM_ENV_EXAMPLE)],
                cwd=AI_DIR,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(completed.returncode, 1)
            self.assertFalse(result_path.exists())
            self.assertIn("AI_TEXT_MODEL must be replaced", completed.stderr)
            self.assertIn("AI_EMBEDDING_MODEL must be replaced", completed.stderr)
            self.assertNotIn("local-provider-token", completed.stderr)
            self.assertNotIn("local-only-ai-service-token", completed.stderr)

    def test_preflight_wrapper_loads_env_file_and_keeps_shell_overrides(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            env_file = Path(tmpdir) / "onprem.env"
            file_result_path = Path(tmpdir) / "file-result.json"
            override_result_path = Path(tmpdir) / "override-result.json"
            env_file.write_text(
                "\n".join(
                    [
                        "export AI_TEXT_PROVIDER=local-openai-compatible",
                        "AI_TEXT_BASE_URL=http://llm.internal:8000/v1",
                        "AI_TEXT_API_KEY=file-token",
                        "AI_TEXT_MODEL=local-llm-model",
                        "AI_TEXT_API_STYLE=chat-completions",
                        "AI_TEXT_STREAM=true",
                        "AI_TEXT_RESPONSE_FORMAT_MODE=json_schema",
                        "AI_EMBEDDING_PROVIDER=local-openai-compatible",
                        "AI_EMBEDDING_BASE_URL=http://embedding.internal:8001/v1",
                        "AI_EMBEDDING_API_KEY=file-token",
                        "AI_EMBEDDING_MODEL=local-embedding-model",
                        "AI_EMBEDDING_DIMENSION=1536",
                        "AI_VECTOR_DIMENSION=1536",
                        "AI_DATABASE_URL=postgresql://meetingmind:secret@db.internal/meetingmind",
                        "AI_INTERNAL_SERVICE_TOKEN=file-service-token",
                        "ONPREM_POC_PROJECT_ID=space-1",
                        "ONPREM_POC_ALLOWED_MEETING_IDS=meeting-1,meeting-2",
                        f"UNUSED_COMMAND_SUBSTITUTION=$(touch {tmpdir}/should-not-exist)",
                        "ONPREM_POC_PREFLIGHT_ONLY=true",
                        "ONPREM_POC_REQUIRE_RETRIEVAL=true",
                        f"ONPREM_POC_RESULT_PATH={file_result_path}",
                        'ONPREM_POC_RAG_QUERY="온프레 AI PoC 출시 일정과 QA 마감"',
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            env = os.environ.copy()
            env["PYTHON"] = sys.executable
            env["ONPREM_POC_RESULT_PATH"] = str(override_result_path)
            completed = subprocess.run(
                ["bash", str(RUN_SCRIPT), str(env_file)],
                cwd=AI_DIR,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(completed.returncode, 0, completed.stderr)
            self.assertFalse(file_result_path.exists())
            self.assertFalse((Path(tmpdir) / "should-not-exist").exists())
            payload = json.loads(override_result_path.read_text(encoding="utf-8"))
            self.assertTrue(payload["preflightOnly"])
            self.assertEqual(payload["run"]["resultSchemaVersion"], 2)
            self.assertTrue(payload["run"]["preflightOnly"])
            self.assertEqual(payload["config"]["textProvider"], "local-openai-compatible")
            self.assertTrue(payload["config"]["textBaseUrlLocalCompatible"])
            self.assertEqual(payload["config"]["textModel"], "local-llm-model")
            self.assertTrue(payload["config"]["embeddingBaseUrlLocalCompatible"])
            self.assertTrue(payload["config"]["databaseConfigured"])
            self.assertTrue(payload["config"]["internalServiceTokenConfigured"])
            self.assertNotIn("file-service-token", completed.stdout)
            self.assertNotIn("secret", completed.stdout)

    def test_wrapper_fails_fast_for_missing_env_file(self):
        missing_path = "/tmp/meetingmind-missing-onprem-test.env"
        completed = subprocess.run(
            ["bash", str(RUN_SCRIPT), missing_path],
            cwd=AI_DIR,
            env={**os.environ, "PYTHON": sys.executable},
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(completed.returncode, 2)
        self.assertIn("on-prem PoC env file not found", completed.stderr)

    def test_wrapper_rejects_invalid_env_file_key(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            env_file = Path(tmpdir) / "onprem.env"
            env_file.write_text("INVALID-KEY=value\n", encoding="utf-8")

            completed = subprocess.run(
                ["bash", str(RUN_SCRIPT), str(env_file)],
                cwd=AI_DIR,
                env={**os.environ, "PYTHON": sys.executable},
                text=True,
                capture_output=True,
                check=False,
            )

        self.assertEqual(completed.returncode, 2)
        self.assertIn("invalid env key", completed.stderr)

    def test_wrapper_uses_own_start_boundary_for_final_validator(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            shim = Path(tmpdir) / "python-shim"
            observed_min_started_at = Path(tmpdir) / "observed-min-started-at.txt"
            env_file = Path(tmpdir) / "onprem.env"
            env_file.write_text(
                "\n".join(
                    [
                        "ONPREM_POC_MIN_STARTED_AT=1999-01-01T00:00:00Z",
                        "ONPREM_POC_RESULT_PATH=/tmp/fake-onprem-result.json",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            shim.write_text(
                "\n".join(
                    [
                        "#!/usr/bin/env bash",
                        "set -euo pipefail",
                        "if [[ \"${1:-}\" == \"-c\" ]]; then",
                        "  printf '2026-07-22T03:00:00Z\\n'",
                        "  exit 0",
                        "fi",
                        "case \"${1:-}\" in",
                        "  onprem_poc_smoke.py)",
                        "    exit 0",
                        "    ;;",
                        "  onprem_poc_validate.py)",
                        f"    printf '%s\\n' \"$ONPREM_POC_MIN_STARTED_AT\" > {observed_min_started_at}",
                        "    exit 0",
                        "    ;;",
                        "esac",
                        "exit 64",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            shim.chmod(0o755)

            completed = subprocess.run(
                ["bash", str(RUN_SCRIPT), str(env_file)],
                cwd=AI_DIR,
                env={
                    **os.environ,
                    "PYTHON": str(shim),
                    "ONPREM_POC_MIN_STARTED_AT": "1998-01-01T00:00:00Z",
                },
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(completed.returncode, 0, completed.stderr)
            self.assertEqual(observed_min_started_at.read_text(encoding="utf-8").strip(), "2026-07-22T03:00:00Z")


if __name__ == "__main__":
    unittest.main()
