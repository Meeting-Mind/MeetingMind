# Implementation Log: BFF Auth and Gradual MSA

## Scope

M001 문서·설계 기준선, M002의 T010~T016 Web BFF 호환 경로, M003의 T020~T023 Browser session cutover와 M004의 T030 Auth 보안 shared contract, T031 foundation, T032 credential/session/revoke runtime, T033 KMS signing/JWKS/Resource validator 및 T036 CI security hardening까지 구현했다.

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
| 2026-07-20 | T022 session expiry regression | Web BFF | Codex | `bff/**` | 보호 요청의 세션 부재와 절대 만료를 모두 `401 SESSION_INVALID` JSON으로 통일해 Frontend 전역 세션 만료 처리가 누락되지 않게 수정 |
| 2026-07-16 | T023 BFF Rollout | Integration | Codex | BFF rollout readiness/metrics/config/tests, Compose/env, `rollout-runbook.md`, 관련 설계·검증 문서 | traffic drain flag, bounded metrics, 단계별 guardrail과 안정 BFF rollback 기준 구현 |
| 2026-07-17 | T030 Auth Security Decisions | Shared Contract | Codex | auth/security requirements, `clarify.md`, `research.md`, Auth/event contracts, data model/ERD/plan/tasks/analyze | refresh family, JWT/JWKS, revoke event, mTLS workload identity와 logout-all 재인증 확정 |
| 2026-07-17 | T031 Auth Service Foundation | Auth Service | Codex | `auth/**`, `compose.local.yml`, `.github/workflows/ci.yml`, root/config 및 관련 설계·검증 문서 | 독립 서비스·전용 PostgreSQL, forward-only schema, 최소 권한 runtime 계정과 health/CI 경계 구현 |
| 2026-07-17 | T032 Auth Runtime | Auth Service | Codex | `auth/src/main/**`, `auth/src/test/**`, Compose/CI/root 및 Auth 계약/data/plan/analyze | local/Google 자격 검증, refresh family rotation/reuse, revoke-all, 감사/outbox와 workload/fail-closed signer 경계 구현 |
| 2026-07-17 | T033 Auth Keys/JWKS | Auth Service | Codex | `auth/**`, `backend/**/auth/target/**`, Compose/env 및 Auth 계약/data/plan/tasks/analyze | AWS KMS RS256 signer, rotation key ring, 내부 JWKS와 비활성 Resource validator 구현 |
| 2026-07-18 | T036 CI Security Hardening | Integration | Codex | `bff/build.gradle`, `auth/build.gradle`, `.gitleaksignore`, 관련 spec/plan/tasks/implement | BFF/Auth 수정 가능 취약점 제거와 테스트 fixture Gitleaks 오탐 정밀 예외 처리 |

동시에 같은 파일을 수정한 다른 agent는 없으며 통합은 Requirements → Spec/Plan → Contracts/Data → legacy reference → analysis 순서로 진행한다.

## Changes

- `POL-SESSION-01`과 `NFR-SEC-02` 충돌을 브라우저 무토큰, BFF 암호문, Auth hash의 세 역할로 해소했다.
- 일반 세션 유휴 60분/절대 12시간, Remember me 7일 sliding 유휴/14일 절대 만료를 정책 기준선으로 확정했다.
- 별도 Spring Boot Web BFF, Spring Session Redis, encrypted Token Vault, 내부 비대칭 access/JWKS 방향을 문서화했다.
- AWS EKS 단일 리전 Multi-AZ와 LiveKit Cloud, 서비스별 DB 소유권과 failure behavior를 문서화했다.
- 현재 Backend token API는 Phase 1 compatibility/rollback 대상으로 보존하고 목표 browser 계약에서는 public refresh를 제거했다.
- 독립 `bff` Gradle 프로젝트를 Java 21/Spring Boot 3.5.14로 추가했다. 기존 Backend와 같은 버전을 사용해 별도 version 선택을 만들지 않았다.
- `spring-boot-starter-actuator`, `spring-boot-starter-data-redis`, `spring-session-data-redis`는 T010의 health/probe와 외부 세션 저장소를 구현하기 위해 추가했다. security/proxy/회복성 라이브러리는 후속 task 전까지 추가하지 않았다.
- 운영 readiness에는 Redis를 포함하고 liveness에서는 제외했다. Redis 장애 시 Pod 재시작 반복 대신 traffic 수용만 중지하는 경계를 적용했다.
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
- local/test key가 비어 있거나 256-bit가 아니면 임시 key나 평문 fallback 없이 시작을 거부하고, 운영은 KMS provider와 EKS workload IAM을 사용한다.
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
- 내부 endpoint는 mTLS SPIFFE workload identity, NetworkPolicy와 principal/endpoint allowlist를 동시에 요구한다. 인증서 제품은 Q-012/T040에 남기되 shared secret/client credential 방식으로 계약을 되돌리지 않는다.
- 모든 기기 로그아웃은 최근 10분 `authenticatedAt` 또는 local 비밀번호/새 Google credential 재인증을 요구한다. Q-016은 결정됐지만 실제 UI/API는 T032 revoke-all/outbox와 T034/T035 실제 AuthSession cutover 뒤 T024에서 연결한다.
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
- T036은 CI Trivy가 탐지한 BFF/Auth의 `jackson-databind 2.21.2` HIGH 2건과 `tomcat-embed-core 10.1.54` HIGH 3건/CRITICAL 3건을 이미 Backend에서 검증한 Jackson `2.21.4`, Tomcat `10.1.55` 전체 모듈 정렬로 해소했다. 새 라이브러리나 API/DB 계약은 추가하지 않았다.
- Gitleaks가 탐지한 10건은 모두 커밋된 고정 테스트 master key 또는 잘못된 key 길이 negative fixture였다. 경로·규칙 전체를 허용하지 않고 기존 `.gitleaksignore` 정책대로 commit/path/rule/line fingerprint만 등록해 이후 실제 secret 탐지를 유지했다.

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
| T026 Auth unit and PostgreSQL integration | Pass | `AUTH_DB_INTEGRATION=true ... ./gradlew test`로 Auth 전체 테스트와 V1~V3 migration을 실행했다. reset token delivery recorder를 사용해 `202` request, opaque `mmpr_` token, 단일 소비 `400 PASSWORD_RESET_TOKEN_INVALID`, 기존 세션 revoke, 새 password login을 실제 PostgreSQL에서 검증했다. |
| T034 Core projection unit regression | Pass | `cd backend && ./gradlew test --tests com.meetingmind.demo.auth.CoreAuthUserProjectionServiceTest --tests com.meetingmind.demo.auth.AuthServiceTest --tests com.meetingmind.demo.auth.target.TargetAccessTokenValidatorTest`로 deterministic Core ID, immutable mapping replay/conflict와 target JWT validator 회귀를 검증했다. |
| T035 BFF token/client regression | Pass | `cd bff && ./gradlew test`와 target client HTTP test로 legacy session/proxy 회귀, audience별 token 선택, Auth target response의 세 audience 검증, Core projection에 Core token만 전달하는 동작을 검증했다. |
| T036 BFF/Auth tests | Pass | BFF와 Auth에서 각각 `./gradlew test`; 두 서비스 모두 `BUILD SUCCESSFUL` |
| T036 container builds | Pass | `docker build --tag meetingmind-bff:ci bff`, `docker build --tag meetingmind-auth:ci auth` 성공 |
| T036 Trivy image scan | Pass | Trivy `0.72.0`, `--ignore-unfixed --scanners vuln --severity HIGH,CRITICAL`; BFF/Auth 모두 Alpine 0건, JAR 0건 |
| T036 repository secret scan | Pass | Gitleaks `8.30.1`이 현재 브랜치 HEAD 전체 이력 57 commits/약 3.44 MB를 검사해 `no leaks found` |
| T036 diff validation | Pass | `git diff --check` 통과 |
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

M039 Markdown download 보완에서 BFF response allowlist에 `Content-Disposition`을 추가했다. Core의 attachment filename을 Browser까지 보존하며, `BffProxyControllerTest`가 해당 header 전달을 검증한다. `cd bff && ./gradlew test`가 통과했다.

## Open Implementation Gates

- Q-011~Q-013 AWS region, EKS/IaC/node, SLO/RTO/RPO.
- mTLS/SPIFFE 인증서 발급·회전 제품과 event transport 제품 선택은 Q-012/T040에서 한다. identity/event 의미는 T030 계약을 유지한다.
- T024는 T032 revoke-all 계약은 충족했지만, 실제 AuthSession을 BFF가 보유하는 T034/T035가 끝나기 전에는 연결하지 않는다.
- Auth outbox transport publisher/consumer, Phase 1 revoke 실패의 암호화된 durable retry queue와 운영 경보는 T045 출시 gate다.
- T033 Resource validator는 현재 Core 요청 경로에 의도적으로 연결하지 않았다. T034 데이터 이전 뒤 T035에서 legacy/new issuer dual validation과 BFF→Auth cutover를 함께 검증한다.
- 2026-07-20: T034 사전 점검에서 Core `users.id`가 `varchar(64)`의 `user-<uuid>` 값이고 다수의 업무 FK가 이를 참조하는 반면 Auth Service `auth_users.id`는 UUID임을 확인했다. Auth UUID canonical + Core immutable mapping projection을 선택했고, `backend` V19 `auth_user_mappings` migration을 추가했다. Core는 target `meetingmind-core` JWT를 검증한 뒤에만 `POST /internal/v1/core/auth-users/projection`으로 deterministic `user-<auth UUID>` mapping을 생성·재검증한다. mapping service는 검증된 UUID만 받아 DB mapping 책임과 JWT 검증 책임을 분리했다. legacy mapping은 이메일 자동 연결이 아닌 검증 manifest로만 적재하며 T035와 logout-all UI는 manifest/reconciliation 및 dual validation 완료 뒤 연결한다.
- 2026-07-20: Q-019~Q-022를 reset 15분·account/IP rate limit, password 변경/reset 전체 session revoke, JPEG/PNG/WebP 5 MiB opaque object storage, 단독 Space OWNER 차단·30일 anonymization으로 결정했다. Browser/Auth contracts, data model/ERD, policy와 Auth V3 lifecycle schema를 갱신했다. Auth runtime, BFF/UI, Core owner blocker API와 E2E는 T026/T027에서 후속 구현한다.
- 2026-07-20: T026 Auth Runtime 1차를 구현했다. V3의 `auth_password_reset_tokens`, `auth_password_history`, disabled/withdrawal columns을 JDBC repository와 연결했고, reset token은 domain-separated HMAC hash만 저장한다. reset request는 active local account에만 account 3/hour·IP prefix 10/hour 조건에서 configured delivery port로 전달하며 응답은 항상 `202`다. password change/reset은 최근 3 hash 재사용을 거부하고 모든 AuthSession을 각각 `PASSWORD_CHANGED`/`PASSWORD_RESET` reason으로 revoke한다. profile display name 수정과 recent-auth withdrawal도 같은 transaction에서 session revoke/audit을 처리한다. delivery provider, image storage, Core owner blocker/30일 anonymization 및 Browser BFF 연결은 아직 구현하지 않았다.
- 2026-07-20: T035 1차로 BFF TokenBundle에 `audience -> access token/expiry` 집합을 추가하고 proxy가 Core/AI/LiveKit 서비스별 audience를 선택하게 했다. `BFF_AUTH_MODE=target`에서는 Target Auth internal client가 signup/login/google/refresh/revoke를 AuthSession ID 기반으로 호출하고, 신규 login 뒤 Core projection endpoint를 Core audience token으로 호출한다. 기본 `legacy` mode와 Browser response shape는 유지한다. RS256 signing key, mTLS workload transport, target Auth/Core 실프로세스 E2E와 dual-run traffic 관측은 아직 준비되지 않았다.
- 2026-07-20: T024 BFF 1차로 `POST /api/v1/auth/logout-all`을 추가했다. BFF는 browser user/session ID를 받지 않고 Redis BffSession의 Auth UUID/AuthSession/`authenticatedAt`만 사용한다. 최근 10분이면 바로 target Auth revoke-all을 호출하고, 초과하면 password 또는 Google credential 정확히 하나를 target Auth re-authenticate로 검증한 뒤 revoke-all을 호출한다. 성공 또는 Auth revoke 호출 뒤 local BFF session/TokenBundle은 fail closed로 정리한다. CSRF 누락 거부, 인증된 요청의 204, recent/stale 재인증 분기와 local session 정리를 BFF 단위 테스트로 검증했다. Frontend는 password 재인증과 Google Identity callback 재인증을 제공하며, Google credential을 사용자가 붙여넣게 하지 않는다. 다른 브라우저의 실제 후속 요청 차단 E2E는 아직 구현하지 않았다.
- 2026-07-20: T027 1차로 target BFF가 password reset request/confirm, password change, display name profile update를 Auth Runtime internal API에 연결했다. BFF는 reset IP를 `getRemoteAddr()`의 IPv4 `/24` 또는 IPv6 `/64`로만 전달하고, browser body로 Auth User/AuthSession ID를 받지 않는다. password change 성공 또는 Auth session invalid 응답 뒤 local BFF session을 폐기하며, 잘못된 현재 비밀번호는 기존 session을 유지한다. profile response는 현재 BFF SecurityContext/session bootstrap에 즉시 반영한다. Frontend 세션 제어는 profile update, password change, logout-all을 CSRF same-origin 호출로 제공한다. reset delivery/token entry UI, profile image storage, Core 단독 OWNER blocker·anonymization은 아직 구현하지 않았다.
- 2026-07-20: T027 profile projection을 보완했다. Auth UUID와 Core ID mapping은 계속 immutable하게 유지하되, target Auth profile update 뒤 BFF가 현재 TokenBundle의 `meetingmind-core` access로 같은 internal Core projection을 재호출한다. Core는 mapping의 email identity를 다시 확인하고 displayName/pictureUrl만 갱신한다. target refresh도 TokenBundle 교체 전에 같은 projection을 재호출하므로, Core 또는 Auth 갱신 실패를 서로 성공으로 위장하지 않고 다음 authenticated projection에서 idempotent하게 재시도한다. Backend service와 BFF token/client 테스트로 ID 불변성, profile 변경, refresh projection, Core audience token 경계를 검증했다.
- 2026-07-20: T037을 완료했다. Frontend는 `/password-reset`에서 reset request와 confirm을 CSRF BFF boundary로만 호출한다. reset link token은 `#token` fragment에서 읽은 직후 URL에서 제거하고 request 범위 메모리에만 두며 browser storage, cookie, query/referrer, telemetry에 보관하지 않는다. frontend unit 34건과 production build가 통과했다.
- 2026-07-20: T038을 완료했다. Auth Service에 S3-compatible profile image storage port와 AWS SDK adapter를 추가했고 local compose의 `meetingmind-minio-local`이 healthy 상태로 검증됐다. Auth와 BFF는 JPEG/PNG/WebP의 declared MIME, magic byte, 5 MiB를 각각 확인한다. Auth는 `profile-images/<authUserId>/<random>` opaque key만 생성해 DB commit 뒤 이전 managed object를 best-effort 삭제하며 BFF/browser는 bucket credential이나 object key를 받지 않는다. validator/client tests, frontend build, compose config를 실행했다. ERD는 별도 blob table이 생기지 않아 관계 변경이 없고 data model의 object lifecycle 설명만 갱신했다.
- 2026-07-20: T039 1차를 구현했다. Core V20 `account_withdrawal_reservations`가 Auth UUID/Core mapping과 `PREPARED|COMPLETED|CANCELLED` 상태를 저장한다. Core token subject-bound reservation은 active Space OWNER를 차단하고 PREPARED 계정의 새 Space 생성·OWNER 이양을 막는다. BFF는 recent-auth/reauth 후 Core prepare, Auth disable, Core complete 순서로 호출하며 Auth 실패 때만 cancel한다. Auth disable transaction은 `pictureUrl`을 즉시 비우고 commit 뒤 managed profile image object를 best-effort 삭제한다. `COMPLETED`만 30일 scheduler로 Core displayName/pictureUrl을 익명화하고, completion 확인을 잃은 PREPARED row는 익명화하지 않는다. 빈 local PostgreSQL DB에서 V1~V20 Flyway integration test를 통과했고, 기존 개발 DB는 V11 checksum mismatch가 있어 repair 없이 검증 대상에서 제외했다. Auth outbox consumer로 completion을 재조정하는 출시 전 보강은 남아 있다.
- 2026-07-20: 실제 Core `db` profile 기동에서 multi-constructor reservation service가 Spring 주입 대상으로 선택되지 않고, scheduler의 PostgreSQL bind가 `Instant` type을 추론하지 못하는 문제를 발견했다. `ObjectProvider<Clock>` 생성자를 명시 주입 대상으로 고정하고 모든 reservation SQL의 시간을 `Timestamp`로 전달했다. Backend 전체 test와 V20 임시 DB `28080` 기동으로 scheduler 오류 없이 검증했다.
- 2026-07-21: T039 completion 응답 유실 보정을 구현했다. Auth의 `ACCOUNT_WITHDRAWAL` session-revocation outbox scheduler는 Core internal reconciliation endpoint에 at-least-once로 전달하고, 성공 event만 `publishedAt`을 기록한다. Core는 Auth workload identity를 확인한 뒤 `PREPARED` 또는 TTL 만료로 `CANCELLED`된 reservation을 `COMPLETED`로 멱등 전환한다. reservation이 없거나 Core 전달이 실패하면 event는 unpublished 상태와 오류 횟수를 유지해 재시도한다. `WithdrawalOutboxReconcilerTest`, `CoreAccountWithdrawalServiceTest`와 Auth/Backend 전체 test를 통과했다. 실제 RS256 KMS·mTLS 다중 프로세스 배포 검증은 별도 Platform 환경이 필요해 완료로 표시하지 않았다.
- 2026-07-21: T024 다중 브라우저 보강을 구현했다. `BffAuthUser`를 stable Auth UUID principal로 인덱싱하고 BFF Redis Session을 `indexed` repository로 고정했다. revoke-all, 성공한 password change와 withdrawal은 같은 user로 인덱스된 모든 BFF session과 각 encrypted Token Bundle을 폐기한다. 실제 local Redis에서 인덱스 생성·두 세션 삭제를 검증했고, Target mode BFF와 HTTP Auth/Core fixture에서 두 번 로그인한 뒤 첫 브라우저 `logout-all`이 두 번째 브라우저 `/auth/session`을 즉시 unauthenticated로 만드는 E2E를 통과했다. 실제 RS256 KMS·mTLS Auth/Core deployment E2E는 아직 수행하지 않았다.
- BFF의 public AI route는 FastAPI로 직접 전달하지 않고 Core API로 보낸다. Core가 사용자 access token으로 ACL scope를 확정한 뒤 `X-MeetingMind-Service-Token`을 사용해 AI internal endpoint를 호출한다.
