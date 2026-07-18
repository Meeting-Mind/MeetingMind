# Clarification Log: BFF Auth and Gradual MSA

## Questions

| ID | Priority | Question | Why It Matters | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| Q-001 | High | BFF는 기존 Backend와 함께 둘까, 별도 서비스로 둘까? | 브라우저 trust boundary와 서비스 추출 순서를 결정한다. | Decided | 별도 Spring Boot `web-bff` 서비스를 둔다. |
| Q-002 | High | MSA 전환을 한 번에 할까, 점진적으로 할까? | 장애·롤백 범위와 계약 호환 전략을 결정한다. | Decided | Web BFF 도입, Auth Service 추출, 도메인 서비스 순차 분리의 Strangler 방식으로 진행한다. |
| Q-003 | High | 브라우저가 MeetingMind access/refresh token을 보유할까? | XSS 노출과 refresh 책임을 결정한다. | Decided | 브라우저에는 token을 노출하지 않고 BFF 세션 쿠키만 둔다. BFF가 내부 token을 관리한다. |
| Q-004 | High | BFF 서버 세션은 어디에 저장할까? | 수평 확장과 장애 경계를 결정한다. | Decided | Spring Session + BFF 전용 Redis를 사용하고 sticky session에 의존하지 않는다. |
| Q-005 | Medium | Google 로그인도 즉시 Authorization Code로 바꿀까? | 구현 범위와 Google API token 보관 여부를 결정한다. | Decided | 인증 전용 Google ID credential 검증을 유지하되 검증 책임을 Auth Service로 이동한다. 범용 OIDC와 Google API 권한 위임은 후속이다. |
| Q-006 | High | 운영 배포와 실시간 미디어 플랫폼은 무엇인가? | 인프라 어댑터와 가용성 설계를 결정한다. | Decided | AWS EKS 단일 리전 Multi-AZ, LiveKit Cloud를 사용한다. |
| Q-007 | High | 기본 세션 만료와 로그아웃 범위는 무엇인가? | Redis TTL, 쿠키와 보안 UX를 결정한다. | Decided | 일반 세션은 유휴 60분/절대 12시간, Remember me는 최대 14일이다. 현재 세션과 모든 기기 로그아웃을 모두 지원한다. |
| Q-008 | High | 서비스별 데이터는 어떻게 격리할까? | MSA 장애 격리와 DB 소유권을 결정한다. | Decided | Auth 추출 시 Auth 전용 DB/계정을 사용하고 신규 서비스는 자신의 DB만 직접 조회한다. 로컬은 같은 PostgreSQL 인스턴스의 논리 분리를 허용하고 운영 목표는 물리 장애 격리다. |
| Q-009 | High | 이전 refresh token 재사용을 감지하면 어떤 token family 범위를 폐기할까? | Refresh 탈취 재사용 대응과 DB lineage를 결정한다. | Decided | 로그인/기기별 AuthSession 하나를 family 경계로 삼고 1회용 rotation을 적용한다. 사용된 refresh 재사용 시 동시 grace 없이 해당 AuthSession과 후속 family 전체를 원자 폐기하며 다른 기기 세션은 유지한다. |
| Q-010 | High | Access JWT 알고리즘·필수 claim·`kid`/JWKS·키 교체 주기는 무엇인가? | Resource Service 로컬 검증과 운영 키 교체를 결정한다. | Decided | AWS KMS RSA-2048 `RS256`, audience별 10분 access, 60초 skew, 필수 `iss/aud/sub/sid/jti/iat/nbf/exp/ver`, 90일 rotation/1시간 overlap/5분 JWKS cache를 사용한다. 로그아웃은 durable `sid` revoke event와 Resource Service 로컬 denylist로 보완한다. |
| Q-011 | Medium | 첫 운영 AWS 리전은 어디인가? | EKS/RDS/ElastiCache/CloudWatch 리소스와 데이터 거주성을 결정한다. | Open | 단일 리전 Multi-AZ만 결정했다. |
| Q-012 | Medium | EKS node 운영 방식, IaC와 mTLS/SPIFFE 인증서 제품은 무엇인가? | 프로비저닝, HPA, upgrade, 비용과 workload 인증서 자동 회전을 결정한다. | Open | EKS와 mTLS/SPIFFE 계약만 결정했다. |
| Q-013 | High | BFF/Auth/Core/AI의 SLO, RTO, RPO는 무엇인가? | replica, PDB, backup, alarm과 장애 테스트 기준을 결정한다. | Open | |
| Q-014 | Medium | Remember me 세션에도 60분 유휴 만료를 적용할까? | 14일 자동로그인 의미와 Redis TTL을 결정한다. | Decided | Remember me는 마지막 유효 요청 기준 7일 sliding 유휴 만료와 최초 로그인 기준 14일 절대 만료를 함께 적용한다. sliding 갱신은 절대 만료를 연장하지 않는다. |
| Q-015 | High | BFF와 내부 서비스 간 workload 인증은 mTLS, client credential, 서명 요청 중 무엇인가? | private network 침해 시 내부 endpoint 오용을 방지한다. | Decided | mTLS와 SPIFFE workload identity를 사용하고 NetworkPolicy, public ingress 차단, 목적지·principal allowlist를 함께 적용한다. 제품 선택은 Q-012/T040에서 한다. |
| Q-016 | High | 모든 기기 로그아웃의 최근 인증 허용 시간과 재인증 UX는 무엇인가? | 탈취된 BFF 세션이 다른 정상 기기까지 폐기하는 것을 막고 local/Google 사용자의 재인증 방식을 일관되게 정한다. | Decided | 최근 인증 10분을 허용하고 초과 시 local은 비밀번호, Google은 새 ID credential을 재검증한다. Auth revoke-all이 durable하게 기록된 뒤 성공 처리한다. |
| Q-017 | High | Auth Service가 revoke-all의 사용자 소유권을 어떤 값으로 독립 검증할까? | `userId`만 신뢰하면 내부 BFF 침해나 결합 오류가 다른 사용자의 모든 세션을 폐기할 수 있다. | Decided | BFF가 서버 세션의 `currentAuthSessionId`와 `userId`, `authenticatedAt`을 함께 보내고 Auth Service가 AuthSession 소유자 결합과 최근 10분을 재검증한다. 브라우저 입력은 전달하지 않는다. |

## Blocking Decisions

- Q-009, Q-010, Q-015는 T030에서 결정됐다. Auth Service runtime은 이 계약을 구현하는 T031~T035 순서를 따른다.
- Q-016은 결정됐지만 모든 기기 로그아웃은 Auth revoke-all과 revoke event를 구현하는 T032 이후 T024에서 노출한다.
- Q-017은 T032에서 계약·runtime에 반영한다. 이미 폐기된 동일 요청의 멱등 재시도를 위해 current AuthSession row의 소유자 결합은 유지하되 신규 active session 폐기는 최근 인증 검증을 계속 요구한다.
- Q-011~Q-013은 로컬/CI 구현을 막지 않지만 운영 EKS 프로비저닝과 출시 승인을 막는다.

## Decisions

- D-001: 기존 `sessionStorage + Bearer` 흐름은 Current Prototype 호환 경로이며 목표 계약은 `specs/002-bff-auth-msa/contracts/*`가 대체한다.
- D-002: 외부 브라우저 API path는 `/api/v1/*`를 유지하고 BFF가 현재 Backend 또는 추출된 서비스로 명시적으로 라우팅한다.
- D-003: 브라우저-BFF는 쿠키 세션과 CSRF, BFF-Resource Service는 내부 access token을 사용한다.
- D-004: BFF 세션에는 token 원문 대신 Token Bundle 참조를 두고 Token Bundle은 AWS KMS 경계로 암호화한다.
- D-005: Auth Service는 User/AuthIdentity/AuthSession과 refresh hash/revoke를 소유하고 업무 RBAC/ACL은 소유하지 않는다.
- D-006: Resource Service는 Auth Service에 매 요청 동기 조회하지 않고 access JWT를 로컬 검증한다.
- D-007: Google ID credential은 검증 뒤 보관하지 않고 내부 MeetingMind token과 BFF 세션만 생성한다.
- D-008: AWS EKS는 단일 리전 Multi-AZ로 시작하고 멀티리전은 별도 재해복구 스펙으로 둔다.
- D-009: LiveKit Cloud 장애는 회의 metadata/보고서/AI 저장 데이터를 mock 성공으로 대체하지 않고 실시간 기능 unavailable로 격리한다.
- D-010: 점진 전환 중 현재 Backend DB와 API를 즉시 삭제하지 않고 compatibility adapter와 rollback window를 유지한다.
- D-011: 일반 BFF 세션은 유휴 60분/절대 12시간, Remember me 세션은 7일 sliding 유휴/14일 절대 만료를 사용한다. 모든 sliding 갱신은 최초 로그인 기준 절대 만료를 넘지 않는다.
- D-012: Token Bundle payload는 bundle/session/version을 인증 데이터로 묶은 AES-256-GCM envelope encryption을 사용한다. 운영 data key는 AWS KMS와 workload IAM으로 생성·복호화하고, local/test adapter는 외부 주입한 256-bit AES master key만 허용한다.
- D-013: Phase 1의 현재 Backend는 안정된 논리 AuthSession ID를 응답하지 않으므로 Web BFF가 브라우저 로그인마다 호환용 `authSessionId`를 생성해 BffSession/TokenBundle만 묶는다. 이 값은 브라우저나 현재 Backend로 전달하지 않으며 Auth Service 추출 시 서버 발급 ID로 대체한다.
- D-014: Browser session cutover 이후 rollback은 direct Backend가 아니라 동일 cookie/Redis/Token Vault 계약을 사용하는 안정 BFF release로 수행한다. 신규 release는 readiness drain 뒤 ingress traffic weight를 0으로 내리고, Browser token/Bearer 코드는 복원하지 않는다.
- D-015: Refresh credential은 AuthSession별 한 family와 1회용 lineage를 사용한다. 사용된 credential 재사용은 grace 없이 해당 AuthSession/family 전체를 revoke하고 durable event를 기록하며 다른 기기의 AuthSession은 유지한다.
- D-016: Access는 KMS RSA-2048 `RS256`으로 서명한 audience별 10분 JWT다. 각 JWT는 단일 Resource Service audience와 `iss/sub/sid/jti/iat/nbf/exp/ver`를 필수로 가지며 업무 권한 원본은 포함하지 않는다.
- D-017: 현재/전체 로그아웃과 보안 폐기는 Auth DB revoke와 transactional outbox를 함께 커밋한다. Resource Service는 at-least-once revoke event를 idempotent하게 소비해 `sid`를 access 만료까지 로컬 denylist에 보관한다.
- D-018: 내부 서비스 호출은 mTLS SPIFFE workload identity, NetworkPolicy와 principal/endpoint allowlist를 함께 사용한다. 모든 기기 로그아웃은 최근 10분 인증 또는 local/Google 재인증을 요구한다.
