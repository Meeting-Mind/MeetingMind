# Data Model: BFF Auth and Gradual MSA

이 문서는 목표 인증 구조의 논리 데이터 모델이다. 현재 `backend`의 `auth_sessions`와 Frontend `sessionStorage`는 점진 전환 중 호환 대상으로만 유지하며, 목표 모델의 저장 역할은 Redis, BFF Token Vault, Auth DB로 분리한다.

## Storage Boundaries

| Store | Owner | Stores | Must Not Store |
| --- | --- | --- | --- |
| BFF Session Redis | Web BFF | opaque session, 사용자/인증 세션/Token Bundle 참조, expiry | access/refresh 원문, 업무 권한 원본 |
| BFF Token Vault | Web BFF | KMS 경계로 암호화된 access/refresh bundle | 평문 token index/log, 비밀번호 |
| Auth PostgreSQL | Auth Service | User, AuthIdentity, AuthSession, refresh hash/revoke, 감사 이벤트 | refresh 원문, Space/Meeting RBAC |
| Resource PostgreSQL | 각 Resource Service | 서비스 소유 업무 데이터와 최신 RBAC/ACL | BFF 세션, refresh token |

운영에서는 store별 계정과 최소 권한을 분리한다. 로컬 개발은 같은 PostgreSQL/Redis 인스턴스의 논리 분리를 허용하지만 다른 서비스의 table/key를 직접 읽지 않는다.

T031부터 Auth PostgreSQL의 물리 table은 `auth_users`, `auth_identities`, `auth_sessions`, `auth_refresh_credentials`, `session_audits`, `auth_outbox_events`를 사용한다. Flyway migration role이 table과 `flyway_schema_history`를 소유한다. Auth runtime role은 업무 table의 `SELECT`, `INSERT`, `UPDATE`, 감사 table의 `SELECT`, `INSERT`만 가지며 `DELETE`, schema `CREATE`, table DDL과 Flyway history 접근 권한을 갖지 않는다.

T032 runtime은 local `passwordHash`에 BCrypt만 사용하고 refresh `tokenHash`는 환경별 최소 32자 secret의 `hmac_sha256$...` lookup hash로 저장한다. raw password/Google credential/refresh는 어떤 Auth table에도 저장하지 않는다. access token은 T033 KMS `AccessTokenIssuer` adapter의 반환값만 메모리에서 응답하며 signer 부재나 계약 불일치 시 User/Identity/Session/credential transaction을 rollback한다. T033 rotation key ring은 DB entity가 아니라 배포 설정이며 private/public key 원문 없이 `kid`, KMS key ID와 공개 기간만 포함한다. JWKS 공개키는 KMS `GetPublicKey` 결과를 메모리 cache해 만들며 Auth DB에 저장하지 않는다.

## Entity Overview

### BffSession

- `id`: 브라우저 cookie가 참조하는 충분한 entropy의 opaque session ID. 원문은 cookie에만 있고 Redis key는 namespace를 분리한다.
- `userId`: 표시 정보와 downstream subject 연결을 위한 Auth User ID.
- `authSessionId`: Auth Service의 논리 로그인 세션 ID.
- `tokenBundleId`: Token Vault 암호문 참조. token 원문을 session attribute에 넣지 않는다.
- `createdAt`
- `lastAccessedAt`
- `idleExpiresAt`: 일반 세션은 마지막 유효 요청에서 60분, Remember me는 마지막 유효 요청에서 7일. 요청 시 갱신하되 절대 만료를 넘지 않는다.
- `absoluteExpiresAt`: 일반 세션은 생성 후 12시간, Remember me는 생성 후 14일이며 갱신하지 않는다.
- `rememberMe`: persistent cookie 사용 여부.
- `authenticatedAt`: 모든 기기 로그아웃 등 민감 동작의 최근 인증 판단 기준.
- `status`: `ACTIVE`, `LOGOUT_PENDING`, `REVOKED`, `EXPIRED`의 논리 상태. Redis 삭제가 최종 비활성 상태다.

`csrfToken` 원문은 문서화된 Spring Security repository가 관리하며 애플리케이션 로그나 범용 session dump에 출력하지 않는다.

### TokenBundle

- `id`
- `authSessionId`
- `encryptedPayload`: audience별 access JWT 집합, refresh token과 필요한 token metadata를 인증 암호화한 ciphertext.
- `encryptedDataKey`: envelope encryption 사용 시 KMS로 암호화한 data key.
- `keyId`: KMS key/alias 식별자.
- `accessExpiresAtByAudience`: `meetingmind-core`, `meetingmind-ai`, `meetingmind-livekit`별 만료시각 map.
- `refreshExpiresAt`
- `issuer`
- `audiences`
- `scopesByAudience`: audience별 최소 scope 집합. Space/Meeting 동적 권한은 넣지 않는다.
- `version`: optimistic concurrency와 refresh single-flight 결과 교체에 사용.
- `schemaVersion`: target audience별 bundle은 `2`, Phase 1 단일 legacy access bundle은 `1`.
- `createdAt`
- `updatedAt`

복호화된 payload는 요청 처리 메모리에서만 짧게 사용하고 로그, metric label, exception, tracing attribute에 남기지 않는다. refresh 성공 시 bundle 전체를 원자 교체하고 이전 암호문은 재사용하지 않는다.

물리 저장 형식은 AES-256-GCM ciphertext와 KMS encrypted data key만 포함한다. `bundleId`, `authSessionId`, `version`을 encryption context/AAD에 묶어 다른 bundle이나 version으로 ciphertext를 이동하면 복호화를 거부한다. 운영은 AWS KMS `GenerateDataKey`/`Decrypt`와 workload IAM을 사용하고, local/test는 외부 주입한 256-bit AES master key adapter만 허용한다. local key가 없거나 잘못된 길이면 애플리케이션은 평문 또는 임시 키로 우회하지 않고 시작을 거부한다.

### User

- `id`
- `email`: canonical/unique 정책 적용.
- `displayName`
- `pictureUrl`
- `status`: `ACTIVE`, `DISABLED`.
- `createdAt`
- `updatedAt`
- `lastLoginAt`

Auth Service가 소유한다. Resource Service는 필요한 사용자 projection만 API/event로 동기화하며 Auth DB를 직접 조회하지 않는다.

### AuthIdentity

- `id`
- `userId`
- `provider`: `LOCAL`, `GOOGLE`.
- `providerUserId`: local canonical email 또는 Google `sub`.
- `passwordHash`: local만 사용하고 password 원문은 보관하지 않는다.
- `createdAt`
- `lastUsedAt`

Google ID credential 원문은 signature/issuer/audience/expiry/nonce 검증에만 사용하고 저장하지 않는다.

### AuthSession

- `id`: 한 기기의 논리 로그인 세션을 식별하는 안정된 ID.
- `userId`
- `createdAt`
- `lastRotatedAt`
- `refreshFamilyId`: 해당 로그인/기기의 단일 refresh family ID.
- `expiresAt`: refresh 가능한 절대 상한.
- `revokedAt`
- `revokeReason`: `CURRENT_LOGOUT`, `ALL_DEVICE_LOGOUT`, `USER_DISABLED`, `REFRESH_REUSE`, `ADMIN_REVOKE`, `EXPIRED` 등.
- `deviceLabel`: 사용자에게 표시 가능한 최소 정보. raw User-Agent 전체 저장은 최소화한다.
- `lastIpPrefix`: 필요성과 보존 기한이 결정된 경우에만 최소화해 저장한다.

Access JWT는 `sid` claim에 AuthSession ID를 포함한다. AuthSession revoke와 outbox event가 durable하게 기록되면 Resource Service는 해당 `sid`를 access 만료까지 로컬 denylist에 보관한다.

### AuthRefreshCredential

- `id`
- `authSessionId`
- `tokenHash`: 서버 secret을 사용한 keyed hash 또는 동등한 안전한 lookup hash.
- `issuedAt`
- `expiresAt`
- `usedAt`
- `revokedAt`
- `replacementId`: 성공한 rotation에서 생성된 다음 credential 참조. active leaf는 null이다.
- `familyId`: AuthSession `refreshFamilyId`와 같으며 해당 기기의 전체 폐기 범위다.

AuthSession당 active credential은 최대 하나다. refresh 성공은 이전 credential의 `usedAt`과 `replacementId`, 새 credential insert를 한 트랜잭션으로 처리한다. 이미 사용된 credential이 다시 제시되면 같은 `familyId`의 모든 credential과 AuthSession을 `REFRESH_REUSE`로 revoke하며 동시 grace는 없다.

### SessionAudit

- `id`
- `userId`
- `authSessionId`
- `bffSessionIdHash`: 필요한 경우 원문 대신 비가역 식별자.
- `eventType`: login success/failure, refresh, logout, revoke, reuse detection, session expiry.
- `reasonCode`
- `occurredAt`
- `traceId`
- `metadata`: token/credential/민감 개인정보가 없는 allowlist 구조.

T032 event type은 `SIGNUP_SUCCESS`, `LOGIN_SUCCESS|FAILURE`, `GOOGLE_LOGIN_SUCCESS|FAILURE`, `REFRESH_SUCCESS|FAILURE|REUSE`, `SESSION_REVOKED`를 사용한다. 실패 감사에는 이메일, 자격 존재 여부, credential 원문을 넣지 않고 고정 reason code와 traceId만 기록한다.

### AuthOutboxEvent

- `id`: revocation event의 전역 유일 ID.
- `aggregateType`: `AUTH_SESSION`.
- `aggregateId`: 폐기된 AuthSession ID.
- `eventType`: `AUTH_SESSION_REVOKED`.
- `eventVersion`: `1`.
- `payload`: `userId`, `authSessionId`, `reason`, `occurredAt`, `denyUntil`, `traceId`만 포함한 JSON.
- `createdAt`
- `publishedAt`
- `attemptCount`
- `lastErrorCode`: provider raw error가 아닌 allowlist code.

AuthSession revoke와 AuthOutboxEvent insert는 같은 Auth PostgreSQL 트랜잭션으로 커밋한다. publisher와 consumer는 at-least-once delivery와 `eventId` idempotency를 전제로 한다.

## Relationships

- User 1:N AuthIdentity.
- User 1:N AuthSession.
- AuthSession 1:N AuthRefreshCredential. 한 AuthSession은 하나의 family와 최대 하나의 active leaf를 가진다.
- AuthSession 1:N AuthOutboxEvent.
- AuthSession 1:N BffSession을 허용하되 정상 웹 흐름은 기기/브라우저 로그인당 1:1을 목표로 한다.
- BffSession 1:1 active TokenBundle.
- 현재 세션 로그아웃은 BffSession, TokenBundle, 연결 AuthSession을 폐기한다.
- 모든 기기 로그아웃은 User의 모든 AuthSession을 폐기하고 session별 revoke event로 해당 사용자의 모든 BffSession/TokenBundle과 Resource local denylist를 정리한다.

## Permission Rules

- BffSession은 인증 상태만 나타내며 SpaceRole/MeetingParticipant 권한의 원천이 아니다.
- Resource Service는 access JWT 서명과 필수 claim을 검증한 뒤 자신의 최신 RBAC/ACL을 확인한다.
- `userId`, `authSessionId`, `tokenBundleId`는 현재 session과 연결된 값인지 서버에서 검증하고 브라우저 입력으로 받지 않는다.
- Token Vault 읽기/쓰기 권한은 Web BFF Token Manager workload에만 부여한다.
- 모든 기기 로그아웃은 최근 10분 인증 또는 local 비밀번호/새 Google credential 재인증을 요구한다.
- AI/RAG 검색은 서비스 분리 후에도 검색 전 권한 선필터를 적용한다.

## Validation and Invariants

- BffSession의 `absoluteExpiresAt`은 생성 후 연장하지 않는다.
- `idleExpiresAt <= absoluteExpiresAt`이어야 한다.
- Remember me가 false면 운영 cookie는 session cookie이고 서버 절대 만료는 12시간을 넘지 않는다.
- Remember me가 true면 cookie와 서버 세션은 7일 sliding 유휴 만료와 14일 절대 만료를 함께 적용하고, 유휴 갱신은 절대 만료를 연장하지 않는다.
- TokenBundle `authSessionId`는 BffSession과 일치해야 하며 mismatch 시 fail closed하고 보안 이벤트를 남긴다.
- TokenBundle의 audience별 access JWT는 각 entry의 audience와 JWT `aud`가 같아야 하고 600초 수명을 넘지 않는다.
- refresh 성공은 새 credential 활성화, 이전 credential 사용 처리, TokenBundle 교체를 재시도 안전하게 수행해야 한다.
- 이미 사용된 refresh 재사용은 같은 transaction에서 AuthSession/family revoke와 outbox insert까지 완료해야 한다.
- revoke/expiry된 AuthSession에서는 새 access/refresh를 발급하지 않는다.
- raw access/refresh/password/Google credential은 로그, audit, analytics, tracing에 포함하지 않는다.

## Retention and Deletion

- BffSession: idle/absolute expiry 중 먼저 도달한 시점에 만료한다. logout 시 즉시 삭제한다.
- TokenBundle: BffSession/AuthSession보다 오래 보관하지 않고 logout, refresh 영구 실패, session expiry 시 삭제한다.
- AuthRefreshCredential: 원문은 저장하지 않는다. hash/reuse 감사 보존 기간은 보안·개인정보 정책으로 별도 결정한다.
- AuthSession/SessionAudit: 계정 삭제, 보안 감사, 법적 요구를 고려한 보존 기간을 운영 정책에서 확정한다.
- AuthOutboxEvent: publish 완료 뒤 운영 감사·재처리 정책 기간 동안 보존하고 backlog/실패 event는 삭제하지 않는다.
- Google credential: 검증 요청 범위 밖으로 보존하지 않는다.
- User 비활성화/삭제 시 모든 AuthSession을 revoke하고 BFF 정리 event를 발행한다.

## Migration Notes

1. 현재 Backend `/api/v1/auth/*`, `auth_sessions` schema는 Phase 1 BFF compatibility rollback window까지 유지하되 Browser token 저장 코드는 복원하지 않는다.
2. Web BFF가 현재 token response를 서버 측에서 소비해 TokenBundle로 암호화하고 브라우저에는 session cookie만 반환한다.
   - Phase 1의 현재 Backend는 안정된 논리 AuthSession ID를 반환하지 않으므로 BFF가 로그인마다 호환용 ID를 생성한다. 이 ID는 BffSession/TokenBundle 연결에만 사용하고 Backend refresh row 또는 목표 AuthSession ID로 해석하지 않는다.
3. Frontend cutover 후 token storage/Bearer 구성 코드는 제거하고, rollback은 동일 session/Vault schema를 사용하는 안정 BFF release로 제한한다.
4. T031의 Auth 전용 V1/V2 forward-only migration으로 AuthSession `refreshFamilyId`, credential lineage, AuthOutboxEvent 물리 schema와 최소 runtime 권한을 추가한다. audience별 TokenBundle schema v2는 BFF가 Auth runtime으로 전환되는 T035에서 별도 Vault document migration으로 적용한다.
5. 기존 refresh row를 새 AuthSession/credential lineage로 추측 변환하지 않는다. 재로그인 또는 명시적 session migration으로 전환한다.
6. Phase 1 TokenBundle schema v1은 legacy access를 `meetingmind-legacy`로만 해석한다. Auth Service 로그인/refresh 성공 시 schema v2 audience별 bundle로 원자 교체하며 v1 token을 신규 Resource Service audience로 복제하지 않는다.
7. Core/Auth dual validation 기간이 끝난 뒤에만 legacy issuer와 token endpoint를 제거한다.
