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
  "id": "user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
  "email": "miju@meetingmind.ai",
  "displayName": "이미주",
  "pictureUrl": null,
  "status": "ACTIVE"
}
```

`User.id`는 Browser/Core 업무 API의 안정된 resource ID인 `user-{Auth UUID}`다. Auth UUID 원문은 Browser 응답에 노출하지 않으며 BFF 내부 session index와 JWT `sub`에만 사용한다.

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
    "id": "user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
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
- Auth Service 발급 성공 뒤 Core User projection을 멱등 생성한 경우에만 BFF session과 cookie를 만든다. projection 실패는 `503 USER_PROJECTION_UNAVAILABLE`로 실패시키고 발급된 AuthSession을 best-effort revoke한다.
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
    "id": "user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
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

## POST /api/v1/auth/reauthenticate

모든 기기 로그아웃 같은 민감 동작을 위한 최근 인증을 갱신한다. 새 로그인 세션이나 token을 발급하지 않는다.

### Local Request

```json
{
  "method": "PASSWORD",
  "password": "password-123!"
}
```

### Google Request

```json
{
  "method": "GOOGLE",
  "credential": "new-google-id-credential"
}
```

### Response `204`

- BFF는 현재 서버 세션의 `authSessionId`와 Auth `userId`만 내부 Auth 요청에 사용한다.
- Auth Service가 현재 AuthSession/User 결합과 계정 상태를 확인한 뒤 local 비밀번호 또는 이미 연결된 Google identity를 검증한다.
- 성공하면 Auth Service가 반환한 서버 시각을 BFF session의 `authenticatedAt`으로 교체한다. Browser가 시각이나 사용자/세션 ID를 보내지 않는다.
- Google 재인증은 새 credential을 검증만 하고 User/AuthIdentity를 생성하거나 연결하지 않는다.
- 실패는 계정·provider 존재 여부를 구분하지 않는 `401 REAUTHENTICATION_FAILED`다.
- 만료·폐기되거나 사용자 결합이 다른 현재 AuthSession은 `401 SESSION_INVALID`로 정리한다.

## POST /api/v1/auth/logout-all

현재 사용자의 모든 AuthSession과 BffSession/TokenBundle을 폐기한다.

### Request

body를 사용하지 않는다. 사용자 ID, AuthSession ID, `authenticatedAt`과 재인증 credential을 이 endpoint 입력으로 받지 않는다.

### Response `204`

- 최근 인증 또는 동등한 재인증이 필요하다.
- 조건을 충족하지 않으면 `403 REAUTHENTICATION_REQUIRED`를 반환한다.
- BFF session의 `authenticatedAt`이 최근 10분 이내면 허용한다. 초과하면 Frontend가 `POST /reauthenticate`를 완료한 뒤 이 요청을 한 번 재시도한다.
- Auth Service의 사용자 전체 revoke와 transactional outbox 기록이 durable하게 커밋된 뒤 `204`를 반환하고 현재 cookie를 삭제한다.
- BFF는 Auth UUID Spring Session index로 다른 BffSession과 Token Bundle을 먼저 삭제하고 현재 요청의 session을 마지막에 무효화한다. 완료되지 않은 정리를 `204`로 응답하지 않는다.
- 명시적 legacy provider는 실제 AuthSession 전체 revoke를 제공할 수 없으므로 local-only 삭제를 성공으로 위장하지 않고 `409 AUTH_FEATURE_UNAVAILABLE`를 반환한다.

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
