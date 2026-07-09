# Auth API Contract

Auth API는 target backend 계약이며 `/api/v1/auth/*`에서 시작한다. 현재 코드에 prototype 구현이 있다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Current Prototype, Target Backend |
| Owner | Auth/Login |
| Related requirements | FR-AUTH-01, FR-AUTH-02, FR-AUTH-04, FR-AUTH-05, FR-AUTH-06, FR-AUTH-07, FR-AUTH-08, FR-AUTH-09, FR-AUTH-10, FR-AUTH-13, FR-AUTH-16, NFR-SEC-01, NFR-SEC-02, NFR-SEC-03, NFR-SEC-06, POL-TOKEN-01 |
| Related data model | User, AuthIdentity, AuthSession |

## Token Rules

- Backend는 access token과 refresh token을 발급한다.
- Frontend는 token pair를 `sessionStorage`에 저장한다.
- 인증 API 요청은 `Authorization: Bearer {accessToken}`를 사용한다.
- Refresh token 원문은 서버에 저장하지 않고 `AuthSession.refreshTokenHash`만 저장한다.
- 기본 만료 후보는 access token 1시간, refresh token 14일이다.

## POST /api/v1/auth/signup

자체 이메일/비밀번호 계정을 만들고 token pair를 발급한다.

### Status

- Current Prototype
- Target Backend

### Auth and Permissions

- 공개 endpoint
- 가입 완료 후 내부 `User.id`를 access token subject로 사용한다.

### Data Scope

- User scope
- Space/Meeting 권한은 생성하지 않는다.

### Request

```json
{
  "email": "miju@meetingmind.ai",
  "password": "password-123",
  "displayName": "이미주"
}
```

### Validation

- `email`: required, email format, unique
- `password`: required, 정책 길이/복잡도 후보 적용
- `displayName`: required, blank 금지

### Response

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "mmr_...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshExpiresIn": 1209600,
  "user": {
    "id": "user-001",
    "email": "miju@meetingmind.ai",
    "displayName": "이미주",
    "pictureUrl": null,
    "status": "active"
  }
}
```

### Errors

- `400 INVALID_REQUEST`: email/password/displayName 형식 오류
- `409 EMAIL_ALREADY_REGISTERED`: 이미 가입된 이메일

### Audit

- No audit event for normal signup.
- 보안 이벤트 로그 후보: 중복/검증 실패 반복.

### Requirement Trace

- FR-AUTH-01: 자체 계정 가입
- FR-AUTH-02: 이메일 형식/중복 검증
- FR-AUTH-03: 비밀번호 정책 후보
- FR-AUTH-07: token pair 발급
- NFR-SEC-06: 서버측 입력 검증
- POL-TOKEN-01: access/refresh token 기준

### Notes

- 현재 prototype은 in-memory store를 사용한다. DB 영속화는 Data/Backend 후속 작업이다.

## POST /api/v1/auth/login

이메일/비밀번호로 로그인한다.

### Status

- Current Prototype
- Target Backend

### Auth and Permissions

- 공개 endpoint
- 성공 시 내부 `User.id` subject로 token pair를 발급한다.

### Data Scope

- User scope
- Space/Meeting 권한은 로그인 이후 각 API에서 별도 계산한다.

### Request

```json
{
  "email": "miju@meetingmind.ai",
  "password": "password-123"
}
```

### Validation

- `email`: required, email format
- `password`: required

### Response

`POST /api/v1/auth/signup`과 같은 token response.

### Errors

- `400 INVALID_REQUEST`: email/password 형식 오류
- `401 INVALID_CREDENTIALS`: 계정 없음 또는 비밀번호 불일치

### Audit

- No audit event for normal login.
- 보안 이벤트 로그 후보: 로그인 실패 반복.

### Requirement Trace

- FR-AUTH-06: 자체 계정 로그인
- FR-AUTH-07: token pair 발급
- NFR-SEC-01: 인증 정보 보호
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 실패 응답은 계정 존재 여부를 노출하지 않는다.

## POST /api/v1/auth/google

Google ID token을 backend가 검증한 뒤 내부 사용자와 연결하고 token pair를 발급한다.

### Status

- Current Prototype
- Target Backend

### Auth and Permissions

- 공개 endpoint
- Backend가 Google ID token의 signature, issuer, audience, expiry를 검증한다.

### Data Scope

- User/AuthIdentity scope
- Google `sub`는 `AuthIdentity.providerUserId`로 저장한다.

### Request

```json
{
  "credential": "google-id-token"
}
```

### Validation

- `credential`: required
- Google ID token 검증 실패 시 내부 사유를 응답에 노출하지 않는다.

### Response

`POST /api/v1/auth/signup`과 같은 token response.

### Errors

- `400 INVALID_REQUEST`: credential 누락
- `401 INVALID_CREDENTIALS`: Google token signature, issuer, audience, expiry 검증 실패

### Audit

- No audit event for normal Google login.
- 보안 이벤트 로그 후보: Google credential 검증 실패 반복.

### Requirement Trace

- FR-AUTH-04: Google OAuth 진입
- FR-AUTH-05: 소셜 계정 연결/생성
- FR-AUTH-07: token pair 발급
- NFR-SEC-03: 소셜 ID token 서버 검증
- PERF-EXT-04: Google OAuth ID token 검증

### Notes

- 동일 이메일의 자체 계정과 Google identity 연결 정책은 Auth owner가 확정한다.

## POST /api/v1/auth/refresh

Refresh token rotation으로 새 token pair를 발급한다.

### Status

- Current Prototype
- Target Backend

### Auth and Permissions

- 공개 endpoint
- 유효한 refresh token 필요

### Data Scope

- AuthSession scope
- refresh token 원문은 저장하지 않는다.

### Request

```json
{
  "refreshToken": "mmr_..."
}
```

### Validation

- `refreshToken`: required
- hash 일치, 만료, revoke 상태를 검증한다.

### Response

`POST /api/v1/auth/signup`과 같은 token response.

### Errors

- `400 INVALID_REQUEST`: refresh token 누락
- `401 REFRESH_TOKEN_INVALID`: refresh token 없음, 만료, 위조, 폐기

### Audit

- No audit event for normal refresh.
- 보안 이벤트 로그 후보: refresh token 재사용/폐기 token 사용.

### Requirement Trace

- FR-AUTH-08: refresh token 재발급
- FR-AUTH-16: 인증 만료 처리
- POL-TOKEN-01: refresh token rotation
- NFR-SEC-02: refresh token 원문 저장 금지

### Notes

- rotation 이후 이전 refresh session은 재사용할 수 없다.

## GET /api/v1/auth/me

현재 access token의 사용자 프로필을 조회한다.

### Status

- Current Prototype
- Target Backend

### Auth and Permissions

- 인증 필요
- 본인 사용자만 조회

### Data Scope

- User scope

### Request

None.

### Validation

- `Authorization` header의 bearer token을 검증한다.

### Response

```json
{
  "id": "user-001",
  "email": "miju@meetingmind.ai",
  "displayName": "이미주",
  "pictureUrl": null,
  "status": "active"
}
```

### Errors

- `401 UNAUTHORIZED`: token 없음, 만료, 위조

### Audit

- No audit event.

### Requirement Trace

- FR-AUTH-10: 세션 유지/자동로그인
- FR-AUTH-13: 프로필 조회
- POL-TOKEN-01: access token 검증

### Notes

- 권한 목록은 이 endpoint에서 직접 반환하지 않는다. Space/Meeting별 API에서 계산한다.

## POST /api/v1/auth/logout

현재 refresh session을 폐기한다.

### Status

- Current Prototype
- Target Backend

### Auth and Permissions

- 인증 필요 후보
- refresh token이 있으면 해당 session을 revoke한다.

### Data Scope

- AuthSession scope

### Request

```json
{
  "refreshToken": "mmr_..."
}
```

### Validation

- `refreshToken`: required
- 이미 폐기된 token도 idempotent 성공으로 처리할 수 있다.

### Response

```json
{
  "loggedOut": true
}
```

### Errors

- `400 INVALID_REQUEST`: refresh token 누락
- `401 UNAUTHORIZED`: 인증 요구 정책을 적용한 경우 access token 없음/만료

### Audit

- No audit event for normal logout.

### Requirement Trace

- FR-AUTH-09: 로그아웃
- POL-TOKEN-01: refresh token 폐기
- NFR-SEC-02: refresh token 원문 저장 금지

### Notes

- 모든 기기 로그아웃은 별도 endpoint 후보로 둔다.
