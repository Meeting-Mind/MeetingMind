#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT_DIR/tmp/local-dev-logs"
mkdir -p "$LOG_DIR"

echo "[1/4] backend"
nohup "$ROOT_DIR/scripts/run-backend.sh" >"$LOG_DIR/backend.log" 2>&1 &
echo $! >"$LOG_DIR/backend.pid"

sleep 3

echo "[2/4] ai"
nohup "$ROOT_DIR/scripts/run-ai.sh" >"$LOG_DIR/ai.log" 2>&1 &
echo $! >"$LOG_DIR/ai.pid"

sleep 2

echo "[3/4] bff legacy"
nohup "$ROOT_DIR/scripts/run-bff-legacy.sh" >"$LOG_DIR/bff.log" 2>&1 &
echo $! >"$LOG_DIR/bff.pid"

sleep 3

echo "[4/4] frontend"
nohup "$ROOT_DIR/scripts/run-frontend.sh" >"$LOG_DIR/frontend.log" 2>&1 &
echo $! >"$LOG_DIR/frontend.pid"

echo "logs: $LOG_DIR"
