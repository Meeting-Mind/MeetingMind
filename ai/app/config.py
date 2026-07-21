import os
from pathlib import Path


APP_ROOT = Path(__file__).resolve().parent.parent
DOTENV_CANDIDATES = (
    APP_ROOT / ".env",
    APP_ROOT.parent / ".env",
    APP_ROOT.parent / "backend" / ".env",
)
ENV_ALIASES = {
    "OPENAI_API_KEY": ("OPEN_AI_KEY",),
}


def load_dotenv() -> dict[str, str]:
    values: dict[str, str] = {}
    for candidate in DOTENV_CANDIDATES:
        if not candidate.exists():
            continue
        for raw_line in candidate.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values.setdefault(key.strip(), value.strip().strip("\"'"))
    return values


ENV_CACHE = load_dotenv()


def get_env(key: str, default: str | None = None) -> str | None:
    keys = (key, *ENV_ALIASES.get(key, ()))
    for source in (os.environ, ENV_CACHE):
        for candidate in keys:
            value = source.get(candidate)
            if value:
                return value
    return default
