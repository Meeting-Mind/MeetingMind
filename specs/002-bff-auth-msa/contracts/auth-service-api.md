# BFF-Auth Internal API Contract

## Document Status

| Field | Value |
| --- | --- |
| Status | Implemented through T035 BFF/Core cutover; production mTLS/KMS/ECS Fargate wiring remains T040+ |
| Owner | Auth Service |
| Base path | `/internal/v1/auth` |
| Consumers | Web BFF only for login/refresh/revoke; Resource Services for JWKS only |
| Related data model | User, AuthIdentity, AuthSession, AuthRefreshCredential, AuthOutboxEvent, SessionAudit |

## Boundary Rules

- 이 API는 public ingress에 노출하지 않는다.
- mTLS SPIFFE workload identity, 서비스별 Security Group과 목적지/principal allowlist를 모두 적용한다.
- access/refresh는 이 내부 응답에만 존재하며 body/header/log/tracing을 redaction한다.
- Browser cookie/CSRF를 이 API의 인증 수단으로 사용하지 않는다.
- Error는 provider raw body, credential 존재 여부, token 원문을 노출하지 않는다.
- refresh는 BFF session 단위 single-flight를 정상 동시성 경계로 사용하며 Auth Service 자체 grace나 성공 응답 replay를 제공하지 않는다.

## Internal Token Response

```json
{
  "accessTokens": [
    {
      "audience": "meetingmind-core",
      "token": "eyJ...",
      "expiresIn": 600
    },
    {
      "audience": "meetingmind-ai",
      "token": "eyJ...",
      "expiresIn": 600
    },
    {
      "audience": "meetingmind-livekit",
      "token": "eyJ...",
      "expiresIn": 600
    }
  ],
  "refreshToken": "mmr_...",
  "tokenType": "Bearer",
  "refreshExpiresIn": 1209600,
  "authSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "user": {
    "id": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
    "email": "miju@meetingmind.ai",
    "displayName": "이미주",
    "pictureUrl": null,
    "status": "ACTIVE"
  }
}
```

- 각 access JWT의 `aud`는 정확히 하나이며 다른 Resource Service에서 사용할 수 없다.
- 내부 응답 `user.id`는 Auth UUID이며 JWT `sub`와 같다. BFF는 Browser/Core 외부 응답에 이를 직접 노출하지 않고 deterministic `user-{Auth UUID}` resource ID를 만든다.
- access 만료는 600초, 허용 clock skew는 검증 시 60초다.
- refresh 절대 상한은 14일이다.
- `accessTokens`는 고정 allowlist audience만 포함하며 요청 body가 임의 audience를 선택하지 않는다.

## POST /internal/v1/auth/signup

### Request

```json
{
  "email": "miju@meetingmind.ai",
  "password": "password-123!",
  "displayName": "이미주",
  "clientContext": {
    "deviceLabel": "Chrome on macOS"
  }
}
```

### Response

- `201` Internal Token Response.
- `400 INVALID_REQUEST`, `409 EMAIL_ALREADY_REGISTERED`.

## POST /internal/v1/auth/login

### Request

```json
{
  "email": "miju@meetingmind.ai",
  "password": "password-123!",
  "clientContext": {
    "deviceLabel": "Chrome on macOS"
  }
}
```

### Response

- `200` Internal Token Response.
- `401 INVALID_CREDENTIALS`는 계정 존재 여부를 구분하지 않는다.

## POST /internal/v1/auth/google

### Request

```json
{
  "credential": "google-id-credential",
  "clientContext": {
    "deviceLabel": "Chrome on macOS"
  }
}
```

### Response

- `200` Internal Token Response.
- Auth Service가 credential signature, issuer, audience, expiry를 검증하고 원문은 저장하지 않는다.
- `401 GOOGLE_CREDENTIAL_INVALID`, `503 AUTH_PROVIDER_UNAVAILABLE`.

## POST /internal/v1/auth/refresh

### Request

```json
{
  "authSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "refreshToken": "mmr_..."
}
```

### Response

- `200` 새 audience별 access 집합과 refresh를 포함한 Internal Token Response.
- `401 REFRESH_TOKEN_INVALID`, `401 AUTH_SESSION_REVOKED`, `409 REFRESH_REUSE_DETECTED`.
- 성공 시 이전 refresh는 재사용할 수 없다.
- Auth Service는 credential row를 잠그고 현재 active/hash/expiry/session 상태 검증, 이전 `usedAt` 기록, replacement insert와 `replacementId`, AuthSession `lastRotatedAt` 갱신을 한 트랜잭션으로 커밋한다.
- 이미 `usedAt`이 존재하는 credential이 다시 제시되면 해당 AuthSession과 같은 `familyId`의 현재·후속 credential 전체를 `REFRESH_REUSE`로 revoke하고 revocation outbox를 함께 기록한다. 다른 AuthSession은 유지한다.
- 동시 refresh grace와 이전 성공 응답 replay는 없다. 응답 유실 뒤 이전 refresh를 다시 제시하면 보안 우선으로 재사용 탐지·세션 폐기한다.
- BFF는 `409 REFRESH_REUSE_DETECTED`를 Browser `401 SESSION_INVALID`로 정규화하고 session/Token Bundle/cookie를 정리한다.

## POST /internal/v1/auth/revoke

현재 논리 AuthSession을 폐기한다.

### Request

```json
{
  "authSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "reason": "CURRENT_LOGOUT"
}
```

### Response

- `204`, 이미 폐기됐거나 만료된 경우에도 멱등하다.
- BFF는 session에 연결된 `authSessionId`만 보내며 브라우저 값을 전달하지 않는다.
- AuthSession revoke와 `AuthSessionRevokedV1` outbox insert가 같은 DB 트랜잭션에 durable하게 커밋된 뒤 응답한다.

## POST /internal/v1/auth/reauthenticate

현재 AuthSession에 민감 동작용 최근 인증을 부여하되 새 AuthSession, refresh family 또는 access JWT를 만들지 않는다.

### Local Request

```json
{
  "currentAuthSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "userId": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
  "method": "PASSWORD",
  "password": "password-123!",
  "credential": null
}
```

### Google Request

```json
{
  "currentAuthSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "userId": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
  "method": "GOOGLE",
  "password": null,
  "credential": "new-google-id-credential"
}
```

정확히 선택한 method의 credential 하나만 허용한다. BFF는 ID를 서버 session에서, credential만 Browser의 현재 요청에서 가져온다.

### Response `200`

```json
{
  "authenticatedAt": "2026-07-18T04:10:00Z"
}
```

- Auth Service는 현재 AuthSession을 잠그고 `userId` 소유권, active/expiry와 User `ACTIVE`를 먼저 확인한다.
- `PASSWORD`는 해당 User에 이미 연결된 `LOCAL` identity의 BCrypt hash만 비교한다.
- `GOOGLE`은 signature/issuer/audience/expiry를 새로 검증하고 해당 Google `sub` identity가 같은 User에 이미 연결됐는지 확인한다. 계정이나 identity를 생성·연결하지 않는다.
- 성공 시 `REAUTHENTICATION_SUCCESS`, 실패 시 credential/provider 존재를 노출하지 않는 `REAUTHENTICATION_FAILURE` 감사를 남기며 credential 원문은 저장·로그하지 않는다.
- credential 또는 identity 불일치는 `401 REAUTHENTICATION_FAILED`, session/user 결합 불일치는 `403 AUTH_SESSION_SUBJECT_MISMATCH`, 만료·폐기 session은 `401 AUTH_SESSION_REVOKED`다.

## POST /internal/v1/auth/revoke-all

사용자의 모든 논리 AuthSession을 폐기한다.

### Request

```json
{
  "currentAuthSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "userId": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
  "reason": "ALL_DEVICE_LOGOUT",
  "authenticatedAt": "2026-07-16T00:55:00Z"
}
```

### Response

- `204`, 이미 모두 폐기된 경우에도 멱등하다.
- BFF가 최근 10분 인증 또는 전용 local/Google 재인증을 먼저 적용하고 Auth Service도 workload principal과 subject/user binding을 검증한다.
- `currentAuthSessionId`와 `userId`는 BFF 서버 세션에서만 가져오며 브라우저 값을 전달하지 않는다. Auth Service는 해당 AuthSession row의 `userId` 결합과 `authenticatedAt`이 현재 기준 10분 이내이고 60초 이상 미래가 아닌지 다시 검증한다.
- 결합 불일치는 `403 AUTH_SESSION_SUBJECT_MISMATCH`, 최근 인증 미충족은 `401 RECENT_AUTH_REQUIRED`로 거부하며 어떤 세션도 변경하지 않는다.
- 사용자 소유의 각 active AuthSession을 revoke하고 session별 `AuthSessionRevokedV1` outbox를 같은 트랜잭션에 기록한 뒤 응답한다.

## T032/T033 Delivery Boundary

- T032는 자격 검증, refresh HMAC hash/lineage, AuthSession, revoke/revoke-all, 감사와 transactional outbox producer를 구현한다.
- audience별 access 생성은 `AccessTokenIssuer` port를 호출한다. T033의 KMS `RS256` adapter가 없는 실행 환경은 임시 HMAC/private key나 성공 응답을 만들지 않고 `503 TOKEN_ISSUER_UNAVAILABLE`로 전체 발급 transaction을 rollback한다.
- T032 자동 테스트의 signer는 test source에서만 제공하며 runtime image에는 포함하지 않는다.
- outbox transport 제품, publish 완료 갱신과 재시도/경보는 T045 출시 gate다. T032 API 성공은 DB revoke와 unpublished outbox row의 durable commit까지를 의미한다.

## GET /.well-known/jwks.json

Resource Service가 access JWT 서명을 로컬 검증할 공개키 집합을 반환한다.

- public internet endpoint가 아니라 mTLS 내부 서비스 discovery 경로로만 노출한다.
- `kid`를 기준으로 active와 rotation overlap key를 제공한다.
- `Cache-Control: public, max-age=300`과 `ETag`를 제공한다. validator는 모르는 `kid`에서 JWKS를 한 번 즉시 재조회한 뒤에도 없으면 fail closed한다.
- 정기 signing key rotation은 90일이다. 새 공개키를 먼저 게시하고 최소 JWKS cache 5분 뒤 신규 `kid`로 서명하며 이전 공개키는 1시간 overlap 후 제거한다.
- emergency compromise에서는 해당 key 신규 서명을 즉시 중단하고 JWKS에서 제거해 cache 갱신을 강제한다. cache된 공개키로 인한 최대 5분 잔여 위험을 보안 사건으로 추적한다.
- private signing key는 JWKS, application config, container image에 포함하지 않는다.
- signing key는 AWS KMS 비대칭 `RSA_2048` key이며 algorithm은 `RS256`만 허용한다.
- Auth runtime key ring에는 private key나 public key 원문을 넣지 않고 `kid`, KMS key ID, `publishedAt`, 선택적 `publishUntil`만 둔다. `activeKid`와 `activeSince`가 실제 서명 key를 선택한다.
- 정기 교체 설정은 active key가 `activeSince` 최소 5분 전에 게시됐고 직전 key의 `publishUntil`이 `activeSince + 1시간` 이후인지 시작 시 검증한다. 침해 key의 즉시 제거는 명시적 `EMERGENCY` rotation mode에서만 overlap 예외를 허용한다.
- KMS 호출은 `MessageType=RAW`, `RSASSA_PKCS1_V1_5_SHA_256`만 사용하고 `GetPublicKey` 결과가 `RSA_2048`, `SIGN_VERIFY`, 해당 signing algorithm인지 확인한다. KMS나 key metadata 검증 실패 시 평문/private-key fallback 없이 신규 발급을 `503 TOKEN_ISSUER_UNAVAILABLE`로 중단한다.

## Access JWT Validation Baseline

JWT header는 `typ=at+jwt`, `alg=RS256`, non-empty `kid`를 필수로 한다. Payload 필수 claim은 다음과 같다.

- `iss`: 환경별로 고정된 MeetingMind Auth issuer URI. 운영 기본 논리값은 `https://auth.meetingmind.internal`이다.
- `aud`: `meetingmind-core`, `meetingmind-ai`, `meetingmind-livekit` 중 정확히 하나.
- `sub`: Auth User UUID.
- `sid`: AuthSession UUID.
- `jti`: access JWT별 유일 UUID.
- `iat`, `nbf`, `exp`: `exp - iat = 600초`, 최대 clock skew 60초.
- `ver`: JWT profile version `1`.

Resource Service는 signature, 고정 `RS256`, issuer, 자신의 단일 audience, 모든 필수 claim과 시간 조건을 로컬 검증한다. SpaceRole/MeetingRole은 token에 권한 원본으로 넣지 않고 서비스 소유 DB의 최신 RBAC/ACL을 확인한다.

T033 Resource validator는 JWKS를 최대 5분 메모리 cache하고 ETag 재검증을 사용한다. cache에 없는 `kid`는 JWKS를 즉시 한 번 강제 갱신한 뒤에도 없으면 거부한다. 만료된 cache를 갱신할 수 없으면 stale key나 Auth 동기 introspection으로 우회하지 않고 fail closed한다. Core의 기존 issuer와 함께 실제 요청 경로에 연결하는 dual-validation cutover는 T035에서 수행한다.

로그아웃·reuse·사용자 비활성화 event를 받은 Resource Service는 `sid`를 해당 access의 최대 만료 시점까지 로컬 denylist에 저장한다. 요청의 `sid`가 denylist에 있으면 서명과 시간이 유효해도 거부한다. 중앙 Auth/Redis를 매 요청 조회하지 않으며 event 지연 또는 consumer 장애 시 잔여 위험은 10분 access TTL과 최대 60초 검증 skew로 제한한다.

## Workload Authentication and Authorization

- BFF/Auth/Resource 내부 호출은 양방향 TLS와 SPIFFE URI SAN workload identity를 사용한다.
- identity 형식은 `spiffe://meetingmind.internal/ns/{namespace}/sa/{serviceAccount}`다.
- `/internal/v1/auth/signup|login|google|refresh|revoke|reauthenticate|revoke-all`은 Web BFF principal만 호출할 수 있다.
- JWKS는 등록된 Resource Service와 Web BFF principal만 호출할 수 있다.
- public ingress는 `/internal/**`와 JWKS internal route를 라우팅하지 않는다.
- Security Group으로 caller/callee ECS Service 경계를 제한하고 애플리케이션은 신뢰 프록시가 검증한 principal만 allowlist와 대조한다.
- 인증서 발급·자동 회전 제품은 Q-012/T040에서 선택하되 shared client secret, Browser cookie나 사용자 access JWT를 workload 인증 대용으로 사용하지 않는다.

## Compatibility Adapter

Phase 1에는 Web BFF가 현재 Backend `/api/v1/auth/signup|login|google|refresh|logout`을 서버 측에서 호출할 수 있다. 이 응답은 BFF 밖으로 전달하지 않고 즉시 Token Bundle로 암호화한다. Auth Service 추출과 dual validation 종료 전에는 기존 endpoint/schema를 삭제하지 않는다.

현재 Backend token 응답에는 목표 논리 `authSessionId`가 없으므로 Phase 1 BFF가 내부 호환 ID를 생성한다. 이 값은 Browser나 현재 Backend로 전달하지 않고 BffSession/TokenBundle 연결에만 사용하며, Auth Service 전환 시 Internal Token Response의 서버 발급 `authSessionId`로 교체한다.

Auth Service 전환 모드는 명시적 설정으로만 선택한다. target Auth 호출 실패를 legacy Backend로 자동 재시도하지 않으며 rollback은 BFF provider 설정과 Core validation mode를 함께 바꾸는 운영 절차로 수행한다.
