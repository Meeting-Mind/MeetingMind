"""`mtls-proxy` internal auth mode의 exact 단일 XFCC 재검증 경계 테스트."""

import json
import os
import unittest
from unittest.mock import patch

from app.main import app

CORE_SPIFFE_ID = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core"
AI_SPIFFE_ID = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-ai"
VALID_XFCC = f"By={AI_SPIFFE_ID};Hash=0123abcd;URI={CORE_SPIFFE_ID}"
INTERNAL_PATH = "/api/internal/meeting-ai/chat"

MTLS_PROXY_ENV = {
    "AI_INTERNAL_AUTH_MODE": "mtls-proxy",
    "AI_INTERNAL_ALLOWED_SPIFFE_ID": CORE_SPIFFE_ID,
    "AI_INTERNAL_SERVICE_TOKEN": "stale-shared-token",
}


async def asgi_request_with_raw_headers(
    method: str,
    path: str,
    extra_headers: list[tuple[str, str]],
) -> tuple[int, dict[str, object]]:
    raw_headers = [
        (b"host", b"testserver"),
        (b"content-type", b"application/json"),
    ]
    for key, value in extra_headers:
        raw_headers.append((key.lower().encode("latin-1"), value.encode("latin-1")))
    scope = {
        "type": "http",
        "asgi": {"version": "3.0", "spec_version": "2.3"},
        "http_version": "1.1",
        "method": method,
        "scheme": "http",
        "path": path,
        "raw_path": path.encode("ascii"),
        "query_string": b"",
        "headers": raw_headers,
        "client": ("127.0.0.1", 12345),
        "server": ("testserver", 80),
    }
    sent_body = False
    status = 500
    response_body = bytearray()

    async def receive() -> dict[str, object]:
        nonlocal sent_body
        if sent_body:
            return {"type": "http.disconnect"}
        sent_body = True
        return {"type": "http.request", "body": b"{}", "more_body": False}

    async def send(message: dict[str, object]) -> None:
        nonlocal status
        if message["type"] == "http.response.start":
            status = int(message["status"])
        elif message["type"] == "http.response.body":
            response_body.extend(message.get("body", b""))

    await app(scope, receive, send)
    payload: dict[str, object] = {}
    if response_body:
        payload = json.loads(bytes(response_body).decode("utf-8"))
    return status, payload


class InternalMtlsProxyAuthTest(unittest.IsolatedAsyncioTestCase):
    async def _request(self, headers: list[tuple[str, str]]) -> tuple[int, dict[str, object]]:
        return await asgi_request_with_raw_headers("POST", INTERNAL_PATH, headers)

    async def test_verified_single_xfcc_uri_passes_authentication(self) -> None:
        with patch.dict(os.environ, MTLS_PROXY_ENV, clear=False):
            status, payload = await self._request(
                [("x-forwarded-client-cert", VALID_XFCC)]
            )
        self.assertNotEqual(status, 401)
        self.assertNotEqual(payload.get("code"), "AI_INTERNAL_UNAUTHORIZED")

    async def test_missing_or_duplicated_xfcc_is_rejected(self) -> None:
        cases: dict[str, list[tuple[str, str]]] = {
            "missing header": [],
            "duplicated header": [
                ("x-forwarded-client-cert", VALID_XFCC),
                ("x-forwarded-client-cert", VALID_XFCC),
            ],
            "multiple certificate elements": [
                (
                    "x-forwarded-client-cert",
                    f"{VALID_XFCC},Hash=ffff;URI={CORE_SPIFFE_ID}",
                )
            ],
            "multiple uri keys": [
                (
                    "x-forwarded-client-cert",
                    f"Hash=0123abcd;URI={CORE_SPIFFE_ID};URI={CORE_SPIFFE_ID}",
                )
            ],
            "missing uri key": [
                ("x-forwarded-client-cert", f"By={AI_SPIFFE_ID};Hash=0123abcd")
            ],
            "wrong principal": [
                (
                    "x-forwarded-client-cert",
                    "Hash=0123abcd;URI=spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-bff",
                )
            ],
            "quoted value": [
                ("x-forwarded-client-cert", f'Hash="0123abcd";URI={CORE_SPIFFE_ID}')
            ],
            "malformed field": [
                ("x-forwarded-client-cert", f"Hash;URI={CORE_SPIFFE_ID}")
            ],
        }
        for name, headers in cases.items():
            with self.subTest(name=name):
                with patch.dict(os.environ, MTLS_PROXY_ENV, clear=False):
                    status, payload = await self._request(headers)
                self.assertEqual(status, 401)
                self.assertEqual(payload["code"], "AI_INTERNAL_UNAUTHORIZED")

    async def test_shared_token_header_is_ignored_in_mtls_proxy_mode(self) -> None:
        with patch.dict(os.environ, MTLS_PROXY_ENV, clear=False):
            status, payload = await self._request(
                [("x-meetingmind-service-token", "stale-shared-token")]
            )
        self.assertEqual(status, 401)
        self.assertEqual(payload["code"], "AI_INTERNAL_UNAUTHORIZED")

    async def test_mtls_proxy_mode_fails_closed_without_allowed_principal(self) -> None:
        environment = {
            "AI_INTERNAL_AUTH_MODE": "mtls-proxy",
            "AI_INTERNAL_ALLOWED_SPIFFE_ID": "",
        }
        with patch.dict(os.environ, environment, clear=False):
            status, payload = await self._request(
                [("x-forwarded-client-cert", VALID_XFCC)]
            )
        self.assertEqual(status, 401)
        self.assertEqual(payload["code"], "AI_INTERNAL_UNAUTHORIZED")

    async def test_unknown_mode_fails_closed(self) -> None:
        environment = dict(MTLS_PROXY_ENV, AI_INTERNAL_AUTH_MODE="disabled")
        with patch.dict(os.environ, environment, clear=False):
            status, payload = await self._request(
                [
                    ("x-forwarded-client-cert", VALID_XFCC),
                    ("x-meetingmind-service-token", "stale-shared-token"),
                ]
            )
        self.assertEqual(status, 401)
        self.assertEqual(payload["code"], "AI_INTERNAL_UNAUTHORIZED")

    async def test_shared_token_mode_ignores_xfcc_header(self) -> None:
        environment = {
            "AI_INTERNAL_AUTH_MODE": "shared-token",
            "AI_INTERNAL_SERVICE_TOKEN": "service-secret",
        }
        with patch.dict(os.environ, environment, clear=False):
            status, payload = await self._request(
                [("x-forwarded-client-cert", VALID_XFCC)]
            )
        self.assertEqual(status, 401)
        self.assertEqual(payload["code"], "AI_INTERNAL_UNAUTHORIZED")

    async def test_health_stays_public_and_reports_auth_mode(self) -> None:
        with patch.dict(os.environ, MTLS_PROXY_ENV, clear=False):
            status, payload = await asgi_request_with_raw_headers("GET", "/health", [])
        self.assertEqual(status, 200)
        self.assertEqual(payload["internal_auth_mode"], "mtls-proxy")


if __name__ == "__main__":
    unittest.main(verbosity=2)
