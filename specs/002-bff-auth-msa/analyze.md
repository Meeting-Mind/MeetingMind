# Consistency Analysis: BFF Auth and Gradual MSA

이 문서는 검증 기록이다. 발견 사항의 원본 수정은 requirements/spec/plan/contracts/data/tasks에서 수행하고 이 파일에는 추적 상태만 남긴다.

## Checks

- Requirements vs user decisions: Pass
- Policy vs NFR token storage: Pass
- Spec vs Plan: Pass
- Plan vs Tasks: Pass
- Browser contract vs Data Model: Pass
- Auth contract vs ERD: Pass; T030 lineage/JWT/outbox/workload 계약과 T032 물리 runtime 일치, 새 관계/migration 불필요
- Legacy vs Target labeling: Pass
- Verification coverage: T010, T011, T012, T013, T014, T015, T016, T020, T021, T022, T023, T024, T030, T031, T032, T033, T034, T035, T036 Pass; M002 Web BFF compatibility, M003 Browser session cutover와 M004 Auth Service 추출 완료

## Findings

| Severity | Finding | Impact | Recommended Action | Source | Status |
| --- | --- | --- | --- | --- | --- |
| High | Refresh 원문 비저장 원칙과 BFF 갱신 책임은 단순히 같은 저장 금지 문구로는 동시에 만족할 수 없다. | BFF가 refresh를 재사용할 수 없거나 저장소 dump에 장기 token이 노출될 수 있다. | 브라우저 무저장, BFF KMS 암호문, Auth hash/revoke로 역할을 분리한다. | `requirements/*`, `data-model.md`, `plan.md` | Resolved in docs |
| High | 로그아웃 access 차단을 중앙 조회 없이 구현하면 event 전달 전 짧은 유효 창이 남는다. | 엄격한 동기 차단과 Auth 장애 격리를 동시에 완전히 만족할 수 없다. | audience별 10분 JWT, DB revoke+transactional outbox와 Resource local `sid` denylist를 사용하고 revoke-to-apply 지연을 출시 지표로 관리한다. | `clarify.md` Q-010, `auth-service-api.md`, `auth-revocation-event.md` | T032 producer verified; signing/transport/consumer pending T033/T045 |
| High | Refresh 재사용 감지 시 폐기 범위와 동시 grace가 미정이었다. | 후속 credential만 살아남거나 정상 동시 요청과 탈취를 구분하지 못할 수 있었다. | BFF single-flight를 정상 동시성 경계로 두고 AuthSession별 family를 원자 rotation하며 재사용 시 grace 없이 해당 기기 family 전체를 폐기한다. | `clarify.md` Q-009, `data-model.md`, `auth-service-api.md` | T032 runtime verified; data cutover pending T034 |
| High | 내부 Auth endpoint workload 인증이 미정이었다. | private network 침해 시 login/refresh/revoke endpoint가 오용될 수 있었다. | mTLS SPIFFE principal, NetworkPolicy와 endpoint allowlist를 함께 적용하고 public ingress에서 내부 route를 제거한다. | `clarify.md` Q-015, `auth-service-api.md` | T030 Decided; product pending Q-012/T040 |
| Medium | Remember me 최대 14일은 정했지만 유휴 만료가 미정이었다. | Redis TTL/cookie UX가 구현자마다 달라질 수 있었다. | 7일 sliding 유휴/14일 절대 만료를 적용하고 절대 만료는 갱신하지 않는다. | `clarify.md` Q-014, `requirements/policies.md` | Decided |
| Medium | 단일 리전 Multi-AZ만으로는 출시 가능한 가용성 수치가 결정되지 않는다. | replica, backup, failover, alarm과 비용을 산정할 수 없다. | Q-011~Q-013에서 region, SLO/RTO/RPO와 IaC/node 방식을 결정한다. | `clarify.md` Q-011~013 | Open, blocks production |
| Medium | 현재 Core auth 문서가 Frontend `sessionStorage` token을 target으로 표현했다. | 신규 구현이 legacy 흐름을 다시 강화할 수 있었다. | 기존 구현 설명은 보존하고 target supersession notice를 추가한다. | `specs/001-meetingmind-core/**` | Resolved in docs |
| Low | Web BFF foundation이 기존 Backend와 분리된 실행·세션 저장 경계를 필요로 했다. | 같은 프로세스/메모리 세션이면 MSA 장애 격리와 복수 Pod 조건을 검증할 수 없다. | 독립 Gradle/Docker 서비스, Spring Session Redis와 복수 context 공유 테스트를 유지한다. | `bff/**`, `compose.local.yml`, `.github/workflows/ci.yml` | T010 Verified |
| Low | Browser session 보안 기준이 구현에 연결되지 않으면 cookie 또는 CSRF 설정이 profile마다 달라질 수 있다. | 운영 token 노출, CSRF 또는 session fixation 위험이 생길 수 있다. | 운영/로컬 cookie profile, session CSRF와 ID 교체 전략을 자동 테스트하고 운영 profile 실응답을 확인한다. | `bff/**`, `contracts/browser-auth-api.md` | T011 Verified |
| Low | BFF가 refresh를 재사용하려면 복호화 가능 저장이 필요하지만 단순 Redis 저장은 P0 평문 금지와 충돌한다. | Redis dump/운영 조회로 장기 credential이 노출되거나 KMS 장애 시 평문 fallback 위험이 생긴다. | AES-256-GCM envelope encryption, KMS/local key adapter, 별도 namespace와 fail-closed 원자 교체를 유지한다. | `bff/**`, `data-model.md`, `research.md` | T012 Verified |
| Low | 현재 Backend token 응답을 Browser에 그대로 중계하면 목표 BFF 경계가 성립하지 않는다. | token이 다시 Browser 저장소/응답에 노출되고 Remember me/session bootstrap이 구현별로 갈라질 수 있다. | 고정 compatibility client에서 token을 즉시 Vault로 옮기고 Browser에는 user/session view와 opaque cookie만 반환한다. | `bff/**`, `contracts/browser-auth-api.md` | T013 Verified |
| Low | 복수 BFF Pod가 같은 refresh를 동시에 사용하면 현재 Backend의 즉시 rotation 정책에서 정상 세션도 무효화될 수 있다. | 동시 만료 요청 중 한 요청만 성공하고 나머지가 폐기된 refresh를 사용해 최종 401을 만들 수 있다. | Redis 소유권 lock과 Vault version polling으로 한 요청만 refresh하고 나머지는 회전된 bundle을 재사용한다. | `bff/**`, `requirements/functional-requirements-detail.md` FR-AUTH-16 | T014 Verified |
| Low | 범용 `/api/v1/**` proxy가 Browser path나 header로 목적지를 정하면 SSRF, Auth 우회와 서비스 장애 전파가 가능하다. | 임의 내부 경로 호출, Browser Bearer 주입, AI/LiveKit 포화가 Core 요청을 소진할 수 있다. | 실제 Backend route의 method/path와 엔티티별 prefix+UUID 형식만 허용하고 목적지·Authorization·timeout/circuit/bulkhead는 서비스별 서버 설정으로 고정한다. | `bff/**`, `contracts/bff-proxy-routes.md`, NFR-REL-01 | T015 Verified; T020 E2E에서 ID 계약 보정 |
| Low | 스텁 E2E만으로는 현재 Backend의 실제 refresh rotation과 logout 계약 변화가 탐지되지 않는다. | BFF 단위 테스트가 통과해도 배포 시 status/body/token 수명 차이로 인증 경로가 실패할 수 있다. | 실제 Backend `test` 프로필과 BFF/Redis를 별도 프로세스로 실행해 강제 선제 refresh, proxy, logout, stale cookie와 token/log scan을 CI에 고정한다. | `scripts/bff-backend-compat-e2e.sh`, `.github/workflows/ci.yml` | T016 Verified |
| Low | Frontend가 `sessionStorage` token과 Browser Bearer를 유지하면 BFF session/refresh 책임이 우회된다. | reload 후 stale 로그인 표시와 token 노출이 계속되고 BFF 자동 refresh가 적용되지 않는다. | 상대경로 same-origin 요청, `/auth/session` bootstrap loading 상태, 공통 CSRF client와 BFF proxy를 사용하고 브라우저 저장소·header에서 token을 제거한다. | `frontend/src/auth/**`, `frontend/src/api/**`, `App.tsx`, Playwright/CI | T020 Verified |
| Low | Frontend logout 실패를 성공처럼 처리하면 서버 세션이 남은 상태에서 UI만 로그아웃되어 재접속 시 인증 상태가 되살아날 수 있다. | 사용자가 로그아웃됐다고 오인하고 공유 브라우저에 유효 세션을 남길 수 있다. | BFF `204`에서만 전역 상태를 초기화하고 네트워크/비정상 응답은 로그인 상태를 유지한 채 명시적 재시도 오류를 표시한다. | `frontend/src/auth/session.ts`, `AuthSessionControls.tsx`, Playwright | T021 Verified |
| Low | 개별 API가 최종 `401`을 각자 처리하면 일부 화면은 로그인처럼 보이거나 동시 응답이 redirect loop를 만들 수 있다. | BFF가 세션을 폐기했는데도 stale UI가 남고 사용자는 요청마다 반복 실패를 겪는다. | 모든 보호 요청을 공통 BFF fetch에 연결하고 code가 정확히 `SESSION_INVALID`일 때만 1회 document reload, 재로그인 안내와 검증된 same-origin return path를 사용한다. | `frontend/src/auth/sessionInvalidation.ts`, `csrf.ts`, `App.tsx`, Playwright | T022 Verified |
| Low | Browser cutover 뒤 direct Backend rollback을 유지하려면 제거한 token 저장/Bearer client를 다시 배포해야 한다. | 장애 대응 코드가 P0 Browser token 무노출을 깨고 장기간 이중 인증 경로를 남길 수 있다. | 신규 BFF readiness를 내려 drain하고 동일 cookie/Redis/Token Vault 계약을 사용하는 안정 BFF release로 traffic weight만 복원한다. | `research.md` D-014, `rollout-runbook.md`, BFF rollout health/metrics | T023 Verified |
| Low | Auth Service가 Core 프로세스·DB 계정과 분리되지 않으면 추출 뒤에도 장애와 DDL 권한이 공유된다. | Auth DB 장애가 Core와 같은 수명 주기를 가지거나 runtime 침해가 schema/history 변경으로 확대될 수 있다. | 독립 Gradle/Docker 서비스와 전용 PostgreSQL을 유지하고 migrator/runtime 계정, DB readiness/process liveness, table별 최소 권한을 자동 검증한다. | `auth/**`, `compose.local.yml`, `.github/workflows/ci.yml` | T031 Verified |
| Low | revoke-all이 `userId`와 BFF 최근 인증 주장만 받으면 Auth DB가 현재 session 소유자 결합을 독립 확인할 수 없다. | BFF 결합 버그나 침해가 다른 사용자의 모든 AuthSession 폐기로 확대될 수 있다. | `currentAuthSessionId`와 `userId`를 함께 받고 AuthSession owner, 최근 10분과 최대 60초 미래 skew를 변경 전에 검증한다. | Q-017, `auth-service-api.md`, `AuthRuntimeService` | T032 Verified |
| Low | T033 전 임시 signer를 넣으면 HMAC/로컬 private key가 운영 계약으로 굳거나 test token이 image에 포함될 수 있다. | Resource validator 계약과 키 수명 경계가 갈라지고 서명키가 container에 노출될 수 있다. | production `AccessTokenIssuer`는 fail-closed port로 두고 test source signer로만 T032 DB/API를 검증하며 signer 부재 시 transaction rollback을 확인한다. | D-019, `auth/**` | T033 Resolved; KMS-only runtime adapter/JWKS/validator verified |
| Low | JWKS rotation 순서가 운영자 기억에만 의존하면 새 `kid`가 validator cache에 없거나 이전 token이 overlap 전에 실패할 수 있다. | 정상 rotation 중 이미 발급된 access가 최대 10분 내인데도 거부되거나 Auth 장애 격리가 깨질 수 있다. | key ring이 정기 rotation의 5분 선게시와 1시간 이전 key overlap을 시작 시 검증하고 unknown `kid` 1회 refresh/fail-closed를 자동 테스트한다. | `auth/**`, target validator, `auth-service-api.md` | T033 Verified; 90일 운영 경보는 T045 |
| Medium | T024가 T032만 의존하면 현재 BFF의 legacy 호환 UUID를 Auth Service의 실제 AuthSession으로 오인하게 된다. | Auth owner binding이 항상 실패하거나 실제 다른 기기 세션을 폐기하지 못한 채 UI만 성공으로 보일 수 있다. | T034 데이터 이전과 T035 BFF→Auth 전환에서 실제 AuthSession ID와 BFF 사용자 session index를 확보한 뒤 T024를 노출한다. | `tasks.md`, BFF compatibility auth, `auth-service-api.md` | T034→T035→T024 순차 구현과 실제 PostgreSQL/Redis 검증 완료 |
| High | Auth User UUID를 Browser session `user.id`로 그대로 노출하면 Core 응답의 `user-{UUID}`와 달라 Frontend 참가자·멤버·현재 사용자 비교가 실패한다. | 로그인 뒤 회의 입장/권한 UI가 현재 사용자를 찾지 못하거나 자기 자신을 초대 대상으로 처리할 수 있다. | external resource user ID와 internal Auth UUID를 분리하고 BFF session에 두 값을 저장한다. | Q-020, Browser/Core contracts, BFF session | T035 Verified |
| High | T034는 기존 User만 Auth/Core에 대사하므로 Auth cutover 뒤 신규 signup은 Core projection row가 없다. | 유효 target JWT가 발급돼도 Core가 `sub`를 업무 User로 해석하지 못해 첫 요청이 401이 된다. | BFF가 target Auth 성공 직후 Core projection을 동기 멱등 생성하고 성공 전 Browser session 생성을 막는다. | Q-021, `core-user-projection-api.md`, BFF/Core runtime | T035 Verified |
| High | Core가 target validator 실패 뒤 legacy HS256 검증을 시도하면 알고리즘/profile 혼동이 downgrade 경로가 된다. | 잘못된 target token이 더 약한 legacy validator에 수용될 수 있다. | unverified header는 validator 선택에만 사용하고 `RS256/at+jwt/kid` 또는 legacy profile을 결정적으로 분류한 뒤 한 validator만 실행한다. | Core access resolver/tests | T035 Verified |
| Medium | 단일 access/expiry Token Bundle은 target 서비스별 audience access를 표현하지 못한다. | Core token을 AI/LiveKit에 재사용하거나 잘못된 token으로 모든 route가 401이 될 수 있다. | schema v1/v2를 구분하고 v2 audience→token/expiry map을 원자 refresh하며 route가 정확한 audience를 선택한다. | BFF Token Vault/Manager/Proxy tests | T035 Verified |

## Recommendation

1. T034는 Core UUID projection, User/AuthIdentity forward-only 이전과 reconciliation/rollback 경계를 구현해 검증을 완료했다. legacy refresh session은 lineage와 원문을 복구할 수 없어 신규 AuthSession으로 변환하지 않는다.
2. T035는 T033 validator를 Core dual validation에 연결하고 BFF target Auth, 실제 AuthSession/Auth UUID index, audience Token Bundle과 신규 User projection을 검증 완료했다.
3. T024는 BFF 최근 인증/전용 재인증과 Frontend 모든 기기 로그아웃 UI를 실제 사용자 session index에 연결해 완료했다.
4. Q-011~Q-013을 결정하기 전 EKS production 리소스를 생성하지 않는다.
5. T023 runbook의 7일 관측 window와 guardrail을 실제 배포 지표로 통과한 뒤 compatibility 경로 제거를 승인한다.
6. Auth Service 추출 뒤 도메인 분리는 EKS 관측 근거를 확보하고 별도 spec으로 진행한다.
