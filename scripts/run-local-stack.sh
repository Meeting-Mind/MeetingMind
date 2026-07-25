#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT_DIR/tmp/local-dev-logs"
mkdir -p "$LOG_DIR"

BACKEND_PORT="${BACKEND_SERVER_PORT:-8080}"
AI_PORT="${AI_PORT:-8000}"
BFF_PORT="${BFF_SERVER_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

# stale listener가 남아 있으면 포트는 열려 보이는데 실제 요청은 이전 프로세스가
# 받는다. 그 상태로 smoke에 들어가면 LiveKit/STT/AI 결과가 false negative로
# 보이므로, 기동 전에 점유 상태를 먼저 확인하고 멈춘다.
check_port() {
  local name="$1" port="$2"
  local holder
  holder="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | tail -n +2 || true)"
  if [[ -n "$holder" ]]; then
    echo "error: $name 포트 $port 가 이미 사용 중이다." >&2
    echo "$holder" >&2
    echo "기존 프로세스를 종료한 뒤 다시 실행한다." >&2
    return 1
  fi
}

echo "[preflight] 포트 점유 확인"
preflight_failed=0
check_port backend "$BACKEND_PORT" || preflight_failed=1
check_port ai "$AI_PORT" || preflight_failed=1
check_port bff "$BFF_PORT" || preflight_failed=1
check_port frontend "$FRONTEND_PORT" || preflight_failed=1
if (( preflight_failed )); then
  exit 1
fi

# nohup은 실행 시작만 보장한다. 포트 bind 실패나 venv 누락으로 즉시 죽은
# 경우까지 성공으로 보고하지 않도록 기동 후 생존 여부를 확인한다.
start_service() {
  local label="$1" name="$2" script="$3" wait_seconds="$4"
  echo "$label $name"
  nohup "$ROOT_DIR/scripts/$script" >"$LOG_DIR/$name.log" 2>&1 &
  local pid=$!
  echo "$pid" >"$LOG_DIR/$name.pid"
  sleep "$wait_seconds"
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "error: $name 이 기동 직후 종료됐다. 로그: $LOG_DIR/$name.log" >&2
    tail -n 20 "$LOG_DIR/$name.log" >&2 || true
    return 1
  fi
}

start_service "[1/4]" backend run-backend.sh 3
start_service "[2/4]" ai run-ai.sh 2
start_service "[3/4]" bff run-bff-legacy.sh 3
start_service "[4/4]" frontend run-frontend.sh 0

echo "logs: $LOG_DIR"
