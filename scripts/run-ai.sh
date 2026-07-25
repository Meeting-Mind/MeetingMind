#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
AI_DIR="$ROOT_DIR/ai"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  source "$ROOT_DIR/.env"
  set +a
fi

if [[ -f "$AI_DIR/.env" ]]; then
  set -a
  source "$AI_DIR/.env"
  set +a
fi

export AI_PORT="${AI_PORT:-8000}"

cd "$AI_DIR"
if [[ -x "$AI_DIR/.venv/bin/uvicorn" ]]; then
  exec "$AI_DIR/.venv/bin/uvicorn" app.main:app --host 127.0.0.1 --port "$AI_PORT"
fi

exec python3 -m uvicorn app.main:app --host 127.0.0.1 --port "$AI_PORT"
