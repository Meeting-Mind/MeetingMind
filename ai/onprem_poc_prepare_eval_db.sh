#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATION_DIR="${ONPREM_POC_MIGRATION_DIR:-"$REPO_ROOT/backend/src/main/resources/db/migration"}"
ADMIN_DATABASE_URL="${ONPREM_POC_ADMIN_DATABASE_URL:-postgresql://meetingmind:meetingmind_local@localhost:5434/postgres}"
EVAL_DATABASE_NAME="${ONPREM_POC_EVAL_DATABASE_NAME:-meetingmind_onprem_eval}"
RESET_EVAL_DATABASE="${ONPREM_POC_RESET_EVAL_DATABASE:-false}"
POSTGRES_CONTAINER="${ONPREM_POC_POSTGRES_CONTAINER:-meetingmind-postgres-local}"
POSTGRES_USER="${ONPREM_POC_POSTGRES_USER:-meetingmind}"

if [[ ! "$EVAL_DATABASE_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "Invalid ONPREM_POC_EVAL_DATABASE_NAME: $EVAL_DATABASE_NAME" >&2
  echo "Use letters, numbers, and underscores, starting with a letter or underscore." >&2
  exit 2
fi

if [[ ! -d "$MIGRATION_DIR" ]]; then
  echo "Migration directory not found: $MIGRATION_DIR" >&2
  exit 2
fi

PSQL_MODE="host"
if ! command -v psql >/dev/null 2>&1; then
  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -Fxq "$POSTGRES_CONTAINER"; then
    PSQL_MODE="docker"
  else
    echo "psql is required to prepare the on-prem evaluation database." >&2
    echo "Install psql or start the local compose PostgreSQL container: $POSTGRES_CONTAINER" >&2
    exit 2
  fi
fi

run_admin_sql() {
  if [[ "$PSQL_MODE" == "host" ]]; then
    psql "$ADMIN_DATABASE_URL" -v ON_ERROR_STOP=1 -v "dbname=$EVAL_DATABASE_NAME"
  else
    docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -v "dbname=$EVAL_DATABASE_NAME"
  fi
}

run_eval_file() {
  local migration="$1"
  if [[ "$PSQL_MODE" == "host" ]]; then
    psql "$EVAL_DATABASE_URL" -v ON_ERROR_STOP=1 -f "$migration"
  else
    docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$EVAL_DATABASE_NAME" -v ON_ERROR_STOP=1 < "$migration"
  fi
}

if [[ "$RESET_EVAL_DATABASE" == "true" ]]; then
  run_admin_sql <<'SQL'
SELECT format('DROP DATABASE IF EXISTS %I WITH (FORCE)', :'dbname')\gexec
SQL
fi

run_admin_sql <<'SQL'
SELECT format('CREATE DATABASE %I', :'dbname')
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = :'dbname'
)\gexec
SQL

EVAL_DATABASE_URL="${AI_TEST_DATABASE_URL:-postgresql://meetingmind:meetingmind_local@localhost:5434/$EVAL_DATABASE_NAME}"

while IFS= read -r migration; do
  run_eval_file "$migration"
done < <(find "$MIGRATION_DIR" -maxdepth 1 -name 'V*.sql' -type f | sort -V)

cat <<EOF
Prepared on-prem evaluation database.

AI_TEST_DATABASE_URL=$EVAL_DATABASE_URL

Run the pgvector smoke with:
RUN_ONPREM_POC_POSTGRES_INTEGRATION=true \\
AI_TEST_DATABASE_URL=$EVAL_DATABASE_URL \\
./.venv/bin/python -m unittest tests.test_onprem_poc_postgres_integration
EOF
