#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BFF_DIR="$ROOT_DIR/bff"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  source "$ROOT_DIR/.env"
  set +a
fi

export BFF_AUTH_PROVIDER="${BFF_AUTH_PROVIDER:-legacy}"
export BFF_AUTH_ISSUER="${BFF_AUTH_ISSUER:-meetingmind-core-legacy}"
export BFF_BACKEND_BASE_URL="${BFF_BACKEND_BASE_URL:-http://127.0.0.1:8080}"
export BFF_CORE_BASE_URL="${BFF_CORE_BASE_URL:-http://127.0.0.1:8080}"
export BFF_AI_BASE_URL="${BFF_AI_BASE_URL:-http://127.0.0.1:8080}"
export BFF_LIVEKIT_BASE_URL="${BFF_LIVEKIT_BASE_URL:-http://127.0.0.1:8080}"
export BFF_SESSION_COOKIE_NAME="${BFF_SESSION_COOKIE_NAME:-mm-session}"
export BFF_SESSION_COOKIE_SECURE="${BFF_SESSION_COOKIE_SECURE:-false}"
export BFF_TOKEN_VAULT_KEY_PROVIDER="${BFF_TOKEN_VAULT_KEY_PROVIDER:-local}"
export BFF_TOKEN_VAULT_LOCAL_KEY_BASE64="${BFF_TOKEN_VAULT_LOCAL_KEY_BASE64:-AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=}"
export BFF_REDIS_PORT="${BFF_REDIS_PORT:-6380}"
export BFF_SERVER_PORT="${BFF_SERVER_PORT:-8081}"

cd "$BFF_DIR"
exec ./gradlew bootRun
