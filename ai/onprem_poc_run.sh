#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON:-"$SCRIPT_DIR/.venv/bin/python"}"
ENV_FILE="${1:-${ONPREM_POC_ENV_FILE:-}}"

if [[ ! -x "$PYTHON_BIN" ]]; then
  PYTHON_BIN="${PYTHON_FALLBACK:-python3}"
fi

if [[ -n "$ENV_FILE" ]]; then
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "on-prem PoC env file not found: $ENV_FILE" >&2
    exit 2
  fi
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    line="${raw_line#"${raw_line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    if [[ -z "$line" || "$line" == \#* || "$line" != *=* ]]; then
      continue
    fi
    if [[ "$line" == export[[:space:]]* ]]; then
      line="${line#export}"
      line="${line#"${line%%[![:space:]]*}"}"
    fi
    key="${line%%=*}"
    key="${key%"${key##*[![:space:]]}"}"
    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "invalid env key in $ENV_FILE: $key" >&2
      exit 2
    fi
    if [[ -n "${!key+x}" ]]; then
      continue
    fi
    value="${line#*=}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "$value" == \"*\" && "$value" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "$key=$value"
  done < "$ENV_FILE"
fi

export RUN_ONPREM_AI_POC_SMOKE="${RUN_ONPREM_AI_POC_SMOKE:-true}"
export ONPREM_POC_REQUIRE_RETRIEVAL="${ONPREM_POC_REQUIRE_RETRIEVAL:-true}"
export ONPREM_POC_RESULT_PATH="${ONPREM_POC_RESULT_PATH:-/tmp/meetingmind-onprem-poc-result.json}"
ONPREM_POC_WRAPPER_STARTED_AT="$("$PYTHON_BIN" -c 'from datetime import UTC, datetime; print(datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z"))')"

cd "$SCRIPT_DIR"
"$PYTHON_BIN" onprem_poc_smoke.py
if [[ "${ONPREM_POC_PREFLIGHT_ONLY:-false}" =~ ^(1|true|TRUE|yes|YES|y|Y|on|ON)$ ]]; then
  exit 0
fi
ONPREM_POC_MIN_STARTED_AT="$ONPREM_POC_WRAPPER_STARTED_AT" "$PYTHON_BIN" onprem_poc_validate.py "$ONPREM_POC_RESULT_PATH"
