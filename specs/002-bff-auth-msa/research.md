# Research: BFF Auth and Gradual MSA

## Context

- 현재 React는 MeetingMind access/refresh token을 `sessionStorage`에 저장하고 모든 API에 Bearer access를 전달한다.
- 현재 Spring Backend는 token 발급·검증과 업무 API를 함께 제공하고 PostgreSQL `auth_sessions`에는 refresh hash/revoke 상태를 저장한다.
- Frontend 자동 refresh와 logout 호출은 연결되지 않았고 access token은 logout 뒤에도 만료 전까지 유효하다.
- 목표는 브라우저 토큰 노출을 제거하면서 AWS EKS에서 Web BFF, Auth, Core, AI, Realtime 장애를 점진적으로 격리하는 것이다.

## Questions

- RQ-001: 브라우저 token 탈취를 줄이면서 서비스 분리 후 사용자 권한을 어떻게 전달할까?
- RQ-002: 현재 모놀리스에서 MSA로 big-bang 없이 이동할 수 있는 경계는 무엇인가?
- RQ-003: 세션과 token을 어디에 저장해야 EKS 수평 확장과 즉시 로그아웃을 함께 지원할까?
- RQ-004: Auth Service 장애가 모든 Resource Service 장애로 전파되지 않게 하려면 어떻게 검증할까?
- RQ-005: Google 로그인과 LiveKit 미디어 평면을 MSA 전환에 어떻게 포함할까?

## Decisions

| ID | Decision | Reason | Alternatives | Impact |
| --- | --- | --- | --- | --- |
| D-001 | 별도 Spring Boot Web BFF | 브라우저 계약과 내부 서비스 분리를 위한 안정된 경계이며 Spring Security/Session 패턴을 재사용할 수 있다. | 기존 Backend 통합 BFF, Node BFF | 새 `bff` 서비스와 proxy/aggregation 테스트가 필요하다. |
| D-002 | 브라우저 token 미노출, 서버 세션 cookie | 장기 refresh를 XSS가 읽지 못하고 BFF가 refresh와 logout을 일관 처리한다. | sessionStorage, token-mediating backend | Frontend auth model과 CSRF 처리가 바뀐다. |
| D-003 | BFF Redis session + 암호화 Token Vault | 다중 Pod 세션 공유와 token 역할 분리를 지원한다. | JDBC session, client-side encrypted session | Redis/KMS 인프라와 장애 대응이 필요하다. |
| D-004 | Auth Service가 token 발급, Resource Service가 JWT 로컬 검증 | Auth Service 매 요청 의존을 제거해 장애 전파를 줄인다. | 매 요청 introspection, shared server session | 비대칭 key/JWKS와 claim 정책이 필요하다. |
| D-005 | Strangler 전환 | BFF 호환 경계를 먼저 만들고 Auth/도메인을 순차 추출해 rollback 범위를 줄인다. | big-bang MSA | 임시 compatibility adapter가 필요하다. |
| D-006 | Google ID credential 검증 유지 | 현재 요구는 인증이며 Google API 권한 위임이 아니다. | 즉시 Authorization Code/OIDC 전환 | 검증 코드를 Auth Service로 이동하고 credential은 보관하지 않는다. |
| D-007 | AWS EKS 단일 리전 Multi-AZ | Kubernetes 확장성과 서비스별 독립 배포를 장기 목표로 선택했다. | ECS Fargate, 멀티리전 EKS | readiness, graceful shutdown, HPA/PDB/NetworkPolicy/IAM 설계가 필요하다. |
| D-008 | LiveKit Cloud | 미디어 plane 운영과 UDP/TURN 장애를 MeetingMind EKS 경계에서 분리한다. | EKS/EC2 자체 호스팅 | provider 장애 시 실시간 기능 degradation이 필요하다. |
| D-009 | 서비스별 데이터 소유 | 공유 DB가 장애/배포 결합점이 되는 것을 줄인다. | 모든 서비스 shared schema | Auth DB 분리와 cross-service transaction 대체가 필요하다. |
| D-010 | AES-256-GCM envelope encryption + AWS KMS data key | payload 기밀성과 무결성을 함께 보장하고, EKS workload IAM으로 KMS 권한을 분리하며 저장소에는 ciphertext와 encrypted data key만 남긴다. | token 직접 KMS Encrypt, 애플리케이션 고정키 직접 암호화 | AWS SDK v2 KMS adapter와 local/test key adapter, encryption context 및 fail-closed 테스트가 필요하다. |
| D-011 | Legacy Backend auth compatibility client | Browser 계약을 먼저 BFF session으로 전환하면서 현재 인증 구현과 rollback 경로를 보존한다. | Backend 응답을 바로 Browser에 전달, Auth Service 즉시 추출 | BFF가 token 응답을 서버 내부에서만 소비하고 호환용 authSessionId를 생성하며 public refresh는 노출하지 않는다. |
| D-014 | Browser cutover rollback은 안정 BFF release로 제한 | direct Backend 복귀는 제거한 Browser token/Bearer 코드를 다시 배포해야 해 P0 token 무노출과 rollback 단순성을 깨뜨린다. | Frontend direct Backend flag, 긴 dual client 유지 | 신규 BFF를 readiness drain하고 동일 cookie/Redis/Vault 계약의 안정 release로 traffic을 되돌리며 현재 Backend compatibility API/DB는 server-side 경계로만 보존한다. |
| D-015 | AuthSession 범위 refresh family 전체 폐기, grace 없음 | BFF Redis single-flight가 정상 동시 refresh를 이미 직렬화하므로 grace는 탈취 credential의 유효 시간만 늘린다. 사용자 전체가 아닌 의심 기기만 재로그인시켜 보안과 UX를 균형화한다. | 이전 credential만 거부, 사용자 전체 session revoke, grace window | `familyId`/`replacementId` lineage와 행 잠금 원자 rotation, reuse audit/outbox가 필요하다. |
| D-016 | KMS RSA-2048 `RS256`, 서비스별 10분 JWT와 JWKS | Spring/JWT 생태계 호환성과 비반출 KMS private key를 사용하면서 Auth 매 요청 introspection 없이 Resource Service가 로컬 검증할 수 있다. | ES256, shared HMAC, opaque introspection | audience별 access 집합, 필수 claim 검증, 90일 rotation/1시간 overlap/5분 cache가 필요하다. |
| D-017 | `sid` revoke event와 Resource local denylist | 매 요청 중앙 조회 없이 로그아웃된 access를 빠르게 차단하고, event 지연/장애 시 위험을 10분 access TTL과 최대 60초 skew로 제한한다. | 중앙 denylist 매 요청 조회, 짧은 JWT만 사용 | Auth transactional outbox, at-least-once idempotent consumer와 expiry-bounded cache가 필요하다. |
| D-018 | mTLS SPIFFE workload identity | shared secret 없이 BFF/Auth/Resource workload를 상호 인증하고 EKS 서비스 분리 후 principal 단위 최소 권한을 적용한다. | OAuth client credentials, 자체 요청 서명 | NetworkPolicy, ingress 차단, 인증서 자동 회전과 principal allowlist가 필요하다. |
| D-019 | T032 token runtime과 T033 KMS signer를 port로 분리 | refresh rotation/revoke transaction을 먼저 완성하면서 임시 HMAC·로컬 private key가 목표 RS256 계약으로 유출되는 것을 막는다. | T032 임시 signer, T033까지 전체 endpoint 보류 | 테스트 signer로 T032 계약을 검증하되 운영 adapter가 없으면 token 발급 transaction 전체를 rollback하고 `TOKEN_ISSUER_UNAVAILABLE`로 fail closed한다. |
| D-020 | Refresh lookup hash는 환경 secret 기반 HMAC-SHA-256 | 랜덤 refresh 원문을 DB에 저장하지 않으면서 indexed lookup과 secret 교체 경계를 제공한다. | 평문, 단순 SHA-256, 느린 password hash | 최소 32자 secret을 secret manager로 주입하고 DB에는 `hmac_sha256$...`만 저장한다. |
| D-021 | T032는 transactional outbox producer까지만 구현 | transport 제품이 Q-012/T040에 남아 있어 임시 broker/no-op publisher가 durable revoke를 성공으로 위장하면 안 된다. | 임시 in-memory/no-op publish, 제품 선결정 | revoke transaction은 outbox row까지 durable하게 커밋하고 실제 전송·재시도·관측 adapter는 T045 출시 gate로 유지한다. |
| D-012 | 명시 route registry와 JDK/Spring 기반 서비스별 회복성 guard | 현재 실제 API만 목적지·method와 함께 고정해 SSRF/과도한 proxy 범위를 막고, 새 라이브러리 없이 timeout·Semaphore bulkhead·연속 실패 circuit 요구를 충족한다. | 동적 reverse proxy, Resilience4j 신규 도입, 모든 `/api/v1/**` 전달 | Core/AI/LiveKit별 설정과 route/error 자동 테스트가 필요하며 운영 SLO 확정 뒤 기본값을 조정한다. |
| D-022 | Core 문자열 User PK 유지 + Auth UUID projection | 이미 Space/Meeting 등 다수 업무 FK가 `users.id`를 참조하므로 전면 PK 재작성 없이 Auth의 UUID subject 계약을 지킨다. | Auth도 legacy 문자열 사용, 모든 Core FK를 UUID로 일괄 변환, 별도 mapping table | Core `users.auth_user_id UUID` unique projection과 canonical `user-{UUID}` backfill이 필요하며 비정형 인증 ID는 fail closed한다. |
| D-023 | 오프라인 snapshot/delta 이관 + 짧은 인증 쓰기 중단 | 현재 규모에서 dual-write/CDC 운영 복잡도를 추가하지 않고 동일 입력의 반복 실행과 exact reconciliation으로 전환을 검증할 수 있다. | application dual-write, CDC/DMS/Debezium, login 시 lazy migration | User/AuthIdentity만 이전하고 최종 delta 동안 login/signup/Google 쓰기를 중단한다. 기존 refresh/AuthSession은 이전하지 않아 사용자는 전환 후 재로그인한다. |

## Evidence

- IETF OAuth browser-app BFF draft는 BFF가 cookie session에 access/refresh를 연결하고 브라우저에 token을 노출하지 않은 채 Resource Server 요청에 access를 붙이는 패턴을 설명한다: https://datatracker.ietf.org/doc/draft-ietf-oauth-browser-based-apps/26/
- Spring Session은 `HttpSession`을 Redis/JDBC로 외부화해 다중 인스턴스 세션을 지원한다: https://docs.spring.io/spring-session/reference/http-session.html
- Spring Security는 session fixation 방어, CSRF, logout session invalidation을 제공한다: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html, https://docs.spring.io/spring-security/reference/features/exploits/csrf.html
- Google Identity Services는 로그인 ID token과 Google API authorization token을 구분하고 ID token 만료를 앱 세션 관리에 사용하지 않도록 안내한다: https://developers.google.com/identity/gsi/web/guides/overview
- AWS와 Azure MSA 지침은 service별 데이터 소유, circuit breaker, bulkhead와 점진적 Strangler 전환이 장애 격리의 필수 조건임을 설명한다: https://docs.aws.amazon.com/prescriptive-guidance/latest/modernization-data-persistence/database-per-service.html, https://learn.microsoft.com/en-us/azure/architecture/microservices/design/patterns
- AWS EKS 운영은 security, networking, autoscaling, upgrade와 application reliability를 별도 Day-2 책임으로 다룬다: https://docs.aws.amazon.com/eks/latest/best-practices/introduction.html
- ElastiCache는 TLS, at-rest encryption, IAM/AUTH/RBAC 경계를 제공하고 Secrets Manager는 AWS KMS envelope encryption을 사용한다: https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/encryption.html, https://docs.aws.amazon.com/secretsmanager/latest/userguide/security-encryption.html
- AWS KMS `GenerateDataKey`는 즉시 사용할 plaintext data key와 KMS key 아래 암호화된 data key를 함께 반환하며, plaintext는 사용 후 제거하고 encrypted data key만 저장하는 envelope encryption 경계를 제공한다: https://docs.aws.amazon.com/kms/latest/developerguide/example_kms_GenerateDataKey_section.html

## Rejected Options

- Frontend `sessionStorage` 유지: 구현은 작지만 P0 보안 정책과 충돌하고 XSS가 refresh를 읽을 수 있어 목표 아키텍처에서 제외한다.
- Refresh만 HttpOnly cookie, Access는 브라우저 보관: long-lived token 탈취는 줄지만 브라우저가 Resource Service token을 계속 다루므로 full BFF보다 약하다.
- 기존 Spring을 영구 통합 BFF로 사용: 현재 모놀리스에는 단순하지만 명시된 MSA 전환 후 BFF와 업무 장애 경계를 다시 결합한다.
- BFF와 Auth/Core를 동시에 big-bang 분리: rollback과 문제 격리가 어렵고 기존 인증·권한 회귀 범위가 너무 크다.
- 매 요청 Auth introspection: 즉시 revoke에는 유리하지만 Auth Service 장애와 지연이 모든 Resource Service에 전파된다.
- Spring Session JDBC: 신규 인프라가 적지만 세션 hot write가 Core PostgreSQL과 장애 자원을 공유한다.
- Google Authorization Code 즉시 전환: Google API 권한이 없는 현재 로그인 요구에 불필요한 scope/token 운영을 추가한다.
- LiveKit 자체 호스팅: 미디어 node, UDP/TURN, autoscaling 운영이 현재 제품 핵심 개발 범위를 잠식한다.
- Auth subject에 legacy 문자열 ID 유지: 초기 변경은 작지만 UUID/JWT 계약과 서비스별 독립 모델에 legacy 형식이 계속 전파된다.
- Core User PK/FK 일괄 UUID 변환: 신규 데이터가 거의 없고 모든 도메인 팀을 동시에 멈출 수 있을 때는 깔끔하지만, 현재 점진 전환과 충돌·rollback 범위에 비해 위험이 크다.
- application dual-write 또는 CDC 선도입: 무중단 대규모 이전에는 적합하지만 현재는 분산 실패 보상, 순서·재처리 관측과 추가 인프라가 T034 범위를 크게 넘는다.

## Follow-up

- T030에서 확정한 refresh family, JWT/JWKS, revoke event와 mTLS 계약을 Auth Service 구현에 적용한다.
- 서비스별 SLO/RTO/RPO와 장애 주입 테스트를 EKS 프로비저닝 전에 확정한다.
- EKS node 운영 방식, IaC 도구, 첫 AWS 리전과 mTLS/SPIFFE 구현 제품을 별도 ADR로 확정한다.
- 기업 SSO 요구가 생기면 Google 인증과 분리된 범용 OIDC/SAML 스펙을 만든다.
