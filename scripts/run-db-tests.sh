#!/bin/zsh
set -euo pipefail

# T441: dev DB와 분리된 테스트 전용 PostgreSQL을 준비하고 DB-gated 검증을 실행한다.
#
# 배경:
# - dev용 `meetingmind-postgres-local`(5434)에 직접 돌리면 두 가지가 깨진다.
#   1) `MigrationIntegrationTest`는 Flyway를 처음부터 적용하며 실행 건수를 단정하므로
#      이미 마이그레이션된 DB에서는 `expected: 10 but was: 0`으로 실패한다.
#   2) `ClovaSttTranscriptSmokeIntegrationTest` 등은 `@Transactional`이 아니라
#      dev 데이터를 오염시킨다.
# - 따라서 매 실행마다 database를 drop/create해 pristine 상태를 보장한다.
#
# CI의 `PostgreSQL Migration` job과 같은 이미지를 사용해 재현성을 맞춘다.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

CONTAINER="${MEETINGMIND_TEST_DB_CONTAINER:-meetingmind-postgres-test}"
IMAGE="${MEETINGMIND_TEST_DB_IMAGE:-pgvector/pgvector:0.8.2-pg16-bookworm}"
HOST_PORT="${MEETINGMIND_TEST_DB_PORT:-5435}"
DB_NAME="${MEETINGMIND_TEST_DB_NAME:-meetingmind_test}"
DB_USER="${MEETINGMIND_TEST_DB_USER:-meetingmind}"
DB_PASSWORD="${MEETINGMIND_TEST_DB_PASSWORD:-meetingmind_test}"

if [[ "$HOST_PORT" == "5434" ]]; then
  echo "error: 5434는 dev DB(meetingmind-postgres-local) 포트다. 테스트 DB와 분리해야 한다." >&2
  exit 1
fi

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "[1/3] 테스트 DB 컨테이너 생성 ($CONTAINER, port $HOST_PORT)"
  docker run -d --name "$CONTAINER" \
    -e POSTGRES_DB="$DB_NAME" \
    -e POSTGRES_USER="$DB_USER" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -p "$HOST_PORT":5432 \
    "$IMAGE" >/dev/null
elif [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" != "true" ]]; then
  echo "[1/3] 기존 테스트 DB 컨테이너 기동 ($CONTAINER)"
  docker start "$CONTAINER" >/dev/null
else
  echo "[1/3] 테스트 DB 컨테이너 이미 실행 중 ($CONTAINER)"
fi

echo "[2/3] 준비 대기 후 $DB_NAME 초기화"
for i in $(seq 1 60); do
  if docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d postgres >/dev/null 2>&1; then
    break
  fi
  if (( i == 60 )); then
    echo "error: 테스트 DB가 준비되지 않았다." >&2
    exit 1
  fi
  sleep 1
done

# pristine 보장: 남은 연결을 끊고 database를 재생성한다.
docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "select pg_terminate_backend(pid) from pg_stat_activity where datname = '$DB_NAME' and pid <> pg_backend_pid();" \
  -c "drop database if exists $DB_NAME;" \
  -c "create database $DB_NAME;" >/dev/null

echo "[3/3] DB-gated 검증 실행"
cd "$ROOT_DIR/backend"
CI_POSTGRES_URL="jdbc:postgresql://localhost:$HOST_PORT/$DB_NAME" \
CI_POSTGRES_USER="$DB_USER" \
CI_POSTGRES_PASSWORD="$DB_PASSWORD" \
exec ./gradlew test "$@"
