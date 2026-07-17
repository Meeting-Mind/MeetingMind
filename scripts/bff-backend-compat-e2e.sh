#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${E2E_BACKEND_PORT:-18080}"
BFF_PORT="${E2E_BFF_PORT:-18081}"
REDIS_HOST="${BFF_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${BFF_REDIS_PORT:-6379}"
TEST_ID="t016-$$-${RANDOM}"
WORK_DIR="$(mktemp -d)"
COOKIE_JAR="${WORK_DIR}/cookies.txt"
BACKEND_LOG="${WORK_DIR}/backend.log"
BFF_LOG="${WORK_DIR}/bff.log"
BACKEND_PID=""
BFF_PID=""

cleanup() {
  if [[ -n "${BFF_PID}" ]]; then
    kill "${BFF_PID}" 2>/dev/null || true
    wait "${BFF_PID}" 2>/dev/null || true
  fi
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

fail() {
  echo "T016 E2E 실패: $1" >&2
  exit 1
}

fail_startup() {
  echo "T016 E2E 시작 실패: $1" >&2
  if [[ -s "${BACKEND_LOG}" ]]; then
    tail -n 40 "${BACKEND_LOG}" >&2
  fi
  if [[ -s "${BFF_LOG}" ]]; then
    tail -n 40 "${BFF_LOG}" >&2
  fi
  exit 1
}

assert_port_available() {
  local port="$1"
  if (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
    exec 3>&- 3<&-
    fail "포트 ${port}가 이미 사용 중임; E2E_BACKEND_PORT/E2E_BFF_PORT로 빈 포트를 지정할 것"
  fi
}

wait_for_http() {
  local url="$1"
  local pid="$2"
  local attempts=60
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if ! kill -0 "${pid}" 2>/dev/null; then
      return 1
    fi
    if curl --silent --max-time 1 --output /dev/null "${url}"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local step="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    fail "${step} HTTP ${expected} 예상, 실제 ${actual}"
  fi
}

assert_no_token_material() {
  local target="$1"
  if grep --quiet --extended-regexp \
      '("accessToken"|"refreshToken"|mmr_[A-Za-z0-9_-]+|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)' \
      "${target}"; then
    echo "T016 E2E 실패: 브라우저 응답 또는 로그에서 token material 감지" >&2
    exit 1
  fi
}

cd "${ROOT_DIR}"
assert_port_available "${BACKEND_PORT}"
assert_port_available "${BFF_PORT}"
./backend/gradlew -p backend --no-daemon bootJar >/dev/null
./bff/gradlew -p bff --no-daemon bootJar >/dev/null

BACKEND_JAR="$(find backend/build/libs -name '*.jar' ! -name '*-plain.jar' -print -quit)"
BFF_JAR="$(find bff/build/libs -name '*.jar' ! -name '*-plain.jar' -print -quit)"
[[ -n "${BACKEND_JAR}" && -n "${BFF_JAR}" ]] || fail "실행 가능한 bootJar를 찾지 못함"

SPRING_PROFILES_ACTIVE=test \
AUTH_JWT_SECRET='t016-backend-jwt-secret-for-compatibility-e2e-only' \
java -jar "${BACKEND_JAR}" --server.port="${BACKEND_PORT}" >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!
wait_for_http "http://127.0.0.1:${BACKEND_PORT}/api/v1/auth/me" "${BACKEND_PID}" \
  || fail_startup "현재 Backend 시작 실패"

SPRING_PROFILES_ACTIVE=local \
BFF_SERVER_PORT="${BFF_PORT}" \
BFF_REDIS_HOST="${REDIS_HOST}" \
BFF_REDIS_PORT="${REDIS_PORT}" \
BFF_BACKEND_BASE_URL="http://127.0.0.1:${BACKEND_PORT}" \
BFF_SESSION_COOKIE_SECURE=false \
BFF_TOKEN_VAULT_LOCAL_KEY_BASE64='AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=' \
java -jar "${BFF_JAR}" \
  --spring.session.redis.namespace="meetingmind:bff:t016:session:${TEST_ID}" \
  --meetingmind.bff.token-vault.namespace="meetingmind:bff:t016:vault:${TEST_ID}" \
  --meetingmind.bff.token-manager.lock-namespace="meetingmind:bff:t016:lock:${TEST_ID}" \
  --meetingmind.bff.token-manager.access-expiry-skew=2h \
  >"${BFF_LOG}" 2>&1 &
BFF_PID=$!
wait_for_http "http://127.0.0.1:${BFF_PORT}/actuator/health/readiness" "${BFF_PID}" \
  || fail_startup "Web BFF 시작 또는 Redis readiness 실패"

csrf_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/csrf.json" --write-out '%{http_code}' \
  --cookie-jar "${COOKIE_JAR}" \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/csrf")"
assert_status "${csrf_status}" 200 "CSRF bootstrap"
csrf_token="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "${WORK_DIR}/csrf.json")"
[[ -n "${csrf_token}" ]] || fail "CSRF token 파싱 실패"

missing_csrf_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/missing-csrf.json" --write-out '%{http_code}' \
  --cookie "${COOKIE_JAR}" \
  --header 'Content-Type: application/json' \
  --data '{"email":"e2e@example.com","password":"password-123!","displayName":"E2E User","rememberMe":false}' \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/signup")"
if [[ "${missing_csrf_status}" != "401" && "${missing_csrf_status}" != "403" ]]; then
  fail "CSRF 누락 signup이 거부되지 않음: HTTP ${missing_csrf_status}"
fi

signup_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/signup.json" --write-out '%{http_code}' \
  --cookie "${COOKIE_JAR}" --cookie-jar "${COOKIE_JAR}" \
  --header 'Content-Type: application/json' \
  --header "X-CSRF-TOKEN: ${csrf_token}" \
  --data '{"email":"e2e@example.com","password":"password-123!","displayName":"E2E User","rememberMe":false}' \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/signup")"
assert_status "${signup_status}" 201 "BFF→Backend signup"
assert_no_token_material "${WORK_DIR}/signup.json"

stale_cookie="$(awk '$6 == "mm-session" {print $6 "=" $7}' "${COOKIE_JAR}" | tail -n 1)"
[[ -n "${stale_cookie}" ]] || fail "인증 세션 cookie 확인 실패"

spaces_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/spaces.json" --write-out '%{http_code}' \
  --cookie "${COOKIE_JAR}" \
  "http://127.0.0.1:${BFF_PORT}/api/v1/spaces")"
assert_status "${spaces_status}" 200 "강제 선제 refresh 후 Core proxy"
assert_no_token_material "${WORK_DIR}/spaces.json"

missing_logout_csrf_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/missing-logout-csrf.json" --write-out '%{http_code}' \
  --cookie "${COOKIE_JAR}" --request POST \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/logout")"
assert_status "${missing_logout_csrf_status}" 403 "CSRF 누락 logout"

logout_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/logout.json" --write-out '%{http_code}' \
  --cookie "${COOKIE_JAR}" --cookie-jar "${COOKIE_JAR}" \
  --header "X-CSRF-TOKEN: ${csrf_token}" --request POST \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/logout")"
assert_status "${logout_status}" 204 "현재 세션 logout"

stale_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/stale.json" --write-out '%{http_code}' \
  --header "Cookie: ${stale_cookie}" \
  "http://127.0.0.1:${BFF_PORT}/api/v1/spaces")"
assert_status "${stale_status}" 401 "폐기 세션 재사용"

new_csrf_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/new-csrf.json" --write-out '%{http_code}' \
  --cookie-jar "${COOKIE_JAR}" \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/csrf")"
assert_status "${new_csrf_status}" 200 "logout 멱등성용 CSRF bootstrap"
new_csrf_token="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "${WORK_DIR}/new-csrf.json")"
idempotent_logout_status="$(curl --silent --show-error \
  --output "${WORK_DIR}/idempotent-logout.json" --write-out '%{http_code}' \
  --cookie "${COOKIE_JAR}" --cookie-jar "${COOKIE_JAR}" \
  --header "X-CSRF-TOKEN: ${new_csrf_token}" --request POST \
  "http://127.0.0.1:${BFF_PORT}/api/v1/auth/logout")"
assert_status "${idempotent_logout_status}" 204 "logout 멱등성"

for response_file in "${WORK_DIR}"/*.json; do
  assert_no_token_material "${response_file}"
done
assert_no_token_material "${BFF_LOG}"
assert_no_token_material "${BACKEND_LOG}"

echo "T016 BFF→현재 Backend 호환 E2E 통과"
