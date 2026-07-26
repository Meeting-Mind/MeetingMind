#!/bin/zsh
set -euo pipefail

# Embedding worker는 FastAPI app(`app.main`)이 아니라 독립 프로세스다.
# `scripts/run-ai.sh`는 이 worker를 띄우지 않고, `compose.local.yml`의
# `meetingmind-ai-worker`는 `profiles: ["ai"]` 뒤에 있어 기본 `up`으로는 뜨지 않는다.
# 그래서 local에서 `embedding_jobs`가 소비되지 않고 PENDING으로 쌓인다.
#
# 주의: 실제 embedding provider를 호출하므로 OpenAI 과금이 발생한다.

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

# local dev PostgreSQL host port는 5432가 아니라 5434다.
export AI_DATABASE_URL="${AI_DATABASE_URL:-postgresql://meetingmind:meetingmind_local@localhost:5434/meetingmind}"

if [[ -z "${OPENAI_API_KEY:-}" && -z "${AI_EMBEDDING_API_KEY:-}" && "${AI_EMBEDDING_PROVIDER:-openai}" == "openai" ]]; then
  print -u2 "OPENAI_API_KEY가 없다. embedding provider 호출이 PROVIDER_UNAVAILABLE로 실패한다."
  exit 1
fi

cd "$AI_DIR"
if [[ -x "$AI_DIR/.venv/bin/python" ]]; then
  exec "$AI_DIR/.venv/bin/python" -m app.embedding_worker
fi

exec python3 -m app.embedding_worker
