# Browser-BFF Auth API Contract

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Browser Contract |
| Owner | Web BFF |
| Base path | `/api/v1/auth` |
| Related requirements | FR-AUTH-01~10, FR-AUTH-16, FR-AUTH-18, FR-BFF-001~010, NFR-SEC-01~08 |
| Related data model | BffSession, TokenBundle |

## Security Rules

- 브라우저 응답과 JavaScript 저장소에는 MeetingMind access/refresh token이 없다.
- 운영 session cookie 기본값은 `__Host-mm-session; Secure; HttpOnly; SameSite=Strict; Path=/`이며 `Domain`을 설정하지 않는다.
- 로컬 HTTP 개발은 `__Host-`/`Secure`를 사용할 수 없으므로 profile이 분리된 `mm-session` host-only cookie만 예외로 허용한다. 운영 profile에서 이 예외를 사용할 수 없어야 한다.
- 일반 세션은 60분 유휴/12시간 절대 만료, Remember me는 7일 sliding 유휴/14일 절대 만료를 사용한다. sliding 갱신은 최초 로그인 기준 절대 만료를 연장하지 않는다.
- login/signup/google/logout/logout-all을 포함한 상태 변경 요청은 CSRF header가 필요하다.
- BFF는 same-origin만 허용하고 credentialed wildcard CORS를 허용하지 않는다.
- 인증 성공 시 session ID를 교체한다.
- 보호 API는 session이 없거나 최종 refresh가 실패하면 `401 SESSION_INVALID`를 반환한다.
- 목표 계약에는 브라우저용 `/auth/refresh`가 없고 `Authorization: Bearer`도 받지 않는다.

## Common Shapes

### User

```json
{
  "id": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
  "email": "miju@meetingmind.ai",
  "displayName": "이미주",
  "pictureUrl": null,
  "status": "ACTIVE"
}
```

### Session View

```json
{
  "expiresAt": "2026-07-16T12:00:00Z",
  "idleExpiresAt": "2026-07-16T01:00:00Z",
  "rememberMe": false
}
```

`expiresAt`은 절대 만료, `idleExpiresAt`은 현재 유휴 만료다. token expiry나 token 값은 노출하지 않는다.

### Error

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호를 확인해 주세요.",
  "fieldErrors": [],
  "traceId": "01J2..."
}
```

## GET /api/v1/auth/csrf

Spring Security CSRF token을 브라우저가 상태 변경 요청에 전달할 수 있게 반환한다.

### Response `200`

```json
{
  "token": "opaque-csrf-token",
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf"
}
```

- `Cache-Control: no-store`
- access/refresh token이나 인증 여부를 포함하지 않는다.

## POST /api/v1/auth/signup

### Request

```json
{
  "email": "miju@meetingmind.ai",
  "password": "password-123!",
  "displayName": "이미주",
  "rememberMe": false
}
```

### Response `201`

```json
{
  "user": {
    "id": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
    "email": "miju@meetingmind.ai",
    "displayName": "이미주",
    "pictureUrl": null,
    "status": "ACTIVE"
  },
  "session": {
    "expiresAt": "2026-07-16T12:00:00Z",
    "idleExpiresAt": "2026-07-16T01:00:00Z",
    "rememberMe": false
  }
}
```

- `Set-Cookie`로 BFF session을 설정한다.
- email/password/displayName 검증은 기존 정책을 유지한다.
- `400 INVALID_REQUEST`, `409 EMAIL_ALREADY_REGISTERED`.

## POST /api/v1/auth/login

### Request

```json
{
  "email": "miju@meetingmind.ai",
  "password": "password-123!",
  "rememberMe": false
}
```

### Response `200`

`POST /signup`과 같은 `user`, `session` shape와 session cookie를 반환한다.

- 실패는 계정 존재 여부를 구분하지 않는 `401 INVALID_CREDENTIALS`를 사용한다.
- rate limit/lockout은 보안 정책 확정 후 공통 error에 추가한다.

## POST /api/v1/auth/google

### Request

```json
{
  "credential": "google-id-credential",
  "rememberMe": false
}
```

### Response `200`

`POST /login`과 같은 `user`, `session` shape와 session cookie를 반환한다.

- Auth 경계가 Google signature, issuer, audience, expiry를 검증한다.
- credential은 검증 후 저장하지 않는다.
- `401 GOOGLE_CREDENTIAL_INVALID`, `503 AUTH_PROVIDER_UNAVAILABLE`.

## GET /api/v1/auth/session

앱 bootstrap에서 사용한다. 이 endpoint만 인증되지 않은 정상 상태도 `200`으로 반환해 UI가 저장 객체를 추측하지 않게 한다.

### Authenticated Response `200`

```json
{
  "authenticated": true,
  "user": {
    "id": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
    "email": "miju@meetingmind.ai",
    "displayName": "이미주",
    "pictureUrl": null,
    "status": "ACTIVE"
  },
  "session": {
    "expiresAt": "2026-07-16T12:00:00Z",
    "idleExpiresAt": "2026-07-16T01:00:00Z",
    "rememberMe": false
  }
}
```

### Unauthenticated Response `200`

```json
{
  "authenticated": false,
  "user": null,
  "session": null
}
```

- expired/invalid cookie는 만료 `Set-Cookie`로 정리한다.
- `Cache-Control: no-store, private`.

## POST /api/v1/auth/logout

현재 BffSession, TokenBundle과 연결 AuthSession을 폐기하고 cookie를 만료한다.

### Response `204`

- session이 이미 없거나 Auth revoke가 이미 끝난 경우에도 멱등하게 `204`를 반환한다.
- Auth Service 일시 장애가 있어도 로컬 BFF session/cookie는 삭제하고 revoke 재처리/감사 이벤트를 남긴다.
- Phase 1 현재 Backend 호환 경로는 revoke 실패를 token 없는 보안 이벤트로 기록하고 로컬 session/cookie를 fail closed한다. 암호화된 durable revoke 재처리는 Auth Service 추출·운영 관측 task의 출시 gate로 유지한다.

## POST /api/v1/auth/logout-all

현재 사용자의 모든 AuthSession과 BffSession/TokenBundle을 폐기한다.

### Response `204`

- 최근 인증 또는 동등한 재인증이 필요하다.
- 조건을 충족하지 않으면 `403 REAUTHENTICATION_REQUIRED`를 반환한다.
- `authenticatedAt`이 최근 10분 이내면 허용한다. 초과하면 local 사용자는 비밀번호, Google 사용자는 새 Google ID credential을 재검증한다.
- Auth Service의 사용자 전체 revoke와 transactional outbox 기록이 durable하게 커밋된 뒤 `204`를 반환하고 현재 cookie를 삭제한다.

## Protected API Final 401

BFF가 access 만료를 감지하면 서버 측 refresh와 원 요청 최대 1회 재시도를 수행한다. 이 동작은 브라우저에 보이지 않는다. refresh 실패 또는 재시도 후 다시 `401`이면:

```json
{
  "code": "SESSION_INVALID",
  "message": "로그인이 만료되었습니다. 다시 로그인해 주세요.",
  "fieldErrors": [],
  "traceId": "01J2..."
}
```

BFF는 이 응답 전에 session/Token Bundle/cookie를 정리하고 Frontend는 전역 unauthenticated 상태로 전환한다.
