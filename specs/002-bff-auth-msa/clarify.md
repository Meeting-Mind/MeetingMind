# Clarification Log: BFF Auth and Gradual MSA

## Questions

| ID | Priority | Question | Why It Matters | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| Q-001 | High | BFF는 기존 Backend와 함께 둘까, 별도 서비스로 둘까? | 브라우저 trust boundary와 서비스 추출 순서를 결정한다. | Decided | 별도 Spring Boot `web-bff` 서비스를 둔다. |
| Q-002 | High | MSA 전환을 한 번에 할까, 점진적으로 할까? | 장애·롤백 범위와 계약 호환 전략을 결정한다. | Decided | Web BFF 도입, Auth Service 추출, 도메인 서비스 순차 분리의 Strangler 방식으로 진행한다. |
| Q-003 | High | 브라우저가 MeetingMind access/refresh token을 보유할까? | XSS 노출과 refresh 책임을 결정한다. | Decided | 브라우저에는 token을 노출하지 않고 BFF 세션 쿠키만 둔다. BFF가 내부 token을 관리한다. |
| Q-004 | High | BFF 서버 세션은 어디에 저장할까? | 수평 확장과 장애 경계를 결정한다. | Decided | Spring Session + BFF 전용 Redis를 사용하고 sticky session에 의존하지 않는다. |
| Q-005 | Medium | Google 로그인도 즉시 Authorization Code로 바꿀까? | 구현 범위와 Google API token 보관 여부를 결정한다. | Decided | 인증 전용 Google ID credential 검증을 유지하되 검증 책임을 Auth Service로 이동한다. 범용 OIDC와 Google API 권한 위임은 후속이다. |
| Q-006 | High | 운영 배포와 실시간 미디어 플랫폼은 무엇인가? | 인프라 어댑터와 가용성 설계를 결정한다. | Decided | AWS ECS Fargate 단일 리전 Multi-AZ, LiveKit Cloud를 사용한다. 기존 EKS 선택은 ADR 002가 대체한다. |
| Q-007 | High | 기본 세션 만료와 로그아웃 범위는 무엇인가? | Redis TTL, 쿠키와 보안 UX를 결정한다. | Decided | 일반 세션은 유휴 60분/절대 12시간, Remember me는 최대 14일이다. 현재 세션과 모든 기기 로그아웃을 모두 지원한다. |
| Q-008 | High | 서비스별 데이터는 어떻게 격리할까? | MSA 장애 격리와 DB 소유권을 결정한다. | Decided | Auth 추출 시 Auth 전용 DB/계정을 사용하고 신규 서비스는 자신의 DB만 직접 조회한다. 로컬은 같은 PostgreSQL 인스턴스의 논리 분리를 허용하고 운영 목표는 물리 장애 격리다. |
| Q-009 | High | 이전 refresh token 재사용을 감지하면 어떤 token family 범위를 폐기할까? | Refresh 탈취 재사용 대응과 DB lineage를 결정한다. | Decided | 로그인/기기별 AuthSession 하나를 family 경계로 삼고 1회용 rotation을 적용한다. 사용된 refresh 재사용 시 동시 grace 없이 해당 AuthSession과 후속 family 전체를 원자 폐기하며 다른 기기 세션은 유지한다. |
| Q-010 | High | Access JWT 알고리즘·필수 claim·`kid`/JWKS·키 교체 주기는 무엇인가? | Resource Service 로컬 검증과 운영 키 교체를 결정한다. | Decided | AWS KMS RSA-2048 `RS256`, audience별 10분 access, 60초 skew, 필수 `iss/aud/sub/sid/jti/iat/nbf/exp/ver`, 90일 rotation/1시간 overlap/5분 JWKS cache를 사용한다. 로그아웃은 durable `sid` revoke event와 Resource Service 로컬 denylist로 보완한다. |
| Q-011 | Medium | 첫 운영 AWS 리전은 어디인가? | ECS/RDS/ElastiCache/CloudWatch 리소스와 데이터 거주성을 결정한다. | Decided | NonProd와 첫 Production 반영 대상 리전은 서울 `ap-northeast-2`로 한다. |
| Q-012 | Medium | 컨테이너 오케스트레이션과 IaC 전환 방식은 무엇인가? | 프로비저닝, autoscaling, upgrade와 운영 복잡도를 결정한다. | Decided | NonProd 전용 단일 ECS Fargate 클러스터를 사용하고 EC2 node를 운영하지 않는다. 현재 수작업 구성을 검증한 뒤 기존 VPC Terraform을 보존하면서 ECS/ALB/IAM/SG/CloudWatch 모듈과 환경별 변수로 전환한다. |
| Q-013 | High | BFF/Auth/Core/AI의 SLO, RTO, RPO는 무엇인가? | desired count, autoscaling, backup, alarm과 장애 테스트 기준을 결정한다. | Open | |
| Q-014 | Medium | Remember me 세션에도 60분 유휴 만료를 적용할까? | 14일 자동로그인 의미와 Redis TTL을 결정한다. | Decided | Remember me는 마지막 유효 요청 기준 7일 sliding 유휴 만료와 최초 로그인 기준 14일 절대 만료를 함께 적용한다. sliding 갱신은 절대 만료를 연장하지 않는다. |
| Q-015 | High | BFF와 내부 서비스 간 workload 인증은 mTLS, client credential, 서명 요청 중 무엇인가? | private network 침해 시 내부 endpoint 오용을 방지한다. | Decided | mTLS와 SPIFFE workload identity를 사용하고 서비스별 Security Group, public 경로 차단, 목적지·principal allowlist를 함께 적용한다. 제품 선택은 Q-023/T040에서 한다. |
| Q-016 | High | 모든 기기 로그아웃의 최근 인증 허용 시간과 재인증 UX는 무엇인가? | 탈취된 BFF 세션이 다른 정상 기기까지 폐기하는 것을 막고 local/Google 사용자의 재인증 방식을 일관되게 정한다. | Decided | 최근 인증 10분을 허용하고 초과 시 local은 비밀번호, Google은 새 ID credential을 재검증한다. Auth revoke-all이 durable하게 기록된 뒤 성공 처리한다. |
| Q-017 | High | Auth Service가 revoke-all의 사용자 소유권을 어떤 값으로 독립 검증할까? | `userId`만 신뢰하면 내부 BFF 침해나 결합 오류가 다른 사용자의 모든 세션을 폐기할 수 있다. | Decided | BFF가 서버 세션의 `currentAuthSessionId`와 `userId`, `authenticatedAt`을 함께 보내고 Auth Service가 AuthSession 소유자 결합과 최근 10분을 재검증한다. 브라우저 입력은 전달하지 않는다. |
| Q-018 | High | Core의 기존 문자열 User PK와 Auth의 UUID subject를 어떻게 연결할까? | 모든 업무 FK를 한 번에 UUID로 바꾸면 T034 범위와 팀 충돌이 커지고, legacy 문자열을 JWT subject로 유지하면 목표 Auth 계약이 오염된다. | Decided | Core `users.id`와 기존 FK는 유지하고 `users.auth_user_id UUID` unique projection을 추가한다. canonical `user-{UUID}`의 suffix를 결정적으로 backfill하며 Auth/JWT는 UUID만 사용한다. 비정형 ID는 추측 변환하지 않고 인증 이관 대사에서 fail closed한다. |
| Q-019 | High | legacy User/AuthIdentity를 Auth DB로 어떤 동기화 방식으로 이전할까? | dual-write/CDC를 도입할지, 짧은 인증 쓰기 중단으로 검증 가능한 export/import를 사용할지 결정한다. | Decided | 반복 실행 가능한 오프라인 snapshot/delta 이관과 대사를 사용한다. 최종 실행은 login/signup/Google 인증 쓰기를 짧게 중단한 뒤 수행하고, User/AuthIdentity만 이전한다. legacy refresh/AuthSession은 lineage를 추정하지 않고 전원 재로그인시킨다. 기존 DB/issuer는 제한된 rollback window 동안 읽기 가능한 상태로 보존한다. |
| Q-020 | High | Auth UUID와 Core 문자열 ID 중 Browser/Core API에 노출할 사용자 ID는 무엇인가? | Auth 로그인 응답이 UUID로 바뀌지만 현재 Core 응답과 Frontend 참가자·멤버 비교는 `user-{UUID}`를 사용해 그대로 전환하면 현재 사용자 판별이 깨진다. | Decided | Browser/Core 외부 계약은 `user-{Auth UUID}` resource ID를 유지하고, Auth UUID는 BFF 내부 session index와 JWT `sub`에만 사용한다. BFF session은 두 ID를 모두 보관하고 Core는 `auth_user_id`로 기존 문자열 resource User를 찾는다. |
| Q-021 | High | Auth cutover 뒤 새 가입 User의 Core projection을 언제, 누가 생성할까? | T034는 기존 사용자만 이전하므로 새 Auth User가 Core `users`에 없으면 유효 JWT여도 첫 업무 요청이 401이 된다. | Decided | BFF가 Auth 발급 성공 직후 `meetingmind-core` access와 workload identity로 idempotent Core internal projection upsert를 호출하고 성공 후에만 Browser session을 만든다. 실패 시 새 AuthSession을 best-effort revoke하고 로그인/가입을 실패 처리하며 Auth 계정은 다음 시도에서 재사용한다. |
| Q-022 | High | 모든 기기 로그아웃의 재인증은 기존 login을 재사용할까, 세션을 만들지 않는 전용 검증을 둘까? | 기존 login 재사용은 민감 동작 확인을 위해 불필요한 AuthSession/refresh family를 만들고, Browser가 `authenticatedAt`을 보내게 하면 최근 인증 시각을 위조할 수 있다. | Decided | Browser와 BFF/Auth 내부 계약에 전용 `reauthenticate`를 둔다. Auth가 현재 AuthSession/User 결합을 먼저 확인하고 local 비밀번호 또는 기존에 연결된 Google identity를 검증한 뒤 서버 시각 `authenticatedAt`만 반환한다. 새 User/AuthSession/token을 만들거나 Google identity를 연결하지 않으며 BFF가 이 시각을 서버 세션에 저장한 뒤 body 없는 `logout-all`을 1회 재시도한다. |
| Q-023 | High | ECS 내부 service discovery와 mTLS/SPIFFE 인증서 발급·회전은 어떤 제품과 주소 체계를 사용할까? | BFF→Auth/Core와 Core→AI가 public ALB를 우회하면서 stable endpoint와 workload identity를 가져야 한다. | Open | Security Group 경계와 포트는 확정됐다. Cloud Map/Service Connect/내부 ALB 중 discovery 방식과 SPIFFE 구현 제품은 T040에서 결정한다. |
| Q-024 | High | CloudFront/WAF를 우회한 public ALB 직접 호출을 어떻게 차단하고 ALB→BFF target protocol을 무엇으로 할까? | 요청 경로를 CloudFront/WAF로 강제하지 않으면 public ALB가 보안 경계를 우회할 수 있고 ALB Target Group은 TCP protocol을 직접 사용하지 않는다. | Open | ALB Security Group의 BFF 대상 포트는 TCP `8081`로 고정한다. CloudFront origin 검증/접근 제한과 HTTP 또는 HTTPS Target Group protocol은 T045 전에 확정한다. |
| Q-025 | Medium | NonProd NAT Gateway를 AZ별로 둘까, 단일 NAT 장애·비용을 수용할까? | Fargate Task는 Multi-AZ여도 단일 NAT Gateway/AZ 장애가 image pull과 외부 API egress에 영향을 줄 수 있다. | Open | 현재 NAT Gateway와 private route는 재사용한다. 실제 개수와 route/AZ 매핑을 T052 전에 확인하고 Production 이중화 기준을 정한다. |
| Q-026 | Medium | 공용 리소스의 `Service` tag와 기존 Terraform tag 값을 어떻게 수렴할까? | ALB/VPC/NAT처럼 단일 서비스에 속하지 않는 리소스도 필수 tag가 필요하고, 현재 Terraform은 `Project=MeetingMind`, `ManagedBy=terraform`을 사용해 수작업 기준과 다르다. | Open | 수작업 신규 리소스는 확정된 tag 기준을 사용한다. 공용 `Service` 값과 기존 Terraform tag migration은 T053 모듈화 전에 결정하며 현재 Terraform을 이번 문서 작업에서 수정하지 않는다. |

## Blocking Decisions

- Q-009, Q-010, Q-015는 T030에서 결정됐다. Auth Service runtime은 이 계약을 구현하는 T031~T035 순서를 따른다.
- Q-016은 T032의 Auth revoke-all/revoke event와 T035의 실제 AuthSession ID/BFF 사용자 session index를 선행한 뒤 T024에서 모든 기기 로그아웃으로 구현했다.
- Q-017은 T032에서 계약·runtime에 반영한다. 이미 폐기된 동일 요청의 멱등 재시도를 위해 current AuthSession row의 소유자 결합은 유지하되 신규 active session 폐기는 최근 인증 검증을 계속 요구한다.
- Q-018과 Q-019는 T034에서 Core projection, 오프라인 이관·대사 도구와 강제 재로그인 운영 경계로 구현한다.
- Q-020과 Q-021은 권장안으로 결정됐다. T035는 external resource ID와 internal Auth UUID를 분리하고 동기 Core projection 성공을 Browser session 생성의 선행 조건으로 구현한다.
- Q-022는 T024 준비에서 결정됐다. 재인증 증명과 시각은 Browser가 `logout-all`에 직접 보내지 않고 전용 Browser→BFF→Auth 경계에서 검증하며, legacy provider에서는 불완전한 local-only 전체 로그아웃을 성공으로 위장하지 않는다.
- Q-012는 ECS Fargate로 결정됐다. Q-013과 Q-023~Q-026은 기반 리소스 기록을 막지 않지만 관련 ECS Service/edge 출시 또는 Production IaC 전환 승인을 막는다.

## Decisions

- D-001: 기존 `sessionStorage + Bearer` 흐름은 Current Prototype 호환 경로이며 목표 계약은 `specs/002-bff-auth-msa/contracts/*`가 대체한다.
- D-002: 외부 브라우저 API path는 `/api/v1/*`를 유지하고 BFF가 현재 Backend 또는 추출된 서비스로 명시적으로 라우팅한다.
- D-003: 브라우저-BFF는 쿠키 세션과 CSRF, BFF-Resource Service는 내부 access token을 사용한다.
- D-004: BFF 세션에는 token 원문 대신 Token Bundle 참조를 두고 Token Bundle은 AWS KMS 경계로 암호화한다.
- D-005: Auth Service는 User/AuthIdentity/AuthSession과 refresh hash/revoke를 소유하고 업무 RBAC/ACL은 소유하지 않는다.
- D-006: Resource Service는 Auth Service에 매 요청 동기 조회하지 않고 access JWT를 로컬 검증한다.
- D-007: Google ID credential은 검증 뒤 보관하지 않고 내부 MeetingMind token과 BFF 세션만 생성한다.
- D-008 (Superseded): AWS EKS 단일 리전 Multi-AZ 선택은 2026-07-23 ADR 002의 ECS Fargate 결정으로 대체됐다. 멀티리전은 계속 별도 재해복구 스펙으로 둔다.
- D-009: LiveKit Cloud 장애는 회의 metadata/보고서/AI 저장 데이터를 mock 성공으로 대체하지 않고 실시간 기능 unavailable로 격리한다.
- D-010: 점진 전환 중 현재 Backend DB와 API를 즉시 삭제하지 않고 compatibility adapter와 rollback window를 유지한다.
- D-011: 일반 BFF 세션은 유휴 60분/절대 12시간, Remember me 세션은 7일 sliding 유휴/14일 절대 만료를 사용한다. 모든 sliding 갱신은 최초 로그인 기준 절대 만료를 넘지 않는다.
- D-012: Token Bundle payload는 bundle/session/version을 인증 데이터로 묶은 AES-256-GCM envelope encryption을 사용한다. 운영 data key는 AWS KMS와 ECS 서비스별 Task Role로 생성·복호화하고, local/test adapter는 외부 주입한 256-bit AES master key만 허용한다.
- D-013: Phase 1의 현재 Backend는 안정된 논리 AuthSession ID를 응답하지 않으므로 Web BFF가 브라우저 로그인마다 호환용 `authSessionId`를 생성해 BffSession/TokenBundle만 묶는다. 이 값은 브라우저나 현재 Backend로 전달하지 않으며 Auth Service 추출 시 서버 발급 ID로 대체한다.
- D-014: Browser/Core 외부 사용자 ID는 `user-{Auth UUID}`를 유지한다. Auth UUID는 JWT `sub`, BFF 내부 session attribute와 Spring Session principal index, Core `users.auth_user_id`에서만 사용한다.
- D-015: 신규 Auth User의 Core projection은 BFF가 인증 성공 직후 동기 생성한다. Core는 target JWT와 workload identity를 함께 검증하고 `sub == authUserId`, `resourceUserId == "user-" + authUserId`일 때만 멱등 upsert한다. projection 실패 시 BFF는 Browser session을 만들지 않고 AuthSession을 best-effort revoke한다.
- D-014: Browser session cutover 이후 rollback은 direct Backend가 아니라 동일 cookie/Redis/Token Vault 계약을 사용하는 안정 BFF release로 수행한다. 신규 release는 readiness drain 뒤 ingress traffic weight를 0으로 내리고, Browser token/Bearer 코드는 복원하지 않는다.
- D-015: Refresh credential은 AuthSession별 한 family와 1회용 lineage를 사용한다. 사용된 credential 재사용은 grace 없이 해당 AuthSession/family 전체를 revoke하고 durable event를 기록하며 다른 기기의 AuthSession은 유지한다.
- D-016: Access는 KMS RSA-2048 `RS256`으로 서명한 audience별 10분 JWT다. 각 JWT는 단일 Resource Service audience와 `iss/sub/sid/jti/iat/nbf/exp/ver`를 필수로 가지며 업무 권한 원본은 포함하지 않는다.
- D-017: 현재/전체 로그아웃과 보안 폐기는 Auth DB revoke와 transactional outbox를 함께 커밋한다. Resource Service는 at-least-once revoke event를 idempotent하게 소비해 `sid`를 access 만료까지 로컬 denylist에 보관한다.
- D-018: 내부 서비스 호출은 mTLS SPIFFE workload identity, 서비스별 Security Group과 principal/endpoint allowlist를 함께 사용한다. 모든 기기 로그아웃은 최근 10분 인증 또는 local/Google 재인증을 요구한다.
- D-019: Core의 업무 식별자 `users.id`는 문자열 PK로 유지하고 Auth subject용 `users.auth_user_id UUID`를 projection으로 둔다. canonical legacy ID만 결정적으로 변환하고 비정형 ID는 수동 정리 없이는 Auth로 이전하지 않는다.
- D-020: T034는 별도 source/target JDBC 연결을 사용하는 오프라인 도구로 User/AuthIdentity snapshot과 최종 delta를 이관한다. dry-run/apply/verify와 exact reconciliation을 제공하며 DB link, runtime dual-write와 legacy AuthSession 변환을 사용하지 않는다.
- D-021: 모든 기기 로그아웃의 step-up 인증은 세션 비생성 전용 endpoint를 사용한다. Auth Service가 현재 AuthSession/User와 local/Google identity를 결합 검증해 서버 시각만 반환하고, Auth 전체 revoke의 durable commit 뒤 BFF가 Auth UUID Spring Session index로 다른 세션을 먼저, 현재 세션을 마지막에 삭제한다.
- D-022: NonProd 네트워크는 `ap-northeast-2`의 `10.20.0.0/16` VPC로 시작하고 2개 AZ에 Public, Private app, Data subnet을 둔다. 초기 Terraform은 VPC/subnet/route table/IGW 기준선을 보존하고, 이후 수작업으로 구성된 NAT Gateway/private route/S3 Gateway Endpoint는 장기 IaC 모듈화 범위에 포함한다.
- D-023: NonProd는 단일 ECS Fargate 클러스터에서 BFF/Auth/Core/AI/Realtime STT를 별도 ECS Service/Task Definition/Task Role/Security Group/7일 Log Group으로 격리한다. Realtime STT는 컨테이너 포트 `8083`에서 Core SG의 내부 호출만 허용하고 public ALB에는 직접 연결하지 않는다. Frontend는 S3+CloudFront+WAF, API는 Route 53→CloudFront/WAF→public ALB→BFF를 사용한다.
