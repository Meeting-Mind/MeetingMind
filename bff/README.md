# MeetingMind Web BFF

브라우저 세션과 내부 서비스 진입점을 담당할 별도 Spring Boot 서비스다. Spring Session Redis, 보안 session cookie/CSRF, encrypted Token Vault, 자동 refresh와 현재 Backend의 allowlist proxy를 포함한다.

## Local Run

```bash
docker compose -f compose.local.yml up -d meetingmind-redis
cd bff
BFF_TOKEN_VAULT_LOCAL_KEY_BASE64="$(openssl rand -base64 32)" ./gradlew bootRun
```

- Application: `http://127.0.0.1:8081`
- Liveness: `http://127.0.0.1:8081/actuator/health/liveness`
- Readiness: `http://127.0.0.1:8081/actuator/health/readiness`

Readiness에는 Redis 연결 상태가 포함되며 liveness에는 포함되지 않는다. Redis 장애 시 새 BFF 인증 세션을 신뢰하거나 로컬 메모리로 우회하지 않는다.

rollout 중 신규 release를 drain할 때는 `BFF_ACCEPT_BROWSER_TRAFFIC=false`를 적용한다. 이 값은 rollout readiness만 `DOWN`으로 만들고 liveness는 유지한다. Frontend를 direct Backend로 되돌리거나 Browser token을 다시 발급하지 않으며, ingress traffic은 같은 cookie/Redis/Token Vault 계약의 안정 BFF release로 복원한다.

BFF는 `meetingmind.bff.browser.requests`, `meetingmind.bff.refresh`, `meetingmind.bff.session.invalid` Micrometer metric을 등록한다. label에는 URL path variable, 사용자/session ID와 token을 넣지 않는다. 운영 exporter와 dashboard 연결은 T045 범위이며 단계별 traffic/guardrail/rollback 절차는 `../specs/002-bff-auth-msa/rollout-runbook.md`를 따른다.

로컬 Token Vault key는 매 실행마다 바꾸면 기존 bundle을 복호화할 수 없으므로 `.env` 등 Git에 포함되지 않는 로컬 secret에 고정한다. 운영은 `BFF_TOKEN_VAULT_KEY_PROVIDER=kms`와 `BFF_TOKEN_VAULT_KMS_KEY_ID`를 사용하고 BFF ECS Task Role로 KMS 권한을 부여한다. key가 없거나 잘못되면 평문/임시 key로 우회하지 않고 시작을 거부한다.

기본 인증 provider는 별도 Auth Service다. `BFF_AUTH_SERVICE_BASE_URL`의 고정 internal signup/login/google/refresh/revoke만 호출하고, Auth 성공 직후 `BFF_CORE_BASE_URL`의 internal User projection을 완성한 뒤 Browser session을 만든다. Core/AI/LiveKit route는 정확한 audience access만 사용한다. Auth 장애를 legacy로 자동 우회하지 않으며 rollback은 `BFF_AUTH_PROVIDER=legacy`, `BFF_AUTH_ISSUER=meetingmind-core-legacy`와 Core validation mode를 함께 명시적으로 전환한다.

`POST /api/v1/auth/logout-all`은 최근 인증 10분을 요구한다. 시간이 지난 세션은 `POST /api/v1/auth/reauthenticate`에서 local 비밀번호 또는 새 Google credential을 검증하고, Auth가 반환한 서버 시각만 session에 저장한다. 성공 시 Auth의 전체 revoke를 먼저 확정하고 Auth UUID Spring Session index로 다른 BFF session과 Token Bundle을 제거한 뒤 현재 session을 마지막에 무효화한다. `legacy` provider는 불완전한 로컬 삭제를 성공으로 처리하지 않고 기능 사용 불가로 응답한다.

로컬/CI의 `X-MeetingMind-Test-Principal`은 Auth/Core 양쪽에서 명시적으로 허용한 profile에서만 사용한다. 운영 ECS Fargate에서는 이 설정을 끄고 mTLS SPIFFE workload identity를 사용한다.

## Verification

```bash
./gradlew test bootJar
```

Redis 공유 세션 검증은 Redis를 실행한 상태에서 다음 환경변수를 추가한다.

```bash
BFF_REDIS_INTEGRATION=true BFF_REDIS_PORT=6380 ./gradlew test
```
