# Implementation Log: BFF Auth and Gradual MSA

## Scope

M001 문서·설계 기준선, M002의 T010~T016 Web BFF 호환 경로, M003의 T020~T023 Browser session cutover와 M004의 T030 Auth 보안 shared contract, T031 foundation, T032 credential/session/revoke runtime, T033 KMS signing/JWKS/Resource validator, T034 Auth 데이터 이전, T035 BFF→Auth/Core cutover, T036 CI security hardening, T037 CI dependency sync 및 T024 모든 기기 로그아웃까지 구현했다.

## Work Allocation

| Date | Workstream | Owner | Agent | Files | Result |
| --- | --- | --- | --- | --- | --- |
| 2026-07-16 | Requirements/Policy | Docs/Contracts | Codex | `requirements/*` | Browser/BFF/Auth token 저장 책임, BffSession 상태와 EKS/LiveKit 정책 정리 |
| 2026-07-16 | Spec/Research/Plan | Docs/Contracts | Codex | `spec.md`, `clarify.md`, `research.md`, `plan.md` | 사용자 결정, open gate, 점진 전환/장애 격리 설계 |
| 2026-07-16 | Contracts/Data | Shared Contract | Codex | `contracts/*`, `data-model.md`, `erd.md` | Browser-BFF/BFF-Auth와 Redis/Vault/Auth DB 경계 분리 |
| 2026-07-16 | T010 Web BFF Foundation | Web BFF | Codex | `bff/**`, `compose.local.yml`, `.github/workflows/ci.yml`, `.gitignore` | 독립 Spring Boot/Gradle/Docker, Spring Session Redis, health/probe와 CI 기반 구현 |
| 2026-07-16 | T011 BFF Security | Web BFF | Codex | `bff/src/main/**`, `bff/src/test/**`, `bff/build.gradle`, `.env.example` | Spring Security, CSRF, 운영/로컬 cookie profile, session fixation 방지와 세션 수명 설정 구현 |
| 2026-07-16 | T012 Encrypted Token Vault | Web BFF | Codex | `bff/src/main/java/com/meetingmind/bff/tokenvault/**`, BFF config/tests/docs | AES-256-GCM envelope encryption, AWS KMS/local key adapter, Redis ciphertext store와 fail-closed rotation 구현 |
| 2026-07-16 | T013 Backend Auth Compatibility | Web BFF | Codex | `bff/src/main/java/com/meetingmind/bff/auth/**`, BFF security/config/tests/docs | 고정 Backend auth client, tokenless Browser 응답, BFF session/bootstrap와 Remember me 수명 연결 |
| 2026-07-16 | T014 BFF Token Manager | Web BFF | Codex | `bff/src/main/java/com/meetingmind/bff/auth/**`, `tokenvault/**`, config/tests/docs | access 만료 선제 refresh, Redis single-flight, downstream 401 1회 retry와 실패 cleanup 구현 |
| 2026-07-16 | T015 BFF Allowlist Proxy | Web BFF | Codex | `contracts/bff-proxy-routes.md`, `bff/src/main/java/com/meetingmind/bff/proxy/**`, config/tests/docs | 실제 API method/path allowlist, Core/AI/LiveKit timeout·circuit·bulkhead와 오류 정규화 구현 |
| 2026-07-16 | T016 Compatibility Integration | Integration | Codex | BFF auth/tests, `scripts/bff-backend-compat-e2e.sh`, CI/docs | 현재 세션 logout, Redis Browser 계약 테스트와 실제 Backend 프로세스 E2E 구현 |
| 2026-07-16 | T020 Frontend Session Cutover | Frontend | Codex | `frontend/src/auth/**`, `frontend/src/api/**`, `App.tsx`, LiveKit request, Vite/Playwright/CI/docs | Browser token/Bearer 제거, BFF session bootstrap, same-origin cookie/CSRF와 전체 브라우저 E2E 구현 |
| 2026-07-16 | T021 Current Session Logout | Frontend | Codex | `frontend/src/auth/**`, `frontend/src/components/AuthSessionControls.tsx`, `App.tsx`, styles/tests/E2E/docs | CSRF logout 호출, 전역 사용자 상태 정리, 성공/멱등/네트워크 실패 UX 구현 |
| 2026-07-16 | T022 Final 401 Handling | Frontend | Codex | `frontend/src/auth/**`, API clients, `App.tsx`, auth modal, LiveKit request, styles/tests/E2E/docs | 공통 `SESSION_INVALID` 감지, 1회 document reload, 재로그인 안내와 요청 경로 복귀 구현 |
| 2026-07-16 | T023 BFF Rollout | Integration | Codex | BFF rollout readiness/metrics/config/tests, Compose/env, `rollout-runbook.md`, 관련 설계·검증 문서 | traffic drain flag, bounded metrics, 단계별 guardrail과 안정 BFF rollback 기준 구현 |
| 2026-07-17 | T030 Auth Security Decisions | Shared Contract | Codex | auth/security requirements, `clarify.md`, `research.md`, Auth/event contracts, data model/ERD/plan/tasks/analyze | refresh family, JWT/JWKS, revoke event, mTLS workload identity와 logout-all 재인증 확정 |
| 2026-07-17 | T031 Auth Service Foundation | Auth Service | Codex | `auth/**`, `compose.local.yml`, `.github/workflows/ci.yml`, root/config 및 관련 설계·검증 문서 | 독립 서비스·전용 PostgreSQL, forward-only schema, 최소 권한 runtime 계정과 health/CI 경계 구현 |
| 2026-07-17 | T032 Auth Runtime | Auth Service | Codex | `auth/src/main/**`, `auth/src/test/**`, Compose/CI/root 및 Auth 계약/data/plan/analyze | local/Google 자격 검증, refresh family rotation/reuse, revoke-all, 감사/outbox와 workload/fail-closed signer 경계 구현 |
| 2026-07-17 | T033 Auth Keys/JWKS | Auth Service | Codex | `auth/**`, `backend/**/auth/target/**`, Compose/env 및 Auth 계약/data/plan/tasks/analyze | AWS KMS RS256 signer, rotation key ring, 내부 JWKS와 비활성 Resource validator 구현 |
| 2026-07-23 | T043-1~3 STT CI Integration | Integration | Codex | `.github/workflows/ci.yml`, `specs/002-bff-auth-msa/clarify.md`, `specs/002-bff-auth-msa/tasks.md` | STT Gradle test/bootJar, Docker build/digest, Trivy scan과 CI Gate/summary 편입 |
| 2026-07-24 | T040 Q-023 Platform Decision | Platform | 사용자/Codex | `clarify.md`, `plan.md`, `tasks.md`, `adr/002-ecs-fargate.md` | Cloud Map Private DNS `meetingmind.internal`, direct mTLS, 공통 내부 CA, 서비스별 SPIFFE URI SAN 인증서와 NonProd ECS force deployment rotation 확정 |
| 2026-07-25 | T047-B/C2 Implementation Plan | Platform/Security | 사용자/Codex | `infra/aws/nonprod-v2/mtls-implementation-plan.md`, `clarify.md`, `plan.md`, `tasks.md`, V2 plan/README | offline NonProd CA, 전용 ARM64 cert-loader, 서비스별 TLS bundle, AI Envoy와 material/runtime gate 분리 계획 확정 |
| 2026-07-25 | T047-B1 Offline PKI Tooling | Platform/Security | Codex | `scripts/pki/nonprod/**`, `tasks.md`, `implement.md` | encrypted offline CA, 5개 exact service manifest, leaf 발급·검증과 bundle JSON 도구를 표준 라이브러리/OpenSSL로 구현 |
| 2026-07-18 | T036 CI Security Hardening | Integration | Codex | `bff/build.gradle`, `auth/build.gradle`, `.gitleaksignore`, 관련 spec/plan/tasks/implement | BFF/Auth 수정 가능 취약점 제거와 테스트 fixture Gitleaks 오탐 정밀 예외 처리 |
| 2026-07-23 | T037 CI Dependency Sync | Integration | Codex | `frontend/package-lock.json`, `backend/build.gradle`, `auth/build.gradle`, `tasks.md`, `implement.md` | 병합에서 누락된 Frontend peer lock 항목 복원과 Core/Auth PostgreSQL JDBC 취약점 수정 |
| 2026-07-18 | T034 Auth Data Migration | Data | Codex | Core V13, `auth/src/migration/**`, PostgreSQL tests, migration runbook과 Core/Auth data/ERD/plan/tasks | 문자열 업무 PK를 보존한 UUID projection, 반복 가능한 User/AuthIdentity offline 이전과 exact reconciliation 구현 |
| 2026-07-18 | T035 Preparation | Integration | Codex | BFF/Auth/Core runtime, Frontend identity comparisons, contracts/data/plan/tasks/analyze | external/internal User ID 불일치와 신규 Auth User projection 공백을 발견하고 blocking Q-020/Q-021 및 순차 구현 경계 정의 |
| 2026-07-18 | T035 Auth Cutover | Integration | Codex | BFF Auth/Token Vault/session/proxy, Core validator/projection, Compose/CI/E2E/contracts/runbook | target Auth client, actual AuthSession/Auth UUID index, audience bundle v2, deterministic dual validation과 동기 Core User projection 구현·검증 |
| 2026-07-18 | T024 Preparation | Frontend/Auth | Codex | Browser/Auth 재인증 계약, BFF indexed logout-all, Frontend session controls/tests | Q-022 전용 step-up 인증과 Auth-first/현재 BFF session last cleanup 경계를 확정하고 구현 단위를 shared contract→Auth→BFF→Frontend로 고정 |
| 2026-07-18 | T024 All-device Logout | Frontend/Auth | Codex | Auth 재인증, BFF indexed revoke/cleanup, Frontend session controls/modal, metrics/docs/tests | 세션 비생성 local/Google step-up, Auth-first 전체 revoke, 다른 BFF session/Token Bundle 정리와 현재 session last 폐기 UI/API 구현·검증 |

동시에 같은 파일을 수정한 다른 agent는 없으며 통합은 Requirements → Spec/Plan → Contracts/Data → legacy reference → analysis 순서로 진행한다.

## Changes

- `POL-SESSION-01`과 `NFR-SEC-02` 충돌을 브라우저 무토큰, BFF 암호문, Auth hash의 세 역할로 해소했다.
- 일반 세션 유휴 60분/절대 12시간, Remember me 7일 sliding 유휴/14일 절대 만료를 정책 기준선으로 확정했다.
- 별도 Spring Boot Web BFF, Spring Session Redis, encrypted Token Vault, 내부 비대칭 access/JWKS 방향을 문서화했다.
- AWS EKS 단일 리전 Multi-AZ와 LiveKit Cloud, 서비스별 DB 소유권과 failure behavior를 문서화했다. 이 당시 EKS 선택은 2026-07-23 ADR 002에서 ECS Fargate로 대체됐다.
- 현재 Backend token API는 Phase 1 compatibility/rollback 대상으로 보존하고 목표 browser 계약에서는 public refresh를 제거했다.
- 독립 `bff` Gradle 프로젝트를 Java 21/Spring Boot 3.5.14로 추가했다. 기존 Backend와 같은 버전을 사용해 별도 version 선택을 만들지 않았다.
- `spring-boot-starter-actuator`, `spring-boot-starter-data-redis`, `spring-session-data-redis`는 T010의 health/probe와 외부 세션 저장소를 구현하기 위해 추가했다. security/proxy/회복성 라이브러리는 후속 task 전까지 추가하지 않았다.
- 운영 readiness에는 Redis를 포함하고 liveness에서는 제외했다. Redis 장애 시 container/Task 재시작 반복 대신 traffic 수용만 중지하는 경계를 적용했다.
- 로컬 Compose에 Redis와 `bff` profile을 추가하고 BFF Docker image와 GitHub Actions BFF test/container scan 경계를 연결했다.
- Spring Security는 health와 CSRF bootstrap만 익명 허용하고 나머지 요청을 인증 대상으로 고정했다. 인증되지 않은 요청은 redirect 없이 `401`, 인증된 상태 변경 요청의 CSRF 누락은 `403`으로 처리한다.
- `GET /api/v1/auth/csrf`는 session 기반 token, header/parameter 이름을 반환하며 `Cache-Control: no-store`를 적용했다.
- 운영 cookie는 `__Host-mm-session`, `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/`, Domain 없음으로 고정하고 로컬 HTTP profile만 `mm-session`과 `Secure=false`를 사용한다.
- 인증 성공 시 사용할 `ChangeSessionIdAuthenticationStrategy`를 등록하고 session fixation 회귀 테스트를 추가했다.
- 일반 60분 idle/12시간 absolute와 Remember me 7일 sliding idle/14일 absolute 설정을 타입 안전하게 바인딩하고 양수 및 `idle <= absolute`를 검증한다. 로그인별 TTL과 absolute expiry는 T013의 BFF session 생성에 연결했다.
- Token Bundle payload를 매 bundle마다 생성한 AES-256 data key로 GCM 인증 암호화하고, data key는 운영 AWS KMS 또는 local/test AES master key로 다시 암호화하는 envelope 구조를 구현했다. bundle ID, authSessionId, version은 payload AAD와 KMS encryption context에 함께 묶는다.
- AWS KMS 연동에는 공식 AWS SDK for Java v2 `kms`와 JDK URL Connection client를 추가했다. JCA만으로 AWS request signing/workload credential/KMS API를 안전하게 대체할 수 없어 새 의존성이 필요하며, 선택 시점 공식 v2 release `2.46.8` BOM으로 모듈 버전을 고정했다.
- Redis Token Vault는 session Redis와 다른 namespace를 사용하고 ciphertext, encrypted data key와 비밀이 아닌 조회 metadata만 JSON으로 저장한다. 생성은 NX, rotation은 expected version과 현재 serialized value를 Lua compare-and-set한 뒤 TTL까지 한 명령으로 교체한다.
- 암호화가 실패하면 Redis 쓰기를 시작하지 않고 CAS 경쟁에서 패하면 기존 bundle을 유지한다. 복호화/AAD/KMS/key mismatch는 token이나 provider raw error가 없는 고정 `TokenVaultException.Code`로 fail closed한다.
- local/test key가 비어 있거나 256-bit가 아니면 임시 key나 평문 fallback 없이 시작을 거부하고, 운영은 KMS provider와 BFF ECS Task Role을 사용한다.
- `data-model.md`에는 물리 암호화/AAD 경계를 추가했다. 기존 ERD의 `encryptedPayload`, `encryptedDataKey`, `keyId`, `version` 필드로 표현 가능해 ERD 변경은 없고, Browser/Auth API shape도 바뀌지 않아 contracts 변경은 없다.
- 현재 Backend 전용 RestClient는 설정된 http(s) origin과 코드에 고정된 `signup|login|google` 경로만 사용한다. Browser의 `rememberMe`는 Backend request로 전달하지 않으며 User-Agent는 제어문자를 제거하고 256자로 제한한다.
- 입력 검증에는 이미 사용하는 Spring Boot validation starter를 BFF에 추가했다. email/필수값/credential·문자열 상한을 BFF 신뢰 경계에서 먼저 검사하고 Backend의 기존 password/Google 검증은 그대로 유지한다.
- Backend의 token 응답은 메모리에서 즉시 Token Vault payload로 변환하고 BFF session에는 `userId`, 호환 `authSessionId`, `tokenBundleId`, 생성/절대만료/최근인증/Remember me만 저장한다. Backend나 기존 schema는 변경하지 않았다.
- 인증 성공은 기존 CSRF session ID를 교체하고 SecurityContext를 명시적으로 HttpSession에 저장한다. Browser 응답은 `user`와 `session`만 포함하며 access/refresh/token expiry를 포함하지 않는다.
- 일반 세션은 Redis idle 60분/절대 12시간과 session cookie, Remember me는 idle 7일/절대 14일과 최초 로그인 기준 `Max-Age=14일` persistent cookie를 사용한다. absolute expiry filter는 session/Token Bundle을 정리하고 보호 요청을 `401`로 차단한다.
- `GET /api/v1/auth/session`은 인증 여부와 user/session view만 `no-store, private`로 반환한다. session principal과 user/authSession/tokenBundle 참조가 맞지 않으면 fail closed하고 session을 무효화한다.
- Backend 오류 code는 allowlist로 Browser 오류에 정규화하고 provider raw error/response cause를 보존하지 않는다. 세션 생성 실패 시 생성된 ciphertext를 삭제하고 현재 Backend logout을 best effort로 호출한다.
- Browser 계약 shape는 기존 `browser-auth-api.md`와 일치해 변경하지 않았다. `data-model.md`와 `auth-service-api.md`에는 Phase 1 호환 ID 의미만 추가했고 기존 ERD 필드/관계는 변하지 않아 ERD 갱신은 없다.
- Token Vault는 refresh가 유효한 동안 만료된 access payload도 복호화할 수 있고 version과 payload를 함께 읽어 CAS rotation에 사용한다. 신규/회전 bundle은 access가 현재보다 미래이고 refresh가 access보다 이르지 않아야 하는 검증을 유지한다.
- Token Manager는 access 만료 30초 전 한 번 선제 refresh하며, 만료 전 token이 downstream `401`을 받으면 refresh 후 원 호출을 정확히 한 번만 재시도한다. 이미 선제 refresh한 token 또는 재시도도 `401`이면 추가 loop 없이 최종 `SESSION_INVALID`로 끝낸다.
- 복수 BFF Pod의 refresh 경쟁은 Redis `SET NX` lease lock으로 한 owner만 허용하고, 대기 요청은 Token Vault version 변경을 polling해 새 bundle을 재사용한다. Lua release는 owner 값이 일치할 때만 key를 삭제해 다른 요청의 lock을 해제하지 않는다.
- 현재 Backend compatibility client에는 코드에 고정된 `/api/v1/auth/refresh`만 추가했다. 회전 응답의 user가 BFF session user와 다르면 fail closed하고, 새 access/refresh 수명은 BFF session absolute expiry를 넘기지 않는다.
- refresh 실패, lock/Vault 실패 또는 최종 downstream `401`은 provider 상세를 버리고 `401 SESSION_INVALID`로 정규화한다. 현재 token으로 Backend revoke를 best effort 수행한 뒤 Token Bundle과 BFF session을 삭제하며 Phase 1 compatibility revoke의 durable 재처리는 T045 출시 gate로 남긴다.
- T014는 기존 Browser 응답과 Auth/ERD 데이터 shape를 바꾸지 않고 T015 proxy가 호출할 `AuthorizedDownstreamCall` 실행 경계를 추가했으므로 contracts, data model과 ERD 갱신은 필요하지 않다.
- T015 proxy는 현재 Backend에 실제 존재하는 `/api/v1/spaces|meetings` method/path 조합과 `space-*`, `meeting-*`, participant/report/task 등 엔티티별 prefix+UUID 형식만 허용한다. transcription session만 Backend 계약대로 bare UUID를 사용한다. Browser가 목적 URL을 전달할 수 없고 `/api/v1/auth/*`, legacy `/api/workspace|livekit|stt`, encoded/matrix path와 미등록 method는 Token Manager 호출 전에 `404 ROUTE_NOT_ALLOWED`로 거부한다.
- Browser request에서는 `Content-Type`과 `Accept`만 전달하고 `Authorization`, cookie, CSRF, Host/forwarding header는 버린다. downstream Authorization은 T014 Token Manager가 만든 Bearer만 사용하며 응답 header는 `Content-Type`, `Cache-Control`, `ETag`만 전달한다.
- Phase 1은 Core, AI, LiveKit 논리 서비스가 같은 현재 Backend origin을 기본 사용하되 각각 독립 설정과 HTTP client/guard를 가진다. Core `1s/3s·64·5회/30s`, AI `1s/30s·8·3회/30s`, LiveKit `1s/2s·16·3회/15s` connect/read·동시성·circuit 기본값을 적용했다.
- queue 없는 `Semaphore` bulkhead는 포화 요청을 즉시 거부한다. circuit은 연속 transport/5xx 실패로 열리고 open 기간 뒤 단일 half-open probe만 허용한다. circuit이 열린 뒤 늦게 완료된 기존 성공 요청은 circuit을 닫지 못한다.
- downstream `2xx~4xx`는 status/body를 유지하되 `401`은 T014가 최대 한 번만 처리한다. 5xx/timeout/circuit/bulkhead는 raw body/cause 없이 Core `CORE_SERVICE_UNAVAILABLE`, AI `AI_PROVIDER_UNAVAILABLE`, LiveKit `LIVEKIT_SERVICE_UNAVAILABLE` 503으로 구분한다. 별도 자동 retry나 mock 성공 fallback은 추가하지 않았다.
- 새 회복성 dependency를 추가하지 않았다. 현재 요구는 고정 연속 실패 circuit과 동시성 제한이며 JDK `HttpClient`, `Semaphore`, atomic state와 기존 Spring `RestClient`로 충족한다. API shape는 기존 Core 계약을 전달하며 새 route/error 경계만 `contracts/bff-proxy-routes.md`에 추가했다. 데이터 관계가 없어 data model과 ERD 영향은 없다.
- `POST /api/v1/auth/logout`은 CSRF를 필수로 하되 인증 session이 이미 없는 경우도 `204`로 멱등 처리한다. 유효 session은 access 만료 임박 시 먼저 single-flight refresh하고 현재 Backend logout으로 회전된 refresh를 폐기한 뒤 Token Bundle, Spring Session과 cookie를 삭제한다.
- Backend/Vault/refresh 오류에서도 Browser session은 fail closed하고, compatibility revoke 실패는 token/cause 없는 `compat_auth_revoke_failed` 보안 이벤트로 기록한다. durable revoke 재처리는 암호화된 payload와 운영 queue 설계가 필요한 Auth 추출·관측 출시 gate로 남기고 T016의 현재 Backend 호환 범위에는 포함하지 않았다.
- 실제 Redis 통합 테스트는 Browser 응답과 Redis에 token 원문이 없는지, public refresh route 거부, 선제 refresh, logout CSRF negative, cookie 만료, stale cookie `401`, logout 멱등성과 애플리케이션 로그의 token 문자열 0건을 검증한다.
- `scripts/bff-backend-compat-e2e.sh`는 현재 Backend `test` 프로필과 Web BFF를 별도 프로세스로 실행한다. 테스트 전용 access skew `2h`로 Backend의 1시간 access를 즉시 refresh하고 signup→Core proxy→logout→stale cookie 거부를 실제 compatibility 계약으로 CI에 고정한다.
- Frontend `AuthSession`은 사용자 표시 정보와 session expiry view만 보유하며 access/refresh/token type을 포함하지 않는다. 앱 시작은 `/api/v1/auth/session` 응답 전까지 보호 경로를 loading 상태로 유지하고 authenticated/unauthenticated 응답을 명시적으로 검증한다.
- 인증, Workspace/Meeting CRUD와 LiveKit token 요청은 모두 상대 `/api` 경로와 `credentials: same-origin`을 사용한다. 상태 변경 요청은 공통 CSRF client가 `/api/v1/auth/csrf`의 `X-CSRF-TOKEN`을 캐시해 붙이며 `403` 또는 인증 성공 뒤에는 재발급하도록 초기화한다.
- Vite 개발 proxy의 유일한 API 대상은 `VITE_BFF_PROXY_TARGET`이며 기본값은 `http://127.0.0.1:8081`이다. Browser가 Backend/BFF의 다른 origin을 직접 지정하는 `VITE_API_BASE_URL` 경로는 제거했다.
- Playwright는 격리 포트의 Backend, BFF, Redis와 Frontend를 함께 실행하고 UI 로그인, reload session 복원, 브라우저 `sessionStorage` token 부재, 회의 CRUD와 참가자 ACL을 검증한다. 이 E2E에서 T015의 bare UUID 가정이 실제 Backend prefix ID와 불일치함을 발견해 route 계약/구현/테스트를 엔티티별 prefix+UUID로 보정했다.
- Frontend 현재 세션 로그아웃은 공통 CSRF client로 `POST /api/v1/auth/logout`을 호출하고 계약의 `204`에서만 인증 상태와 Workspace/Meeting/초대/참여자/task 등 사용자별 메모리 상태를 초기화한 뒤 랜딩을 새로 로드한다. 진행 중이던 사용자 요청도 문서 수명과 함께 종료해 늦은 응답이 메모리를 다시 채우지 않게 하며, 이미 세션이 없는 BFF의 멱등 `204`도 같은 성공 경로로 처리한다.
- logout network/비정상 응답은 서버 세션이 남았을 가능성이 있으므로 UI만 로그아웃된 것으로 위장하지 않는다. 사용자 세션 control을 유지하고 명시적 오류와 재시도 버튼을 제공한다. CSRF `403`은 cache를 초기화해 다음 시도에서 새 token을 받는다.
- T021의 기존 task 문구와 Browser 계약을 대조해 모든 기기 로그아웃에는 최근 인증 시간/재인증 UX와 Auth revoke-all이 선행돼야 함을 확인했다. Q-016을 open gate로 추가하고 현재 세션 로그아웃과 분리한 T024로 이동했으며, 준비되지 않은 `/auth/logout-all` UI나 가짜 성공은 노출하지 않았다.
- `bffFetch`는 기존 same-origin cookie/CSRF 요청 준비 뒤 응답을 복제해 `401`의 common error code만 검사한다. code가 정확히 `SESSION_INVALID`일 때만 구독자에게 알리고 원본 response/body는 각 API의 기존 오류 처리에 그대로 남긴다. 로그인 실패 `INVALID_CREDENTIALS`, provider 오류와 비 JSON `401`은 전역 만료를 발생시키지 않는다.
- Workspace/Meeting/AI/report/transcription API와 LiveKit token, auth state-changing 요청을 공통 fetch 경계에 연결했다. CSRF bootstrap과 `/auth/session` bootstrap은 각각 token 준비 재귀와 잘못된 bootstrap redirect loop를 피하기 위해 native fetch를 유지한다.
- App의 전역 구독자는 동시 최종 `401` 중 첫 이벤트만 처리하고 현재 path/query가 `/`로 시작하며 `//`가 아닌지 검증한 뒤 `/?auth=session-expired&returnTo=...`로 document를 교체한다. 새 document는 BFF의 unauthenticated bootstrap 뒤 만료 안내와 로그인 modal을 열어 stale 메모리와 진행 중 요청을 제거한다.
- 재로그인 성공은 검증된 `returnTo`로 replace 이동하며 modal을 닫으면 만료 query를 제거한다. 외부 origin이나 protocol-relative return path는 `/spaces`로 제한해 open redirect를 허용하지 않는다. Browser/Auth API shape와 데이터 관계는 바뀌지 않아 contracts, data model과 ERD 갱신은 필요하지 않다.
- T023 검토에서 기존 direct Backend rollback 문구는 Browser token/Bearer 제거와 충돌함을 확인했다. Browser 경로는 항상 same-origin BFF로 유지하고 `BFF_ACCEPT_BROWSER_TRAFFIC=false`가 신규 release의 readiness만 `DOWN`으로 내려 liveness와 데이터 저장소는 유지하며, ingress가 동일 session/Vault 계약의 안정 BFF release로 traffic을 복원하도록 설계를 바로잡았다.
- `meetingmind.bff.browser.requests`는 고정 operation과 outcome만 기록하고 raw path, ID, token, PII를 label에 포함하지 않는다. 실제 refresh owner의 성공/실패와 정확한 `SESSION_INVALID` 발생을 별도 counter로 기록해 일반 보호 API `401` probe가 session invalid 지표를 부풀리지 않게 했다.
- 5%·25%·50%·100% rollout 체류 시간과 최소 표본, server error·refresh failure·logout cleanup·session invalid 비율 guardrail, 즉시 rollback 조건과 7일 window를 `rollout-runbook.md`에 고정했다. Micrometer는 기존 Actuator 의존성으로 충족해 새 라이브러리를 추가하지 않았다.
- T023은 Browser/Auth API shape나 데이터 관계를 바꾸지 않는다. `data-model.md`는 rollback 중 Backend compatibility DB를 server-side로 보존한다는 수명 규칙만 정정했고 contracts와 ERD 갱신은 필요하지 않다.
- T030은 refresh credential을 AuthSession별 하나의 family로 고정하고 BFF single-flight 밖의 grace를 허용하지 않는다. rotation은 credential row lock, 이전 `usedAt`/`replacementId`, 새 leaf insert와 AuthSession 갱신을 원자 처리하며 사용된 credential 재사용 시 해당 기기 family와 AuthSession을 `REFRESH_REUSE`로 폐기한다.
- Access는 AWS KMS 비반출 RSA-2048 key의 `RS256`으로 서명하고 `meetingmind-core|ai|livekit` 단일 audience별 10분 JWT를 발급한다. 필수 header/claim, 60초 skew, 90일 rotation, 1시간 overlap과 5분 JWKS cache를 Auth 계약에 고정하고 업무 RBAC/ACL은 token에 넣지 않는다.
- 로그아웃의 DB revoke와 `AuthSessionRevokedV1` transactional outbox를 함께 커밋한다. BFF/Resource Service는 at-least-once event를 idempotent 처리해 `sid`를 `denyUntil`까지 로컬 denylist에 유지하며 중앙 Auth/Redis 매 요청 조회를 추가하지 않는다.
- 내부 endpoint는 mTLS SPIFFE workload identity, 서비스별 Security Group과 principal/endpoint allowlist를 동시에 요구한다. Q-023에서 Cloud Map Private DNS, direct mTLS, 공통 내부 CA와 NonProd 수동 rotation을 확정했으며 shared secret/client credential 방식으로 계약을 되돌리지 않는다.
- 모든 기기 로그아웃은 최근 10분 `authenticatedAt` 또는 local 비밀번호/새 Google credential 재인증을 요구한다. Q-016 결정과 T032 Auth revoke-all/outbox, T035 session index를 선행한 뒤 T024에서 실제 UI/API를 연결했다.
- target Token Bundle을 audience별 access expiry와 schema version 2로 확장하고 AuthSession `refreshFamilyId`, AuthOutboxEvent를 data model/ERD에 추가했다. 이는 future Auth DB와 Vault document의 forward-only 변경이며 현재 Backend DB나 BFF runtime code는 T030에서 수정하지 않았다.
- `auth`를 Java 21/Spring Boot 3.5.14 독립 Gradle 프로젝트와 비루트 Docker image로 추가했다. 기존 Java 운영 스택과 같은 버전을 사용하고 health/JDBC/Flyway/PostgreSQL 외에 T031 범위를 넘는 인증·키 의존성은 추가하지 않았다.
- liveness는 process 상태만, readiness는 실제 Auth DB 연결을 포함한다. DB 중단 검증에서 liveness는 `UP`/200을 유지하고 readiness만 `DOWN`/503으로 전환돼 장애 시 재시작 반복 대신 traffic 수용을 중지한다.
- Core DB와 다른 PostgreSQL service/database/volume을 Compose `auth` profile로 추가했다. canonical runtime role은 `meetingmind_auth_app`으로 고정하고 password만 secret으로 주입하며, migration credential과 runtime credential을 분리했다.
- V1은 T030의 User/Identity/Session/refresh lineage/audit/outbox table·제약·index를 생성한다. 실제 적용 뒤 V1을 수정해 checksum을 깨지 않고 V2에서 broad/default privilege를 회수한 다음 table별 `SELECT/INSERT/UPDATE` 또는 audit `SELECT/INSERT`만 다시 부여했다.
- 통합 테스트는 실제 PostgreSQL에서 V1→V2 이력과 정확한 table 집합을 확인하고 runtime 계정의 허용 DML을 rollback transaction으로 검증한다. `DELETE`, schema `CREATE`, Flyway history 조회는 SQLSTATE `42501`로 거부됨을 확인한다.
- T032/T033의 login/refresh/revoke/JWKS 경로는 가짜 성공 응답으로 만들지 않았다. T031 smoke에서 `/internal/v1/auth/login`은 `404`이며 현재 Backend/BFF/Frontend 계약은 변경하지 않았다.
- GitHub Actions에 Auth PostgreSQL service 기반 실제 migration/권한 통합 테스트, bootJar, Docker build/scan과 최종 gate/digest를 추가했다.
- T032는 `spring-security-crypto`의 BCrypt와 기존 Spring validation을 Auth에 추가했다. 비밀번호 구현을 직접 만들지 않고 검증된 단방향 hash를 사용하며 cost 10~16만 허용하고, 가입 정책은 기존 `POL-PW-01`의 8~128자·4종 중 3종을 서버에서 확인한다.
- refresh는 `SecureRandom` 32-byte 원문과 최소 32자 환경 secret의 HMAC-SHA-256 lookup hash를 사용한다. 원문은 internal 응답/요청 메모리에만 있고 Auth DB에는 `hmac_sha256$...`, lineage와 revoke 상태만 저장한다.
- 성공 refresh는 단일 PostgreSQL CTE에서 이전 credential의 `usedAt/replacementId`와 새 active leaf insert를 처리해 V1의 active leaf unique/check/FK를 모두 유지한다. 재사용은 해당 AuthSession family와 credential, 감사와 outbox를 예외 응답 전에 commit하며 다른 기기 session은 건드리지 않는다.
- local 가입/로그인은 canonical email, 중복·비활성 계정과 자격 불일치를 고정 code로 처리한다. Google 검증은 JDK RSA signature와 공개 JWKS cache로 RS256, `kid`, 두 허용 issuer, client-id allowlist, expiry와 verified email을 검사하고 unknown `kid`에서 1회 공개키를 새로 조회한다.
- verified Google email은 기존 User에 `GOOGLE` AuthIdentity로 연결한다. Google `sub` advisory transaction lock과 DB unique constraint로 동시 최초 로그인을 직렬화하며 credential 원문은 저장·감사·로그에 남기지 않는다.
- internal API는 direct client certificate의 SPIFFE URI SAN과 Web BFF principal allowlist를 검사한다. local/test/integration에서만 test principal header를 명시 허용하고 `prod`에서는 설정값이 true여도 무시하는 자동 테스트를 추가했다.
- revoke와 revoke-all은 AuthSession/family revoke, session별 `AuthSessionRevokedV1` unpublished outbox와 allowlist audit을 한 transaction으로 기록한다. revoke-all은 Q-017의 `currentAuthSessionId`/`userId` owner binding, 최근 10분과 60초 미래 skew를 변경 전에 재검증하고 멱등 재요청을 허용한다.
- access signing은 T033 `AccessTokenIssuer` port로 분리했다. runtime image의 기본 adapter는 `503 TOKEN_ISSUER_UNAVAILABLE`만 반환해 User/Identity/Session/credential transaction을 rollback하며 test signer는 test source에만 있고 bootJar/image scan에서 제외됨을 확인했다.
- V1/V2 table과 관계로 T032 invariants를 모두 표현할 수 있어 새 migration이나 ERD 변경은 만들지 않았다. outbox transport/published 상태 갱신과 consumer 관측은 제품 결정을 기다리는 T045 출시 gate로 유지한다.
- T033은 BFF Token Vault와 같은 공식 AWS SDK v2 `2.46.8` BOM의 `kms`/JDK URL Connection client를 Auth에 추가했다. KMS workload credential과 비반출 asymmetric `Sign`/`GetPublicKey` API를 JCA만으로 대체할 수 없어 기존 검증된 dependency 선택을 재사용했다.
- signer는 KMS `RSA_2048`, `SIGN_VERIFY`, `RSASSA_PKCS1_V1_5_SHA_256` metadata를 확인하고 `MessageType=RAW`로만 서명한다. 반환 algorithm과 signature를 확인한 뒤 KMS 공개키로 결과를 다시 검증하며 실패는 `503 TOKEN_ISSUER_UNAVAILABLE`로 발급 transaction을 rollback한다.
- access는 `typ=at+jwt`, `alg=RS256`, non-empty `kid`와 환경 고정 issuer, 단일 audience, UUID `sub/sid/jti`, `iat=nbf`, `exp-iat=600`, `ver=1`을 가진다. 세 audience별 token은 서로 다른 `jti`를 사용하고 업무 RBAC/ACL을 포함하지 않는다.
- rotation key ring에는 `kid`, KMS key ID와 공개 기간만 두며 private/public key 원문을 설정이나 DB에 저장하지 않는다. `REGULAR`은 새 active key 5분 선게시와 직전 key 1시간 overlap을 시작 시 강제하고, 침해 대응의 즉시 제거만 명시적 `EMERGENCY` mode로 허용한다.
- 내부 JWKS는 active/overlap public key를 `RSA`/`sig`/`RS256` JWK로 제공하고 5분 public cache와 stable ETag/304를 지원한다. Auth API와 별도 BFF/Resource SPIFFE principal allowlist를 적용하며 public ingress 제외 계약을 유지한다.
- Core에 추가한 target validator는 JWKS를 최대 5분/ETag로 cache하고 unknown `kid`에서 정확히 한 번 강제 갱신한 뒤 fail closed한다. 서명, algorithm/type/kid, issuer, 정확히 하나의 Core audience, UUID/시간/profile claim을 검증하고 identity만 반환한다. 기존 HMAC issuer 요청 경로와의 dual validation 활성화는 T035이므로 T033에서 현재 Core 동작은 바꾸지 않았다.
- T033은 DB entity/relation을 만들지 않는다. key ring은 배포 설정, public key cache는 프로세스 메모리이므로 migration과 ERD 변경은 필요 없고 contract/data-model/plan/tasks만 영향에 맞게 갱신했다.
- T034는 Core V13에 nullable unique `users.auth_user_id UUID`와 canonical `user-{UUID}` backfill/check를 추가했다. 기존 문자열 User PK와 Space/Meeting FK는 바꾸지 않았고 legacy `JdbcAuthStore` 신규 가입은 같은 UUID projection을 함께 기록한다.
- Auth 이관 도구는 `src/migration` source set에 분리해 runtime bootJar에 넣지 않았다. source/target JDBC transaction을 별도로 열고 identity가 있는 User/AuthIdentity만 staging한 뒤 ID/projection/email/provider ownership을 검사한다. `APPLY`는 target transaction 안에서 멱등 upsert와 exact reconciliation을 수행하고 `VERIFY`는 쓰기 없이 불일치를 차단한다.
- legacy BCrypt hash와 Google provider subject는 보존하지만 refresh hash/AuthSession은 복사하지 않는다. 최종 delta 전 인증 쓰기 drain, `DRY_RUN→APPLY→VERIFY`, 강제 재로그인과 forward-only rollback 경계를 `auth-data-migration-runbook.md`에 기록했다.
- T034는 Browser/BFF/Auth API shape를 바꾸지 않으므로 contracts 변경은 없다. Core/Auth 물리 관계가 바뀌어 두 feature의 data model/ERD와 plan/tasks를 함께 갱신했다.
- Q-020/Q-021은 권장안으로 확정했다. Browser/Core `User.id`는 `user-{Auth UUID}`를 유지하고 Auth UUID는 JWT `sub`, BFF session principal index와 Core `users.auth_user_id`에만 사용한다. BFF는 target Auth 성공 직후 Core projection `204`를 받아야 session/Vault를 확정하며 실패 시 실제 AuthSession을 best-effort revoke한다.
- BFF 인증 client는 명시적 `auth-service|legacy` provider로 나눴다. target은 실제 `authSessionId`, Auth UUID와 고정 3개 audience access를 검증하고 provider 장애를 legacy로 자동 fallback하지 않는다. schema v1은 `meetingmind-legacy` 단일 access, v2는 Core/AI/LiveKit access·expiry map이며 refresh는 bundle 전체를 CAS 원자 교체한다.
- Spring Session Redis는 indexed repository를 사용하고 Auth UUID 문자열을 principal index로 저장한다. 기존 직렬화 session/Vault 문서는 추측 변환하지 않고 fail closed하며 T024가 같은 index를 logout-all에 사용한다.
- Core access resolver는 unverified header를 validator 선택에만 사용한다. exact legacy `HS256/JWT/no kid`와 target `RS256/at+jwt/kid`만 구분하고 선택한 validator 하나만 실행해 target 실패의 legacy downgrade를 차단한다. target `sub`는 Core UUID projection으로 기존 문자열 업무 User를 찾는다.
- Core internal projection은 BFF workload와 `meetingmind-core` target JWT를 함께 검증하고 `sub == authUserId`, `resourceUserId == "user-" + authUserId` ownership 아래 표시 정보를 멱등 upsert한다. local/test header는 명시적 비운영 profile에서만 허용한다.
- T036은 CI Trivy가 탐지한 BFF/Auth의 `jackson-databind 2.21.2` HIGH 2건과 `tomcat-embed-core 10.1.54` HIGH 3건/CRITICAL 3건을 이미 Backend에서 검증한 Jackson `2.21.4`, Tomcat `10.1.55` 전체 모듈 정렬로 해소했다. 새 라이브러리나 API/DB 계약은 추가하지 않았다.
- Gitleaks가 탐지한 10건은 모두 커밋된 고정 테스트 master key 또는 잘못된 key 길이 negative fixture였다. 경로·규칙 전체를 허용하지 않고 기존 `.gitleaksignore` 정책대로 commit/path/rule/line fingerprint만 등록해 이후 실제 secret 탐지를 유지했다.
- T037은 `origin/dev` 통합 뒤 Frontend `package.json`과 lockfile이 어긋나 `npm ci`가 요구한 `@emnapi/core@1.11.2`, `@emnapi/runtime@1.11.2` 항목만 기존 무결성 정보로 복원했다. 동시에 Backend/Auth의 PostgreSQL JDBC를 `42.7.11`에서 CVE 수정 버전 `42.7.12`로 올렸으며 API, 데이터 모델과 DB migration은 변경하지 않았다.
- T024 Auth 재인증은 현재 AuthSession/User 결합과 provider별 credential을 검증하고 Auth 서버 시각만 반환한다. local/Google 모두 새 User, identity, AuthSession, refresh/access를 만들지 않으며 실패 감사만 남긴다.
- BFF는 최근 인증이 없으면 `403 REAUTHENTICATION_REQUIRED`를 반환하고, 재인증 성공 시 Auth의 durable revoke-all을 먼저 호출한다. 이후 Auth UUID principal index의 다른 BffSession/Token Bundle을 정리하고 현재 request session을 마지막에 무효화하며 indexed 정리 실패는 `503`으로 성공을 위장하지 않는다.
- Frontend는 현재 세션 로그아웃과 별도로 확인 modal을 제공한다. 첫 logout-all이 최근 인증을 요구할 때만 비밀번호 또는 Google step-up을 수행하고 body 없는 logout-all을 정확히 한 번 재시도하며, `204` 뒤에만 전역 사용자 상태를 지운다.
- `reauthenticate`와 `logout_all`을 별도 저카디널리티 Browser metric operation으로 분류했고 사용자/session ID나 raw path를 label에 넣지 않는다.

## Verification

| Check | Result | Notes |
| --- | --- | --- |
| Documentation/terminology review | Pass | legacy/target 표기, 저장 역할, open gate 참조와 task ID 중복 없음 확인 |
| Canonical conflict scan | Pass | 요구사항에 FE `sessionStorage` 지시, 구형 cookie 미결정 문구, Auth target 오표기 없음 확인 |
| Trailing whitespace scan | Pass | 변경 대상 요구사항/Core 문서와 `specs/002-bff-auth-msa/**` 결과 없음 |
| `git diff --check` | Pass | tracked 문서 diff 오류 없음; 신규 문서는 별도 trailing whitespace scan 수행 |
| BFF test/package | Pass | `BFF_REDIS_INTEGRATION=true BFF_REDIS_PORT=6380 ./gradlew clean test bootJar`; health/probe, Redis session/vault, T011~T013 security/auth tests, 31 tests/0 skipped/0 failures, 독립 bootJar 생성 |
| Runtime probe smoke | Pass | 격리 포트 `18081`에서 `/actuator/health/liveness`, `/actuator/health/readiness` 모두 `UP`; readiness는 실제 local Redis 포함 |
| T011 CSRF/security negative tests | Pass | 익명 보호 자원 `401`/redirect 없음, 인증된 상태 변경 요청의 CSRF 누락 `403`, CSRF 포함 요청 `204`, 인증 후 session ID 교체 확인 |
| Production cookie runtime smoke | Pass | `prod` profile/격리 포트 `18082`의 CSRF 응답에서 `__Host-mm-session; Path=/; Secure; HttpOnly; SameSite=Strict`와 Domain 없음 확인 |
| T012 plaintext persistence scan | Pass | 실제 Redis raw JSON에서 rotation 전후 access/refresh token 4개 문자열 모두 0건, read/삭제 정상 동작 확인 |
| T012 crypto/fail-closed tests | Pass | KMS `AES_256` data key/encryption context, local key 필수값, AAD version 위변조, 암호화 실패와 CAS 경쟁 시 기존 bundle 보존 확인 |
| T013 Browser→BFF→Backend stub E2E | Pass | 실제 Redis에서 CSRF session→login→session ID 교체→Remember me cookie→bootstrap 복원, Browser token 0건 확인 |
| T013 Redis boundary inspection | Pass | BffSession attribute는 user/authSession/tokenBundle/expiry 참조만 포함하고 Vault raw JSON은 access/refresh 평문 0건 확인 |
| T013 auth negative tests | Pass | CSRF 누락 `403`, invalid request `400`, Google 오류 정규화, absolute expiry 보호 요청 `401`, Backend/Browser request field 분리 확인 |
| T014 Token Manager unit tests | Pass | access 선제 refresh, 동시 요청 1회 refresh와 회전 bundle 공유, downstream 호출 최대 2회, refresh/최종 401의 revoke·Vault·session cleanup 확인 |
| T014 Redis single-flight integration | Pass | 실제 Redis에서 단일 owner만 lock 획득, 다른 owner의 release 거부, 정상 owner release 후 다음 owner 획득 확인 |
| T014 compatibility refresh contract | Pass | 고정 `/api/v1/auth/refresh`와 refresh-only body, token 응답 validation 및 Browser 무노출 경계 확인 |
| T015 route/header security | Pass | 실제 Core/AI/LiveKit route 분류, unknown/auth/non-UUID/encoded/matrix path와 미등록 method 선차단, Browser Authorization/cookie/CSRF 미전달 확인 |
| T015 timeout/circuit/bulkhead | Pass | 강제 read 지연 timeout, queue 없는 동시성 거부, 연속 실패 open, 단일 half-open 복구와 늦은 성공의 circuit 유지 확인 |
| T015 error isolation | Pass | Core/AI/LiveKit 5xx·guard 실패가 서로 다른 503 code와 raw provider detail 없는 common error shape로 정규화됨 |
| T016 Redis Browser contract E2E | Pass | token response/storage/log 0건, public refresh 거부, 선제 refresh, CSRF login/logout negative, logout cookie/session/vault 삭제와 멱등성 확인 |
| T016 actual Backend compatibility E2E | Pass | 실제 Backend `test` 프로필+BFF+Redis 별도 프로세스에서 signup, 강제 선제 refresh, Core proxy, logout, stale cookie `401`과 token/log scan 통과 |
| T010-T020 full BFF regression | Pass | 실제 Redis 통합과 T020 route ID 호환 보정을 포함한 61 tests, 0 skipped, 0 failures와 bootJar 생성 |
| T020 Frontend unit tests | Pass | Vitest 4 files, 17 tests; session bootstrap/tokenless 응답, CSRF cache/reset과 same-origin API 요청 검증 |
| T020 Frontend lint/build | Pass | ESLint 0 errors/기존 warnings 9건, TypeScript+Vite production build 성공; 기존 bundle size warning만 존재 |
| T020 Browser E2E | Pass | 격리 포트 Backend+BFF+Redis+Frontend에서 Playwright 6/6 통과; UI login, reload session 복원, browser token 무저장, 회의 CRUD/ACL 검증 |
| T021 Frontend unit tests | Pass | Vitest 4 files, 19 tests; CSRF logout `204` 반복 호출과 network failure 정규화 검증 |
| T021 Browser E2E | Pass | 격리 포트 Backend+BFF+Redis+Frontend에서 Playwright 8/8 통과; logout `204`, 인증 UI 제거, 이전 session 재사용 차단과 network failure 세션 유지·오류 UX 검증 |
| T022 Frontend unit tests | Pass | Vitest 4 files, 20 tests; `INVALID_CREDENTIALS` 무시, `SESSION_INVALID` 1회 발행과 원본 response body 보존 검증 |
| T022 Browser E2E | Pass | 격리 포트 Backend+BFF+Redis+Frontend에서 Playwright 9/9 통과; 최종 401 1회 전환, stale 인증 UI 제거, 만료 안내와 `/spaces` 재로그인 복귀 검증 |
| T023 rollout readiness | Pass | `BFF_ACCEPT_BROWSER_TRAFFIC=false`에서 liveness는 `UP`/200을 유지하고 readiness만 `DOWN`/503이 되는 자동 테스트 통과 |
| T023 observability tests | Pass | login/refresh/logout/session/proxy 고정 operation/outcome, raw path ID label 부재, 실제 refresh owner 성공/실패와 정확한 `SESSION_INVALID` counter 검증 |
| T023 full BFF regression | Pass | 실제 Redis에서 `BFF_REDIS_INTEGRATION=true BFF_REDIS_PORT=6380 ./gradlew clean test bootJar`; 64 tests, 0 skipped, 0 failures와 bootJar 생성 |
| T023 Frontend regression | Pass | Vitest 4 files/20 tests, ESLint 0 errors/기존 warnings 9건, production build 성공; 기존 bundle size warning만 존재 |
| T023 Browser regression | Pass | 격리 포트 Backend+BFF+Redis+Frontend에서 Playwright 9/9 통과 |
| T030 decision closure | Pass | Q-009/Q-010/Q-015/Q-016 `Decided`; AuthSession family revoke, RS256/JWKS, `sid` event, mTLS/SPIFFE와 최근 10분 재인증 정책 확인 |
| T030 contract examples | Pass | Auth internal/event/Browser 계약의 모든 JSON code block parse 성공, Markdown code fence 균형 확인 |
| T030 contract/data/ERD consistency | Pass | audience별 access expiry/scopes, Token Bundle schema v2, `refreshFamilyId`, credential lineage와 `AUTH_OUTBOX_EVENT` 필드 일치 및 stale open/provisional 표현 0건 |
| T030 requirements consistency | Pass | 정책 ID 17개 중복 없음, FR/NFR/status/glossary와 spec/plan/tasks 계약값 일치, requirements INDEX routing 변경 불필요 확인 |
| T030 runtime tests | Not run | shared contract/requirements/data model 문서만 변경했고 현재 Backend/BFF runtime과 migration은 수정하지 않음; 구현 검증은 T031~T035에서 수행 |
| T031 Auth unit/package | Pass | `./gradlew clean test bootJar`; test profile context와 health 계약 3 tests, 0 skipped/0 failures, 독립 bootJar 생성 |
| T031 PostgreSQL migration/privileges | Pass | 실제 PostgreSQL에서 기존 V1 checksum을 보존한 V2 적용, 6개 table·Flyway 1/2 이력, 허용 DML과 DELETE/DDL/history 거부 검증 |
| T031 Compose runtime probes | Pass | Auth DB 포함 liveness/readiness `UP`, 미구현 login `404`; DB 중단 시 liveness `UP`/200과 readiness `DOWN`/503 확인 |
| T031 Docker image | Pass | `meetingmind-auth:t031` build 성공, runtime user `meetingmind` 확인 |
| T031 Compose/CI config | Pass | `docker compose --profile auth -f compose.local.yml config --quiet`, init script shell 문법과 Ruby CI YAML parse/auth job wiring 확인 |
| T031 current services regression | Not run | T031은 신규 `auth` 경계와 root Compose/CI/docs만 변경했고 기존 Backend/BFF/Frontend runtime code를 수정하지 않음 |
| T032 Auth full test/package | Pass | 실제 PostgreSQL 환경에서 `./gradlew clean test bootJar`; 8 tests, 0 skipped/0 failures, 독립 bootJar 생성 |
| T032 local/Google credentials | Pass | BCrypt 저장, 중복/불일치 고정 code, actual RSA/JWKS signature·issuer·audience·expiry·verified-email negative와 unknown `kid` refresh 검증 |
| T032 refresh family | Pass | 유효 refresh 1회 회전, 이전 token `409 REFRESH_REUSE_DETECTED`, 새 leaf 후속 `401`, 해당 session `REFRESH_REUSE`와 다른 session의 후속 `CURRENT_LOGOUT` 확인 |
| T032 revoke/outbox | Pass | current revoke 2회와 revoke-all 2회 `204`, owner mismatch `403`, stale auth `401`, 사용자 active session 0건과 revoked session별 outbox 정확히 1건 확인 |
| T032 secret persistence | Pass | local password는 BCrypt, refresh는 `hmac_sha256$`만 저장되고 raw refresh/Google credential/email이 hash/audit/outbox payload에 0건임을 실제 DB에서 확인 |
| T032 signer fail closed | Pass | test signer 없는 실제 application/Compose에서 signup `503 TOKEN_ISSUER_UNAVAILABLE`, 해당 email DB row 0건과 runtime jar test signer 0건 확인 |
| T032 workload boundary | Pass | principal 없음 `401`, 미허용 SPIFFE `403`, direct X509 SPIFFE URI SAN 허용, prod profile test header 강제 거부 확인 |
| T032 Docker/Compose/CI | Pass | `meetingmind-auth:t032` 및 Compose image build, non-root `meetingmind`, liveness/readiness `UP`, CI PostgreSQL runtime test wiring과 Compose/YAML 문법 확인 |
| T032 current services regression | Not run | 신규 Auth runtime과 Auth/root docs/Compose/CI만 변경했고 기존 Backend/BFF/Frontend runtime code는 수정하지 않음 |
| T033 Auth unit/package | Pass | `./gradlew test bootJar`; 전체 16 tests 중 PostgreSQL 환경 gate 1건만 skipped, 0 failures/errors와 독립 bootJar 생성 |
| T033 KMS/JWT profile | Pass | KMS `RAW` RS256 request/response algorithm, RSA-2048 `SIGN_VERIFY` metadata, 반환 signature 재검증과 audience별 필수 header/claim/고유 `jti` 확인 |
| T033 rotation/JWKS | Pass | `REGULAR` 5분 선게시·1시간 overlap 거부/허용, 명시적 `EMERGENCY`, active+old JWK, stable ETag/304, 300초 cache와 별도 Resource workload allowlist 확인 |
| T033 Resource validator | Pass | Backend 전체 `./gradlew test`; 118 tests 중 기존 환경 gate 7건 skipped, 0 failures/errors. ETag/300초 HTTP fetch, RS256/issuer/audience/expiry/필수 claim negative, unknown `kid` 1회 갱신, old/new overlap과 미발견 fail-closed 확인 |
| T033 runtime artifact | Pass | bootJar에 KMS adapter/JWKS와 AWS SDK KMS가 포함되고 test signer/config 0건, `meetingmind-auth:t033` image build와 non-root `meetingmind` user 확인 |
| T033 PostgreSQL/ERD | Not run | DB schema/SQL/entity 관계를 변경하지 않았고 T032 실제 PostgreSQL 회귀가 signer port 경계를 이미 검증함; migration/ERD 변경 불필요 |
| T034 Backend tests | Pass | `backend ./gradlew test`; 전체 단위 테스트 통과 |
| T034 Core PostgreSQL migration | Pass | 격리 pgvector PostgreSQL에서 V1→V10, 기존 데이터 삽입, V11→V13 upgrade와 Flyway 1~13 이력, canonical projection backfill·비정형 ID null 유지 검증 |
| T034 Auth tests | Pass | `auth ./gradlew test`; migration source set compile과 기존 Auth 단위 테스트 전체 통과 |
| T034 Auth PostgreSQL migration | Pass | 격리 PostgreSQL에서 실제 Auth runtime 회귀와 별도 source schema의 dry-run/apply/verify, 동일 snapshot 재실행, local/Google identity exact 대사, projection mismatch fail-closed 검증 |
| T034 runtime artifact | Pass | `./gradlew bootJar` 성공, Auth bootJar의 `com/meetingmind/auth/migration` class 0건 확인 |
| T034 diff validation | Pass | `git diff --check` 통과 |
| T035 BFF full regression/package | Pass | `bff ./gradlew test`, `bootJar`; target Auth/Core client, 실제 AuthSession·external/internal ID, audience 선택/원자 refresh, projection 실패 revoke와 legacy schema 회귀 통과 |
| T035 Core full regression/package | Pass | `backend ./gradlew test`, `bootJar`; deterministic dual-mode/downgrade negative, UUID User lookup, projection subject/ID/workload 검증과 기존 Core 회귀 통과 |
| T035 actual Redis | Pass | 실제 Redis에서 indexed Spring Session의 Auth UUID→BffSession 조회/로그아웃 삭제, ciphertext 무원문, refresh lock/CAS와 복수 context session 공유 통과 |
| T035 actual PostgreSQL | Pass | 실제 Core PostgreSQL에서 `auth_user_id` 조회, 신규 projection insert·표시 정보 멱등 update와 resource/Auth ownership conflict 차단 통과 |
| T035 explicit legacy rollback E2E | Pass | 격리 포트 `28080/28081` 실제 Backend+BFF+Redis에서 `BFF_AUTH_PROVIDER=legacy` signup→refresh→Core proxy→logout→stale cookie 차단과 token/log 무노출 통과 |
| T035 Auth regression/package | Pass | `auth ./gradlew test bootJar`; 기존 AuthSession/refresh/revoke/KMS/JWKS runtime 회귀와 독립 artifact 생성 통과 |
| T036 BFF/Auth tests | Pass | BFF와 Auth에서 각각 `./gradlew test`; 두 서비스 모두 `BUILD SUCCESSFUL` |
| T036 container builds | Pass | `docker build --tag meetingmind-bff:ci bff`, `docker build --tag meetingmind-auth:ci auth` 성공 |
| T036 Trivy image scan | Pass | Trivy `0.72.0`, `--ignore-unfixed --scanners vuln --severity HIGH,CRITICAL`; BFF/Auth 모두 Alpine 0건, JAR 0건 |
| T036 repository secret scan | Pass | Gitleaks `8.30.1`이 현재 브랜치 HEAD 전체 이력 57 commits/약 3.44 MB를 검사해 `no leaks found` |
| T036 diff validation | Pass | `git diff --check` 통과 |
| T037 Frontend dependency/install | Pass | 누락 peer lock 2건만 복원한 뒤 CI와 동일한 `npm ci` 성공 |
| T037 Frontend regression | Pass | ESLint 0 errors/기존 warnings 7건, Vitest 4 files/32 tests, TypeScript+Vite production build 성공; 기존 bundle size warning만 존재 |
| T037 Backend/Auth regression | Pass | Backend와 Auth에서 각각 `./gradlew test`; 두 서비스 모두 `BUILD SUCCESSFUL` |
| T037 container security | Pass | Backend/Auth 이미지 build 성공, Trivy `--ignore-unfixed --scanners vuln --severity HIGH,CRITICAL`에서 두 이미지 모두 Alpine 0건/JAR 0건 |
| T024 Auth PostgreSQL integration | Pass | 실제 PostgreSQL에서 malformed/subject mismatch/wrong local·Google credential을 무변경 거부하고 local/Google 성공 시 새 AuthSession/token 없이 Auth 서버 시각만 갱신한 뒤 전체 revoke/outbox를 검증 |
| T024 BFF Redis integration | Pass | 실제 Redis에 동일 Auth UUID의 BffSession 2개를 만들고 stale logout-all `403`→재인증→`204`, 두 cookie 후속 보호 요청 `401`, principal index와 Token Vault 0건을 검증 |
| T024 BFF full regression/package | Pass | `./gradlew test bootJar`; 82 tests, 6 environment-gated skipped, 0 failures/errors와 독립 bootJar 생성 |
| T024 Frontend regression | Pass | Vitest 4 files/22 tests, ESLint 0 errors/기존 warnings 9건, TypeScript+Vite production build 성공; 기존 bundle size warning만 존재 |
| T024 Browser E2E | Pass | 격리 포트 Backend+BFF+Redis+Frontend에서 Playwright 10/10 통과; 확인 modal, step-up proof 최소 body, logout-all 정확히 1회 재시도와 인증 UI 정리 검증 |
| T024 Auth regression/package | Pass | `./gradlew test bootJar`; 16 tests 중 PostgreSQL environment gate 1건 skipped, 0 failures/errors와 독립 bootJar 생성 |
| `docker build --tag meetingmind-bff:t013 bff` | Pass | validation/compatibility client를 포함한 non-root Java 21 runtime image 생성 |
| `docker build --tag meetingmind-bff:t012 bff` | Pass | AWS SDK/KMS adapter를 포함한 non-root Java 21 runtime image 생성 |
| `docker build --tag meetingmind-bff:t010 bff` | Pass | non-root Java 21 runtime image 생성 |
| `docker compose -f compose.local.yml config --quiet` | Pass | 로컬 Redis/BFF profile Compose 문법 검증 |
| CI YAML parse | Pass | Ruby YAML parser로 `.github/workflows/ci.yml` 문법 검증 |
| Backend unit tests | Not run | Backend code/schema는 변경하지 않았고 실제 Backend bootJar/호환 E2E를 실행함 |
| AI compile | Not run | T016에서 AI code를 변경하지 않음 |

첫 Redis 통합 테스트는 test JVM의 default property보다 `application.yml`의 `8081`이 우선되어 두 번째 context가 port 충돌로 실패했다. 두 context의 port/Redis/namespace를 command-line property로 고정해 실제 랜덤 포트와 동일 namespace를 사용하도록 수정한 뒤 clean test가 통과했다.

T011의 첫 security test는 CSRF token 생성이 Redis-backed session을 사용해 단위 테스트가 외부 Redis에 의존했다. security 단위 테스트에 test-only `MapSessionRepository`를 주입해 외부 의존성을 제거하고, 실제 Redis 연동은 기존 통합 테스트에서 별도로 검증하도록 경계를 나눈 뒤 전체 clean test가 통과했다.

T012 구현 중 첫 compile은 Jackson의 byte-array `readValue`가 `JsonProcessingException`보다 넓은 `IOException`을 선언해 실패했다. 복호화 payload parse 경계에서 `IOException`을 고정 `CRYPTO_FAILURE`로 변환하도록 수정한 뒤 단위/Redis 통합/bootJar와 Docker build가 모두 통과했다.

T013의 첫 웹 계약 테스트는 test profile에서도 Spring Session Redis filter가 선택되어 외부 Redis 없이 실행할 단위 테스트 1건이 실패했다. security 단위 테스트와 동일하게 test-only `MapSessionRepository`를 주입해 격리한 뒤 Redis 의존 검증은 별도 integration test로 유지했다. 이어 public signup의 CSRF 누락 기대값을 실제 Spring Security 계약인 `403`으로 바로잡은 뒤 전체 테스트가 통과했다.

T016 최종 E2E 재실행에서 기본 포트 `18080`을 기존 개발 서버가 사용 중인데도 readiness가 그 서버를 새 child process로 오인해 signup `409`가 발생했다. 실행 전 두 포트 점유를 거부하고 매 polling 전에 시작한 PID 생존을 확인하도록 스크립트를 수정했다. 기존 서버는 종료하거나 변경하지 않았고 격리 포트 `28080/28081`에서 재실행해 전체 흐름과 child process 정리를 확인했다.

T020 첫 전체 Playwright는 Vite의 생성된 로컬 config가 이전 Backend proxy 값을 사용해 BFF login을 우회했다. TypeScript config를 다시 build해 로컬 산출물을 갱신한 뒤 BFF login 경계를 확인했다. 다음 실행에서는 BFF allowlist가 Backend의 `meeting-*`, `space-*` ID를 bare UUID로만 허용해 `ROUTE_NOT_ALLOWED`가 발생했다. 실제 Backend ID 계약에 맞춰 T015 계약·registry·단위 테스트를 엔티티별 prefix+UUID로 보정한 뒤 Playwright 6건이 모두 통과했다.

T023 지표 초안은 보호 API의 모든 `401`을 session invalid로 셀 수 있었다. 요청 filter의 일반 보호 `401`은 `unauthenticated` outcome으로만 분류하고, BFF 예외 code가 정확히 `SESSION_INVALID`일 때만 전용 counter를 증가시키도록 분리해 인증 probe나 다른 `401`이 rollout guardrail을 왜곡하지 않게 한 뒤 전체 회귀를 실행했다.

T031 최초 migration 검토에서 runtime role에 `DELETE`와 future table 기본 DML이 남아 완료 조건보다 권한이 넓음을 발견했다. 이미 실제 DB에 적용된 V1을 고치면 Flyway checksum과 forward-only 원칙을 깨므로 V1 원문을 복원하고 V2 privilege tightening migration을 추가했다. runtime role 이름도 migration과 설정이 달라질 수 없도록 canonical `meetingmind_auth_app`으로 고정한 뒤 기존 V1 DB의 upgrade와 새 application 기동을 모두 재검증했다.

T032 첫 Google 연결 통합 테스트는 `pg_advisory_xact_lock`의 PostgreSQL `void` 반환값을 `Long`으로 읽어 SQLSTATE `22003`이 발생했다. 잠금 결과를 값으로 변환하지 않고 실행 완료만 확인하도록 repository callback을 수정한 뒤 동일 verified email의 local User에 Google AuthIdentity가 연결되고 전체 회귀가 통과했다.

Refresh rotation은 기존 V1의 `usedAt/replacementId` check, replacement FK와 active leaf partial unique 때문에 두 개의 순차 SQL로는 중간 상태가 제약을 위반한다. 적용 migration을 완화하지 않고 data-modifying CTE 한 statement로 이전 leaf update와 replacement insert를 함께 수행해 최종 invariant와 forward-only 기준을 보존했다.

## BFF Workspace Route Expansion

- 2026-07-21: Core의 Workspace API 확장에 맞춰 BFF route allowlist를 갱신했다. Space detail/update/delete, invitation, DomainTerm, TaskCard, ProjectKnowledge, calendar/dashboard, report history/download, term explanation, Project AI history와 candidate dismissal을 entity prefix+UUID 검증으로 허용한다. `ProxyRouteRegistryTest` 전체 회귀로 unknown route/method 차단과 Core/AI downstream 분류를 확인했다.

## Open Implementation Gates

- 2026-07-21: T039 NonProd network design을 시작했다. 서울 리전(`ap-northeast-2`), VPC `10.20.0.0/16`, 2개 AZ의 Public `/24`, Private app `/20`, Data `/24` subnet 기준을 `infra/aws/nonprod/network/**`와 `adr/001-nonprod-vpc.md`에 기록했다. 사용자가 예산은 필요하면 늘릴 수 있으므로 비용을 이유로 계획을 임의 변경하지 말라고 정정해, 비용/Free Tier 문구를 계획 변경 요인이 아니라 별도 운영 모니터링 맥락으로 분리했다. 첨부된 AWS foundation 진행 현황은 `infra/aws/foundation-status.md`에 정리했다. 2026-07-23 공식 Terraform 1.6.6 Docker 이미지에서 `fmt -check -recursive`와 AWS provider 초기화 후 `validate`가 통과했다. 실제 AWS `plan/apply`와 콘솔 검증은 실행하지 않았다.
- 2026-07-23: NonProd 배포 플랫폼을 EKS에서 ECS Fargate로 변경했다. `adr/002-ecs-fargate.md`가 기존 EKS 결정을 대체하며, 사용자가 완료로 확정한 ECR/lifecycle, 단일 ECS cluster, service-linked role, 공통 execution role, 서비스별 task role/SG/7일 Log Group, NAT/private route 상태를 T041과 `infra/aws/foundation-status.md`에 기록했다. 이 변경은 문서만 갱신했으며 AWS 리소스와 애플리케이션 코드는 변경하지 않았다. API endpoint/payload와 데이터 모델은 영향이 없어 유지했고 contract 파일은 배포 플랫폼 annotation만 ECS Security Group/ALB 경계로 갱신했다. `analyze.md`는 당시 검증 결과를 보존하는 읽기 전용 역사 기록이므로 수정하지 않았다.
- Q-013 SLO/RTO/RPO와 Q-024 정식 edge 보안.
- Q-023의 Cloud Map source와 staged runtime/digest gate는 T047-A에서 구현했다. direct mTLS 인증서 발급·delivery/rotation, 애플리케이션 TLS와 principal negative 검증은 T047-B~D 후속 작업으로 남아 있으며 identity/event 의미는 T030 계약을 유지한다.
- Auth outbox transport publisher/consumer, Phase 1 revoke 실패의 암호화된 durable retry queue와 운영 경보는 T045 출시 gate다.
- T035에서 T033 Resource validator를 실제 Core 요청 경로의 `DUAL` resolver에 연결했다. legacy issuer 종료는 7일 관측 뒤 Core `TARGET_ONLY` 전환과 별도 운영 승인이 필요하다.
## T044 진행 기록

- STT에 `spring-boot-starter-actuator`를 추가하고 `/actuator/health/liveness`, `/actuator/health/readiness` probe를 구성했다.
- liveness는 `livenessState`만 사용하고 readiness는 `readinessState,db`를 사용해 DB 장애 시 readiness만 실패하도록 했다.
- 로컬 `./gradlew test bootJar`는 승인된 실행으로 성공했다.

## T054 NonProd V2 Terraform 계획

- 기존 수작업 NonProd를 import하지 않고 같은 계정의 새 `nonprod-v2` 환경을 병렬 구축하기로 한 사용자 결정을 반영했다.
- `infra/aws/nonprod-v2/implementation-plan.md`에 Terraform source 구조, state bootstrap, VPC/Regional NAT/NACL/route/SG/VPC endpoint, KMS/IAM/ECR/CloudWatch, RDS/Valkey/Secrets Manager, ALB/ECS 구현 계약과 단계별 완료 기준을 기록했다.
- 기존 RDS 데이터는 이관하지 않고 빈 PostgreSQL 16에서 시작한다. RDS master는 RDS-managed secret, Valkey는 IAM authentication을 사용한다. Terraform은 그 밖의 secret container와 IAM만 관리하고 실제 application/provider 값은 사용자가 AWS Console에서 입력해 state에 장기 secret 원문을 넣지 않는다.
- 도메인 발급 전 ALB DNS는 제한된 smoke test에만 사용한다. `Secure` cookie, Route 53/ACM/CloudFront/WAF와 LiveKit Egress WSS end-to-end는 도메인 발급 후 T059 출시 gate로 분리했다.
- Q-013 SLO/RTO/RPO와 Q-024 정식 edge 보안은 기반 Terraform 구현과 분리된 blocking decision으로 유지한다. Q-023은 2026-07-24 사용자 결정으로 해소됐고 T047 구현 gate로 이동했다.
- 기존 T044 actuator 변경과 검증 기록은 수정하거나 되돌리지 않았다. 이번 작업은 문서만 변경했으며 Terraform 코드 작성, AWS `plan/apply`, AWS 리소스 변경과 애플리케이션 테스트는 수행하지 않았다.

### T054 검증

| Check | Result | Notes |
| --- | --- | --- |
| `git diff --check` | Pass | tracked 문서 변경에 whitespace 오류 없음 |
| 오래된 IaC 결정 scan | Pass | superseded network/STT/tag 결정 표현을 V2 병렬 구축 기준으로 정리함 |
| Task table shape | Pass | T039 이후 task row의 열 수가 표 계약과 일치함 |
| Terraform/AWS validation | Not run | 계획 문서 단계이며 Terraform 코드와 AWS 리소스를 생성하지 않음 |

## T055~T057 NonProd V2 Terraform 코드

- Terraform 1.15.8과 AWS Provider 6.56.0 기준으로 `infra/aws/bootstrap/state`, 재사용 모듈과 `infra/aws/environments/nonprod-v2` root를 구현했다.
- State bootstrap은 versioning/SSE-KMS/Block Public Access/TLS-only S3 bucket policy와 native S3 lockfile backend 값을 출력하며 bucket과 KMS key에 `prevent_destroy`를 적용한다.
- Network는 `10.20.0.0/16`, 2개 AZ Public/Private/Data subnet, IGW, Regional NAT Gateway automatic mode, route table, 명시적 기본 NACL, VPC Flow Logs와 S3/interface endpoint를 구성한다.
- Security/KMS/IAM/ECR은 서비스별 SG, application/data/log/JWT key, execution/task role, immutable KMS-encrypted ECR와 선택적 GitHub OIDC role을 구성한다.
- Data는 빈 PostgreSQL 16 Single-AZ RDS의 RDS-managed master secret과 Valkey 7.2 primary+replica Multi-AZ/IAM authentication을 구성한다. Terraform은 application/provider secret container만 만들고 version은 만들지 않는다.
- ALB/ECS/CloudWatch는 public ALB와 BFF/STT target group, Fargate cluster/task definition/service module, 7일 encrypted Log Group, dashboard와 alarm을 구성한다.
- `enable_runtime_services=false`를 기본값으로 두고 Q-013/Q-023, secret version, DB bootstrap, image push와 BFF Valkey IAM client가 준비되기 전 ECS Service 생성을 차단했다. HTTP smoke listener도 제한 CIDR이 없으면 plan이 실패한다.
- `.gitignore`에 Terraform working directory, state, 실제 tfvars와 saved plan을 추가했고 두 root의 `.terraform.lock.hcl`은 추적 대상으로 유지했다.

### T055~T057 검증

| Check | Result | Notes |
| --- | --- | --- |
| Terraform format | Pass | `terraform fmt -check -recursive infra/aws` |
| State bootstrap validate | Pass | Terraform 1.15.8, AWS Provider 6.56.0 |
| NonProd V2 validate | Pass | 전체 local module과 environment root schema 검증 |
| Mock foundation plan | Pass | `terraform test`; 기본 runtime off, HTTP CIDR gate, runtime acknowledgement 3/3 |
| State bootstrap AWS plan | Pass | `meetingmind-nonprod` profile, `9 add / 0 change / 0 destroy`; saved plan은 `/private/tmp` |
| Terraform source secret scan | Pass | `gitleaks dir --no-banner --redact infra/aws`; 165.73 KB, no leaks found |
| NonProd V2 AWS plan | Not run | remote state bootstrap을 아직 apply하지 않아 S3 backend가 존재하지 않음 |
| AWS apply | Not run | 사용자가 코드 구현을 요청했으며 resource 생성 승인은 별도 단계 |
| Application tests | Not run | 이번 변경은 Terraform/docs/.gitignore이며 기존 STT application 변경을 수정하지 않음 |

## T047-A/E Internal Discovery와 Source Least Privilege

- Cloud Map Private DNS namespace `meetingmind.internal`와 `auth`, `core`, `ai`, `stt` A record service를 foundation에 추가했다. BFF는 public ALB 경계를 유지하므로 내부 service registry에 등록하지 않는다.
- ECS Service는 Cloud Map registry ARN을 선택적으로 받아 private task IP를 등록한다. 내부 endpoint는 `https://auth.meetingmind.internal:8082`, `https://core.meetingmind.internal:8080`, `https://ai.meetingmind.internal:8000`, `https://stt.meetingmind.internal:8083`의 exact map만 허용한다.
- 전역 kill switch 외에 `runtime_enabled_services`, 서비스별 SHA-256 image digest, desired count와 `internal_mtls_ready`를 별도 gate로 추가했다. 따라서 기본 foundation plan은 runtime off이고, mTLS 애플리케이션 경계가 검증되기 전에는 acknowledgement와 digest가 있어도 ECS Service를 만들 수 없다.
- 공통 execution role을 서비스별 execution role로 분리하고 각 역할이 자신의 secret ARN만 읽도록 IAM policy를 좁혔다. Security Group egress는 내부 caller→callee port, DB/cache, VPC endpoint HTTPS로 명시했고 외부 provider HTTPS만 동적 IP 특성 때문에 NonProd 임시 예외로 남겼다.
- SNS alarm topic은 customer-managed Logs KMS key를 사용하도록 바꾸고 SNS service principal에 SourceAccount/SourceArn 조건을 둔 최소 KMS grant를 추가했다.
- T047-A는 source 검증 완료로 닫았다. T047-E의 실제 AWS principal 교차 거부와 reachability 검증은 apply 뒤 수행해야 하므로 완료로 표시하지 않았다.

### T047-A/E 검증

| Check | Result | Notes |
| --- | --- | --- |
| Terraform format | Pass | `terraform fmt -recursive infra/aws` 후 변경 없음 |
| State bootstrap validate | Pass | Terraform provider schema 검증 |
| NonProd V2 validate | Pass | Cloud Map, ECS registry, service별 IAM과 runtime gate 전체 schema 검증 |
| Mock Terraform tests | Pass | `terraform test`; foundation/HTTP CIDR/acknowledgement/global switch/digest/staged Auth/mTLS gate 7/7 |
| IaC HIGH/CRITICAL scan | Pass with reviewed exceptions | `trivy config --skip-check-update --severity HIGH,CRITICAL --exit-code 1 infra/aws/modules`; 탐지 0, public ALB 1건과 외부 provider TCP 443 4건은 2026-10-31 만료 inline 예외 |
| Terraform source secret scan | Pass | `gitleaks dir --no-banner --redact infra/aws`; 203.63 KB, no leaks found |
| `git diff --check` | Pass | whitespace 오류 없음 |
| AWS plan/apply와 runtime negative test | Not run | remote backend/apply 승인과 T047-B~D mTLS 구현이 선행돼야 함 |
| Application tests | Not run | 이번 슬라이스는 Terraform/docs만 변경했고 애플리케이션 runtime은 수정하지 않음 |

## T047-C1 Java mTLS Runtime과 Core Health

- BFF/Auth/Core/STT에 `/run/meetingmind/tls/tls.crt`, `tls.key`, `ca.crt`를 읽는 `meetingmind-internal` PEM SSL bundle과 opt-in `mtls` profile을 추가했다. 로컬·test 기본 profile은 기존 HTTP 동작을 유지한다.
- BFF의 Auth login/refresh/revoke와 Core projection/downstream client, Core의 Auth JWKS/AI/STT client는 같은 internal SSL bundle로 client certificate와 CA trust를 적용한다. JDK HTTPS hostname verification은 비활성화하지 않았다.
- Auth/Core/STT workload filter는 Tomcat이 검증한 client certificate의 URI SAN에서 정확히 하나의 SPIFFE principal만 읽는다. 누락, allowlist 불일치와 둘 이상의 SPIFFE URI SAN은 fail closed하고 production profile은 test principal header를 무시한다.
- Core→STT 호출에 남아 있던 `X-MeetingMind-Service-Token` 전송과 `core/stt-internal-token` Terraform secret/IAM 참조를 제거했다. AI는 Q-029가 열려 있어 shared token을 유지하고 T047-C2에서 제거한다.
- Core에 이미 다른 Java 서비스가 사용하는 Spring Boot Actuator를 추가했다. liveness는 process 상태만, `db` profile readiness는 DB를 포함하며 ECS task definition source에 Core liveness health check를 연결했다.
- Uvicorn은 TLS client certificate 요구 옵션은 제공하지만 ASGI TLS extension으로 certificate chain을 application에 전달하지 않는다. 검증되지 않은 forwarded header를 추가하지 않고 AI termination/runtime 선택을 Q-029로 기록했다.

### T047-C1 검증

| Check | Result | Notes |
| --- | --- | --- |
| BFF full tests | Pass | `./gradlew test`; SSL bundle client factory와 기존 auth/session/proxy 전체 회귀 |
| Auth full tests | Pass | `./gradlew test`; 단일 허용 SPIFFE URI SAN 수락과 ambiguous identity 거부 포함 |
| Core full tests | Pass | `./gradlew test`; internal SSL client factory, Core principal, liveness/readiness와 STT token 제거 포함 |
| STT full tests | Pass | `./gradlew test`; Core certificate principal 수락, wrong principal와 production test header 거부 포함 |
| Terraform format/validate | Pass | `terraform fmt -recursive infra/aws`, NonProd V2 `terraform validate` |
| Terraform mock tests | Pass | runtime gate/Cloud Map/IAM/ECS source 7/7 |
| Changed source secret scan | Pass with broader baseline pending | Auth/Core/STT/infra와 이번에 변경한 BFF 파일은 no leaks; 변경 범위 밖 `bff/src` finding 8건은 별도 baseline triage 필요 |
| `git diff --check` | Pass | whitespace 오류 없음 |
| 실제 TLS handshake와 AWS runtime | Not run | CA/certificate file delivery와 Q-029/T047-B가 선행돼야 함 |
| Core DB runtime privilege test | Not run | NonProd V2 DB bootstrap/apply와 runtime/migrator credential version이 아직 없음 |

## T047-B/C2 Offline PKI, cert-loader와 AI Envoy 계획

- Q-029를 AI Task의 Envoy sidecar로 결정했다. Envoy가 external XFCC를 sanitize하고 exact Core SPIFFE URI를 TLS/RBAC로 검증한 뒤 loopback Uvicorn에 verified URI만 전달하며 FastAPI가 exact 단일 URI를 재검증한다.
- AWS Private CA 대신 repository 밖 암호화 저장소의 offline NonProd root/intermediate CA를 사용한다. Terraform은 CA/private key/leaf secret version을 만들거나 읽지 않고 서비스별 TLS bundle secret container와 최소 IAM만 관리한다.
- 전용 ARM64 cert-loader는 task role로 자신의 bundle을 가져와 chain, key match, validity, URI/DNS SAN과 EKU를 검증하고 `/run/meetingmind/tls`에 원자적으로 기록한다. loader 실패 시 application/Envoy가 시작되지 않는다.
- 공식 AWS CLI image의 shell/JSON/PEM 도구는 지원 인터페이스가 아니므로 Go 정적 binary와 AWS SDK v2를 기본 구현으로 정했다. 이는 새 runtime 의존성을 추가하지만 작고 검증 가능한 image, 표준 `crypto/x509` 검증과 secret redaction을 얻기 위한 제한된 선택이다.
- 기존 `internal_mtls_ready`의 순환을 material-ready와 runtime-verified gate로 분리한다. private validation mode에서 Auth→AI/STT→Core를 public traffic 없이 검증하고 positive/negative/rotation evidence 뒤에만 정상 runtime acknowledgement를 허용한다.
- 구체 identity/bundle schema, 파일 경계, 단계, 검증 행렬과 rollback은 `infra/aws/nonprod-v2/mtls-implementation-plan.md`에 기록했다. 이번 작업은 계획 문서만 변경했으며 PKI material 생성, AWS secret write, Terraform apply와 ECS deployment는 수행하지 않았다.

## T047-B1 Offline PKI Tooling

- `scripts/pki/nonprod/manifests/*.json`에 BFF/Auth/Core/AI/Realtime STT의 exact service account, 단일 SPIFFE URI SAN, DNS SAN과 EKU 계약을 고정했다. manifest field 추가·누락과 승인된 값의 변경은 발급 전에 거부한다.
- `scripts/pki/nonprod/pki.py`에 AES-256으로 암호화한 ECDSA P-256 root/intermediate CA 초기화, 90일 leaf 발급, chain·validity·P-256·certificate/private-key·SAN/EKU 검증과 Secrets Manager TLS bundle JSON 생성을 구현했다. root는 5년, intermediate는 1년이며 leaf 만료까지 30일 이하면 `rotationRequired=true`를 반환한다.
- 출력은 absolute path를 명시해야 하고 repository 내부, 기존 경로, symlink와 group/other permission이 열린 parent를 거부한다. CA/passphrase/private key 입력도 repository 밖의 non-symlink private 경계만 허용한다. 발급 material은 `0700` staging에서 완성한 뒤 rename하고 private key와 bundle JSON은 `0600`으로 기록한다.
- 새 dependency는 추가하지 않았다. Python 3 표준 라이브러리는 JSON/경로·권한/원자 쓰기와 오류 redaction을 담당하고 시스템 OpenSSL은 key/X.509/chain을 담당한다. shell-only 대안은 JSON·SAN/EKU 파싱과 fail-closed path 검사가 취약하고, 별도 Python X.509 package는 현재 범위에 불필요해 선택하지 않았다.
- `scripts/pki/nonprod/README.md`에 외부 암호화 위치, passphrase file, 발급·검증·bundle 명령과 Production CA 분리 원칙을 기록했다. 이 구현과 검증에서는 실제 NonProd CA/private key를 생성하지 않았고 테스트 전용 material은 OS 임시 디렉터리에서만 생성 후 삭제했다.

### T047-B1 검증

| Check | Result | Notes |
| --- | --- | --- |
| Offline PKI integration | Pass | `PYTHONDONTWRITEBYTECODE=1 python3 scripts/pki/nonprod/test_pki.py`; 임시 encrypted CA로 5개 service certificate와 bundle 생성·검증, 10 tests |
| Identity negative matrix | Pass | wrong/multiple SPIFFE URI, wrong/wildcard DNS, IP SAN, wrong EKU와 변경된 manifest 거부 |
| Certificate negative matrix | Pass | certificate/private-key mismatch, expired/not-yet-valid certificate와 90일 초과 leaf 거부 경계 검증 |
| Output custody | Pass | repository 내부, symlink, `0755` parent와 group/other permission이 열린 private input 거부; CA key encryption과 bundle `0600` 확인 |
| Python syntax/CLI | Pass | 별도 temp pycache로 `compileall`, executable CLI/test entrypoint와 `--help` 확인 |
| Secret scan | Pass | `gitleaks dir --no-banner --redact scripts/pki/nonprod`; private key/certificate PEM marker와 실제 passphrase 없음 |
| `git diff --check` | Pass | T047-B1 source/docs에 whitespace 오류 없음 |
| Terraform/AWS/ECS | Not run | T047-B1 범위 밖이며 Terraform, secret version write, AWS apply와 ECS deployment를 수행하지 않음 |
| Application regression | Not run | Java/AI application runtime은 변경하지 않았고 이번 검증은 독립 PKI tooling에 한정함 |

## T047-B2 ARM64 Certificate Loader

- `cert-loader/`에 Go 1.26 정적 binary를 추가했다. loader는 ECS task role의 기본 credential chain으로 exact service secret ARN과 `AWSCURRENT`, `AWSPENDING` 또는 `AWSPREVIOUS` stage만 읽으며 secret value를 flag나 환경변수로 받지 않는다.
- binary에 BFF/Auth/Core/AI/STT의 service, SPIFFE URI, DNS SAN과 순서가 고정된 EKU 계약을 내장했다. strict JSON, leaf+intermediate와 intermediate+root chain, ECDSA P-256/SHA-256, CA constraints, 최대 90일 validity, key match, exact SAN/EKU와 bundle metadata를 모두 검증한다. 만료까지 30일 이하면 rotation 대상으로 표시한다.
- 출력은 `/run/meetingmind/tls`로 고정하고 `os.Root` 경계의 빈 non-symlink, group/other non-writable directory만 허용한다. `0700` `.staging-*` 안에서 `fsync`와 `10001:10001` chown을 끝낸 뒤 `tls.key` `0400`, `tls.crt`/`ca.crt` `0444`를 rename하며 중간 실패 시 노출된 파일과 staging을 모두 제거한다.
- 성공 로그는 fingerprint, expiry와 secret version ID만, 실패 로그는 고정 error code만 기록한다. AWS cause, ARN, identity 입력, JSON, PEM과 private key는 출력하지 않는다.
- AWS SDK for Go v2 `config`와 `service/secretsmanager`만 새 dependency로 추가하고 `go.sum`으로 고정했다. 공식 task-role credential/GetSecretValue 지원을 사용하면서 직접 SigV4/credential provider 구현을 피하기 위한 선택이며, AWS CLI+shell/OpenSSL 대안은 strict schema·X.509 계약·원자 세 파일 쓰기·오류 redaction을 하나의 최소 runtime으로 제공하지 못해 제외했다. 상세 근거와 실행 계약은 `cert-loader/README.md`에 기록했다.
- Docker builder는 공식 Go 1.26.5 Alpine 3.24 manifest digest로 고정했고 `$BUILDPLATFORM`에서 `CGO_ENABLED=0 GOOS=linux GOARCH=arm64`로 교차 컴파일한다. runtime은 CA bundle과 binary만 담은 scratch image이며 loader의 제한된 init 작업을 위해 `0:0`으로 실행한다.
- CI에 독립 Go module/test/vet job과 ARM64 image build, content digest summary, Trivy HIGH/CRITICAL gate를 추가했다. 이 단계에서는 실제 NonProd CA/private key, certificate 결과물 또는 secret value를 생성·저장하지 않았다.

### T047-B2 검증

| Check | Result | Notes |
| --- | --- | --- |
| Go module/test/vet | Pass | 고정 builder image에서 `go mod verify`, `go test -cover ./...`, `go vet ./...`; CLI 52.4%, loader 81.5% statement coverage |
| Five-service bundle contract | Pass | 메모리에서만 만든 일회성 ECDSA CA/leaf로 BFF/Auth/Core/AI/STT exact SPIFFE/DNS/EKU와 30일 rotation boundary 검증 |
| Certificate negative matrix | Pass | malformed/unknown/trailing JSON, malformed PEM, expired/not-yet-valid/90일 초과 leaf, wrong/multiple SPIFFE, wrong/wildcard DNS, IP/email SAN, wrong/unknown EKU, key mismatch, untrusted/inconsistent chain 거부 |
| Secret source/config boundary | Pass | exact ARN/region/account/service suffix, version stage와 response stage/version/payload 형태 검증; AWS 오류 cause는 고정 code로 redaction |
| Atomic output boundary | Pass | output symlink/group·other writable/non-empty target/empty material 거부, exact file mode 검증과 injected second rename 실패 후 final/staging 파일 0건 |
| ARM64 static image | Pass | `docker buildx build --platform linux/arm64 --load`; image `sha256:95902308d47e831e8f1b0a9ab08720b8fffc546ad61cc3e6c66bb66fad8c5a09`, Linux ARM64, scratch, CGO disabled, `0:0`, 3,505,968 bytes |
| Fail-closed runtime log | Pass | 인자 없는 ARM64 container가 exit 1과 `cert_loader_failed code=config_invalid`만 출력 |
| Image vulnerability scan | Pass | Trivy 0.72.0 `--ignore-unfixed --scanners vuln --severity HIGH,CRITICAL`; Go binary finding 0 |
| Source secret scan | Pass | `gitleaks dir --no-banner --redact cert-loader`; 56.79 KB, no leaks found. private-key/AWS access-key marker 별도 검사도 0건 |
| CI/diff validation | Pass | Ruby YAML parser로 workflow 문법 확인, `git diff --check` 통과 |
| Race detector | Not run | 고정 Alpine builder의 static CGO-off 환경에는 race detector가 요구하는 CGO toolchain이 없음. fail-closed 경계는 일반 단위 테스트와 vet로 검증했고 새 test-only system dependency는 추가하지 않음 |
| AWS/Terraform/ECS | Not run | T047-B2 범위 밖이고 AWS secret read/write, Terraform plan/apply와 ECS deployment를 수행하지 않음 |

## T047-B3 Terraform Certificate Delivery와 Gate 분리

- `modules/secrets`에 값 없는 서비스별 TLS bundle secret container(`bff|auth|core|ai|stt`/`tls-bundle`)와 resource policy를 추가했다. 각 bundle은 `aws:PrincipalArn`이 해당 서비스 task role이 아닌 모든 principal의 `GetSecretValue`를 명시적으로 거부하므로 다른 서비스 role은 identity policy 부재(implicit deny)와 resource policy(explicit deny) 두 단계에서 차단된다. Console 운영자도 값 입력 후 재조회할 수 없으며 이는 의도된 경계로 README에 기록했다.
- `modules/iam`에 task role 전용 `read-own-tls-bundle` 정책을 추가했다. 각 서비스는 자기 bundle ARN 하나의 `DescribeSecret`/`GetSecretValue`와 exact data KMS key decrypt만 얻는다. 기존 execution role secret 정책에는 TLS bundle을 추가하지 않아 bundle이 container 환경변수로 주입될 경로를 만들지 않았다.
- `modules/ecs-task`에 optional `tls_bundle` 입력을 추가했다. 활성 시 task-scoped ephemeral volume `meetingmind-tls`, `essential=false`·`0:0` cert-loader init container(read-write mount, read-only root filesystem, `cert-loader` log stream), application container의 `dependsOn cert-loader:SUCCESS`, read-only `/run/meetingmind/tls` mount와 고정 `10001:10001` app user를 구성한다. loader command에는 secret ARN, version stage, exact service/SPIFFE/DNS/EKU 기대값과 출력 경로만 들어가며 secret 값은 Terraform state/task definition/env 어디에도 존재하지 않는다.
- 순환하던 `internal_mtls_ready`를 제거하고 `internal_mtls_material_ready`(private validation만 허용)와 `internal_mtls_runtime_verified`(정상 runtime 허용)로 분리했다. `enable_mtls_validation_services`+`mtls_validation_services`는 BFF를 변수 validation에서 거부하고, material evidence·서비스별 digest·cert-loader digest·desired count를 요구하며, HTTP smoke listener 및 `enable_runtime_services`와 상호 배타다. validation mode에서는 ALB target group 연결도 비워 public traffic 경로를 제거한다. 정상 runtime gate는 이제 `internal_mtls_runtime_verified`와 cert-loader digest를 추가로 요구한다.
- mTLS wiring(`mtls` Spring profile, loader container, volume)은 validation 또는 runtime mode가 켜질 때만 task definition에 들어가므로 기본 foundation plan의 task definition은 이전과 동일하게 application container 하나만 갖는다. cert-loader ECR repository를 추가했고 loader image는 `cert_loader_image_digest`로 digest 고정한다.
- Terraform은 여전히 secret version, CA/leaf material, AWS apply를 만들거나 수행하지 않는다. AI Envoy sidecar와 shared token 제거는 T047-C2, 실제 AWS cross-secret IAM 거부 evidence는 T047-D 범위다.

### T047-B3 검증

| Check | Result | Notes |
| --- | --- | --- |
| Terraform format | Pass | `terraform fmt -recursive infra/aws` 후 변경 없음 |
| NonProd V2 validate | Pass | Terraform 1.15.8; secrets/iam/ecs-task 모듈과 environment root 전체 schema 검증 |
| Mock Terraform tests | Pass | `terraform test` 15/15; foundation 기본값에서 TLS secret container 존재·loader 미배선, runtime gate의 runtime-verified/cert-loader digest 요구, validation gate의 material/digest/smoke-listener/BFF 거부, validation·runtime 상호 배타, private validation plan에서 `cert-loader`가 application보다 먼저 시작하는 container 순서 검증 |
| Cross-service bundle read 거부(source) | Pass | task role Allow는 자기 bundle ARN 1개뿐이고 bundle resource policy가 비소유 principal `GetSecretValue`를 explicit deny; 실제 AWS 거부 evidence는 T047-D에서 수집 |
| Secret 원문 부재 | Pass | Terraform은 secret version을 생성/조회하지 않고 loader에는 ARN·기대 identity만 전달; task definition/env/output에 bundle 값 경로 없음 |
| IaC HIGH/CRITICAL scan | Pass | `trivy config --skip-check-update --severity HIGH,CRITICAL --exit-code 1 infra/aws/modules` 탐지 0. environments snapshot 모드가 재보고한 3건은 기존 inline 예외(public ALB, endpoints egress)와 snapshot 렌더링 artifact(SNS는 실제로 customer-managed key 사용)로 이번 변경 밖 |
| Terraform source secret scan | Pass | `gitleaks dir --no-banner --redact infra/aws`; 243.37 KB, no leaks found |
| `git diff --check` | Pass | whitespace 오류 없음 |
| AWS plan/apply와 runtime 거부 검증 | Not run | remote backend plan/apply와 secret version 입력은 Phase 8 이후 별도 사용자 승인 범위이며, cross-secret IAM 거부 runtime evidence는 T047-D에서 수행 |
| Application regression | Not run | 이번 슬라이스는 Terraform/docs만 변경했고 Java/AI/loader source는 수정하지 않음 |

## T047-B4 Rotation Runbook, Verifier와 CA Overlap 계약 (source 준비)

- `infra/aws/nonprod-v2/rotation-runbook.md`에 leaf rotation(`AWSPENDING` 입력 → canary 검증 → `AWSCURRENT` 승격 → 해당 서비스만 force deployment → 확인), `AWSCURRENT` rollback과 3단계 CA overlap(old+new trust 확장 → 새 CA leaf caller/callee 순서 배포 → old trust 제거) 절차를 실행 명령 수준으로 고정했다. evidence는 시각/version ID/fingerprint/task revision만 허용하고 PEM·private key·`get-secret-value`는 기록을 금지한다.
- 별도 verifier binary는 만들지 않았다. 오프라인 사전 검증은 기존 `pki.py verify`/`bundle`이, AWS 사후 검증은 cert-loader 자신이 담당한다. 서비스 task definition으로 standalone canary task를 실행하되 cert-loader command만 `--version-stage AWSPENDING`으로 override하면 실제 배포와 동일한 코드 경로로 pending bundle을 검증하며, canary는 ECS service 밖이라 Cloud Map 등록과 트래픽이 없다. 새 코드 대신 기존 구현을 재사용하는 결정이며 runbook 2장에 기록했다.
- 구현 중 계약 충돌을 발견했다: T047-B2 loader와 B1 PKI 검증기가 `caBundlePem`을 정확히 `(intermediate, root)` 1쌍으로 고정해 plan 10장의 old+new trust 동시 배포가 불가능했다. `mtls-implementation-plan.md` 5.1에 쌍 계약을 먼저 명시한 뒤 loader `bundle.go`와 `pki.py`를 확장했다: 쌍 1~2개 허용, 각 쌍의 독립 CA/chain 검증, presented intermediate가 정확히 한 쌍과 일치, 중복 intermediate/root·홀수 개·만료 쌍 거부. overlap window 밖 기본 계약(1쌍)은 그대로다.
- `scripts/pki/nonprod/README.md`와 `cert-loader/README.md`에 overlap 계약을, `tasks.md` T047-B4 row에 실제 수정 파일 범위와 준비 완료 상태를 반영했다. 완료 기준인 drill evidence는 AWS private validation deployment(T048-V)와 Phase 8 사용자 승인 뒤에만 수집할 수 있어 T047-B4는 완료로 표시하지 않았다.
- Terraform, AWS secret write, ECS deployment, 실제 NonProd CA/leaf 생성은 수행하지 않았다. 테스트 material은 Go 테스트의 메모리 내 일회성 CA와 Python 테스트의 OS 임시 디렉터리 CA뿐이며 종료 시 삭제된다.

### T047-B4 검증

| Check | Result | Notes |
| --- | --- | --- |
| Go module/test/vet | Pass | 고정 Go 1.26.5 Alpine builder에서 `go mod verify`, `go test -cover ./...`, `go vet ./...`; loader 82.9%, CLI 52.4% statement coverage |
| Loader CA overlap matrix | Pass | issuing pair 첫/두 번째 위치 수용과 4-cert trust 원본 보존, 중복 쌍·중복 root·홀수 개·issuing pair 부재·만료 쌍 거부 |
| 기존 loader negative 회귀 | Pass | wrong CA/different intermediate/missing root 등 기존 chain·identity·JSON·output 경계 테스트 전체 통과 |
| PKI overlap integration | Pass | `PYTHONDONTWRITEBYTECODE=1 python3 scripts/pki/nonprod/test_pki.py` 11/11; 두 번째/세 번째 일회성 CA로 old/new leaf 검증, 4-cert bundle JSON 생성, 중복 쌍·홀수 개·issuing pair 부재 거부 |
| ARM64 image rebuild | Pass | `docker buildx build --platform linux/arm64 --load`; image `sha256:e40a3166...3fe94f`, scratch, 3,506,818 bytes. 검증 후 로컬 태그 삭제 |
| Image vulnerability scan | Pass | Trivy `--ignore-unfixed --scanners vuln --severity HIGH,CRITICAL` finding 0 |
| Source secret scan | Pass | `gitleaks dir --no-banner --redact`를 `cert-loader`, `scripts/pki/nonprod`, `infra/aws/nonprod-v2`에 개별 실행; 모두 no leaks |
| `git diff --check` | Pass | whitespace 오류 없음 |
| Rotation/rollback/CA overlap drill | Pass | 2026-07-26 Core leaf canary/승격/rollback과 독립 CA의 old+new trust→new leaf→old trust 제거를 실제 AWS에서 완료했다. 상세 evidence는 아래 실행 섹션에 기록했다. |
| Terraform/AWS 정합성 | Pass | 5개 secret의 3단계 version 승격과 4개 validation service force deployment 뒤 Terraform refresh plan `No changes` |

## T047-C2 AI Envoy mTLS 경계와 ECS Shared Token 제거

- `ai/envoy/envoy.yaml`에 static Envoy config를 고정했다. `0.0.0.0:8000` downstream TLS는 client certificate 필수, cert-loader 산출물 사용, TLS validation과 HTTP RBAC 모두 exact Core SPIFFE URI SAN만 허용한다. `SANITIZE_SET`으로 외부 XFCC를 제거하고 검증된 URI만 재생성하며 admin은 `127.0.0.1:9901`에만 bind하고 access log에는 XFCC/certificate/secret을 남기지 않는다. upstream은 `127.0.0.1:8001` loopback Uvicorn 하나다.
- `ai/envoy/Dockerfile`은 upstream `envoyproxy/envoy:distroless-v1.38.3`을 manifest digest로 고정하고 config만 복사하며 `10001:10001`로 실행한다. distroless에는 shell이 없어 container health check 대신 AI app health check가 loopback으로 app `/health`와 admin `/ready`를 함께 확인하는 방식을 택했고 `ai/envoy/README.md`에 기록했다.
- FastAPI에 `AI_INTERNAL_AUTH_MODE`(`shared-token` 기본 | `mtls-proxy`)를 추가했다. `mtls-proxy`의 `/api/internal/**`는 정확히 하나의 XFCC 헤더, 단일 certificate element, quoted/escaped 값 거부, 정확히 하나의 `URI=` key, `AI_INTERNAL_ALLOWED_SPIFFE_ID`와의 상수시간 일치를 모두 요구하고 shared token 헤더를 무시한다. allowed principal 미설정과 unknown mode는 fail closed다. `shared-token` mode는 local/on-prem PoC 호환 경계로 유지하되 ECS에는 넣지 않는다.
- Terraform ecs-task 모듈에 `envoy_sidecar`(tls_bundle 필수 validation, task port 인수, read-only TLS mount, `cert-loader:SUCCESS` 의존, read-only root)와 `container_command`를 추가했다. AI task는 mTLS mode에서 Envoy가 8000을 받고 Uvicorn command가 `127.0.0.1:8001`로 override되며, container 순서는 `cert-loader → envoy → ai`다.
- ECS 경계에서 Core→AI shared token을 제거했다: `core/ai-internal-token` secret container, Core/AI task definition의 `AI_INTERNAL_SERVICE_TOKEN` 주입과 execution role secret 참조를 모두 삭제했다. Core 코드는 token 미설정 시 헤더를 생략하므로 Java 변경 없이 완결된다. AI에는 `AI_INTERNAL_AUTH_MODE`/`AI_INTERNAL_ALLOWED_SPIFFE_ID` 환경변수를 추가했고, envoy image는 새 `ai-envoy` ECR repository와 `ai_envoy_image_digest` 변수로 digest 고정하며 AI가 validation/runtime allowlist에 있으면 gate가 digest를 요구한다.
- CI containers job에 ARM64 AI Envoy 빌드, 일회성 self-signed material 기반 `--mode validate`, content digest 출력과 Trivy HIGH/CRITICAL gate를 추가했다.
- `ai/envoy/local_mtls_check.sh`는 OS 임시 디렉터리의 일회성 CA(정식 manifest 계약 사용)로 로컬 positive/negative matrix를 자동 검증하고 종료 시 material/컨테이너를 삭제한다.

### T047-C2 검증

| Check | Result | Notes |
| --- | --- | --- |
| AI 전체 unit tests | Pass | `python -m unittest discover -s tests` 196개(신규 XFCC 경계 7개 포함), `compileall` 통과 |
| FastAPI XFCC 경계 | Pass | 정상 단일 XFCC 수락; 헤더 부재/중복, 다중 element, 다중/부재 URI key, wrong principal, quoted 값, malformed field, spoofed token 헤더, allowed principal 미설정, unknown mode 모두 401 fail closed; shared-token mode는 XFCC를 무시 |
| Envoy config validate | Pass | 고정 digest 이미지에서 `--mode validate` OK (일회성 self-signed material) |
| 로컬 mTLS matrix | Pass | `ai/envoy/local_mtls_check.sh`; Core client certificate 200 + sanitize된 단일 `URI=core` XFCC, no-cert/wrong-CA/wrong-SPIFFE(BFF) TLS 거부, spoofed XFCC 교체 확인, 타 컨테이너에서 direct `8001` 접근 거부 |
| ARM64 Envoy image | Pass | `docker buildx --platform linux/arm64` 빌드 성공, Trivy `--ignore-unfixed` HIGH/CRITICAL 0건, 검증 후 로컬 태그 삭제 |
| Terraform validate/mock tests | Pass | `terraform validate`, `terraform test` 16/16; AI validation의 envoy digest 요구와 `cert-loader,envoy,ai` container 순서 assert 포함 |
| Shared token 제거 | Pass | Terraform source에서 `core/ai-internal-token` container/참조/주입 0건; Core는 token 미설정 시 헤더 생략을 코드로 확인 |
| CI YAML/diff | Pass | Ruby YAML parser로 workflow 문법 확인, `git diff --check` 통과 |
| Source secret scan | Pass | `gitleaks dir --no-banner --redact`를 `ai`, `infra/aws`에 개별 실행; no leaks |
| 실제 AWS runtime 거부 | Not run | ECS/Cloud Map/SG 계층의 runtime positive/negative는 T048-V/T047-D에서 수집 |
| Envoy ECR mirror push | Not run | 검토된 digest의 ECR mirror는 Phase 6/8의 AWS 작업이며 별도 승인 뒤 수행 |

## NonProd V2 Foundation 정렬 Apply (T048-V 준비)

- 사용자 승인 하에 저장된 plan을 실제 NonProd V2(account `825820234979`)에 apply했다: 64 추가 / 3 변경 / 16 삭제, 오류 0건.
- 삭제 16건은 모두 기록된 결정과 일치한다: 공용 execution role→서비스별 분리(T047-A) 3건, `core/ai-internal-token`(T047-C2)·`core/stt-internal-token`(T047-C1) secret 2건(7일 recovery window), 광범위 egress→최소권한 교체 6건, task definition 신규 revision 교체 5건. 실행 중 ECS 서비스는 0개로 중단 영향 없음.
- 추가분에는 TLS bundle secret 5개+cross-service read deny policy, task-role `read-own-tls-bundle` 정책, `cert-loader`/`ai-envoy` ECR, Cloud Map namespace/서비스, mTLS gate가 포함된다.
- Apply 후 drift 2종을 소스에서 수정해 plan을 `No changes`로 수렴시켰다: Cloud Map의 빈 `health_check_custom_config {}`는 API에 저장되지 않아 영구 replace를 만들므로 `failure_threshold = 1`로 고정(빈 서비스 4개 1회 교체), default NACL은 `subnet_ids` 미선언으로 영구 diff가 생겨 public/private/data subnet을 명시했다. 수정 후 mock tests 16/16 재통과.
- 다음 순서: 7개 이미지 ECR push(digest 기록), 저장소 밖 offline CA 생성과 5개 bundle `AWSCURRENT` 입력(Phase 8), validation mode 활성화(T048-V).

## Phase 8 Material Setup (이미지·CA·TLS Bundle)

- PR #55로 mTLS 구현 전체를 `dev`에 병합했고, 병합 commit `3845b4a` 기준으로 7개 ARM64 이미지를 ECR에 push했다. digest는 환경 `terraform.tfvars`(비추적)에 기록: bff `ec6ae964…`, auth `46a7323b…`, core `f24890bd…`, ai `7019cebc…`, realtime-stt `27d51e9e…`, cert-loader `95a22eaf…`, ai-envoy `21dc26fa…`. `--provenance=false`로 단일 manifest digest를 사용했다.
- 사용자가 지정한 저장소 밖 암호화 경계(0700)에서 offline NonProd root/intermediate CA를 초기화하고 5개 서비스 leaf(만료 2026-10-23, rotation 미해당)를 발급했다. 실제 경로·passphrase·PEM은 저장소에 기록하지 않는다. leaf fingerprint 앞 16자: ai `69a097e74f8228ce`, auth `7923d9dce659da4a`, bff `51c5e4ff7b11c63c`, core `5a2ffa7bb7822ee6`, stt `b7d89c550555d6a7`.
- 5개 TLS bundle JSON을 `AWSCURRENT`로 입력했다(version: bff `8c1e1b36…`, auth `37360d8f…`, core `1cc1f54f…`, ai `3fd48bf0…`, stt `297e9ece…`). 입력 직후 운영자 admin role의 `GetSecretValue`가 resource policy **explicit deny**로 거부됨을 확인해 cross-principal read 차단 evidence를 확보했다.
- `internal_mtls_material_ready`의 근거가 모두 충족됐다: bundle `AWSCURRENT` 5개, 7개 digest·scan, loader/Envoy config, 로컬 mTLS handshake matrix. 다음은 validation mode 활성화다.

다음 경계는 T048-V다. material gate 충족 뒤 public traffic 없이 Auth→AI/STT→Core private validation service를 시작하고 loader/Envoy/application health와 Cloud Map 등록을 확인한다.

## T048-V Private Validation과 T047-D Runtime Matrix

- Core DB bootstrap 기준 이후 추가된 V20~V23을 별도 migrator 경계에서 forward-only 적용했다. Flyway history는 target 23까지 성공했고 `spaces.image_url`이 생성됐으며 runtime role의 schema DDL 권한은 계속 비활성이다.
- provider crash 뒤 Terraform state에서 누락된 기존 Core ECS service만 import했다. import 직후 남은 in-place 차이를 적용하고 Core를 재배포했으며 최종 refresh plan은 `No changes`다.
- 최초 Core caller 검증에서 Core→Auth가 network timeout, Core→STT가 principal 403으로 실패했다. source SG egress에는 Core→Auth가 있었지만 Auth ingress가 BFF만 허용했고, Java 서비스의 기본 SPIFFE namespace가 validation 계약과 달랐다. Auth의 Core JWKS ingress와 Auth/Core/STT의 exact `ns/nonprod-v2` principal을 Terraform에 명시했다.
- task definition 교체 전 `skip_destroy=true`를 먼저 state에 반영했다. Auth/Core/STT 신규 revision 배포 뒤 이전 revision 3/3/5가 모두 `ACTIVE`임을 확인했고, 최종 revision 4/4/6은 loader exit 0과 application `HEALTHY`로 안정화됐다.
- validation 서비스는 모두 private subnet, `assignPublicIp=DISABLED`, load balancer 연결 0개이며 BFF public service는 시작하지 않았다. `internal_mtls_runtime_verified`는 계속 `false`다.

### T048-V evidence

| UTC 시각 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | TLS 결과 | HTTP 결과 | Network 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-26T03:15:26Z | Auth | `37360d8f-d992-4719-9a22-756bac7ba184` | `7923d9dce659da4ad95e4d2d273361ba1db699a1925a25d636340273ef2b24cc` | `auth:4` | loader accepted | health accepted | private connected |
| 2026-07-26T03:15:26Z | Core | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core:4` | loader accepted | health accepted | private connected |
| 2026-07-26T03:15:26Z | AI | `3fd48bf0-f1e9-4735-a846-19fb45c699c7` | `69a097e74f8228ce8fc6447a1711d56dc1d8ababaebbd246f94c9d8e5c84ce94` | `ai:3` | loader/Envoy accepted | health accepted | private connected |
| 2026-07-26T03:15:26Z | Realtime STT | `297e9ece-346a-48fd-8652-497ef121326c` | `b7d89c550555d6a7ccf978034181556029c1b55a10dfa9dbc774471649aae036` | `realtime-stt:6` | loader accepted | health accepted | private connected |
| 2026-07-26T02:54:17Z | Core→Auth JWKS | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T02:54:17Z | Core→AI Envoy | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T02:54:17Z | Core→Realtime STT | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |

### T047-D negative evidence

| UTC 시각 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | TLS 결과 | HTTP 결과 | Network 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-26T02:57:45Z | No client certificate→AI | — | — | `core-verifier:1` | rejected | — | connected |
| 2026-07-26T02:57:43Z | Wrong server CA→AI | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | rejected | — | connected |
| 2026-07-26T02:57:46Z | Hostname mismatch→AI | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | rejected | — | connected |
| 2026-07-26T02:57:43Z | Spoofed XFCC without certificate→AI | — | — | `core-verifier:1` | rejected | — | connected |
| 2026-07-26T02:57:45Z | Direct Uvicorn `8001` | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | not reached | — | timeout |
| 2026-07-26T02:57:46Z | Wrong source SG→AI | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | not reached | — | timeout |
| 2026-07-26T02:57:43Z | Wrong SPIFFE BFF→AI | `8c1e1b36-74b5-470d-a573-84bc255c416f` | `51c5e4ff7b11c63c574c8c2651f458da94fdba661d60547ec9f14b527cc84927` | `bff-verifier:1` | rejected | — | connected |
| 2026-07-26T03:04:29Z | BFF role→Core TLS secret | — | — | `bff-verifier:1` | not reached | — | secret fetch denied |
| 2026-07-26T03:02:50Z | Invalid Core AWSPENDING | `a11f6489-0176-4799-8d2b-c0d40b8a2012` | `4fb1214264af09c256a6c2f648bb321dc19e7af675d8a3a9c3d60c84f74ded1b` | `core:4` | loader rejected | — | secret fetched; app not started |
| 2026-07-26T03:12:00Z | Multiple SPIFFE URI source matrix | — | — | — | PKI/filter rejected | 401 | not applicable |
| 2026-07-26T03:15:26Z | Public bypass topology | — | — | `auth:4/core:4/ai:3/stt:6` | not reached | no public route | public IP/LB absent |

Runtime matrix의 모든 거부 결과는 기대한 TLS, network, loader 또는 secret fetch 경계와 일치했다. 다만 `tasks.md`의 T047-D는 T047-B4를 dependency로 가지므로 CA overlap drill 전까지 formal checkbox를 열어 둔다.

## T047-B4 Core Leaf Rotation과 Rollback Drill

- 보호된 offline 경계의 기존 intermediate CA로 새 Core leaf를 발급하고 exact service/SPIFFE/DNS/EKU bundle 검증을 통과했다. 실제 CA private key를 새로 만들지 않았다.
- metadata가 불일치하는 AWSPENDING canary는 `bundle_invalid`로 종료되고 application은 시작하지 않았다. 검증된 AWSPENDING canary는 loader exit 0과 application health를 통과했다.
- 새 version을 AWSCURRENT로 승격해 Core revision 4를 force deployment했고 새 leaf로 Auth JWKS, AI Envoy, Realtime STT가 모두 200이었다. 이어 이전 version을 AWSCURRENT로 되돌려 force deployment했으며 원래 fingerprint와 Core→AI 200으로 복구를 확인했다.
- 이 시점에는 root/intermediate 계층이 하나뿐이고 실제 CA private key 신규 생성 승인이 없어 runbook 6장을 보류했다. 이후 사용자가 독립된 두 번째 NonProd CA 생성과 5개 서비스의 3단계 rotation을 명시 승인했으며, 아래 CA overlap evidence로 T047-B4 dependency를 닫았다.

### Rotation evidence

| UTC 시각 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | TLS 결과 | HTTP 결과 | Network 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-26T03:05:00Z | Core AWSPENDING canary | `79bffb2d-af29-470f-b4b7-d75da69d5678` | `4fb1214264af09c256a6c2f648bb321dc19e7af675d8a3a9c3d60c84f74ded1b` | `core:4` | loader accepted | health accepted | private connected |
| 2026-07-26T03:07:00Z | Core AWSCURRENT deployment | `79bffb2d-af29-470f-b4b7-d75da69d5678` | `4fb1214264af09c256a6c2f648bb321dc19e7af675d8a3a9c3d60c84f74ded1b` | `core:4` | loader accepted | health accepted | private connected |
| 2026-07-26T03:09:01Z | Rotated Core→Auth | `79bffb2d-af29-470f-b4b7-d75da69d5678` | `4fb1214264af09c256a6c2f648bb321dc19e7af675d8a3a9c3d60c84f74ded1b` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T03:08:58Z | Rotated Core→AI | `79bffb2d-af29-470f-b4b7-d75da69d5678` | `4fb1214264af09c256a6c2f648bb321dc19e7af675d8a3a9c3d60c84f74ded1b` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T03:09:05Z | Rotated Core→Realtime STT | `79bffb2d-af29-470f-b4b7-d75da69d5678` | `4fb1214264af09c256a6c2f648bb321dc19e7af675d8a3a9c3d60c84f74ded1b` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T03:15:26Z | Core rollback | `1cc1f54f-2c20-4f66-9fbb-403db43c12d0` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core:4` | loader accepted | Core→AI 200 | connected |

### 이번 세션 검증

- Core migration runner: V20~V23 적용 및 target 23 확인, build success.
- Terraform: format, validate, clean mock test 16/16, Core service import 후 최종 refresh plan `No changes`.
- ECS: Auth/Core/AI/STT desired/running 1/1, rollout `COMPLETED`, application `HEALTHY`, cert-loader exit 0.
- PKI source matrix: `python3 scripts/pki/nonprod/test_pki.py` 11/11; wrong/multiple SPIFFE와 CA overlap pair 검증 포함.
- Core principal filter: `./gradlew test --tests com.meetingmind.demo.auth.CoreWorkloadIdentityFilterTest`, build success.
- 현재 shell에는 Go toolchain이 없어 cert-loader Go suite는 재실행하지 못했다. 기존 T047-B4 고정 builder 검증 결과는 유지되며 이번 runtime에서는 invalid bundle이 loader에서 실제 거부됐다.

## T047-B4 CA Overlap 3단계 Drill

- 사용자 승인에 따라 보호된 offline 경계에 독립된 두 번째 root/intermediate CA와 별도 random passphrase 파일을 생성했다. CA directory는 `0700`, passphrase는 `0600`이며 값은 출력하지 않았다. 기존 CA, 기존 leaf, secret version과 task definition revision은 삭제하지 않았다.
- 1단계는 기존 leaf/key를 유지하고 trust만 old+new 두 쌍으로 확장했다. Auth → AI/Realtime STT → Core → BFF 순서로 AWSPENDING canary, AWSCURRENT 승격과 배포를 수행했으며 기존 fingerprint와 모든 caller/callee 200이 유지됐다.
- 2단계는 새 CA leaf와 overlap trust를 Auth → AI/Realtime STT → Core → BFF 순서로 배포했다. old-CA Core→new-CA Auth/AI/STT와 new-CA Core→new-CA Auth/AI/STT가 모두 200이어서 전환 중 양방향 호환을 확인했다.
- 3단계는 새 CA leaf를 유지하고 old trust를 제거했다. Auth/AI/Realtime STT → Core → BFF 순서로 new-CA-only bundle을 배포했고 최종 Core→Auth/AI/STT와 BFF→Auth가 모두 200이었다.
- 보존된 1단계 old-CA Core/BFF leaf를 AWSPENDING에 잠시 연결해 새 trust-only AI/Auth로 호출했으며 두 요청 모두 TLS에서 거부됐다. 검증 직후 임시 AWSPENDING labels를 제거했고 모든 secret은 AWSCURRENT+AWSPREVIOUS만 갖는다.
- validation mode는 BFF ECS service를 의도적으로 시작하지 않으므로 BFF task role, BFF cert-loader와 BFF certificate를 그대로 사용하는 standalone verifier로 매 단계 canary와 BFF→Auth 200을 확인했다. Auth/Core/AI/STT services는 모든 단계에서 desired/running 1/1, application `HEALTHY`, rollout `COMPLETED`였다.

### CA overlap deployment evidence

| UTC 시각 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | TLS 결과 | HTTP 결과 | Network 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-26T04:08:06Z | Stage 1 Auth | `37c2e72c-dd0b-4838-a721-55e1fd732899` | `7923d9dce659da4ad95e4d2d273361ba1db699a1925a25d636340273ef2b24cc` | `auth:4` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:13:10Z | Stage 1 AI | `246e862f-c7a4-4edb-bbca-4f70bdbb8e8a` | `69a097e74f8228ce8fc6447a1711d56dc1d8ababaebbd246f94c9d8e5c84ce94` | `ai:3` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:13:00Z | Stage 1 Realtime STT | `51e2c546-c2c1-4744-8397-9e019c67f528` | `b7d89c550555d6a7ccf978034181556029c1b55a10dfa9dbc774471649aae036` | `realtime-stt:6` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:18:01Z | Stage 1 Core | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core:4` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:19:53Z | Stage 1 BFF | `960f1be2-1660-4cc2-b96c-eff6a51d3831` | `51c5e4ff7b11c63c574c8c2651f458da94fdba661d60547ec9f14b527cc84927` | `bff-verifier:1` | canary/current accepted | 200 | connected |
| 2026-07-26T04:26:41Z | Stage 2 Auth | `7deefcc0-90b9-40a1-b7ce-993950c250e1` | `9f21f9c77f7cfe7b3d670e89c4533c16e064a08151b93ccad914700c4ff0e397` | `auth:4` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:32:43Z | Stage 2 AI | `4e568cc4-96fa-464c-addd-d00307ed3b06` | `19de37ecf5a6cd2bf82f051b58d308035d72a5da3486f9eb623663ab75c73e58` | `ai:3` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:32:33Z | Stage 2 Realtime STT | `2eacb9f2-eeb9-40f3-83c6-2ec6d438ebed` | `a257dcfaa7750bcdb2671cc5dc271f5c5ed5e612766a826085ccc2a46bf03011` | `realtime-stt:6` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:38:31Z | Stage 2 Core | `fb2c1b85-adad-4d39-9255-9e3e4514e4c0` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core:4` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:41:49Z | Stage 2 BFF | `16ca44e5-5527-4c80-9832-30b52200edc0` | `4d53106466ba4ed93240382c65a5692a93643ee0f05c52c830fd67346767d6d6` | `bff-verifier:1` | canary/current accepted | 200 | connected |
| 2026-07-26T04:47:28Z | Stage 3 Auth | `a1752c07-208c-4aed-b00f-762985478e8e` | `9f21f9c77f7cfe7b3d670e89c4533c16e064a08151b93ccad914700c4ff0e397` | `auth:4` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:46:35Z | Stage 3 AI | `b318aaff-adb1-4e3a-8921-a7346c24b2c3` | `19de37ecf5a6cd2bf82f051b58d308035d72a5da3486f9eb623663ab75c73e58` | `ai:3` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:46:35Z | Stage 3 Realtime STT | `fc0f9bf9-24b6-4cf7-b09e-c7130f955456` | `a257dcfaa7750bcdb2671cc5dc271f5c5ed5e612766a826085ccc2a46bf03011` | `realtime-stt:6` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:52:17Z | Stage 3 Core | `1f5bb168-e1c5-4fea-99f5-ae5ddb5815d8` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core:4` | canary/current accepted | health accepted | private connected |
| 2026-07-26T04:54:24Z | Stage 3 BFF | `efecc327-3076-437f-8bda-34a04e60774b` | `4d53106466ba4ed93240382c65a5692a93643ee0f05c52c830fd67346767d6d6` | `bff-verifier:1` | canary/current accepted | 200 | connected |

### CA overlap communication evidence

| UTC 시각 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | TLS 결과 | HTTP 결과 | Network 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-26T04:20:53Z | Stage 1 Core→Auth | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:20:57Z | Stage 1 Core→AI | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:20:55Z | Stage 1 Core→STT | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:27:42Z | Stage 2 old-CA Core→new-CA Auth | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:33:42Z | Stage 2 old-CA Core→new-CA AI | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:33:47Z | Stage 2 old-CA Core→new-CA STT | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:39:33Z | Stage 2 new-CA Core→new-CA Auth | `fb2c1b85-adad-4d39-9255-9e3e4514e4c0` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:39:25Z | Stage 2 new-CA Core→new-CA AI | `fb2c1b85-adad-4d39-9255-9e3e4514e4c0` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:39:28Z | Stage 2 new-CA Core→new-CA STT | `fb2c1b85-adad-4d39-9255-9e3e4514e4c0` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:55:42Z | Stage 3 Core→Auth | `1f5bb168-e1c5-4fea-99f5-ae5ddb5815d8` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:55:48Z | Stage 3 Core→AI | `1f5bb168-e1c5-4fea-99f5-ae5ddb5815d8` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:55:56Z | Stage 3 Core→STT | `1f5bb168-e1c5-4fea-99f5-ae5ddb5815d8` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core-verifier:1` | accepted | 200 | connected |
| 2026-07-26T04:57:41Z | Old-CA Core→new-only AI | `caf300ec-820d-4b5a-b20a-49690bab95cb` | `5a2ffa7bb7822ee613cb6411a273dd2341118c8b3036bbb3834fed535d3eb2e0` | `core-verifier:1` | rejected | — | connected |
| 2026-07-26T04:57:37Z | Old-CA BFF→new-only Auth | `960f1be2-1660-4cc2-b96c-eff6a51d3831` | `51c5e4ff7b11c63c574c8c2651f458da94fdba661d60547ec9f14b527cc84927` | `bff-verifier:1` | rejected | — | connected |

최종 AWSCURRENT는 BFF `efecc327…`, Auth `a1752c07…`, AI `b318aaff…`, Realtime STT `fc0f9bf9…`, Core `1f5bb168…`이며 모두 새 CA 단독 trust다. 각 secret의 AWSPREVIOUS는 2단계 overlap bundle이라 reverse overlap rollback 시작점으로 보존했다. Terraform refresh plan은 `No changes`이고 `internal_mtls_runtime_verified`는 별도 승인 전까지 기본값 `false`를 유지한다.

## T048-P Private mTLS Runtime Promotion

- D-033에 따라 T047-D/T048-V의 완료 evidence를 private mTLS runtime promotion 근거로 승인했다. Q-013, BFF Valkey IAM token 갱신·재연결·TLS preflight, T047-E, T048/T049는 BFF/public release gate로 유지한다.
- Terraform에 `release_gates_acknowledged`를 분리해 값이 `false`인 동안 BFF runtime, public listener와 autoscaling을 차단했다. ALB target group attachment도 public listener가 활성화될 때만 ECS service에 연결되므로 private promotion은 ALB 결합을 만들지 않는다.
- gitignored 환경 설정은 validation mode를 종료하고 Auth/AI/Realtime STT/Core runtime을 활성화했으며 `runtime_gates_acknowledged=true`, `internal_mtls_runtime_verified=true`, `release_gates_acknowledged=false`로 전환했다. BFF는 runtime selection에 포함하지 않았다.
- clean tfvars 임시 복사본의 Terraform regression은 20/20 통과했고 `terraform validate`와 `terraform fmt -check -recursive infra/aws`도 통과했다. 기존 Cloud Map `failure_threshold` deprecation warning만 남는다.
- 실제 AWS plan은 `terraform_data` validation gate 제거와 runtime/release gate 생성뿐인 `2 add, 0 change, 1 destroy`였다. 저장된 plan을 그대로 apply했으며 ECS, ALB, autoscaling, task definition, secret과 CA material 변경은 0건이다.
- apply 후 Auth `4`, AI `3`, Realtime STT `6`, Core `4`는 모두 desired/running `1/1`, application `HEALTHY`, rollout `COMPLETED`, `assignPublicIp=DISABLED`, load balancer attachment 0개를 유지했다. 모든 cert-loader는 exit 0이다.
- 실행 중 task는 네 service task뿐이고 BFF ECS service는 0개다. ALB listener와 MeetingMind ECS autoscaling target도 각각 0개다. 새 verifier를 실행하지 않았으며, workload/task/secret이 바뀌지 않았으므로 승인 근거인 최종 T047-D/T048-V TLS/HTTP/network evidence가 그대로 연속된다.
- 2026-07-26T08:58:10Z 최종 refresh plan은 `No changes`다. 기존 CA, AWSPREVIOUS, secret versions와 task definition revisions는 모두 보존했다.

### Promotion evidence

| UTC 시각 | Service | Secret version ID | Leaf fingerprint SHA-256 | Task definition revision | TLS 결과 | HTTP 결과 | Network 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-26T08:58:10Z | Auth | `a1752c07-208c-4aed-b00f-762985478e8e` | `9f21f9c77f7cfe7b3d670e89c4533c16e064a08151b93ccad914700c4ff0e397` | `auth:4` | prior accepted; unchanged | health accepted | private connected; public disabled |
| 2026-07-26T08:58:10Z | AI | `b318aaff-adb1-4e3a-8921-a7346c24b2c3` | `19de37ecf5a6cd2bf82f051b58d308035d72a5da3486f9eb623663ab75c73e58` | `ai:3` | prior accepted; unchanged | health accepted | private connected; public disabled |
| 2026-07-26T08:58:10Z | Realtime STT | `fc0f9bf9-24b6-4cf7-b09e-c7130f955456` | `a257dcfaa7750bcdb2671cc5dc271f5c5ed5e612766a826085ccc2a46bf03011` | `realtime-stt:6` | prior accepted; unchanged | health accepted | private connected; public disabled |
| 2026-07-26T08:58:10Z | Core | `1f5bb168-e1c5-4fea-99f5-ae5ddb5815d8` | `49c20c70a20b68d011f602420c9948235d8e5111b854bd80aaa52f312713da82` | `core:4` | prior accepted; unchanged | Core→Auth/AI/STT prior 200; unchanged | private connected; public disabled |

## T048-P Targeted Release Gate Hardening

- 최종 source review에서 standalone `terraform_data.release_gate`가 ALB/ECS service dependency
  graph에 연결되지 않아 targeted plan/apply가 gate를 graph 밖으로 제외할 수 있음을 발견했다.
- `module.alb`와 `module.service`가 release gate에 명시적으로 의존하도록 수정했다. public listener는
  ALB dependency로, BFF와 module 내부 autoscaling target은 ECS service dependency로 보호한다.
  private Auth/AI/Realtime STT/Core runtime은 release acknowledgement 없이 계속 허용한다.
- clean tfvars 임시 복사본의 Terraform test는 24/24 통과했다. 추가 회귀는 targeted public ALB,
  BFF runtime과 autoscaling이 release acknowledgement 없이 실패하고 targeted private Auth runtime은
  성공하는지 검증한다.
- `terraform fmt -check -recursive infra/aws`, `terraform validate`와 `git diff --check`가 통과했다.
  기존 Cloud Map `failure_threshold` deprecation warning만 남는다.
- `meetingmind-nonprod` profile의 `terraform plan -lock=false -detailed-exitcode`는 실제 remote state와
  AWS refresh 뒤 `No changes`로 종료했다.
- 이 hardening은 Terraform dependency graph와 source test만 변경한다. AWS mutation, staging, commit,
  push와 PR 생성은 수행하지 않았다.

## T048-S1/T048-S2/T059-S1/T059-S2 NonProd Deployment Smoke

- PR #69 병합본을 기준으로 `codex/nonprod-v2-smoke-deploy` 브랜치를 만들고 세 작업 흐름을
  병렬 수행했다. `bff_smoke`는 Valkey IAM/TLS credential 경계, `frontend_edge_smoke`는
  private S3/OAC와 CloudFront, root integration은 release gate와 분리된 smoke gate·ALB/SG/ECS
  wiring을 담당했다. 사용자 소유 architecture 문서 변경은 건드리거나 포함하지 않았다.
- BFF는 ElastiCache IAM SigV4 token을 connection마다 새로 생성하는 Lettuce credential provider를
  사용한다. TLS, IAM 사용자·replication group·region을 fail-closed 검증하고 장기 Redis password와
  URL credential은 거부한다. BFF 97 tests가 통과했고 새 credential/만료·재요청 동작을 단위 검증했다.
- 기본 false인 `enable_deployment_smoke`와 별도 acknowledgement를 추가했다. smoke 상태는
  `release_gates_acknowledged=false`, autoscaling off, 모든 5개 runtime selected, BFF desired 1,
  operator CIDR 없음, CloudFront origin-facing prefix list 전용 ALB ingress만 허용한다. targeted plan도
  deployment/release gate를 우회하지 못하도록 ALB와 ECS service dependency graph에 연결했다.
- 프론트 edge는 public access가 차단된 S3, BucketOwnerEnforced, SSE-S3, CloudFront OAC, SPA rewrite,
  `/assets/*` cache와 cache-disabled `/api/*` ALB origin으로 구성했다. frontend source는 이미 상대
  `/api`와 same-origin을 사용해 변경하지 않았다. frontend 98 tests와 production build가 통과했다.
- 첫 apply에서 두 실제 통합 문제가 발견됐다. BFF task가 ECR layer용 S3 egress 없이 시작하지
  못해 AWS S3 managed prefix list의 TCP 443만 추가했고, Spring Session의 `CONFIG` 호출은
  `SPRING_SESSION_REDIS_CONFIGURE_ACTION=none`으로 비활성화했다. Spring Redis readiness의 읽기 전용
  `INFO`만 Valkey ACL에 명시적으로 재허용했다. 수동 긴급 반영한 S3 rule은 즉시 Terraform state로
  import했고 Valkey ACL도 동일 source로 수렴시켰다.
- CloudFront 최초 생성은 잘못된 managed cache policy ID로 실패했다. AWS managed policy 목록을
  조회해 실제 `Managed-CachingDisabled` ID로 수정한 뒤 부분 생성 리소스를 재사용했다. 실패한
  create waiter가 남긴 BFF ECS service taint는 해제해 서비스를 삭제·재생성하지 않았고, recovery
  plan은 CloudFront/S3 정책 생성, BFF task revision 추가, 기존 BFF service in-place update만 수행했다.
- CloudFront 기본 도메인의 기본 인증서는 minimum protocol policy를 선택할 수 없고 AWS API가
  `TLSv1`로 고정 보고한다. D-034 smoke 예외를 2026-08-09까지 명시했으며 정식 T059에서는 custom
  domain+ACM과 `TLSv1.2_2021`, WAF, dedicated frontend CMK가 필요하다.
- 최종 AWS 상태는 Auth/AI/Realtime STT/Core/BFF 모두 desired/running `1/1`, rollout `COMPLETED`,
  `assignPublicIp=DISABLED`다. Auth/AI/STT/Core의 load balancer attachment는 0, BFF만 1이며 autoscaling
  target은 0이다. BFF cert-loader는 exit 0이고 target은 `healthy`다. 기존 CA, AWSPREVIOUS, secret
  versions와 이전 task revisions는 삭제하지 않았다.
- CloudFront 기본 HTTPS domain은 `Deployed`, S3 public access block 네 항목은 모두 true다. HTTPS
  root와 static asset, CSRF/session endpoint가 200이고 세션 cookie 식별자가 재요청에서 유지됐다.
  존재하지 않는 smoke 계정 login은 BFF→Auth mTLS 경로에서 예상 401을 반환했다. CloudFront 외부의
  ALB 직접 요청은 연결 timeout으로 차단됐다.
- 최종 검증은 Terraform fmt/validate, clean mock test 29/29, Trivy frontend/security/alb HIGH/CRITICAL
  0건, BFF image HIGH/CRITICAL 0건, `git diff --check`와 실제 AWS refresh plan `No changes`다. 기존
  Cloud Map `failure_threshold` deprecation warning만 남는다.
- 이 결과는 빠른 NonProd 배포 smoke만 완료한다. 정식 T048/T049/T059, Q-013, T047-E,
  autoscaling/SLO·부하·장애/rollback drill, custom domain/ACM/WAF와 Production 전환은 계속 open이다.

## T059-S3 NonProd Custom Domain Drift Adoption

- 사용자가 가비아에서 `app.meetingmind.co.kr` CNAME을 CloudFront 생성 도메인으로 연결하고,
  CloudFront 배포 `E3VEAX4F98BOIE`에 `us-east-1` ACM 인증서를 수동 연결했다. 실제 CloudFront
  상태는 alias `app.meetingmind.co.kr`, `sni-only`, `TLSv1.2_2021`이다.
- 기존 Terraform refresh plan은 이 수동 alias와 인증서를 제거하고 기본 인증서/TLSv1로 되돌리는
  in-place update를 계획했다. `frontend-edge` 모듈에 optional custom-domain 객체를 추가하고
  environment root가 이를 전달하도록 수정해 같은 상태를 선언적으로 소유한다.
- 실제 NonProd 값은 gitignored `terraform.tfvars`에 두고, 커밋 가능한 example은 기본 `null`과
  외부 DNS/인증서 prerequisite만 설명한다. 가비아 DNS zone, ACM 발급과 DNS validation lifecycle은
  Terraform state에 가져오지 않는다.
- 모듈 입력은 lowercase DNS name, `us-east-1` ACM ARN 형식과 현재 AWS account 소유 여부를
  fail-closed 검증한다. custom domain을 사용하면 CloudFront alias, ACM ARN, `sni-only`,
  `TLSv1.2_2021`을 설정하고, 값이 없을 때만 기존 D-034 기본-domain fallback을 유지한다.
- `terraform validate`와 실제 tfvars를 제외한 clean mock regression 29/29가 통과했다. 실제
  `meetingmind-nonprod` 전체 refresh plan은 exit 0 `No changes`로 수렴해 CloudFront 또는 다른 AWS
  resource mutation이 필요하지 않음을 확인했다. 기존 Cloud Map `failure_threshold` deprecation
  warning만 남는다.
- 공개 DNS는 `app.meetingmind.co.kr` → `d3tplbf18qeekd.cloudfront.net.` CNAME을 반환하고,
  custom-domain HTTPS root와 `/api/v1/auth/session`은 모두 200을 반환했다. API 계약, ERD와 데이터
  모델은 변경되지 않았다.
- 이 작업은 이미 연결된 NonProd viewer domain의 drift만 수렴한다. Route 53, Terraform-managed ACM,
  WAF, ALB HTTPS, LiveKit WSS, autoscaling/SLO·부하·장애/rollback과 Production 전환은 계속 open이다.

## T059-S4 NonProd Google Login 503 Hotfix

- `app.meetingmind.co.kr` 로그인 화면과 Frontend build에는 `VITE_GOOGLE_CLIENT_ID`가 설정돼 Google
  Identity Services 버튼이 정상 렌더링됐다. 화면의 `play.google.com/log` CORP console 메시지는 버튼의
  부수 telemetry 요청이며 MeetingMind `/api/v1/auth/google` 503과 별개다.
- Auth `GoogleJwtCredentialVerifier`는 허용 client ID가 비면 credential parsing 전에
  `503 AUTH_PROVIDER_UNAVAILABLE`을 반환한다. 기존 NonProd Auth ECS environment에는
  `AUTH_GOOGLE_CLIENT_IDS`가 전혀 없어 BFF가 이를 `503 AUTH_SERVICE_UNAVAILABLE`과
  `인증 서비스에 일시적으로 연결할 수 없습니다.`로 정규화했다.
- Terraform에 공개 식별자 목록 `auth_google_client_ids`를 추가해 Auth task environment로 전달하고,
  browser-facing BFF runtime은 목록이 비어 있으면 runtime gate에서 fail closed하도록 했다. 실제
  gitignored NonProd tfvars에는 Frontend와 같은 Google OAuth web client ID를 설정했다. OAuth client
  secret과 Google credential은 Terraform state나 task environment에 추가하지 않았다.
- `terraform fmt -check -recursive`와 실제 tfvars를 제외한 clean `terraform validate`, mock regression
  30/30이 통과했다. 회귀는 task environment 전달과 client ID 없는 BFF runtime plan 거부를 포함한다.
  기존 Cloud Map `failure_threshold` deprecation warning만 남는다.
- 이후 AWS SSO profile로 실제 plan/apply를 수행해 Auth task definition revision 5를 배포했고, 사용자가
  `app.meetingmind.co.kr`에서 실제 Google 로그인과 세션 진입 성공을 확인했다. 따라서 T059-S4를
  완료 처리했으며 API 계약, ERD와 데이터 모델은 변경되지 않았다.

## T035-H1 Join Now LiveKit Audience Hotfix

- 배포 증상은 회의 생성 후 `POST /api/v1/meetings/{meetingId}/livekit-token`이 401을 반환하고 BFF가
  세션을 무효화해 로그인 화면으로 이동하는 것이었다. 배포된 BFF는 해당 route를 논리
  `LIVEKIT` downstream으로 분류하면서 `meetingmind-livekit` access JWT를 선택했지만, 현재 HTTP
  목적지와 ACL 소유자인 Core는 target mode에서 `meetingmind-core` audience만 검증했다.
- `ProxyRoute`에 실제 resource audience를 명시해 목적지 장애 정책과 JWT audience를 분리했다.
  LiveKit token과 transcription route는 기존 LiveKit timeout, bulkhead, circuit 및 오류 매핑을
  유지하면서 Core access JWT를 사용한다. Browser API, Auth token schema, Core API와 데이터 모델은
  변경하지 않았다.
- BFF 전체 `./gradlew test`가 통과했고 회귀는 LiveKit route가 `meetingmind-core` audience로 token을
  요청하면서 `DownstreamService.LIVEKIT` 정책으로 Core URL을 호출하는 조합을 검증한다.
- ARM64 image `join-now-audience-20260727-8dd5f7fe4809`를 ECR에 push했고 child manifest digest
  `sha256:a9d46431a7382498b7bc04f66cf4b70664dc2ca6e772a5378b74e71ea2bd695f`만 Terraform에 반영했다.
  plan은 BFF task definition replacement와 BFF ECS service in-place update만 포함했고 저장한 plan을
  그대로 apply해 revision 6으로 전환했다.
- 첫 revision 6 task는 Valkey connection initialization 2초 timeout으로 한 번 종료됐으나 ECS 재시도
  task가 정상 기동했다. 최종 BFF는 desired/running `1/1`, ALB target `healthy`, service stable이며
  공개 root와 `/api/v1/auth/session`은 200이다. 실제 로그인 세션의 livekit-token 200과 `Join now`
  화면 진입 확인 전까지 T035-H1은 open으로 유지한다.

## T059-S5 Realtime STT WebSocket 503 Hotfix

- `2026-07-27 01:55 KST` 실제 Core 로그에서 `POST https://stt.meetingmind.internal:8083/internal/v1/transcriptions returned 503`을 확인했다. 배포된 Realtime STT task definition에는 코드가 필수로 요구하는 `PUBLIC_WS_BASE_URL`이 없었고, deployment smoke는 STT target attachment와 `/ws/egress-audio/*` CloudFront/ALB route도 의도적으로 닫아 둔 상태였다.
- Terraform은 명시적 HTTPS `stt_public_ws_base_url`을 STT task에 전달하고 값이 없는 deployment-smoke STT runtime을 거부한다. CloudFront는 cache-disabled WebSocket behavior를 ALB origin에 추가하고, ALB는 해당 path만 HTTPS `8083` STT target으로 전달한다.
- STT readiness는 `MANAGEMENT_SERVER_ADDRESS=0.0.0.0`과 ALB SG에만 열린 HTTP `9083` health check를 사용한다. `/internal/*`은 기존 Core mTLS workload filter를 유지하고 공개 WebSocket은 기존 session-bound HMAC token 검증을 그대로 사용한다.
- 코드/인프라 변경 후 clean Terraform mock regression은 30/30 통과했다. 기존 Cloud Map `failure_threshold` deprecation warning만 남는다. 실제 AWS plan/apply와 WSS/회의 E2E 결과는 이어서 기록한다.
- 첫 실제 plan은 `frontend_edge`의 광범위한 `depends_on = [module.alb]` 때문에 STT target 교체가 S3 account data를 apply-time unknown으로 전파해 frontend bucket 교체를 계획했다. apply하지 않고 해당 module dependency를 제거했으며 `alb_dns_name` 입력의 암시적 의존성과 기존 gate dependency만 유지했다. 두 번째 plan은 frontend bucket/object 보존과 STT 관련 리소스만 포함했다.
- 저장한 두 번째 plan을 apply해 STT task definition revision 7, HTTPS `8083` target group, HTTP `9083` readiness SG rule, ALB WebSocket listener rule과 CloudFront behavior를 배포했다. 최종 ECS service는 desired/running `1/1`, rollout `COMPLETED`, target `healthy`다.
- `https://app.meetingmind.co.kr/ws/egress-audio/invalid-session`에 WebSocket upgrade header를 보내고 token을 생략한 negative check는 CloudFront를 거쳐 `403`으로 거부됐다. 이는 공개 route 도달성과 token fail-closed 경계를 함께 확인한다.
- 최종 실제 refresh plan은 `No changes`로 수렴했다. 제어 가능한 로그인 브라우저 탭이 없어 실제 microphone track의 start→audio→dialogue는 이 세션에서 실행하지 못했으므로 T059-S5는 해당 사용자 E2E 확인 전까지 open으로 둔다.

## T035-H2/T059-S6 Realtime Dialogue and Service-Call Boundary Audit

- 사용자 화면에서 transcription start가 성공해 `STT Active`가 됐지만 live transcript가 비어 있었다. split runtime 코드를 대조한 결과 Realtime STT는 `meetingmind_stt` DB에 final segment/status를 저장하는 반면, Core `/api/v1/meetings/{meetingId}/dialogue`는 Core DB의 start compatibility row와 segment만 읽고 partial만 STT gateway에서 가져오고 있었다. 따라서 final event가 빠르게 확정되면 partial 폴링 구간을 놓치고 STT DB에 저장된 문장은 화면에 영구히 나타나지 않았다.
- `TranscriptionGateway`에 remote authoritative transcript/status snapshot 계약을 추가하고 `ConfiguredTranscriptionGateway`가 선택 구현으로 전달하게 했다. in-process rollback은 기본 empty snapshot으로 기존 Core DB 경로를 유지한다. Core controller는 기존 `meetingTranscript(actor, meetingId)` 호출로 사용자·Meeting ACL을 먼저 검증한 뒤 remote mode에서 STT transcript status/final segments와 partial을 조합한다. stop 응답도 remote status를 우선한다.
- 서비스 호출 대조에서 BFF의 AI route가 실제 `BFF_AI_BASE_URL=core.meetingmind.internal`로 Core를 호출하면서 `meetingmind-ai` access JWT를 선택하는 두 번째 audience 불일치를 확인했다. Meeting/Project AI route를 LiveKit route와 같은 방식으로 논리 AI timeout/circuit/error 정책과 실제 Core resource audience를 분리해 `meetingmind-core` JWT를 사용하도록 수정했다.
- `contracts/service-call-boundaries.md`를 추가해 Browser→BFF, BFF→Auth/Core, Core→Auth/STT/AI/LiveKit, LiveKit Egress→public token WSS, STT/AI→provider의 caller·인증·SG와 데이터 owner를 한 표로 고정했다. Auth/Core/STT/AI DB 직접 교차 조회는 금지한다.
- 이번 수정은 live dialogue read를 해결하지만 Core의 report/task/Meeting AI/embedding context는 여전히 Core DB transcript를 읽는다. STT source ID, revision, duplicate retry와 completion event를 보존하는 projection/context adapter가 없으므로 T059-S7로 분리했고 완료로 주장하지 않는다.

### Verification and NonProd rollout

- Backend targeted controller/gateway tests와 전체 `./gradlew test`, BFF targeted proxy tests와 전체 `./gradlew test`, `git diff --check`가 모두 통과했다.
- Core/BFF ARM64 이미지를 빌드하고 최신 Trivy DB로 Alpine/JAR HIGH/CRITICAL 0건을 확인했다. ECR child manifest digest는 Core `sha256:617932440c5a5bc19736d4284bfd26a75b3f63f971593712cf28ce9da1e76256`, BFF `sha256:8fde30012e1cd4ce910e39f29141924c7e6286730195c46bc77c5d2e1793a361`이다.
- Terraform validate는 성공했고 기존 Cloud Map `failure_threshold` deprecation warning만 남았다. 실제 plan은 Core/BFF task definition replacement와 두 ECS service in-place update만 포함한 `2 add, 2 change, 2 destroy`였으며 RDS/S3/CloudFront/ALB/SG 변경은 없었다. 저장 plan을 apply해 Core revision 5와 BFF revision 7을 배포했다.
- 최종 두 서비스는 desired/running `1/1`, pending `0`, application container `HEALTHY`, rollout `COMPLETED`다. BFF ALB target `10.20.17.199:8081`은 healthy이며 공개 root/session/CSRF는 모두 HTTP 200이다. 최종 실제 Terraform plan은 `No changes`다.
- 로그인된 Safari 세션을 자동 제어할 수 없어 실제 microphone 발화가 새 dialogue 응답에 표시되는지는 사용자의 현재/새 회의 확인이 필요하다. 따라서 T035-H2와 T059-S6은 배포 완료 상태지만 실제 signed-in E2E 전까지 open으로 유지한다.

## T059-S8 Realtime STT Rejoin and Speaker Hotfix

- 사용자 검증에서 live transcript가 표시되는 것은 확인됐지만 화자 영역에 `stt-{session UUID}`가 노출됐고, `Leave` 후 같은 회의 재참여 시 STT가 재개되지 않았다.
- 화자 문제의 원인은 Core가 보유한 인증 사용자 display name이 `TranscriptionStartCommand`에서 STT 내부 요청으로 전달되지 않았고, STT가 session UUID를 speaker label과 speaker name 양쪽에 저장한 것이었다. 내부 시작 계약에 최대 100자의 `participantDisplayName`을 추가해 STT speaker에 사용하고, Frontend selector는 이미 저장된 정확한 `stt-{UUID}` 형식을 `참여자`로 정규화한다.
- 재참여 문제의 원인은 `Leave`가 STT를 중지하지 않고 LiveKit track만 제거해 STT가 사라진 track으로 egress를 재시작한 뒤 실패했으며, Core compatibility transcript는 계속 `PROCESSING`, browser sessionStorage에는 이전 session ID가 남아 재참여 UI가 이를 활성 session으로 오인한 것이었다.
- `Leave`는 소유한 session을 stop한 뒤 sessionStorage/ref를 제거한다. remote authoritative status가 `COMPLETED`/`FAILED`이면 Core와 STT transcript를 새 track으로 재개하고 기존 segment·최초 시작 시각을 보존한다. 새 session의 provider timestamp는 기존 최대 `endMs`를 offset으로 더해 과거 발화 사이에 섞이지 않게 했다. remote status가 `PROCESSING`이면 기존 중복 시작 거부를 유지한다.
- API 필드가 추가됐지만 DB schema, ERD와 entity 관계는 바뀌지 않았다. durable STT→Core/AI projection 미완료 경계(T059-S7)도 그대로 유지한다.
- 배포 전 검증은 Frontend 99 tests/build와 lint 0 error(기존 경고 3), Core 전체 Gradle test, Realtime STT 전체 Gradle test, `git diff --check`가 통과했다.
- Core/Realtime STT ARM64 이미지를 빌드하고 최신 Trivy DB로 Alpine/JAR HIGH/CRITICAL 0건을 확인했다. ECR child manifest digest는 Core `sha256:7fd5a6e65eca6e0a93989d95d745e7d4af1edc23aeafd2fa09d2e5a7eb9daaf1`, Realtime STT `sha256:54adc15dbdb7193760ff4f337251eaf1ee4519017d9493f4ca9c1a0e4c01bc37`이다.
- 실제 Terraform plan은 Core/STT task definition replacement와 두 ECS service in-place update만 포함한 `2 add, 2 change, 2 destroy`였고 저장 plan을 그대로 apply했다. Core revision 6과 Realtime STT revision 8은 desired/running `1/1`, pending `0`, rollout `COMPLETED`, application container `HEALTHY`, cert-loader exit 0이다. STT ALB target도 healthy이며 최종 refresh plan은 `No changes`다.
- Frontend production build를 private S3 origin에 배포하고 CloudFront invalidation `IEBXKKO1DBYY4AR7UCW8D5JZ9S` 완료를 확인했다. 공개 index는 새 `/assets/index-B-gxv_Jk.js`를 참조하고 asset 및 `/api/v1/auth/session`은 HTTP 200이다.
- 사용자 직전 화면이 실제 Join→STT audio→확정 dialogue를 증명하므로 T035-H1, T059-S5, T059-S6은 완료 처리했다. 2026-07-27 후속 확인으로 display name과 leave→rejoin STT 재개도 통과해 T059-S8을 완료 처리했다.

## T059-S9 Realtime STT Partial Revision Hotfix

- 사용자 화면에서 확정 row가 아닌 `LIVE` row 하나가 같은 내용을 반복하며 비정상적으로 길어졌다. Soniox mapper는 provider의 새 final token과 현재 non-final token을 조합해 같은 `soniox-live-{utterance}` segment ID의 최신 전체 가설을 내보낸다. 반면 공통 assembler는 새 가설이 이전 문장의 완전한 prefix가 아니면 두 전체 가설을 문자열로 연결했고, provider가 중간 단어를 수정할 때마다 누적 문장이 증식했다.
- D-042에 따라 mapper→assembler `PARTIAL.text`를 최신 전체 snapshot으로 고정했다. assembler는 같은 partial ID의 후속 event를 교체하며, final 중복 키·fingerprint 처리와 빈 final boundary의 마지막 partial fallback은 변경하지 않는다.
- prefix가 달라지는 partial snapshot 단위 회귀와 Soniox mapper→assembler 통합 회귀를 추가했고, targeted test와 Realtime STT 전체 `./gradlew test`, `git diff --check`가 통과했다.
- ARM64 Realtime STT image `partial-revision-20260727-1`을 빌드하고 Trivy로 Alpine/JAR HIGH/CRITICAL 0건을 확인했다. ECR digest는 `sha256:01b15198aeaebadb93533f69a80c8de62ba865461dbd1784967e190af51f0309`다.
- 실제 Terraform plan은 STT task definition replacement와 STT ECS service in-place update만 포함한 `1 add, 1 change, 1 destroy`였고, 저장 plan을 그대로 apply해 Realtime STT revision 9를 배포했다. task는 desired/running `1/1`, pending `0`, rollout `COMPLETED`, application container `HEALTHY`, cert-loader exit 0이며 STT ALB target은 healthy다. 배포 후 최종 refresh plan은 `No changes`로 수렴했다.
- 실제 마이크 입력에서 긴 문장을 발화하며 provider가 중간 가설을 수정할 때 `LIVE` row가 반복 증식하지 않고 final로 전이하는지는 사용자 확인 전까지 T059-S9을 open으로 유지한다.

## T059-S7 Durable STT Projection and AI Embedding Worker

- AI chat 503과 근거 없음 응답을 분리해 추적했다. AI DB URL은 전용 `meetingmind_ai_app` 계정으로 Core DB를 가리키도록 이미 교정됐지만, Core runtime이 V24 이후 생성된 `ai_usage_events`를 읽고 기록할 권한이 없었고, STT가 소유한 완료 segment가 Core DB로 영속 projection되지 않았으며, ECS AI task에는 구현돼 있던 embedding worker가 실행되지 않았다.
- D-043에 따라 Core의 transcription stop 성공 뒤 authoritative STT snapshot을 가져와 같은 transaction에서 speaker/segment를 원본 STT ID로 교체한다. 동일 snapshot 재시도는 no-op이고, 이미 완료된 transcript의 수정 snapshot은 `FULL_REINDEX`를 정확히 한 번 enqueue한다. Core DB가 report/task/Meeting AI/RAG의 읽기 모델이고 STT DB는 Core나 AI가 runtime에 직접 조회하지 않는다.
- JDBC/JPA/in-memory store에 원자 교체와 embedding enqueue 계약을 추가했다. 기존 segment anchor를 먼저 제거하고 speaker/segment를 교체하며 JPA persistence context를 clear해 JDBC 변경 뒤 stale entity를 반환하지 않게 했다. controller/domain 회귀와 실제 PostgreSQL integration test는 STT ID 보존, 최초 완료 job 1개, 동일 재시도 추가 job 0개, 수정 완료 `FULL_REINDEX` 1개를 검증한다.
- V26은 `meetingmind_core_app` role이 존재할 때 `ai_usage_events`에만 `SELECT, INSERT`를 부여한다. RDS master secret을 일회성 ECS Flyway task에 주입해 v25→v26을 적용했고 로그에서 `Successfully applied 1 migration ... v26`과 exit 0을 확인했다. 임시 execution-role inline policy와 task definition은 즉시 삭제했다.
- ECS task module에 app image/env/secrets를 재사용하는 `background_workers`를 추가하고 NonProd AI에 `python -m app.embedding_worker` 컨테이너를 배치했다. clean Terraform regression 30/30과 실제 plan의 Core/AI task definition replacement 및 service update 4개 address만 확인한 뒤 저장 plan을 apply했다. 결과는 Core revision 7, AI revision 4이며 두 task 모두 `RUNNING/HEALTHY`; AI worker도 계속 실행 중이다.
- 배포 전 Core 전체 `./gradlew test`, 실제 PostgreSQL `JdbcWorkspaceStoreIntegrationTest`, 빈 PostgreSQL V1→V26 `MigrationIntegrationTest`, `python3 -m compileall ai/app`, Terraform regression 30/30과 format이 통과했다. Core ARM64 ECR child digest는 `sha256:42ecbc3c8e4ad21fce9c5a8c2368fe6b6c200c0aa43fffe6dc6bd0e34dd152f4`이고 ECR scan은 완료, HIGH/CRITICAL 0건이다.
- 배포 직후 원문을 출력하지 않는 read-only STT/Core hash audit에서 완료 STT 1건 중 `needsProjection=1`을 확인했다. authoritative STT snapshot으로 그 1건만 transaction backfill했고 `applied=1`; 재감사는 `alreadyProjectedExact=1`, `needsProjection=0`이다. completion trigger가 만든 embedding job generation 1을 worker가 첫 시도 954ms에 완료해 8 chunks를 활성화했고 queue는 pending/processing/failed 모두 0이다. 일회성 master-secret IAM 권한과 audit/backfill task definition도 삭제했다.
- 인앱 브라우저에서 공개 root는 정상 로드됐지만 `/spaces`가 `/login`으로 이동해 로그인 계정의 Project AI 질문은 실행하지 않았다. 따라서 server-side STT→Core→active chunk 경계와 배포는 통과했지만, 실제 BFF→Core→AI source 응답은 사용자의 로그인 UI 확인 전까지 T059-S7의 마지막 acceptance로 남긴다.

## T059-S10 Terminal Egress Stop and Projection Recovery

- 사용자 재현 시 STT는 `Active`였지만 Core stop이 503을 반환하고 Project AI가 근거 없음으로 답했다. CloudWatch에서 LiveKit stop은 HTTP 412 `FAILED_PRECONDITION`과 `EGRESS_FAILED cannot be stopped`였고, Core projection은 `permission denied for table chunk_source_segments`에서 transaction rollback된 것을 확인했다. STT DB에는 완료 원문이 남아 있어 AI DB URL이나 원문 손실 문제가 아니었다.
- `LiveKitEgressService`는 412와 terminal egress 상태가 모두 일치할 때만 stop을 멱등 완료로 처리한다. durable session이 이미 `FAILED`인 stop 재시도도 transcript가 `PROCESSING`이면 `completeTranscript`로 조정해 이후 Core snapshot pull을 막지 않는다. terminal/non-terminal 분류와 failed-session 조정 회귀를 추가했다.
- V27은 `meetingmind_core_app`가 존재할 때 `chunk_source_segments`에 `SELECT, DELETE`를 부여한다. 운영 적용 후 실제 runtime credential 감사에서 기존 bootstrap의 `INSERT, UPDATE`가 남아 있음을 발견했다. 이미 적용된 V27 체크섬은 수정하지 않고 append-only V28에서 `INSERT, UPDATE`를 명시적으로 회수한다. 실제 PostgreSQL V1→V28 통합 테스트는 과도한 기존 권한을 재현한 뒤 `SELECT, DELETE=true`, `INSERT, UPDATE=false`를 확인한다.
- Core/STT ARM64 이미지를 빌드해 ECR scan `COMPLETE`, finding 0건을 확인했다. 최종 child digest는 V28 포함 Core `sha256:2876feaae8e8443827dddf37631226534a74c68ebf0d8479d092c5970abf2137`, STT `sha256:de032b4bf4245daf9732afc8a97a61583ba658f47d259732dab1e062ecb7a0e4`다.
- 서비스 배포 전에 RDS master secret을 일회성 ECS Flyway task에만 주입해 v26→v27을 적용했다. 로그에서 27개 migration 검증, `Migrating ... version 27`, `Successfully applied 1 migration ... v27`, exit 0을 확인했다. 실제 runtime 권한 감사에서 발견한 기존 과다 권한은 같은 방식으로 v27→v28을 선적용했고 28개 migration 검증, v28 성공과 exit 0을 확인했다. `meetingmind_core_app` credential 재감사는 `SELECT/DELETE=true`, `INSERT/UPDATE=false`였다. 각 실행의 임시 IAM policy와 task definition은 즉시 제거했다.
- 첫 Terraform plan은 Core/STT task definition replacement와 두 service update만 포함한 `2 add, 2 change, 2 destroy`였고 Core revision 8/STT revision 10을 배포했다. V28 포함 Core의 후속 plan은 Core task/service만 포함한 `1 add, 1 change, 1 destroy`였으며 저장 plan으로 Core revision 9를 배포했다. Core/STT는 desired/running `1/1`, pending 0, rollout `COMPLETED`, application container `HEALTHY`; STT target도 healthy이고 최종 plan은 `No changes`다.
- 원문을 출력하지 않은 read-only 감사에서 `testin`은 STT `COMPLETED` 3건/49 segments/590자였지만 Core는 `PROCESSING` 3건/0 segments, embedding job/chunk 0건이었다. active/stopping session이 없는 세 회의만 STT ID·sequence·시간·text를 exact 검증하며 transaction backfill했고 `applied=3`, `segments=49`, `jobsExpected=3`으로 완료했다.
- 최종 감사는 STT/Core 모두 `COMPLETED` 3건, 49 segments, 590자로 일치했다. AI worker는 embedding job 3건을 모두 `COMPLETED`로 처리했고 active transcript chunks 12개 전부 vectorized됐다. 일회성 감사/백필 task definition과 AI execution role의 임시 master-secret policy를 모두 제거했다.

## T059-S11 Durable AI Evidence Reconciliation

- 단일 root agent가 Core/Data/계약 순서로 구현했으며 병렬 작업이나 shared-file 충돌은 없었다. 기존 사용자의 `.gitignore`, AWS 문서 및 DNS/NonProd README 변경은 수정하지 않았다.
- Core는 `PROCESSING`, `FAILED`, 또는 segment 없는 `COMPLETED` transcript를 최대 100개 이하의 설정 가능한 batch로 선택한다. NonProd에서는 30초 간격·20개 기본값으로 활성화하고, 각 meeting의 authoritative STT snapshot을 기존 Core→STT mTLS gateway로 조회한다.
- terminal snapshot만 기존 원자 projection을 재사용한다. meeting ID 불일치, 시간 범위 overflow, provider/DB 오류는 원문 없이 meeting ID와 예외 종류만 기록하고 해당 후보에 격리한다. 첫 완료는 기존 `TRANSCRIPT_COMPLETED` trigger를, 이미 완료된 빈/변경 projection은 `FULL_REINDEX`를 사용해 AI worker가 새 generation을 처리하게 한다.
- Project Knowledge 생성 transaction이 감사 로그의 Space를 찾지 못해 rollback되던 원인은 JPA/JDBC audit resolver가 `project_knowledge`를 조회하지 않은 것이었다. 두 persistence 경로 모두 `project_knowledge.id → space_id`를 지원해 공식 지식과 해당 embedding job이 실제로 커밋되게 했다.
- DB migration이나 새 API는 없다. STT가 원본, Core가 report/Meeting AI/RAG용 derived projection이라는 소유권과 권한 선필터 원칙은 유지하며 ERD의 물리 relation도 바뀌지 않는다.
- 검증은 `backend ./gradlew test`, 전용 pgvector PostgreSQL을 초기화한 `./scripts/run-db-tests.sh`, `backend ./gradlew bootJar`, `terraform validate`, `terraform fmt -check`, `git diff --check`가 통과했다. PostgreSQL 회귀는 Project Knowledge 감사 commit, 누락 projection 후보 선택, 동일 STT ID 복원과 `FULL_REINDEX` generation을 포함한다.
- `terraform test`는 gitignored 실제 `terraform.tfvars`가 runtime/mTLS를 활성화해 foundation 기본값 단언과 충돌하면서 0 passed, 2 failed, 28 skipped였다. 이번 환경 변수 추가의 parse/validation은 통과했으며 테스트 실패는 새 assertion이나 리소스 오류가 아니라 기존 로컬 변수 격리 문제다.
- 최초 구현 검증 시점에는 운영 상태를 변경하지 않았고, 아래 제한된 rollout과 자동 복구 검증을 별도 단계로 수행했다.
- 첫 NonProd rollout은 Core revision 10과 digest `sha256:7e5d79a4b818f8ac52252acc084a8cd6f998789406a7f993ff47b8964a5e251f`를 배포했다. task는 healthy였지만 첫 reconciliation에서 후보 8건 중 상태만 조정 가능한 2건은 성공하고 segment 교체가 필요한 6건은 `permission denied for table transcript_segments`로 실패했다. 기존 V27/V28은 `chunk_source_segments` 최소 권한만 보완했고 원자 교체의 다음 두 delete 대상을 빠뜨린 것이 원인이었다.
- append-only V29는 `meetingmind_core_app`에 `transcript_segments`, `meeting_speakers`의 `DELETE`만 추가한다. 전용 PostgreSQL V1→V29 privilege/projection test를 통과한 뒤 RDS-managed master secret을 제한적으로 주입한 일회성 ECS Flyway task로 v28→v29 한 건을 선적용했고 exit 0을 확인했다. 임시 execution-role policy는 삭제하고 task definition은 비활성화했다.
- V29 포함 최종 ARM64 Core digest `sha256:f954a235e11cfb261fc20d25726c1ce58bcc6ed1177ee8e0e477375ee998e043`는 local Trivy와 ECR scan 모두 Alpine/JAR HIGH/CRITICAL 0건이다. 저장 plan은 Core task definition/service만 변경했고 revision 11은 desired/running `1/1`, pending 0, rollout `COMPLETED`, application `HEALTHY`다. 공개 root/session은 200이며 최종 Terraform refresh plan은 `No changes`다.
- 권한 보완 직후 reconciliation은 후보 8건을 `projectedCount=8`, `failedCount=0`으로 처리했다. 완료 전사의 embedding generation 2가 attempt 1에서 성공해 29 chunks를 활성화했고 worker queue는 pending/processing 0이다. 남은 후보 7건은 authoritative STT 상태 자체가 `FAILED`인 terminal 기록이며, 로그인된 회의록/Meeting AI/Project AI source E2E 전까지 task는 open으로 유지한다.

## T059-S12 Google Callback Remount Hotfix

- 운영 `app.meetingmind.co.kr`에서 Google SDK, browser client ID, BFF/Auth 경계가 정상인 상태로 실제 Google 로그인과 `/spaces` 진입을 확인했다. Safari console의 `play.google.com/log` CORP 메시지는 Google telemetry 요청이며 로그인 credential 흐름의 실패 원인이 아니다.
- 실제 무반응 원인은 `GoogleCredentialButton`이 module-global `initializedClientId`로 같은 client ID의 후속 SDK 초기화를 생략한 것이었다. 로그인 요청 중 화면이 unmount되면 Google SDK callback은 이전 컴포넌트의 `disabled=true` ref를 계속 보유했고, 로그인 화면 재진입 후 새 버튼을 눌러도 callback이 조용히 종료될 수 있었다.
- Google script와 client ID별 SDK 초기화는 그대로 재사용하되 SDK callback은 module dispatcher만 참조하고, 버튼 컴포넌트가 마운트될 때마다 dispatcher의 현재 credential handler를 교체하도록 최소 수정했다. Google credential/API/CSRF 계약과 client ID는 바꾸지 않았다.
- `frontend npm test` 99개와 `npm run build`가 통과했다. 새 Playwright 회귀는 첫 Google 요청을 보류한 채 로그인 화면을 unmount하고 다시 진입한 다음 SDK 초기화 1회를 유지하면서 두 번째 Google callback과 `/api/v1/auth/google` 요청이 실행되는 것을 검증해 1/1 통과했다.
- 최종 build asset `index-CuNObSdC.js`를 NonProd private S3에 먼저 업로드하고 no-cache `index.html`을 마지막에 교체했다. CloudFront distribution `E3VEAX4F98BOIE` invalidation `IA2C8FBILNV1J6YIL8KFK9YH7L` 완료 후 원격 HTML/JS SHA-256이 로컬 build와 일치했다.
- 운영 브라우저가 최종 asset을 로드한 상태에서 Google 로그인→로그아웃→새로고침 없는 `/login` 재진입→Google 재로그인이 모두 `/spaces`로 성공했다. 최종 load 이후 SDK 중복 초기화 경고는 없었고, 검증용 Browser session은 즉시 로그아웃해 T059-S12를 완료했다.
- 2026-07-27: Space 생성 분야 선택 UI가 조회하는 `GET /api/v1/glossary/categories`를 Core allowlist에 추가했다. 경로는 고정 literal이며 다른 glossary 관리 경로는 열지 않았다. `ProxyRouteRegistryTest`로 Core 분류를 검증했다.
