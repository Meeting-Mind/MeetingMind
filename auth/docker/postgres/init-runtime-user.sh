#!/bin/sh
set -eu

: "${AUTH_DB_RUNTIME_PASSWORD:?AUTH_DB_RUNTIME_PASSWORD is required}"

psql \
  --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=runtime_password="$AUTH_DB_RUNTIME_PASSWORD" <<-'EOSQL'
SELECT format(
  'CREATE ROLE meetingmind_auth_app LOGIN PASSWORD %L',
  :'runtime_password'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = 'meetingmind_auth_app'
)
\gexec
EOSQL
