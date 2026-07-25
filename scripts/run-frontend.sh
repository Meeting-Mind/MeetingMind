#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"

if [[ -f "$FRONTEND_DIR/.env" ]]; then
  set -a
  source "$FRONTEND_DIR/.env"
  set +a
fi

cd "$FRONTEND_DIR"
exec npm run dev -- --host 127.0.0.1 --port 5173
