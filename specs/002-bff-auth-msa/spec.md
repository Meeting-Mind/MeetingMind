# Feature Specification: BFF Auth and Gradual MSA

## Summary

MeetingMind 웹 클라이언트에서 access/refresh token을 제거하고 별도 Spring Boot Web BFF의 서버 세션으로 인증을 전환한 뒤, Auth Service와 업무 서비스를 AWS ECS Fargate 단일 리전 Multi-AZ에 점진적으로 분리한다.

## Requirement Sources

- Requirements index: `requirements/INDEX.md`
- Glossary: `requirements/glossary.md`의 `BffSession`, `AuthSession`, `TokenBundle`, `WebBff`, `AuthService`
- Permissions: `requirements/permissions.md`
- Functional requirements: `requirements/functional-requirements.md`, `requirements/functional-requirements-detail.md`의 `FR-AUTH-01~18`
- Non-functional requirements: `requirements/non-functional-requirements.md`, `requirements/non-functional-requirements-detail.md`의 `NFR-SEC-01~08`, `NFR-SCAL-02`, `NFR-AVAIL-01~02`, `NFR-REL-01`, `NFR-DATA-05`
- Policies: `requirements/policies.md`의 `POL-TOKEN-01~02`, `POL-SESSION-01~02`, `POL-DEPLOY-01`, `POL-REALTIME-01`
- Status values: `requirements/status-values.md`의 `BffSession`
- Performance/provider targets: `requirements/performance.md`의 LiveKit/Google 외부 연동 및 secret/token log 기준

## Why

- 현재 Frontend는 access/refresh token을 `sessionStorage`에 저장해 P0 `NFR-SEC-02`와 충돌하고 XSS가 장기 refresh token을 읽을 수 있다.
- Access token 만료 후 Frontend 자동 refresh와 logout 연결이 없어 UI 인증 상태와 API 인증 상태가 어긋날 수 있다.
- 현재 Backend는 인증 발급과 모든 업무 API를 한 프로세스에서 처리하므로 일부 기능 장애와 전체 API 장애를 격리하기 어렵다.
- Web BFF는 브라우저에 토큰을 노출하지 않으면서 점진적으로 분리되는 Resource Service에 사용자 access token을 전달하는 안정된 진입점을 제공한다.
- Auth Service와 Resource Service를 분리하고 Resource Service가 access JWT를 로컬 검증하면 Auth Service 장애가 이미 발급된 access의 즉시 전체 장애로 전파되는 것을 줄일 수 있다.

## Users

- Primary: MeetingMind 웹 사용자
- Secondary: 기업 보안 관리자, 플랫폼 운영자, 서비스 개발자

## Scope

### In Scope

- 별도 Spring Boot `web-bff` 서비스와 브라우저 동일 Origin API 진입점
- Spring Security, CSRF, Spring Session Redis 기반 BFF 세션
- 브라우저의 access/refresh token 및 `sessionStorage` 인증 상태 제거
- BFF 전용 Redis와 KMS 기반 암호화 Token Vault 경계
- 현재 Backend token API를 BFF가 서버 측에서 사용하는 호환 단계
- BFF 서버 측 access 만료 확인, refresh single-flight, 원 요청 1회 재시도
- 현재 세션 로그아웃과 모든 기기 로그아웃
- 현재 Google Identity Services credential을 서버에서 검증하고 BFF 세션으로 전환
- Auth Service를 기존 Backend에서 점진 추출하고 내부 access/refresh/JWKS 경계를 제공
- AWS `ap-northeast-2` ECS Fargate 단일 리전 Multi-AZ, LiveKit Cloud를 목표로 한 배포·장애 격리 설계
- NonProd 단일 ECS 클러스터에서 BFF/Auth/Core/AI/Realtime STT를 서비스별 ECS Service, Task Definition, Task Role, Security Group, CloudWatch Log Group으로 격리
- 구조상 독립 서비스인 `realtime-stt`를 ECS Fargate 서비스로 배포하고 Core에서만 접근하도록 격리
- AI/LiveKit 장애 시 Space/Meeting 핵심 기능의 graceful degradation

### Out of Scope

- 한 번에 모든 도메인을 마이크로서비스로 분리하는 big-bang 전환
- 멀티리전 active-active 또는 재해복구 구현
- LiveKit 자체 호스팅
- 모바일/데스크톱/외부 공개 API 인증
- Google Calendar/Drive 권한 위임
- 범용 기업 OIDC/SAML 로그인 구현
- Refresh 탈취 재사용 시 token family 전체 폐기의 세부 알고리즘
- JWT 알고리즘, claim 전체, KMS 서명키 교체 주기의 최종 운영값
- `realtime-stt` ECS Service/Task Definition 배포 및 Core 연동

## User Stories

1. As a 웹 사용자, I want 브라우저에 인증 토큰을 저장하지 않고 로그인 상태를 유지하고 싶다, so that 장기 토큰 탈취 위험을 줄일 수 있다.
2. As a 웹 사용자, I want access 만료가 BFF에서 자동 처리되길 원한다, so that 작업 중 반복 로그인이나 지속적인 401을 겪지 않는다.
3. As a 웹 사용자, I want 로그아웃 즉시 현재 또는 모든 기기 세션을 종료하고 싶다, so that 분실 기기와 공유 브라우저 접근을 차단할 수 있다.
4. As a 운영자, I want Auth/AI/Realtime/Core 장애가 독립적으로 격리되길 원한다, so that 한 기능 장애가 전체 MeetingMind 장애로 번지지 않는다.
5. As a 서비스 개발자, I want 브라우저 계약을 유지한 채 Backend 기능을 순차 분리하고 싶다, so that big-bang 전환 없이 MSA로 이동할 수 있다.
6. As a 운영자, I want 서비스별 AWS 권한·네트워크·로그 경계와 Multi-AZ 배치를 갖고 싶다, so that 한 서비스의 권한 또는 장애가 다른 서비스로 확산되지 않는다.

## Functional Requirements

- FR-BFF-001: 브라우저는 `Secure`, `HttpOnly`, `SameSite` BFF 세션 쿠키만 보유하고 access/refresh token을 응답이나 Web Storage로 받지 않아야 한다.
- FR-BFF-002: 앱 시작 시 BFF session endpoint로 인증 상태와 사용자 정보를 복원해야 한다.
- FR-BFF-003: 모든 상태 변경 브라우저 요청은 CSRF 검증을 통과해야 한다.
- FR-BFF-004: BFF는 access 만료 전 또는 downstream 401 시 서버 측 refresh를 수행하고 원 요청을 최대 한 번 재시도해야 한다.
- FR-BFF-005: 같은 BFF 세션의 동시 refresh는 single-flight 또는 분산 lock으로 하나만 실행해야 한다.
- FR-BFF-006: refresh 실패 시 BFF 세션, Token Bundle, 쿠키를 정리하고 최종 401을 반환해야 한다.
- FR-BFF-007: 현재 로그아웃은 BFF 세션 삭제, Auth 세션 revoke, Token Bundle 삭제와 쿠키 만료를 멱등 처리해야 한다.
- FR-BFF-008: 모든 기기 로그아웃은 사용자에 속한 모든 BFF/Auth 세션을 폐기해야 한다.
- FR-BFF-009: Google credential은 Auth 경계에서 검증한 뒤 보관하지 않고 MeetingMind 내부 세션만 생성해야 한다.
- FR-BFF-010: 점진 전환 동안 브라우저의 `/api/v1/*` 계약을 BFF가 유지하고 현재 Backend 경로로 명시적으로 라우팅해야 한다.
- FR-BFF-011: Auth Service 추출 후 access/refresh token은 BFF 내부 계약에만 존재하고 Resource Service는 access JWT를 로컬 검증해야 한다.
- FR-BFF-012: LiveKit 참가자 token은 BFF/Core 권한 검증 뒤 브라우저에 전달되는 회의 한정 단기 token으로 유지하고 MeetingMind access token과 혼용하지 않아야 한다.

## Non-Functional Requirements

- NFR-BFF-001: Web BFF/Auth/Core/AI/Realtime STT는 각각 독립된 ECS Service와 Task Definition으로 배포·수평 확장이 가능해야 한다.
- NFR-BFF-002: 운영 BFF 세션은 전용 HA Redis에 두고 sticky session에 의존하지 않아야 한다.
- NFR-BFF-003: 운영 Token Bundle은 AWS KMS 기반 암호문으로만 저장하고 평문 token/secret을 로그에 남기지 않아야 한다.
- NFR-BFF-004: BFF의 downstream 호출은 서비스별 timeout, circuit breaker, bulkhead와 허용 목적지 목록을 가져야 한다.
- NFR-BFF-005: Fargate Task/AZ 장애 시 다른 AZ의 정상 Task가 BFF 요청을 수용하고 유효 세션을 복원해야 한다.
- NFR-BFF-006: Redis 장애 시 쿠키 자체를 신뢰하는 fallback을 사용하지 않고 fail closed해야 한다.
- NFR-BFF-007: Auth Service 장애 시 이미 유효한 access token은 만료 전까지 Resource Service가 로컬 검증할 수 있어야 한다.
- NFR-BFF-008: AI 또는 LiveKit Cloud 장애는 핵심 Space/Meeting CRUD 성공으로 위장하거나 mock 성공으로 대체하지 않아야 한다.
- NFR-BFF-009: Frontend, BFF, Auth, Core의 인증 계약과 negative test가 CI에서 검증되어야 한다.
- NFR-BFF-010: BFF/Auth/Core/AI/Realtime STT는 서비스별 Task Role과 Security Group으로 최소 권한·최소 통신 경계를 가져야 한다. 공통 Task Execution Role은 이미지 pull과 로그 전송 같은 실행 권한으로 제한한다.
- NFR-BFF-011: NonProd Fargate Task는 2개 AZ의 private app subnet에 배치하고 public ALB만 public subnet에 두어야 한다.

## Data and Permission Rules

- BffSession은 사용자 인증 진입점일 뿐 Space/Meeting 권한의 원천이 아니다.
- Resource Service는 access JWT subject만으로 동적 Space/Meeting 권한을 신뢰하지 않고 자신의 최신 RBAC/ACL을 확인한다.
- Meeting AI/Project AI 권한 선필터 원칙은 서비스 분리 후에도 Backend/Core 경계에서 먼저 적용한다.
- Token Bundle, refresh hash, 세션 감사 데이터는 목적과 보존 기간을 분리한다.
- 한 서비스는 다른 서비스 DB를 직접 조회하지 않고 API 또는 명시적 이벤트 계약을 사용한다.

## Acceptance Criteria

- AC-001: 정책·요구사항에서 Frontend `sessionStorage` token 저장 지시가 제거되고 BFF/Auth 저장 역할이 구분된다.
- AC-002: Browser-BFF와 BFF-Auth API 계약이 분리되어 있고 public refresh endpoint가 목표 계약에 없다.
- AC-003: 세션/Token Bundle/AuthSession의 관계와 보존·암호화·폐기 규칙이 데이터 모델과 ERD에 일치한다.
- AC-004: `Web BFF → Auth Service → 도메인 서비스` 점진 전환 순서와 호환·롤백 경계가 계획과 tasks에 있다.
- AC-005: AWS ECS Fargate 단일 리전 Multi-AZ와 LiveKit Cloud 결정, 서비스별 IAM/네트워크/로그 격리 및 장애별 degradation이 계획에 있다.
- AC-006: 구현 전 차단 질문과 검증 기준이 `clarify.md`, `tasks.md`, `analyze.md`에서 추적된다.
- AC-007: BFF/Auth 런타임 이미지는 수정 가능한 HIGH/CRITICAL 취약점이 없고, 저장소 전체 이력 secret scan은 실제 secret과 테스트 fixture 오탐을 fingerprint 단위로 구분해 통과해야 한다.

## Open Questions

- Q-003: AWS 리전과 ECS Fargate 배포 플랫폼은 결정됐다. 서비스 discovery/내부 workload 인증, edge 우회 차단/target protocol, NAT Gateway AZ 경계, 공용 tag/IaC 수렴과 서비스별 SLO/RTO/RPO는 `clarify.md` Q-013, Q-023~Q-026에서 결정한다.
