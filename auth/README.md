# MeetingMind Auth Service

T031 foundation과 T032 인증·refresh·폐기 runtime 위에 T033 AWS KMS `RS256` access signing, 내부 JWKS와 rotation key ring을 구현했다.

현재 포함 범위:

- Java 21 / Spring Boot 3.5.14 독립 Gradle project
- liveness와 DB 포함 readiness probe
- Auth 전용 PostgreSQL/Flyway V1 schema와 V2 최소 권한 보정
- Flyway migrator와 DML-only runtime 계정 분리
- non-root Docker image와 CI build/migration/권한 검증
- local BCrypt 가입·로그인과 Google ID credential 검증
- HMAC-SHA-256 refresh lookup hash, 14일 family와 1회용 rotation/reuse 폐기
- 현재/모든 AuthSession revoke, 감사와 unpublished transactional outbox
- 현재 AuthSession/User 결합을 유지하는 local/Google 세션 비생성 재인증
- direct mTLS client certificate SPIFFE URI SAN과 Web BFF principal allowlist
- KMS RSA-2048 `RAW` RS256 서명과 audience별 10분 access JWT
- 5분 cache/ETag 내부 JWKS와 BFF/Resource workload principal allowlist
- 5분 선게시, 1시간 이전 key overlap을 검증하는 rotation key ring
- runtime image와 분리된 legacy User/AuthIdentity offline migration source set

`/internal/v1/auth/*`는 Browser/public API가 아니다. 운영에서는 direct client certificate의 SPIFFE URI SAN만 사용한다. local/test/integration profile은 명시적으로 활성화한 `X-MeetingMind-Test-Principal`만 개발용으로 허용한다.

`AUTH_SIGNING_PROVIDER=disabled`이면 signup/login/Google/refresh가 access token을 발급하려 할 때 `503 TOKEN_ISSUER_UNAVAILABLE`로 전체 DB transaction을 rollback한다. 임시 HMAC JWT나 container 내부 private key로 성공시키지 않는다. 운영은 `aws-kms`만 허용하며 AWS SDK default credential chain과 Auth ECS Task Role을 사용한다. Outbox transport publisher와 재시도/경보는 T049 출시 gate다.

## AWS KMS Signing

KMS key는 asymmetric `RSA_2048`, `SIGN_VERIFY`이고 `RSASSA_PKCS1_V1_5_SHA_256`을 허용해야 한다. application 설정에는 private/public key 원문을 넣지 않고 KMS key ID만 둔다.

```bash
export AUTH_SIGNING_PROVIDER=aws-kms
export AUTH_SIGNING_ISSUER=https://auth.meetingmind.internal
export AUTH_SIGNING_KEY_RING_JSON='{
  "activeKid": "auth-2026-q3",
  "activeSince": "2026-07-17T01:05:00Z",
  "rotationMode": "REGULAR",
  "keys": [
    {
      "kid": "auth-2026-q2",
      "kmsKeyId": "arn:aws:kms:REGION:ACCOUNT:key/PREVIOUS_KEY_ID",
      "publishedAt": "2026-04-01T00:00:00Z",
      "publishUntil": "2026-07-17T02:05:00Z"
    },
    {
      "kid": "auth-2026-q3",
      "kmsKeyId": "arn:aws:kms:REGION:ACCOUNT:key/ACTIVE_KEY_ID",
      "publishedAt": "2026-07-17T01:00:00Z"
    }
  ]
}'
```

정기 교체는 새 key를 key ring에 먼저 추가해 JWKS로 최소 5분 게시한 뒤 `activeKid/activeSince`를 전환하고 이전 key의 `publishUntil`을 전환 1시간 이후로 둔다. 이 순서를 어긴 `REGULAR` 설정은 시작을 거부한다. 침해 key 즉시 제거만 `rotationMode=EMERGENCY`를 사용하며 cache된 공개키의 최대 5분 잔여 위험을 보안 사건으로 추적한다.

JWKS는 `GET /.well-known/jwks.json`이고 public ingress에 노출하지 않는다. mTLS SPIFFE principal은 `AUTH_JWKS_SPIFFE_PRINCIPALS` allowlist에 있어야 하며 응답은 `Cache-Control: max-age=300, public`과 `ETag`를 제공한다.

## Local Run

```bash
docker compose -f compose.local.yml --profile auth up -d meetingmind-auth-db
cd auth
./gradlew bootRun
```

기본 포트는 `8082`, DB 포트는 host `5435`다. 로컬 기본값은 개발 전용이며 운영에서는 `AUTH_DB_*`를 secret으로 주입해야 한다.

내부 API를 로컬에서 호출할 때만 다음 header를 사용한다.

```text
X-MeetingMind-Test-Principal: spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff
```

운영 profile에서는 `AUTH_ALLOW_TEST_WORKLOAD_HEADER=true`를 설정해도 이 header를 인증 수단으로 사용하지 않는다.

Runtime role 이름은 migration 계약과 일치하는 `meetingmind_auth_app`으로 고정한다. 이 계정은 application table의 필요한 `SELECT/INSERT/UPDATE`만 수행하고 감사 table은 `SELECT/INSERT`만 사용한다. `DELETE`, DDL과 Flyway history 접근은 허용하지 않는다. Migration 계정은 Flyway history와 DDL을 소유하며 운영 migration job과 application Pod는 서로 다른 credential을 사용해야 한다.

## Legacy Auth Data Migration

T034 도구는 `src/migration` source set에 있어 Auth runtime `bootJar`에 포함되지 않는다. Core V13 적용 뒤 별도 source/target credential로 실행한다.

```bash
AUTH_DATA_MIGRATION_MODE=DRY_RUN ./gradlew migrateLegacyAuthData
AUTH_DATA_MIGRATION_MODE=APPLY ./gradlew migrateLegacyAuthData
AUTH_DATA_MIGRATION_MODE=VERIFY ./gradlew migrateLegacyAuthData
```

필수 `AUTH_MIGRATION_SOURCE_*`, `AUTH_MIGRATION_TARGET_*` 설정, 인증 쓰기 중단, 대사와 rollback 절차는 [`auth-data-migration-runbook.md`](../specs/002-bff-auth-msa/auth-data-migration-runbook.md)를 따른다. User/AuthIdentity만 이전하며 legacy AuthSession/refresh는 복사하지 않는다.

## Verification

```bash
./gradlew test bootJar
```

실제 PostgreSQL 검증은 `AUTH_DB_INTEGRATION=true`와 `AUTH_TEST_POSTGRES_*` 환경변수를 사용한다. CI는 local/Google 인증, 세션 비생성 재인증, refresh 회전·reuse family 폐기, 현재/전체 세션 revoke, subject/recent-auth negative case, 원문 비저장과 outbox를 실행한다. T033 단위 테스트는 KMS request/key metadata, 필수 JWT claim/signature, JWKS ETag/workload allowlist, 정기/emergency rotation과 old/new overlap을 검증한다.
