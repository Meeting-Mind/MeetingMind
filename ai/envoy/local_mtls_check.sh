#!/usr/bin/env bash
# AI Envoy sidecar의 로컬 mTLS positive/negative matrix.
#
# OS 임시 디렉터리에 일회성 CA/leaf만 생성해 다음을 검증하고 종료 시 전부 삭제한다.
#   1. Core client certificate 요청 성공과 XFCC sanitize(SANITIZE_SET) 확인
#   2. client certificate 없음 → TLS handshake 거부
#   3. 다른 CA의 client certificate → TLS handshake 거부
#   4. wrong SPIFFE(BFF) client certificate → TLS/RBAC 거부
#   5. 위조 X-Forwarded-Client-Cert header가 backend에 전달되지 않음
#   6. 다른 컨테이너에서 direct Uvicorn 8001 접근 거부(loopback bind)
#
# 요구사항: docker, python3, 시스템 curl/openssl. 실제 NonProd CA는 사용하지 않는다.
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKI="${REPOSITORY_ROOT}/scripts/pki/nonprod/pki.py"
MANIFESTS="${REPOSITORY_ROOT}/scripts/pki/nonprod/manifests"
ENVOY_IMAGE_TAG="${ENVOY_IMAGE_TAG:-meetingmind-ai-envoy:local-check}"
BACKEND_IMAGE="python:3.12.13-alpine3.24@sha256:6d43704baacd1bfbe7c295d7f13079d5d8104ed33568873133f8fc69980419df"
ENVOY_CONTAINER="mtls-check-envoy"
BACKEND_CONTAINER="mtls-check-backend"
HOST_PORT=18000

WORK="$(mktemp -d "${TMPDIR:-/tmp}/meetingmind-mtls-check-XXXXXX")"
WORK="$(cd "${WORK}" && pwd -P)"
chmod 700 "${WORK}"

cleanup() {
  docker rm --force "${BACKEND_CONTAINER}" "${ENVOY_CONTAINER}" >/dev/null 2>&1 || true
  rm -rf "${WORK}"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

echo "[1/6] one-time PKI material"
for name in root intermediate other-root other-intermediate; do
  python3 - "$WORK/${name}.passphrase" <<'PY'
import secrets, sys, os
path = sys.argv[1]
with open(path, "w", encoding="utf-8") as stream:
    stream.write(secrets.token_urlsafe(48) + "\n")
os.chmod(path, 0o600)
PY
done
python3 "${PKI}" init-ca --output "${WORK}/ca" \
  --root-passphrase-file "${WORK}/root.passphrase" \
  --intermediate-passphrase-file "${WORK}/intermediate.passphrase" >/dev/null
python3 "${PKI}" init-ca --output "${WORK}/other-ca" \
  --root-passphrase-file "${WORK}/other-root.passphrase" \
  --intermediate-passphrase-file "${WORK}/other-intermediate.passphrase" >/dev/null
for service in ai core bff; do
  python3 "${PKI}" issue --ca-dir "${WORK}/ca" \
    --manifest "${MANIFESTS}/${service}.json" \
    --output "${WORK}/${service}" \
    --intermediate-passphrase-file "${WORK}/intermediate.passphrase" >/dev/null
done
python3 "${PKI}" issue --ca-dir "${WORK}/other-ca" \
  --manifest "${MANIFESTS}/core.json" \
  --output "${WORK}/other-core" \
  --intermediate-passphrase-file "${WORK}/other-intermediate.passphrase" >/dev/null

TLS_DIR="${WORK}/tls"
mkdir -p "${TLS_DIR}"
cp "${WORK}/ai/certificate.pem" "${TLS_DIR}/tls.crt"
cp "${WORK}/ai/private-key.pem" "${TLS_DIR}/tls.key"
cp "${WORK}/ai/ca-bundle.pem" "${TLS_DIR}/ca.crt"
# 일회성 검증 전용 material이므로 container UID 10001이 읽도록 완화한다.
chmod 755 "${WORK}" "${TLS_DIR}"
chmod 644 "${TLS_DIR}"/tls.crt "${TLS_DIR}"/tls.key "${TLS_DIR}"/ca.crt

echo "[2/6] envoy image and containers"
docker buildx build --quiet --platform linux/arm64 --load \
  --tag "${ENVOY_IMAGE_TAG}" "${REPOSITORY_ROOT}/ai/envoy" >/dev/null
docker run --detach --name "${ENVOY_CONTAINER}" \
  --publish "127.0.0.1:${HOST_PORT}:8000" \
  --volume "${TLS_DIR}:/run/meetingmind/tls:ro" \
  "${ENVOY_IMAGE_TAG}" >/dev/null
docker run --detach --name "${BACKEND_CONTAINER}" \
  --network "container:${ENVOY_CONTAINER}" \
  "${BACKEND_IMAGE}" python3 -c '
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        payload = json.dumps(
            {"xfcc": self.headers.get_all("x-forwarded-client-cert") or []}
        ).encode()
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *arguments):
        pass

HTTPServer(("127.0.0.1", 8001), Handler).serve_forever()
' >/dev/null

CURL_BASE=(curl --silent --show-error --max-time 10
  --resolve "ai.meetingmind.internal:${HOST_PORT}:127.0.0.1"
  --cacert "${WORK}/ai/ca-bundle.pem")
URL="https://ai.meetingmind.internal:${HOST_PORT}/echo"
CORE_IDENTITY=(--cert "${WORK}/core/certificate.pem" --key "${WORK}/core/private-key.pem")

echo "[3/6] positive: core client certificate"
POSITIVE=""
STATUS="000"
for _ in $(seq 1 30); do
  if RESPONSE="$("${CURL_BASE[@]}" --write-out $'\n%{http_code}' \
    "${CORE_IDENTITY[@]}" "${URL}" 2>/dev/null)"; then
    STATUS="${RESPONSE##*$'\n'}"
    if [ "${STATUS}" = "200" ]; then
      POSITIVE="${RESPONSE%$'\n'*}"
      break
    fi
  fi
  sleep 1
done
if [ -z "${POSITIVE}" ]; then
  fail "core client certificate request did not return 200 (last status ${STATUS})"
fi
python3 - "$POSITIVE" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
xfcc = payload["xfcc"]
assert len(xfcc) == 1, f"expected exactly one XFCC header, got {len(xfcc)}"
element = xfcc[0]
assert "," not in element, "unexpected multiple certificate elements"
uris = [field.split("=", 1)[1] for field in element.split(";") if field.startswith("URI=")]
assert uris == ["spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core"], uris
assert any(field.startswith("Hash=") for field in element.split(";")), "missing Hash"
PY
echo "  OK: 200 with sanitized single XFCC URI=core"

echo "[4/6] negative: handshake rejections"
if "${CURL_BASE[@]}" "${URL}" >/dev/null 2>&1; then
  fail "request without client certificate must be rejected"
fi
echo "  OK: no client certificate rejected"
if "${CURL_BASE[@]}" --cert "${WORK}/other-core/certificate.pem" \
  --key "${WORK}/other-core/private-key.pem" "${URL}" >/dev/null 2>&1; then
  fail "wrong-CA client certificate must be rejected"
fi
echo "  OK: wrong-CA client certificate rejected"
if "${CURL_BASE[@]}" --cert "${WORK}/bff/certificate.pem" \
  --key "${WORK}/bff/private-key.pem" "${URL}" >/dev/null 2>&1; then
  fail "wrong-SPIFFE client certificate must be rejected"
fi
echo "  OK: wrong-SPIFFE (BFF) client certificate rejected"

echo "[5/6] negative: spoofed XFCC header is replaced"
SPOOFED="$("${CURL_BASE[@]}" "${CORE_IDENTITY[@]}" \
  --header "X-Forwarded-Client-Cert: URI=spiffe://meetingmind.internal/ns/nonprod-v2/sa/attacker" \
  "${URL}")"
python3 - "$SPOOFED" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
xfcc = payload["xfcc"]
assert len(xfcc) == 1, f"expected exactly one XFCC header, got {len(xfcc)}"
assert "attacker" not in xfcc[0], "spoofed XFCC leaked to the backend"
uris = [field.split("=", 1)[1] for field in xfcc[0].split(";") if field.startswith("URI=")]
assert uris == ["spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core"], uris
PY
echo "  OK: spoofed XFCC replaced with verified URI"

echo "[6/6] negative: direct Uvicorn 8001 is loopback-only"
ENVOY_IP="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${ENVOY_CONTAINER}")"
if docker run --rm "${BACKEND_IMAGE}" python3 -c "
import urllib.request
urllib.request.urlopen('http://${ENVOY_IP}:8001/echo', timeout=5)
" >/dev/null 2>&1; then
  fail "direct backend port 8001 must not be reachable from outside the task"
fi
echo "  OK: direct 8001 access rejected"

echo "local mTLS matrix: all checks passed"
