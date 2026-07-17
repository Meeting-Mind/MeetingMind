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

로컬 Token Vault key는 매 실행마다 바꾸면 기존 bundle을 복호화할 수 없으므로 `.env` 등 Git에 포함되지 않는 로컬 secret에 고정한다. 운영은 `BFF_TOKEN_VAULT_KEY_PROVIDER=kms`와 `BFF_TOKEN_VAULT_KMS_KEY_ID`를 사용하고 EKS workload IAM으로 KMS 권한을 부여한다. key가 없거나 잘못되면 평문/임시 key로 우회하지 않고 시작을 거부한다.

인증과 업무 endpoint를 사용하려면 현재 Backend를 `http://127.0.0.1:8080`에 실행하거나 `BFF_BACKEND_BASE_URL`을 해당 origin으로 설정한다. BFF는 고정된 signup/login/google/refresh와 업무 API allowlist만 호출하고 Backend token 응답을 브라우저로 전달하지 않는다. Core/AI/LiveKit 목적지와 timeout/circuit/bulkhead 기본값은 `.env.example`의 서비스별 설정으로 분리할 수 있다.

## Verification

```bash
./gradlew test bootJar
```

Redis 공유 세션 검증은 Redis를 실행한 상태에서 다음 환경변수를 추가한다.

```bash
BFF_REDIS_INTEGRATION=true BFF_REDIS_PORT=6380 ./gradlew test
```
