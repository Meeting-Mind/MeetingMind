이 문서는 MeetingMind Core Prototype의 구현 결과, 작업 배정, 충돌 처리, 남은 작업을 기록하기 위한 Markdown 문서이다.

# Implementation Log: MeetingMind Core Prototype

## Scope Implemented

- Spec Kit 기반 문서 계층을 추가했다.
- 프로젝트 헌법, 공통 에이전트 지침, 기능 스펙, 기술 계획, 데이터 모델, API 계약, 작업 목록, 일관성 분석 문서를 작성했다.
- specs 변경 동반 갱신 규칙, 누락 템플릿, 작업 완료 기준, clarification 우선순위, analysis 추적 상태를 보완했다.
- Git 작업 절차, staging, commit, pull, branch, push 규칙을 구체화했다.
- 팀/에이전트 병렬 작업 계획, 충돌 경계, owner/agent 기반 tasks 형식을 추가했다.
- milestone 기반 task 작성 규칙과 에이전트 친화 작업 단위 기준을 추가했다.
- API_SPEC 초안에서 공통 API 규칙, Meeting status, 오류 응답, transcript/speaker 계약 후보를 MeetingMind 기준으로 선별 반영했다.
- 기존 umbrella task T010-T018을 즉시 배정 가능한 상세 task T024-T069로 분해했다.
- AI 담당 workstream을 T070-T077로 분리하고, 용어 설명 prototype의 AI 서버 endpoint를 추가했다.
- Auth/Login 담당 workstream을 분리하고, Q-001 인증 방식 선택지와 최종 결정을 정리했다.
- Q-001을 Google OAuth와 자체 회원가입/로그인 병행, access/refresh token 발급, `/api/v1/auth/*`, `sessionStorage`, 랜딩 외 보호 route로 확정했다.
- LiveKit token 발급 로직에 JUnit 단위 테스트를 추가하고, AI 용어 설명/RAG source mapping에 Python 표준 unittest를 추가했다.
- Backend Auth API를 `/api/v1/auth/*`로 추가했다. 자체 signup/login, Google credential login, refresh, me, logout endpoint와 access/refresh token 발급을 구현했다.
- Frontend Auth 연결을 추가했다. 자체 로그인/회원가입과 Google credential exchange를 `/api/v1/auth/*`로 보내고, token pair는 `sessionStorage`에 저장한다.
- 랜딩(`/`) 외 앱 route를 보호 route로 감싸 비로그인 사용자는 로그인 모달로 유도하고, 로그인 성공 후 요청했던 route로 복귀하게 했다.
- Backend Auth runtime smoke 중 `AuthTokenService`, `GoogleJwtCredentialVerifier`의 테스트용 보조 생성자 때문에 Spring이 런타임 생성자를 선택하지 못하는 문제를 발견했다. 런타임 생성자에 `@Autowired`를 명시하고 context smoke test를 추가했다.
- Backend 빌드를 Maven에서 Gradle로 전환했다. 기본 Java 26 환경에서도 wrapper가 실행되도록 Gradle 9.6.1을 사용하고, 컴파일/테스트 toolchain은 Java 21로 유지한다.
- Google Sheets 요구사항 정의서를 `requirements/*` Markdown 기준선으로 분할했다.
- 기능/비기능 요구사항은 요약 카탈로그와 전체 우선순위 상세 문서로 분리했다. 상세 문서에는 P2를 포함해 인수 기준, 조건/권한, 실패/예외, 검증 기준 또는 측정 방법을 보존했다.
- 요구사항 읽기 전략을 `AGENTS.md`와 constitution에 반영했다. 구현자는 `requirements/INDEX.md`를 먼저 읽고 필요한 요구사항 문서만 추가로 읽는다.
- 용어집, 권한 매트릭스, 상태값 기준으로 core spec, plan, data-model, contracts, clarify, tasks, analyze를 갱신했다.
- 권한 role enum 표기는 용어집 기준인 `OWNER`/`ADMIN`/`MEMBER`, `HOST`/`EDITOR`/`VIEWER`로 정리했다.
- 정책, 성능지표, 용어집, 상태값 문서는 Google Sheets의 전체 컬럼을 보존하는 상세 스냅샷으로 보강했다.
- Q-002 회의 권한 등급은 `HOST`, `EDITOR`, `VIEWER`로 결정했고, 회의 게스트는 특정 회의의 `MeetingParticipant`로만 접근한다.
- Q-003 STT 원문 보존 기본값은 30일로 결정했다.
- Q-004 Project Knowledge는 SpaceMember가 조회하고 오너/관리자가 수정하며, 회의 게스트는 기본 접근할 수 없도록 정리했다.
- API 명세를 기능군별 파일로 분리하고, backend 전체 도메인 ERD 초안을 추가했다.
- API/ERD/Data Model 변경 시 관련 문서 영향 확인과 `implement.md` 로그 기록을 에이전트 지침에 추가했다.
- AI 담당 범위에서 분리 API/ERD 기준선을 재검토하고, RAG scope/unsupported/sourceId/candidate 안전 검증을 보강했다.

## Work Allocation

| Owner | Agent | Tasks | Scope |
| --- | --- | --- | --- |
| Codex | Codex | T021 | 병렬 작업 지침, 템플릿, core plan/tasks/implement/handoff 문서 보완 |
| Codex | Codex | T022 | milestone 기반 task 작성 규칙과 에이전트 친화 작업 단위 기준 보완 |
| Codex | Codex | T023 | API 계약, 데이터 모델, 계획, clarification 문서 보완 |
| Codex | Codex | T024-T069 planning | milestone별 상세 구현 task 분해 |
| 사용자 | Codex | T070-T077 | 백엔드/프론트엔드 구현 없이 AI 서버 중심으로 용어 설명, 요약/보고서 생성, 챗봇, 태스크 추출 prototype 작업 착수 |
| 사용자 | Codex | T070-T071 | 현재 AI 코드 경계 기록과 AI prototype API 계약 문서화 |
| 사용자 | Codex | T072 | 회의 중 transcript 용어 설명 prototype 구현 |
| 사용자 | Codex | T078-T088 | STT/DB 구현 전 RAG chunk 형식, in-memory retriever, RAG 기반 AI 기능 전환 작업 |
| 사용자 | Codex | T104, T087-T088 | 요구사항 기준선 반영 후 AI/RAG 영향도 점검, RAG safety 검증, AI workstream closeout |
| 사용자 | Codex | T078-T079 | RAG chunk/embeddingText 형식 문서화와 AI 서버 RAG 타입 경계 추가 |
| 사용자(Auth 담당) | Codex | T024 | Google OAuth 단독, 자체 계정/JWT, Google OAuth + 자체 계정 + access/refresh token 병행안 비교와 결정 정리 |
| 사용자(Auth 담당) | Codex | T089-T096 | 로그인/인증 기반 구축 작업 경계, Auth API 계약, Backend 검증, Frontend auth 상태, 보호 route guard 계획 |
| 사용자(Auth 담당) | Codex | T089 | 현재 Frontend Google 로그인 모달과 Backend auth/security 부재 상태 조사 |
| 사용자(Auth 담당) | Codex | T090 | `/api/v1/auth/*` Auth API, User/AuthIdentity/AuthSession 모델, token storage/protected route 계약 문서화 |
| 사용자 | Codex | T107-T109 | 기능군별 API 명세 분리, backend 전체 도메인 ERD 초안, API/ERD 변경 로그 지침 보강 |
| 사용자(Frontend 담당) | Codex | T044 | Frontend route, API client, mock fallback 위치 조사 |
| 사용자(Frontend 담당) | Codex | T045 | Frontend target API TypeScript type 추가 |
| 사용자(Frontend 담당) | Codex | T046 | Frontend project 선택 상태를 stable `spaceId` query 우선으로 정리 |
| 사용자(Frontend 담당) | Codex | T047 | Frontend legacy workspace snapshot client와 target Space/Meeting API client 경계 분리 |
| 사용자(Frontend 담당) | Codex | T121 | FR-DASH/FR-CAL 상세 요구 기반 dashboard/calendar frontend 구현 계획과 M017 task 분해 |
| 사용자(Frontend 담당) | Codex | T131 | FR-MREG/FR-ACL/FR-KAN/FR-PBOT/FR-PERM/FR-OWN 상세 요구 기반 project workspace 구현 계획과 M018 task 분해 |
| 사용자(Frontend 담당) | Codex | T145 | FR-RPT/FR-MBOT/FR-TASK 상세 요구 기반 meeting workspace 구현 계획과 M019 task 분해 |
| 사용자(Data 담당) | Codex | T058 | Backend DB/migration 도구 현황 조사와 Flyway SQL migration 위치 문서화 |
| 사용자(Data 담당) | Codex | T059 | Flyway 기반 User, Space, SpaceMember V1 schema migration 작성 |
| 사용자(Data 담당) | Codex | T060 | Flyway 기반 Meeting, MeetingParticipant, MeetingSpeaker V2 schema migration 작성 |
| 사용자(Data 담당) | Codex | T061 | Flyway 기반 TranscriptSegment, MeetingReport, report decision/action item V3 schema migration 작성 |
| 사용자(Data 담당) | Codex | T062 | Flyway 기반 ProjectKnowledge, EmbeddingChunk, chunk source segment V4 schema migration 작성 |
| 사용자 | Codex | T063-T064, T204-T209 | 로컬 PostgreSQL/pgvector, 전사 보존, 누락 schema, embedding generation migration과 검증 |

## Files Changed

- `AGENTS.md`: Codex 등 크로스툴 공통 지침, 7개 개념 계층, Git 협업 지침
- `AGENT.md`: 사용자가 직접 작성한 코딩 에이전트 행동 규칙
- `CLAUDE.md`: Claude Code 호환용 포인터
- `.specify/memory/constitution.md`: 프로젝트 불변 원칙
- `.specify/templates/*`: 반복 작업용 템플릿
- `.specify/skills/qa-checklist.md`: 도구 중립 QA 체크리스트
- `specs/001-meetingmind-core/*`: MeetingMind 핵심 프로토타입 스펙 세트
- `requirements/*`: Google Sheets 요구사항 정의서의 로컬 Markdown 기준선
- `specs/001-meetingmind-core/contracts/README.md`: 기능군별 API 명세 라우팅과 변경 규칙
- `specs/001-meetingmind-core/contracts/common.md`: 공통 API 규칙, 오류, role/status/source shape
- `specs/001-meetingmind-core/contracts/auth-api.md`: Auth API 명세 초안
- `specs/001-meetingmind-core/contracts/space-api.md`: Space, dashboard, calendar, member, owner transfer API 명세 초안
- `specs/001-meetingmind-core/contracts/meeting-api.md`: Meeting, ACL, transcript, report API 명세 초안
- `specs/001-meetingmind-core/contracts/kanban-api.md`: Kanban, task card, task candidate API 명세 초안
- `specs/001-meetingmind-core/contracts/knowledge-api.md`: Project Knowledge와 Domain Term 관리 API 명세 초안
- `specs/001-meetingmind-core/contracts/ai-api.md`: Meeting AI, Project AI, report/task candidate, term API 명세 초안
- `specs/001-meetingmind-core/contracts/live-stt-api.md`: LiveKit, meeting room, STT/dialogue API 명세 초안
- `specs/001-meetingmind-core/erd.md`: backend 전체 도메인 ERD 초안
- `ai/app/main.py`: `POST /api/meeting-ai/explain-term` endpoint와 Domain Dictionary 우선 응답, AI fallback 추가
- `ai/app/rag.py`: RAG chunk/source/search request/result 타입과 retriever protocol 추가
- `backend/src/main/java/com/meetingmind/demo/service/LiveKitTokenService.java`: 안정적인 단위 테스트를 위해 clock/config provider 주입 경계 추가
- `backend/build.gradle`, `backend/settings.gradle`, `backend/gradlew*`, `backend/gradle/wrapper/*`: Backend 빌드를 Maven에서 Gradle로 전환
- `backend/src/main/java/com/meetingmind/demo/auth/**`: in-memory Auth store, PBKDF2 password hash, HMAC access token, refresh token hash/revoke, Google ID token verifier, Auth controller/error response 추가
- `backend/src/main/resources/db/migration/V1__create_users_spaces.sql`: User, Space, SpaceMember schema 초안과 active membership/owner partial unique index 추가
- `backend/src/main/resources/db/migration/V2__create_meetings_acl.sql`: Meeting, MeetingParticipant, MeetingSpeaker schema 초안과 회의 ACL/speaker 제약 추가
- `backend/src/main/resources/db/migration/V3__create_transcripts_reports.sql`: TranscriptSegment, MeetingReport, report decision/action item schema 초안과 source id JSON 저장 제약 추가
- `backend/src/main/resources/db/migration/V4__create_knowledge_embeddings.sql`: ProjectKnowledge, EmbeddingChunk, chunk source segment schema 초안과 pgvector column 준비 추가
- `backend/src/test/java/com/meetingmind/demo/service/LiveKitTokenServiceTest.java`: LiveKit JWT claim/signature 성공 케이스와 설정 누락 실패 케이스 테스트
- `backend/src/test/java/com/meetingmind/demo/auth/AuthServiceTest.java`: signup/me, 중복 이메일, 비밀번호 실패, refresh rotation, Google identity 연결 테스트
- `backend/src/test/java/com/meetingmind/demo/MeetingMindApplicationTest.java`: Spring bean wiring context smoke 테스트 추가
- `frontend/src/auth/session.ts`: Auth API client, `sessionStorage` 저장/조회, `Authorization` header helper 추가
- `frontend/src/components/GoogleLoginModal.tsx`: Google 로그인 모달을 자체 로그인/회원가입과 Google Backend exchange가 가능한 auth modal로 확장
- `frontend/src/App.tsx`: auth session 상태, protected route, `/api/workspace` Authorization header 전달 추가
- `frontend/src/styles/app.css`: auth modal form/tab style 추가
- `ai/tests/test_meeting_ai.py`: glossary 우선순위, 근거 없음 응답, transcript source 제한, RAG source mapping 테스트

## Conflict Notes

- 제품 코드 변경 범위는 AI prototype, Backend Auth, Frontend Auth로 확장되었다. Auth 외 `backend/**`, `frontend/**` 변경은 각 workstream owner와 합의한다.
- Auth/Login workstream은 `GoogleLoginModal.tsx`, future `frontend/src/auth/**`, future `backend/**/auth/**`를 우선 소유한다. Frontend/Backend 담당자는 auth token 저장/전달, auth endpoint, backend auth package를 수정하기 전에 Auth owner와 합의한다.
- 현재 `.idea/`는 작업 범위 밖 개인 IDE 설정으로 Git 미추적 상태를 유지한다. 커밋 전 포함하지 않는다.

## Integration Result

- 공통 병렬 작업 원칙은 `AGENTS.md`에 추가했다.
- 기능별 병렬 계획은 `plan.md`와 `plan-template.md`에 추가했다.
- 작업별 owner/agent/dependency/files 관리는 `tasks.md`와 `tasks-template.md`에 추가했다.
- milestone과 task granularity 기준은 `AGENTS.md`, `tasks.md`, `tasks-template.md`에 추가했다.
- 실제 배정/충돌/통합 기록은 `implement.md`와 `implement-template.md`에 추가했다.
- 공통 API 규칙, 오류 응답, Meeting status, transcript/speaker 계약은 `contracts/common.md`, `contracts/meeting-api.md`, `contracts/live-stt-api.md`, `data-model.md`, `plan.md`, `clarify.md`, `tasks.md`에 반영했다.
- 실제 구현 착수는 `tasks.md`의 T024-T106 상세 task 기준으로 진행한다.
- AI 담당 workstream은 `tasks.md`의 T070-T088로 분리했다. T070-T072와 T078-T088은 완료했고, `backend/**`와 `frontend/**` 구현은 다른 담당자 배정 전까지 `TBD`로 유지한다.
- AI prototype API 계약은 `contracts/ai-api.md`에 정리했다. 범위는 용어 설명, 회의 요약/보고서 생성, 회의별 챗봇, 프로젝트별 챗봇, 회의 종료 태스크 후보 추출이다.
- 용어 설명 prototype은 `pgvector` 같은 Domain Dictionary 항목을 로컬 응답으로 먼저 처리하고, dictionary에 없지만 transcript 근거가 있는 용어는 AI fallback으로 설명한다.
- RAG 기반 작업은 `tasks.md`의 M011/T078-T088로 세분화했다. T078-T088은 완료했고, 실제 STT 저장 API/DB schema/pgvector migration은 후속 담당자 작업으로 남긴다.
- Auth/Login 기반 작업은 `tasks.md`의 M012/T089-T096으로 세분화했다. T089-T093, T095-T096은 완료했고, LiveKit token을 회의 접근 권한과 연결하는 T094는 T040 이후로 남긴다.
- 요구사항 기준선 반영 작업은 `tasks.md`의 M013/T097-T106으로 세분화했다. T097-T101은 완료했고, backend/frontend/ai/data 영향도 점검은 T102-T105로 남긴다.
- T102-T105는 도메인 구현과 실제 코드 영향도 점검이므로 각 영역 팀원이 담당한다. Codex는 문서 기준선, 상세 요구사항 스냅샷, 용어/enum 정합성까지만 정리했다.
- API/ERD 기준선 작업은 `tasks.md`의 M014/T107-T110으로 세분화했다. T107-T109는 완료했고, 각 기능 owner의 상세 리뷰와 충돌 점검은 T110으로 남긴다.

## Contract and ERD Change Log

- 2026-07-09: 단일 `contracts/api.md` 기준선을 기능군별 명세로 분리했다. 신규 구현 기준은 `contracts/README.md`의 라우팅을 따른다.
- 2026-07-09: `erd.md`를 추가해 Auth, Space, Meeting, STT, Report, Kanban, AI/RAG, Audit 관계 초안을 Mermaid ERD로 작성했다.
- 2026-07-09: `AGENTS.md`에 API/ERD/Data Model 변경 시 문서 영향 확인과 `implement.md` 로그 기록 규칙을 추가했다.
- 2026-07-09: `.specify/templates/api-contract-template.md`와 `contracts/README.md`에 endpoint 표준 섹션 규칙을 추가하고, 분리 API 문서에 Status/Auth/Data Scope/Validation/Errors/Audit/Requirement Trace/Notes 구조를 적용했다.
- 2026-07-09: `contracts/api.md`를 legacy snapshot으로 명확히 하고, `plan.md`, `tasks.md`, `analyze.md`의 API 문서 참조를 분리 contract 파일 기준으로 갱신했다. 검증은 문서 포맷 중심으로 수행한다.
- 2026-07-09: API 문서의 `Requirement Trace`를 실제 `requirements/functional-requirements.md` ID와 대조해 수정하고, 누락된 `knowledge-api.md`를 추가했다. `erd.md`에는 ProjectKnowledge/DomainTerm 상태 필드와 주요 unique/index/nullable 제약을 보강했다.
- 2026-07-09: Invitation은 `SPACE_INVITATION`/`MEETING_INVITATION`으로 분리, MeetingReport는 회의당 current confirmed 1개, ProjectKnowledge embedding은 비동기 재생성으로 결정했다. 관련 결정은 `clarify.md` D-010~D-012와 API/ERD/data-model에 반영했다.
- 2026-07-09: Q-005 보고서 파일 포맷은 Markdown 우선으로 결정했다. PDF/DOCX export는 후속 옵션으로 둔다.
- 2026-07-09: 자체 회원가입 비밀번호 정책을 `POL-PW-01` 수준으로 올렸다. Backend signup은 최소 8자와 영대문자/영소문자/숫자/특수문자 중 3종 이상 포함을 서버에서 검증한다.
- 2026-07-09: Backend auth/권한 후속 구현 순서는 `T039/T040` Space/Meeting 접근 검증 service, `T094` LiveKit token 권한 연동, Auth store DB 영속화 순서로 확정했다.
- 2026-07-09: GitHub Actions CI 기준선을 추가했다. PR/push에서 Backend test, Frontend build, AI compile/unit test를 분리 job으로 실행한다.
- 2026-07-09: SpaceMember 제거 시 같은 Space의 member MeetingParticipant를 `REVOKED`로 전환하는 정책을 확정했다. `MeetingParticipant.accessStatus` canonical 값은 `ACTIVE`, `REVOKED`로 status-values, data-model, ERD, contracts에 반영했다.
- 2026-07-09: HOST 일시 퇴장, 회의 종료, 마지막 HOST 회수/강등/삭제 금지 정책을 확정했다. `ADMIN`은 서비스 전체 운영자가 아니라 SpaceRole의 프로젝트 관리자임을 용어집과 결정 로그에 명시했다.
- 2026-07-09: 회의 삭제 권한은 기본 `OWNER`/`HOST` 전용으로 확정하고, `ADMIN` 삭제는 명시적 예외 정책이 있을 때만 허용하도록 정책/권한/API 계약에 반영했다.
- 2026-07-09: `AuthIdentity.provider` 표기를 `local`, `google`로 통일했다. ERD의 로컬 인증 provider 제약도 같은 기준으로 맞췄다.
- 2026-07-09: T039/T040/T094 구현 전에 사용할 `test-matrix.md`를 추가했다. 요구사항의 성공/실패 기준을 Space access, Meeting access, HOST 보호, SpaceMember 제거, LiveKit token 발급 단위 테스트 케이스로 분해했다.
- 2026-07-09: T102 Backend 영향도 점검을 수행했다. 현재 Auth token 발급은 요구사항 기준과 정합하지만, legacy `/api/livekit/token`은 아직 인증 사용자와 회의 권한을 확인하지 않고 request body의 `identity`/`roomName`을 신뢰한다. 이 gap은 T094에서 target `/api/v1/meetings/{meetingId}/livekit-token`로 전환하며 닫는다.
- 2026-07-09: T039/T040 선행 slice로 `backend/src/main/java/com/meetingmind/demo/authz/**` 권한 policy 계층을 추가했다. `SpaceAccessPolicy`는 active `SpaceMember`와 `OWNER`/`ADMIN` 멤버 관리 권한을 default-deny로 검증하고, `MeetingAccessPolicy`는 `ACTIVE` participant, `OWNER`/`ADMIN` override, `OWNER`/`HOST` 삭제, 마지막 active `HOST` 보호, SpaceMember 제거 시 member participant 회수, LiveKit 접근 상태 차단을 검증한다. 전체 T039/T040 task status는 기존 dependency인 T036/T037 도메인 모델/DTO 통합 전까지 open으로 유지한다.
- 2026-07-09: 마지막 active `HOST` 보호 실패 code `LAST_ACTIVE_HOST_REQUIRED`를 공통 오류 계약과 Meeting participant 변경 계약에 추가했다.
- 2026-07-09: T035 Backend 구조 조사를 수행했다. 현재 backend는 JPA/DB 없이 Auth의 `InMemoryAuthStore`, service, controller, 단위 테스트 패턴을 사용하므로 Space/Meeting 도메인도 같은 in-memory repository/service 경계로 시작한다.
- 2026-07-09: T036/T037로 `backend/src/main/java/com/meetingmind/demo/domain/**` 최소 도메인 record와 `InMemoryWorkspaceStore`, `WorkspaceDomainService`를 추가했다. Space 생성은 생성자를 `OWNER` SpaceMember로 등록하고, 회의 생성은 `OWNER`/`ADMIN`만 허용하며 생성자를 `HOST` MeetingParticipant로 등록한다. 추가 참여자는 SpaceMember인 경우 `VIEWER` participant로 등록한다.
- 2026-07-09: T039/T040 policy를 domain service context adapter와 연결했다. `WorkspaceDomainService`가 Space/Meeting 저장 데이터에서 `SpaceAccessContext`, `MeetingAccessContext`를 구성해 기존 authz policy에 전달할 수 있다.
- 2026-07-09: T094 target LiveKit token 발급 경로를 추가했다. `/api/v1/meetings/{meetingId}/livekit-token`은 `AuthService.currentUser`로 인증 사용자를 확인하고, `MeetingAccessPolicy.requireLiveKitAccess` 통과 후 request body가 아니라 `meetingId`, 인증 사용자 id/displayName으로 LiveKit token을 발급한다. legacy `/api/livekit/token`은 prototype endpoint로 유지한다.
- 2026-07-09: `AuthExceptionHandler`를 전역 REST advice로 확장해 Auth/Authz 예외를 공통 `code`, `message`, `fieldErrors`, `traceId` 응답으로 반환하도록 했다. LiveKit 설정 누락은 target 경로에서 `LIVEKIT_NOT_CONFIGURED`로 변환한다.
- 2026-07-09: T041 target API 전환 지점으로 `/api/v1/spaces`, `/api/v1/spaces/{spaceId}/meetings`를 추가했다. 기존 `/api/workspace` 통합 mock 응답은 그대로 유지하고, target endpoint는 `AuthService.currentUser`와 `WorkspaceDomainService.ensureUser`를 거쳐 in-memory Space/Meeting read/write model을 사용한다.
- 2026-07-09: T042 공통 오류 응답 경계를 보강했다. `AuthExceptionHandler`는 전역 advice로 `AuthException`, `AuthorizationException`, validation error를 처리하고, legacy LiveKit 설정 누락도 `LIVEKIT_NOT_CONFIGURED` code로 변환한다.
- 2026-07-09: T038로 `TranscriptSegment`, `MeetingReport`, `ProjectKnowledge`, `EmbeddingChunk`와 관련 enum을 추가했다. Transcript는 `startMs/endMs`, speaker/source/sequence를 보존하고, MeetingReport decision/action item은 `sourceIds`를 보존하며, ProjectKnowledge는 `sourceMeetingId`, `embeddingStatus`, `embeddingJobId`를 갖는다. EmbeddingChunk는 meeting/project scope, source metadata, transcript source segment 목록, speakerNames, embeddingText, metadata, vector placeholder를 가진다.
- 2026-07-09: T044 Frontend discovery를 수행했다. `frontend/src/App.tsx`가 보호 route, `/api/workspace` 통합 fetch, mock fallback, 프로젝트/회의/멤버 임시 생성 상태를 함께 관리한다. `frontend/src/auth/session.ts`는 인증 API와 Authorization header helper만 갖고, Space/Meeting API client는 아직 분리되어 있지 않다. `frontend/src/types.ts`의 `WorkspaceData`는 legacy `/api/workspace` 응답 shape에 묶여 있어 T045에서 target `Space`, `Meeting`, `Report`, `AI` 타입과 분리해야 한다.
- 2026-07-09: T045로 `frontend/src/types.ts`에 target API용 `SpaceSummary`, `SpaceDetail`, `MeetingSummary`, `MeetingDetail`, `TranscriptResponse`, `ReportSummary`, `TaskCard`, `AiSource`, `ProjectAiChatRequest`, `AiChatResponse` 타입을 추가했다. 기존 `WorkspaceData`는 `/api/workspace` legacy mock fallback용으로 유지한다.
- 2026-07-09: T046으로 legacy `WorkspaceSpace`에 stable `id`를 추가하고, `/spaces` 카드, `WorkspaceSidebar`, `ProjectOverviewPage`, `TeamMembersPage`, `LiveMeetingPage`의 프로젝트 선택 route를 `spaceId` query 우선으로 정리했다. 기존 `project` name query는 fallback과 표시용으로 유지한다.
- 2026-07-09: T047로 `frontend/src/api/workspace.ts`를 추가했다. `fetchLegacyWorkspaceSnapshot`은 기존 `/api/workspace` fallback 경계를 담당하고, `fetchSpaces`, `createSpace`, `fetchSpaceDetail`, `fetchMeetings`, `createMeeting`은 target `/api/v1` Space/Meeting API 전환 지점으로 분리했다. `App.tsx`의 inline `/api/workspace` fetch는 legacy client 호출로 교체했다.
- 2026-07-10: T121로 FR-DASH-01~07, FR-CAL-01~05 상세 요구를 `requirements/functional-requirements-detail.md`에서 확인하고 M017 Dashboard/Calendar Frontend milestone을 추가했다. 현재 backend target API는 Space 생성/목록/회의 생성 일부만 구현되어 있고 Space 수정/삭제, dashboard summary, calendar events는 contract 단계이므로 1차 구현은 frontend mock fallback/local state로 UX를 완성하고 target client/type 경계를 분리한다.
- 2026-07-10: T122로 frontend target type/API client 경계를 보강했다. `frontend/src/types.ts`에 `UpdateSpaceRequest`, `UpdateSpaceResponse`, `DeleteSpaceResponse`, `CalendarEvent`, `CalendarEventsResponse`, `DashboardRecentActivity`, `DashboardSummaryResponse`를 추가하고, `frontend/src/api/workspace.ts`에 `updateSpace`, `deleteSpace`, `fetchDashboardSummary`, `fetchCalendarEvents`를 추가했다. 기존 `WorkspaceData`와 legacy `/api/workspace` mock fallback은 유지한다.
- 2026-07-10: T131로 FR-MREG-01~07, FR-ACL-01~07, FR-KAN-01~08, FR-PBOT-01~05, FR-PERM-01~05, FR-OWN-01~03 상세 요구와 관련 contract를 확인하고 M018 Project Workspace Frontend milestone을 추가했다. 핵심 구현 원칙은 `SpaceMember`와 `MeetingParticipant` ACL을 화면 상태에서도 분리하고, Project AI source는 공식 Project Knowledge와 권한 필터를 통과한 meeting source로 구분하는 것이다.
- 2026-07-10: T145로 FR-RPT-01~07, FR-MBOT-01~04, FR-TASK-01~04 상세 요구와 관련 AI/report/task contract를 확인하고 M019 Meeting Workspace Frontend milestone을 추가했다. 핵심 구현 원칙은 Meeting AI, report generation, task extraction payload를 단일 `meetingId` source로 제한하고, AI 생성 report/task는 확정 전 `CANDIDATE`로만 다루는 것이다.
- 2026-07-10: T123-T130로 `/spaces`를 프로젝트 대시보드/캘린더 홈으로 확장했다. 프로젝트 생성/수정/삭제, 회의 일정 local 생성, 월/주/일 캘린더 표시, 일정→회의/보고서 라우팅, 오늘 회의/최근 활동/Action Item 요약, mock fallback 데이터 소스 표시를 추가했다. Space 수정/삭제, dashboard summary, calendar events backend는 아직 target API gap이며 실제 저장/권한 판정처럼 표현하지 않는다.
- 2026-07-10: T132로 M018 target frontend type/API client 경계를 추가했다. Meeting update/delete, MeetingParticipant CRUD, MeetingInvitation, TaskCard CRUD, SpaceMember/Invitation/OwnerTransfer client 함수와 type을 `WorkspaceData` legacy mock shape와 분리했다.
- 2026-07-10: M018 일부 구현으로 `App.tsx`에 `MeetingParticipant`와 프로젝트 TaskCard local state를 추가하고, `/project-overview`에 회의 상태 변경/삭제, 회의별 ACL role 부여/회수, default-deny 안내, 프로젝트 칸반 카드 생성/상태 이동/삭제 UI를 추가했다. 다만 T133-T139 완료 기준 중 Project AI 공식 지식/source 분리, TeamMembersPage role 변경/멤버 제거/owner transfer, 마지막 active HOST 보호, 감사 로그 표시, 칸반 필터/검색은 아직 남아 있어 해당 task는 open으로 유지한다.
- 2026-07-10: T146으로 M019 source-aware AI/report/task candidate type과 client 경계를 추가했다. `chatMeetingAi`, `generateReportCandidate`, `extractTaskCandidates`, report list/confirm/update/download, task candidate fetch/confirm 함수가 target contract 이름으로 분리되어 있다.
- 2026-07-10: T148-T150으로 `MeetingAiPage`를 `/api/meeting-ai/chat` request shape로 전환하고 `sources[]`, `unsupported` 표시를 추가했다. `ReportAgentPage`에는 회의록 candidate 생성/확정 local flow, task candidate 추출/등록 전 편집/등록 승인 local flow, backend gap 안내를 추가했다. T151-T153 완료 기준 중 current confirmed version 전환, Markdown export 버튼, M018 칸반 state와 `sourceCandidateId` 연계는 아직 후속 작업이다.

## Current Frontend Workstream Notes

- Route 진입점은 `frontend/src/App.tsx`다. 공개 route는 `/`, 보호 route는 `/spaces`, `/project-overview`, `/team-members`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`다.
- 현재 데이터 로딩은 로그인 후 `GET /api/workspace` 한 번으로 동작한다. 실패 시 `frontend/src/data/mockData.ts`의 local mock을 유지한다.
- `frontend/src/App.tsx` 안에 `handleCreateProject`, `handleCreateMeeting`, join request approve/reject 등 임시 in-memory 상태 변경 로직이 있다.
- `frontend/src/pages/WorkspaceHomePage.tsx`는 프로젝트 목록 화면처럼 보이지만 현재 UI 문구와 계산은 회의 카탈로그 중심이다. FR-DASH-01/02/06/07과 연결될 첫 수정 대상이다.
- `frontend/src/pages/ProjectOverviewPage.tsx`는 `project` query param으로 선택된 Space를 찾고, 회의 목록/최근 문서/Project AI prompt/회의 생성 모달을 표시한다. FR-DASH-03, FR-CAL-02/03, FR-MREG-01/05, FR-PBOT-01의 화면 진입점이다.
- `frontend/src/components/WorkspaceSidebar.tsx`는 프로젝트 생성 모달과 project/team navigation을 가진다. 프로젝트 생성 API 연결 시 공통 생성 진입점으로 사용한다.
- `frontend/src/pages/TeamMembersPage.tsx`는 멤버/초대/요청 승인 UI를 갖고 있어 FR-PERM-01~05, FR-OWN-01~03 후속 연결 대상이다.
- `frontend/src/pages/MeetingAiPage.tsx`, `ProjectOverviewPage.tsx`, `ReportAgentPage.tsx`는 각각 Meeting AI, Project AI, report 편집/확정 흐름의 화면 진입점이다.
- 다음 작업 T045에서는 `WorkspaceData` 중심 타입을 유지하되, target API용 `SpaceSummary`, `SpaceDetail`, `MeetingSummary`, `ReportSummary`, `ProjectAiSource` 타입을 별도로 추가해 기존 mock fallback과 실제 API shape가 섞이지 않게 한다.
- 다음 작업 T047에서는 `/api/workspace` legacy fetch와 target `/api/v1/spaces`, `/api/v1/spaces/{spaceId}/meetings` 호출 경계를 분리한다. 기존 mock fallback은 유지한다.
- 2026-07-09: T058 Data discovery를 수행했다. 현재 backend에는 JDBC/JPA, PostgreSQL driver, Flyway, Liquibase 의존성과 datasource 설정이 없다. Data migration 도구는 Flyway SQL migration으로 결정하고, 파일 위치는 `backend/src/main/resources/db/migration`으로 기록했다. T058 범위에서는 의존성, datasource 설정, schema 파일을 추가하지 않고 T059 이후 schema 작업에서 적용한다.
- 2026-07-09: T059로 Flyway V1 migration을 추가했다. 기본 profile에서는 DB 없이 기존 prototype이 실행되도록 `spring.flyway.enabled=false`로 두고, `db` profile에서 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 Flyway를 실행한다. `users`, `spaces`, `space_members`는 text id 기반 PK, FK, enum check, active member unique index, active OWNER partial unique index를 가진다.
- 2026-07-09: T060으로 Flyway V2 migration을 추가했다. `meetings`는 Space FK, `SCHEDULED`/`IN_PROGRESS`/`ENDED`/`CANCELED` status check, `space_id, scheduled_at` index를 가진다. `meeting_participants`는 User/Meeting FK, `HOST`/`EDITOR`/`VIEWER`, `member`/`guest`, `ACTIVE`/`REVOKED` check와 active participant unique index를 가진다. `meeting_speakers`는 meeting별 speaker label unique index와 nullable displayName 제약을 가진다.
- 2026-07-09: T061로 Flyway V3 migration을 추가했다. `transcript_segments`는 `start_ms/end_ms` 시간 범위, meeting별 sequence unique, meeting별 start time index를 가진다. `meeting_reports`는 version unique, current confirmed partial unique index, `CANDIDATE`/`DRAFT`/`CONFIRMED` status check를 가진다. 결정사항과 액션아이템은 `report_decisions`, `report_action_items`로 분리하고, 출처 추적은 `source_ids jsonb` 배열 제약으로 저장한다.
- 2026-07-09: T062로 Flyway V4 migration을 추가했다. `project_knowledge`는 Space FK, `report`/`decision`/`manual`/`external` type, `PUBLISHED`/`ARCHIVED` status, `PENDING`/`PROCESSING`/`COMPLETED`/`FAILED` embedding status, `(space_id, type, updated_at)` index를 가진다. `embedding_chunks`는 `space_id`, `meeting_id`, `scope`, `source_type`, `source_id`, `embedding_text`, `metadata`, nullable pgvector `embedding`을 저장하고, meeting scope는 `meeting_id`가 required다. `chunk_source_segments`는 chunk와 transcript segment 관계를 unique로 추적한다.

## Dashboard and Calendar Frontend Notes

- 관련 상세 요구는 FR-DASH-01~07, FR-CAL-01~05다. 프로젝트는 코드/DB에서 `Space`, 화면에서는 "프로젝트"로 표시한다.
- FR-DASH-01/02/06/07은 `/spaces`가 담당한다. 현재 `WorkspaceHomePage.tsx`는 회의 카탈로그 톤이 강하므로 프로젝트 대시보드, 오늘 회의, 최근 활동, Action Item 요약 중심으로 재정리한다.
- FR-DASH-03은 기존 `/project-overview`가 담당한다. 회의 목록, 최근 문서, Action Item, Project AI 진입점은 유지하되 선택 Space 기준 빈 상태와 route query 보존을 검증한다.
- FR-DASH-04/05는 target contract가 `PATCH /api/v1/spaces/{spaceId}`, `DELETE /api/v1/spaces/{spaceId}`로 문서화되어 있지만 backend 구현은 아직 없다. 이번 frontend slice는 owner/admin 전제 UI, 확인 절차, local state 반영까지만 구현하고 API client는 target 경계만 둔다.
- FR-CAL-01/02/03은 `GET /api/v1/calendar/events` target contract가 문서화되어 있지만 backend 구현은 아직 없다. 월/주/일 뷰와 일정 클릭 라우팅은 mock/local meeting state에서 구성한다.
- FR-CAL-04 일정 생성은 별도 CalendarEvent 생성 API가 아니라 `POST /api/v1/spaces/{spaceId}/meetings`를 사용한다. Frontend local flow도 회의 생성과 캘린더 표시가 같은 데이터를 쓰도록 연결한다.
- FR-CAL-05 회의 알림은 Notification backend가 없으므로 이번 slice에서는 다가오는 회의 표시와 알림 준비 상태만 표현한다. 실제 발송은 후속 notification/backend task로 분리한다.

## Project Workspace Frontend Notes

- 관련 상세 요구는 FR-MREG-01~07, FR-ACL-01~07, FR-KAN-01~08, FR-PBOT-01~05, FR-PERM-01~05, FR-OWN-01~03이다.
- `ProjectOverviewPage.tsx`는 회의 목록과 Project AI 진입점이 이미 있으나 회의 ACL, 회의 삭제/상태 변경, 칸반, 권한별 disabled state는 아직 없다. M018에서는 이 파일이 회의 관리, ACL, 칸반, Project AI source 표시의 중심이 된다.
- `TeamMembersPage.tsx`는 멤버/초대/요청 승인 UI가 있으나 Space invitation, Meeting invitation, role change, owner transfer가 요구사항 기준으로 분리되어 있지 않다. M018에서는 SpaceMember role과 owner transfer 확인 절차를 정리한다.
- Meeting ACL의 canonical role은 `VIEWER`, `EDITOR`, `HOST`이고 access status는 `ACTIVE`, `REVOKED`다. 마지막 active `HOST`의 강등/회수/제거는 금지되며, 이 상태는 UI에서도 막아야 한다.
- Space owner/admin은 회의 ACL 없이 접근할 수 있지만, 회의 삭제는 기본적으로 `OWNER` 또는 해당 회의 `HOST` 전용이다. `ADMIN` 삭제는 명시적 예외 정책 전까지 기본 허용으로 표현하지 않는다.
- Kanban 상태는 `TODO`, `IN_PROGRESS`, `DONE`만 사용한다. 새 drag-and-drop dependency는 추가하지 않고 기존 React/DOM 이벤트나 명시 이동 버튼 중 작은 구현을 선택한다.
- Project AI는 공식 Project Knowledge와 회의 기록 출처를 구분해야 한다. backend 권한 선필터가 들어오기 전까지 frontend mock source도 선택 Space와 접근 가능한 회의 범위로 제한한다.
- Backend gap: meeting participant CRUD, meeting invitation accept/decline, meeting update/delete, kanban CRUD, Space invitation/member role/owner transfer, AuditLog 저장, Project AI backend 권한 필터는 아직 target contract 또는 prototype gap 상태다.

## Meeting Workspace Frontend Notes

- 관련 상세 요구는 FR-RPT-01~07, FR-MBOT-01~04, FR-TASK-01~04다.
- `MeetingAiPage.tsx`는 현재 legacy `/api/meeting-ai/ask`를 직접 호출하고 `answer/model`만 처리한다. M019에서는 source-aware `/api/meeting-ai/chat` shape와 `sources[]`, `unsupported` 상태를 UI에 반영한다.
- `ReportAgentPage.tsx`는 로컬 AI 편집 경험과 apply/revert 흐름이 있지만, report candidate, draft, confirmed/current version, backend confirm/update/download 경계가 아직 분리되어 있지 않다.
- AI 서버에는 `/api/meeting-ai/chat`, `/api/meeting-ai/generate-report`, `/api/meeting-ai/extract-tasks` prototype이 있고, 근거 없음 `unsupported=true`, sourceId 필터링, candidate 정규화 테스트가 존재한다.
- Backend에는 `MeetingReport` 도메인 record와 artifact store 일부가 있지만 report list/confirm/update/download controller와 TaskCandidate 저장/confirm controller는 아직 target contract 단계다.
- Meeting report status는 `CANDIDATE`, `DRAFT`, `CONFIRMED`를 사용한다. 확정 전 candidate는 공식 회의록으로 표시하지 않고, 회의당 current confirmed report는 하나만 유지한다.
- TaskCandidate status는 `CANDIDATE`이고, 확정 전에는 칸반 `TaskCard`가 아니다. 확정 후 `TaskCard.sourceCandidateId`로 원천 후보를 추적한다.
- Report export는 Markdown 우선으로 다루고, PDF/DOCX는 후속 backend/export 작업으로 둔다.
- 모든 Meeting AI/report/task payload는 현재 `meetingId` 하나에 속한 transcript, decision, action, report source만 포함해야 한다. Project 전체 자료나 다른 회의 source는 payload에 넣지 않는다.

## Current Auth Workstream Notes

- 현재 Frontend에는 `frontend/src/components/GoogleLoginModal.tsx`가 있으며, 자체 로그인/회원가입과 Google Backend exchange를 처리한다.
- `frontend/src/App.tsx`는 auth session 상태와 protected route를 관리하고, `/api/workspace` 호출에 `Authorization` header를 전달한다.
- 기존 mock fallback은 유지되지만 로그인 상태는 Backend auth 응답 기준으로 관리한다.
- 현재 Backend Auth 구현은 새 외부 dependency 없이 Java 표준 crypto/Jackson/Spring MVC 기반으로 작성했다. Spring Security는 아직 도입하지 않았다.
- 현재 Backend Auth package는 `backend/src/main/java/com/meetingmind/demo/auth/**`다.
- 확정 구현 방향대로 Google OAuth와 자체 회원가입/로그인을 모두 지원하고, Backend가 access token과 refresh token, user profile을 반환한다.
- Frontend에서 Google credential을 decode하는 코드는 표시용으로만 취급하고 인증 신뢰 경계로 사용하지 않는다.
- Auth API는 기존 prototype API와 충돌하지 않도록 `/api/v1/auth/*`로 시작한다.
- Frontend는 access token과 refresh token을 `sessionStorage`에 저장한다.
- 공개 route는 랜딩(`/`)만 두고, `/spaces`, `/project-overview`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`, `/team-members`는 로그인 필요 대상으로 둔다.
- 로그인 성공 후에는 비로그인 상태에서 요청했던 보호 route로 복귀한다.
- Backend Auth runtime 환경변수는 `MEETINGMIND_JWT_SECRET` 또는 `AUTH_JWT_SECRET`, Google 검증용 `GOOGLE_CLIENT_ID` 또는 `VITE_GOOGLE_CLIENT_ID`를 사용한다.
- 현재 Auth 저장소는 prototype용 in-memory store다. 서버 재시작 시 사용자, identity, refresh session은 사라지며, DB 영속화는 Data/Backend 후속 작업이다.
- LiveKit token을 인증 사용자와 회의 접근 권한에 연결하는 T094는 T040 회의 접근 검증 계층 구현 전까지 구현하지 않는다.

## Current AI Workstream Notes

- AI 서버 현재 진입점은 `ai/app/main.py`의 `/api/meeting-ai/ask`이며, question, transcript, decisions, actions를 받아 OpenAI Responses API를 직접 호출한다.
- 프론트 화면 연결 지점은 읽기 전용으로 확인했다. 실제 `frontend/**` 구현은 Frontend 담당자 작업으로 남긴다.
- 현재 Meeting AI 화면은 AI 서버를 직접 호출하고, Project AI 화면은 같은 `/api/meeting-ai/ask`를 재사용한다. 이 분리는 AI 서버 endpoint/contract부터 정리한다.
- Live Room의 STT 표시와 Report Agent의 로컬 편집 흐름은 현재 프론트 구현을 유지하고, AI 담당 브랜치에서는 수정하지 않는다.
- 백엔드 권한 필터, Meeting/Project context 조립, AI 응답 저장, 태스크 저장 API는 아직 구현되지 않았고 현재 AI 담당 범위에서 제외한다.
- Prototype AI 계약은 `POST /api/meeting-ai/explain-term`, `POST /api/meeting-ai/generate-report`, `POST /api/meeting-ai/chat`, `POST /api/project-ai/chat`, `POST /api/meeting-ai/extract-tasks`로 정의했다.
- `POST /api/meeting-ai/explain-term` 구현을 추가했다. glossary 일치 시 `local-glossary` 모델 라벨로 즉시 응답하고, transcript 근거가 있으면 OpenAI fallback을 호출한다.
- RAG chunk 계획은 STT 원천 데이터 `TranscriptSegment`와 임베딩 검색 단위 `RagChunk/EmbeddingChunk`를 분리한다. 짧은 발화는 여러 segment window로 묶고, `embeddingText`에는 회의명, scope, sourceType, 시간, 발화자, 내용을 포함한다.
- `ai/app/rag.py`에 `RagChunk`, `RagSource`, `RagSearchRequest`, `RagSearchResult`, `RagRetriever` 경계를 추가했다.
- `ai/app/rag.py`에 mock transcript, decision, action item, project knowledge를 `RagChunk`로 변환하는 builder를 추가했다. 짧은 STT 발화는 3-8개 범위의 window chunk로 묶고, `sourceSegmentIds`, 발화자, 시간 범위, `sourceType`, `embeddingText` metadata를 유지한다.
- 2026-07-06: `cd ai && python3 -m compileall app` 검증과 builder smoke test를 통과했다. 다음 작업은 pgvector 전환 전 in-memory retriever와 meeting/project scope 필터를 구현하는 T081이다.
- `ai/app/rag.py`에 `InMemoryRagRetriever`를 추가했다. 검색은 projectId, scope, meetingId 또는 allowedMeetingIds, sourceTypes 필터를 먼저 적용하고, prototype 단계에서는 정규화 token match 점수로 상위 chunk를 반환한다.
- 2026-07-06: retriever smoke test에서 meeting scope가 단일 meetingId만 반환하고, project scope가 `projectKnowledge`와 `allowedMeetingIds`에 포함된 meeting chunk만 검색하는 것을 확인했다. 다음 작업은 회의 중 용어 설명 endpoint를 retriever 기반으로 전환하는 T082이다.
- `POST /api/meeting-ai/explain-term`을 RAG retriever 기반 source 검색으로 전환했다. glossary exact match는 계속 우선 처리하고, transcript fallback은 `TranscriptRow -> TranscriptSegment -> RagChunk -> InMemoryRagRetriever` 경로로 source를 구성한다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 용어 설명 RAG source smoke test를 통과했다. 다음 작업은 회의별 챗봇을 meeting scope RAG로 구현하는 T083이다.
- `POST /api/meeting-ai/chat`을 추가했다. 요청으로 받은 단일 회의 transcript, decision, action item만 RAG chunk로 변환하고 `scope=meeting`/`meetingId` 필터로 검색한 source를 OpenAI에 전달한다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 meeting chat RAG source smoke test를 통과했다. 검색 결과가 없을 때는 OpenAI 호출 없이 `unsupported=true`, `model=context-only`로 응답한다. 다음 작업은 프로젝트별 챗봇을 project scope RAG로 구현하는 T084이다.
- `POST /api/project-ai/chat`을 추가했다. 요청으로 받은 `projectKnowledge`는 공식 프로젝트 지식 chunk로, `meetings[].summary`는 접근 허용된 회의 요약 chunk로 변환하고 `scope=project`로 검색한다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 project chat RAG source smoke test를 통과했다. 검색 결과가 없을 때는 OpenAI 호출 없이 `unsupported=true`, `model=context-only`로 응답한다. 다음 작업은 회의 요약/보고서 생성 prototype인 T085이다.
- `POST /api/meeting-ai/generate-report`를 추가했다. 요청으로 받은 회의 transcript, decision, action item을 RAG chunk/source 구조로 변환하고, OpenAI에는 sourceId가 포함된 회의 근거를 전달해 summary, decisions, actionItems, markdown 초안을 JSON으로 받는다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 report source/JSON parse smoke test를 통과했다. 근거가 없을 때는 OpenAI 호출 없이 `unsupported=true`, `model=context-only`로 응답한다. 다음 작업은 회의 종료 태스크 후보 추출 prototype인 T086이다.
- 2026-07-06: 실제 OpenAI 보고서 생성 호출은 `certifi` CA bundle 적용 후 API endpoint까지 도달했으나, 현재 로컬 OpenAI key가 유효하지 않아 401 invalid key로 실패했다. AI 서버는 루트 `.env`도 읽고 `OPEN_AI_KEY` alias도 `OPENAI_API_KEY`로 인식하도록 보완했으며, 유효한 key 교체 후 실제 생성 결과를 다시 확인한다.
- `POST /api/meeting-ai/extract-tasks`를 추가했다. transcript와 summary를 source-aware RAG chunk로 변환하고, OpenAI에는 참석자 목록과 sourceId가 포함된 회의 근거를 전달해 task candidate JSON을 받는다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 task extraction source/JSON parse smoke test를 통과했다. 모든 태스크 후보는 저장 전 상태인 `confirmationState=candidate`로 정규화한다. 다음 작업은 RAG scope와 컨텍스트 밖 질문 방어를 검증하는 T087이다.
- 2026-07-09: `contracts/ai-api.md`, `contracts/knowledge-api.md`, `erd.md`, `data-model.md` 기준으로 AI owner review를 수행했다. 현재 AI prototype은 Meeting AI 단일 meeting scope, Project AI 공식 지식/허용 meeting summary 분리, 근거 없음 `unsupported=true`, registered glossary LLM 미호출 원칙과 맞는다.
- 2026-07-09: T104 영향도 점검 결과, token budget 축소 정책과 AI/API observability log는 아직 구현되어 있지 않다. 이는 Backend 권한 필터, 실제 RAG 저장소, 운영 로깅이 들어오는 후속 milestone에서 처리한다.
- 2026-07-09: RAG safety unittest를 추가했다. Meeting scope가 다른 meeting/projectKnowledge chunk를 제외하고, Project scope가 허용되지 않은 meeting chunk를 제외하며, 근거 없는 Meeting AI/task extraction은 OpenAI를 호출하지 않는다.
- 2026-07-09: Report action item도 저장 전 산출물 원칙에 맞게 `confirmationState=candidate`로 정규화하도록 수정했다. LLM이 임의 sourceId를 반환해도 제공된 source 목록에 없는 값은 제거된다.
- 2026-07-10: AI prototype endpoint observability를 추가했다. `/api/meeting-ai/*`와 `/api/project-ai/chat` wrapper가 처리 시간, model, source count, unsupported 여부와 reason을 `meetingmind.ai` logger에 기록하며, 질문/본문 같은 입력 원문은 로그에 포함하지 않는다.
- 2026-07-10: `AiObservabilityTest`를 추가해 unsupported 응답의 `NO_SOURCES` reason, model/source count/durationMs 로그 필드, 입력 원문 비노출, 지원 응답의 source count를 검증했다.
- 2026-07-10: AI contract review 결과, 현재 구현은 prototype 호환을 위해 일부 fallback을 유지하고 target 문서는 Backend-to-AI strict contract를 지향해 차이가 있었다. `contracts/ai-api.md`에 Current Prototype과 Target Backend-to-AI 경계를 분리해 `meetingId`/`title` optional fallback, `selectedText` prototype source화, provider error status, audit vs observability, Meeting chat report context gap을 명시했다.
- 2026-07-10: strict code refactor는 즉시 적용하지 않았다. 현재 frontend/prototype 직접 호출 호환을 깨지 않기 위해 error shape, target request schema, report source 처리, Backend context assembly 연결은 T159-T162 후속 작업으로 분리했다.
- 2026-07-10: T163으로 Backend Meeting AI chat 1차 연동을 시작했다. 먼저 `meeting-api.md`에 `POST /api/v1/meetings/{meetingId}/ai/chat` 계약을 추가했고, Frontend가 source context를 직접 넘기지 않고 Backend가 meeting read 권한 확인 후 AI 서버 내부 endpoint로 already-filtered context를 전달하는 방향으로 고정했다.
- 2026-07-10: T163 구현으로 `MeetingAiController`, `MeetingAiService`, `MeetingAiGatewayClient`/`HttpMeetingAiGatewayClient`, AI chat DTO를 추가했다. Backend endpoint는 인증 사용자와 `MeetingAccessPolicy.requireReadAccess`를 확인한 뒤 `WorkspaceDomainService.meetingAiContext`에서 transcript/report decision/action context를 조립해 AI 서버 내부 endpoint로 전달한다. AI provider 실패는 Backend 공통 오류 `503 AI_PROVIDER_UNAVAILABLE`로 매핑한다.
- 2026-07-10: T164로 Meeting AI 화면의 직접 AI 서버 호출 제거를 시작했다. 범위는 `MeetingAiPage`와 frontend workspace API client이며, Project AI/Report candidate/Task candidate의 직접 AI 호출은 별도 후속 작업으로 둔다.
- 2026-07-10: T164 구현으로 `chatMeetingAi` frontend client가 `VITE_AI_API_BASE_URL`의 AI 서버 직접 호출 대신 Backend `POST /api/v1/meetings/{meetingId}/ai/chat`을 호출하도록 바뀌었다. `MeetingAiPage`는 `AuthSession`을 받아 Authorization header와 `{question}`만 보내며, 기존 transcript/decision/action prototype context 직접 전달은 제거했다. 현재 mock-only `/meeting-ai` 링크는 실제 Backend meetingId가 없으면 `MEETING_NOT_FOUND`가 날 수 있으므로 route에서 target meetingId를 넘기는 후속 연결이 필요하다.
- 2026-07-10: T165로 Meeting AI Backend 경유 호출에 필요한 `meetingId` route query 연결을 시작했다. 범위는 `WorkspaceHomePage`, `ProjectOverviewPage`, `MeetingAiPage`이며 mock-only 링크는 Backend 호출을 막고 target meeting id가 있는 회의 이동에서만 실제 호출되도록 한다.
- 2026-07-10: T165 구현으로 `WorkspaceHomePage`와 `ProjectOverviewPage`의 회의 이동 URL이 `meeting.id`가 있을 때 `meetingId` query를 보존한다. `ReportAgentPage`는 현재 query를 유지해 `Meeting AI` 링크를 제공하고, `MeetingAiPage`는 `meetingId` 없는 직접 진입에서 질문 전송/추천 질문 호출을 막는다.
- 2026-07-10: T159-T160으로 AI 서버에 target internal `POST /api/internal/meeting-ai/chat`을 추가했다. 기존 `/api/meeting-ai/chat` prototype endpoint는 유지하고, internal endpoint는 `projectId`, `meetingId`, `question`, `sources[].sourceId/type/meetingId/text` 기반 strict request를 받는다. validation 실패는 `400 INVALID_REQUEST`, source meeting 불일치는 `403 AI_CONTEXT_FORBIDDEN`, provider 설정/HTTP/connection 오류는 `503 AI_PROVIDER_UNAVAILABLE`로 변환한다.
- 2026-07-10: T161로 internal Meeting chat 검색 대상에 `report` source type을 포함했다. Backend가 전달한 report summary source는 `RagChunk`로 변환되어 transcript/decision/actionItem과 같은 meeting scope 검색 필터를 통과한 경우에만 LLM context로 사용된다.
- 2026-07-10: T162로 Backend `MeetingAiGatewayChatRequest`에 `sources[]`를 추가하고 `HttpMeetingAiGatewayClient` 호출 경로를 `/api/internal/meeting-ai/chat`으로 전환했다. `MeetingAiService`는 권한 확인 후 transcript source와 current/confirmed report summary, decision, action item source metadata를 조립한다. Project AI, report candidate, task candidate의 Backend 경유 전환은 아직 별도 후속 작업이다.
- 2026-07-10: API smoke 중 Java `HttpClient`의 HTTP/2 upgrade 요청을 Uvicorn이 `Unsupported upgrade request`로 거부해 Backend가 `503 AI_PROVIDER_UNAVAILABLE`을 반환하는 문제를 확인했다. `HttpMeetingAiGatewayClient`의 AI 요청을 `HTTP_1_1`로 고정했고, `signup -> space 생성 -> meeting 생성 -> POST /api/v1/meetings/{meetingId}/ai/chat` real Backend-to-AI smoke가 `200 context-only`로 통과했다.
- 2026-07-13: M021 Project AI Backend 권한 선필터 연동을 시작했다. public route는 `POST /api/v1/spaces/{spaceId}/ai/chat`, internal route는 `POST /api/internal/project-ai/chat`으로 정하고, Backend가 active SpaceMember와 meeting read 권한을 확인한 뒤 `PUBLISHED` ProjectKnowledge와 읽기 가능한 회의의 current/confirmed report summary만 전달하도록 계약을 갱신했다.
- 2026-07-13: Project AI 계약 변경은 기존 `ProjectKnowledge`, `MeetingReport`, `MeetingParticipant`, source reference 관계를 그대로 사용하므로 `erd.md`와 `data-model.md`의 구조 변경은 없다. 이번 slice는 in-memory read model을 사용하며 실제 PostgreSQL/pgvector, embedding worker, 대화 이력, persistent `AI_REQUESTED` audit는 후속 범위다.
- 2026-07-13: core `spec.md`는 Project AI Backend 권한 선필터 1차 연동을 In Scope로, 실제 PostgreSQL/pgvector 멀티 회의 RAG와 embedding worker를 Out of Scope로 구분해 이번 M021 범위와 맞췄다.
- 2026-07-13: T167-T168로 `/api/internal/project-ai/chat` strict schema와 project/meeting source validator를 구현했다. project 불일치, 허용 목록 밖 meeting summary, 잘못된 source type을 `403 AI_CONTEXT_FORBIDDEN`으로 차단하고, 공식 지식과 회의 요약을 project scope RAG에서 구분한다.
- 2026-07-13: T169-T170으로 `POST /api/v1/spaces/{spaceId}/ai/chat`을 추가했다. Backend는 active SpaceMember를 먼저 확인하고 `MeetingAccessPolicy.canReadAccess`로 읽을 수 있는 회의만 선필터한 뒤 `PUBLISHED`, `embeddingStatus=COMPLETED` ProjectKnowledge와 current/confirmed report summary를 AI에 전달한다. Project AI context는 transcript를 읽지 않는 전용 record로 제한했다.
- 2026-07-13: PR 전 권한 순서 리뷰에서 모든 회의 report를 조회한 뒤 ACL을 적용하던 중간 흐름을 발견했다. `projectAiContext`는 Space와 ProjectKnowledge, Meeting 메타데이터만 조회하고, `MeetingAccessPolicy.canReadAccess`를 통과한 meetingId에 대해서만 `projectMeetingContext`로 report를 조회하도록 바꿔 권한 검증 후 회의 산출물 조회 순서를 보장했다.
- 2026-07-13: T171로 ProjectOverviewPage의 AI 서버 직접 `/api/meeting-ai/ask` 호출과 mock transcript/decision/action payload를 제거했다. Frontend는 인증 header와 질문만 Backend로 보내고 응답 source를 `공식 지식`과 `회의 기록` 태그로 구분한다. 앱은 target `/api/v1/spaces` 목록을 별도로 조회해 선택 Space가 실제 접근 가능한 target Space일 때만 Project AI를 활성화하며 mock/legacy Space ID의 `SPACE_NOT_FOUND` 요청을 사전에 차단한다.
- 2026-07-13: M021은 1명/1 agent 순차 workstream으로 contracts -> AI -> Backend -> Frontend -> verification 순서로 통합했고 파일 충돌은 없었다. AI unittest 19개/compile, Backend test, Frontend build, `git diff --check`가 통과했다.
- 2026-07-13: Backend `18080` + AI `18000` real API smoke에서 signup -> Space 생성 -> Project AI chat이 `200`, `unsupported=true`, `model=context-only`로 통과했다. 비멤버 호출은 `403 SPACE_ACCESS_DENIED`, AI internal allowlist 밖 meeting source는 `403 AI_CONTEXT_FORBIDDEN`으로 차단됐으며 테스트 서버는 종료했다.
- 2026-07-13: `analyze.md`의 stale Project AI Deferred 판단과 observability 미구현 설명을 M021 실제 상태에 맞춰 갱신했다. 다음 AI 구현 milestone은 FR-RPT-01 P0과 report -> task 선행 관계를 기준으로 M022 AI 회의록 candidate Backend 경유 전환으로 선정했고 T174-T181을 계약, AI, Backend context/candidate, Frontend, 검증, closeout으로 분해했다.

## AI RAG Task Priority

1. T078: RAG chunk와 embeddingText 형식 정의
2. T079: AI 서버 내부 RAG 타입과 retriever 경계 추가
3. T080: mock transcript/decision/action/projectKnowledge chunk builder 구현 완료
4. T081: in-memory retriever와 meeting/project scope 필터 구현 완료
5. T082: 회의 중 용어 설명을 retriever 기반으로 전환 완료
6. T083: 회의별 챗봇 RAG scope 구현 완료
7. T084: 프로젝트별 챗봇 RAG scope 구현 완료
8. T085: 회의 요약/보고서 생성 prototype 구현 완료
9. T086: 회의 종료 태스크 후보 추출 prototype 구현 완료
10. T087-T088: RAG safety와 최종 검증 완료

## Git Status Notes

- 요구사항 기준선 반영 변경은 PR #8의 `agent/requirements-docs-baseline` 브랜치에 커밋되어 원격 push되었다.
- 개인 IDE 설정과 에이전트 산출물 디렉터리인 `.idea/`, `output/`, `tmp/`는 Git에 올리지 않고 루트 `.gitignore`에서 제외한다.
- `c15ca74 feat: add backend auth prototype` 이후 Frontend Auth 연결, Backend Auth runtime wiring fix, context smoke test, Auth 검증 문서 갱신을 후속 Auth 변경으로 정리했다.

## Verification

- Passed: `cd ai && python3 -m compileall app`
- Passed: `git diff --check`
- Passed: `cd ai && .venv/bin/python -c "from app.main import ExplainTermRequest, explain_term; ..."`로 Domain Dictionary 우선 응답 확인
- Passed: `curl -fsS http://127.0.0.1:8000/health`
- Passed: `curl -fsS -X POST http://127.0.0.1:8000/api/meeting-ai/explain-term ...`
- Passed: `cd backend && ./gradlew test`
- Passed: `cd backend && ./gradlew build`
- Historical Maven verification before Gradle conversion: `cd backend && mvn -Dtest=LiveKitTokenServiceTest test`, `cd backend && mvn test`
- Passed: `cd ai && python3 -m unittest discover -s tests`
- Passed: `cd ai && python3 -m compileall app tests`
- Passed: `cd ai && python3 -m compileall app tests` after AI RAG safety tests
- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests`, 9 tests
- Passed: `cd ai && python3 -m compileall app tests` after AI observability logging
- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests`, 11 tests, after AI observability logging
- Passed: `git diff --check` after AI contract prototype/target split docs
- Passed: `cd frontend && npm run build`
- Passed: `cd frontend && npm run build` after Frontend Auth route guard changes
- Passed: `cd backend && ./gradlew test` after Gradle conversion, total 8 backend tests
- Passed: `cd frontend && npm run build` after Auth runtime fix and docs update candidate
- Passed: `cd ai && python3 -m unittest discover -s tests`, 4 tests
- Passed: `cd ai && python3 -m compileall app tests`
- Passed: `git diff --check`
- Passed: `cd backend && ./gradlew test` after `POL-PW-01` password policy and CI baseline changes
- Passed: `cd frontend && npm run build` after CI baseline changes
- Passed: `cd ai && python3 -m compileall app tests` after CI baseline changes
- Passed: `cd ai && python3 -m unittest discover -s tests`, 4 tests, after CI baseline changes
- Passed: `git diff --check` after CI baseline changes
- Passed: `git diff --check` after authz test matrix docs
- Passed: `cd backend && ./gradlew test` after backend authz policy slice, total 31 backend tests
- Passed: `cd backend && ./gradlew test` after workspace domain model/service, total 37 backend tests
- Passed: `cd backend && ./gradlew test` after target LiveKit authorization path, total 41 backend tests
- Passed: `cd backend && ./gradlew test` after target Space/Meeting API and common error handling, total 44 backend tests
- Passed: `cd backend && ./gradlew test` after artifact/RAG domain model, total 48 backend tests
- Passed: `cd backend && ./gradlew test` after Backend Meeting AI chat integration slice, total 51 backend tests
- Passed: `cd frontend && npm run build` after Meeting AI frontend switched to Backend AI chat endpoint
- Passed: `cd frontend && npm run build` after Meeting AI route `meetingId` query preservation
- Passed: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai`, 15 tests, after Backend-to-AI target internal schema
- Passed: `cd ai && ./.venv/bin/python -m compileall app` after Backend-to-AI target internal schema
- Passed: `cd backend && ./gradlew test` after Backend-to-AI source metadata payload
- Passed: `cd frontend && npm run build` after full Meeting AI Backend-to-AI review
- Passed: `git diff --check` after full Meeting AI Backend-to-AI review
- Passed: real API smoke on Backend `18080` and AI `18000`: signup, space creation, meeting creation, Backend Meeting AI chat returned `200` with `unsupported=true`, `model=context-only`
- Passed: `cd frontend && npm run build` after T045 target frontend API types
- Passed: `git diff --check` after T044-T045 frontend workstream docs/types
- Passed: `cd frontend && npm run build` after T046 stable project route state
- Passed: `git diff --check` after T046 stable project route state
- Passed: `cd frontend && npm run build` after T047 frontend workspace API client split
- Passed: `git diff --check` after T047 frontend workspace API client split
- Passed: `cd frontend && npm run build` after T122 dashboard/calendar frontend target type and API client boundary
- Passed: `cd frontend && npm run build` after M017 dashboard/calendar UI, M018 project workspace local ACL/kanban slice, and M019 Meeting AI/report candidate UI changes
- Passed: `git diff --check` after M017-M019 frontend changes
- Passed: local frontend HTTP smoke on `http://127.0.0.1:5173/`, `/project-overview`, `/report-agent`, `/meeting-ai` with HTTP 200. Initial sandboxed dev server/curl attempts failed with localhost permission restrictions, then passed after approved local dev server/curl execution.
- Passed: `git diff --check` after T058 data discovery docs
- Passed: `cd backend && ./gradlew test` after T059 Flyway V1 migration setup
- Passed: `git diff --check` after T059 Flyway V1 migration setup
- Passed: `cd backend && ./gradlew test` after T060 Flyway V2 meeting ACL migration
- Passed: `git diff --check` after T060 Flyway V2 meeting ACL migration
- Passed: `cd backend && ./gradlew test` after T061 Flyway V3 transcript/report migration
- Passed: `git diff --check` after T061 Flyway V3 transcript/report migration
- Passed: `cd backend && ./gradlew test` after T062 Flyway V4 knowledge/embedding migration
- Passed: `git diff --check` after T062 Flyway V4 knowledge/embedding migration
- Passed: local runtime smoke with `MEETINGMIND_JWT_SECRET=dev-test-secret GOOGLE_CLIENT_ID=dev-google-client mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080` before Gradle conversion
- Passed: `curl -fsS http://127.0.0.1:18080/api/workspace`
- Passed: `curl -fsS http://127.0.0.1:18080/api/v1/auth/signup -H 'Content-Type: application/json' -d '{"email":"api-smoke-18080@meetingmind.ai","password":"password-123","displayName":"API Smoke"}'`
- Not run: Browser automation verification. `agent-browser` CLI and Playwright packages are not available in this environment; adding a new browser test library was avoided because existing frontend test framework is not present.
- Not run: `cd ai && python -m compileall app`는 이 환경에 `python` 명령이 없어 `python3`로 대체했다.
- Not run: Flyway migration against a real PostgreSQL datasource after T059-T062. Local `db` profile datasource is not configured in this environment; migration 적용 검증은 T064에서 수행한다.
- Note: 첫 `mvn -Dtest=LiveKitTokenServiceTest test` 실행은 Maven이 `~/.m2`에 Surefire provider를 쓸 권한이 없어 실패했고, 승인 후 재실행해 통과했다.
- Note: `npm ci` 후 `npm audit`이 moderate 1건, high 1건을 보고했다. `npm audit fix --force`는 breaking change 가능성이 있어 실행하지 않았다.
- Note: `8080`, `8081`, `5173`, `5174`는 기존 로컬 프로세스가 사용 중이었다. Auth runtime smoke는 충돌을 피하려고 Backend `18080`, Frontend `5176`으로 실행했다.

## Remaining Work

- `clarify.md`의 Open 질문 결정
- Backend Auth 영속화: 현재 in-memory Auth store를 DB 기반 User/AuthIdentity/AuthSession 저장소로 전환
- LiveKit token auth 연결(T094): T040 회의 접근 검증 계층 구현 후 인증 사용자와 회의 권한을 확인하도록 전환
- 요구사항 기준선 영향도 점검: T102-T105에서 backend/frontend/ai/data가 새 용어, 권한, 상태값, 성능/토큰 기준과 충돌하는지 확인
- mock API 분리
- Target API base URL 결정
- 실제 STT 파일 업로드 방식 결정
- Meeting AI 권한 필터링 경로 강화
- Project AI 실제 DB/pgvector RAG 연결과 Backend 권한 선필터 통합
- Frontend 연결: Live Room 용어 설명 UI, Meeting/Project AI source 표시, Report Agent 연결은 Frontend 담당 TBD
- Frontend/Backend 연결: AI prototype endpoint를 권한 필터 이후 Backend route와 화면에 연결
- M018 Frontend 후속: TeamMembersPage의 Space role 변경/멤버 제거/owner transfer 확인 절차, 마지막 active HOST 보호 disabled state, ACL 감사 로그 표시, 칸반 검색/필터와 `sourceCandidateId` 표시
- M019 Frontend 후속: Report current confirmed/version UI, Markdown export 버튼, task candidate confirm 후 M018 칸반 state와 `TaskCard.sourceCandidateId` 연계, Meeting AI source 시간/발화자/결정 ID 세분 표시
- Backend/API gap: Space 수정/삭제, dashboard/calendar events, MeetingParticipant/Invitation, meeting update/delete, Kanban CRUD, report confirm/update/download, TaskCandidate 저장/confirm, Project/Meeting AI backend 권한 필터와 audit log runtime 구현

## M023 Session Handoff Shared/Local Split

### Decision

- `.specify/memory/session-handoff.md`는 병합 후에도 유효한 팀 공통 기준, 통합 경계, 다음 shared milestone만 유지한다.
- 개인 이름, 브랜치, 로컬 커밋, 변경 파일, 개인 TODO는 `.specify/memory/session-handoff.local.md`에 기록한다.
- local 파일은 `.gitignore`로 제외하고, 팀이 같은 구조를 사용할 수 있도록 `.specify/memory/session-handoff.example.md`만 추적한다.
- 상세 구현 로그는 `implement.md`, task 상태는 `tasks.md`, PR 변경 설명은 PR 본문을 기준으로 하여 공용 handoff의 중복 누적을 막는다.

### Changes

- `AGENTS.md`에 공용/local handoff의 읽기 시점, 허용 내용, 커밋 금지 규칙을 추가했다.
- 누적된 과거 브랜치와 세션 로그를 공용 handoff에서 제거하고 현재 통합 기준과 M022만 남겼다.
- owner, branch, base commit, progress, verification, blocker를 기록하는 개인 템플릿을 추가했다.
- 개인 local handoff를 생성했으며 이 파일은 Git 상태에 노출되지 않는다.
- API 계약, ERD, data model, 애플리케이션 코드에는 영향이 없다.

### Verification

- Passed: `git check-ignore -v .specify/memory/session-handoff.local.md`
- Passed: 공용 handoff에서 owner, branch, base commit, 과거 session heading, 커밋 전/미추적 상태 패턴이 검색되지 않음
- Passed: ignored local handoff가 `git status --short --untracked-files=all`에 나타나지 않음
- Passed: `git diff --check`
- Not run: Frontend/Backend/AI 검증. 이번 변경은 docs/process와 ignore 규칙에만 한정된다.

## M022 AI Report Candidate Backend Route

### Contract and Model

- public `POST /api/v1/meetings/{meetingId}/reports/generate`와 internal `POST /api/internal/meeting-ai/generate-report`를 분리했다.
- 생성 권한은 Space `OWNER`/`ADMIN` 또는 Meeting `HOST`/`EDITOR`로 제한한다.
- `MeetingReport.CANDIDATE`에 `markdown`, `createdBy`, `sourceIds`를 추가하고 V5 migration에 candidate metadata를 반영했다.
- candidate는 임시 저장하지만 공식 report와 Project AI source에서는 제외하고, `unsupported=true` 결과는 저장하지 않는다.

### Implementation

- AI internal endpoint는 `transcript`, `decision`, `actionItem`만 허용하며 다른 meeting/source type을 `AI_CONTEXT_FORBIDDEN`으로 차단한다.
- Backend는 권한 확인 후에만 해당 meeting context를 조회하고, AI 응답 source도 원래 선필터된 request source에서 다시 구성한다.
- supported 결과는 증가한 version과 `current=false`를 가진 candidate로 저장한다. source가 없거나 응답 근거가 선필터 목록과 교집합이 없으면 저장하지 않는다.
- Frontend Report Agent는 AI 서버 직접 호출과 로컬 candidate 생성을 제거하고 인증된 Backend API를 호출한다.
- 아직 구현되지 않은 confirm은 로컬 성공 상태로 바꾸지 않고 비활성 상태로 표시한다.

### Verification

- Passed: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai`, 24 tests
- Passed: `cd ai && ./.venv/bin/python -m compileall app`
- Passed: `cd backend && ./gradlew test`
- Passed: `cd frontend && npm run build` (기존 500 kB 초과 bundle warning 유지)
- Passed: `git diff --check`
- Passed: Backend service test에서 supported candidate 저장, source allowlist 정규화, VIEWER 사전 차단, unsupported 미저장을 검증했다.
- Passed: Backend `18080` 실제 public API smoke에서 owner의 source 없는 회의는 `200`, `candidate=null`, `unsupported=true`, `model=context-only`를 반환했다.
- Passed: 같은 meeting에 대한 비권한 사용자는 `403 MEETING_ACCESS_DENIED`를 반환했다.
- Note: 첫 smoke 스크립트는 Space 응답을 `.space.id`로 잘못 읽어 null path 404가 발생했으며, 실제 응답의 `.id`로 수정 후 통과했다.
- Not run: V5 migration 실제 PostgreSQL 적용. 로컬 DB profile datasource가 구성되지 않아 schema 적용은 후속 data verification에서 확인한다.

### Remaining Boundary

- report confirm, manual update, version history 조회, Markdown/PDF/DOCX download
- `MeetingReport` PostgreSQL repository와 candidate 만료/취소 정책
- `AI_REQUESTED`, `REPORT_CANDIDATE_CREATED` persistent audit log
- 실제 STT 입력 API 연결 후 supported public end-to-end smoke

## M024 Report Confirm and Current Version

### Contract and Decision

- `POST /api/v1/meetings/{meetingId}/reports/{reportId}/confirm`을 구현 대상으로 확정했다.
- 확정 권한은 Space `OWNER`/`ADMIN` 또는 Meeting `HOST`/`EDITOR`다.
- `CANDIDATE`/`DRAFT`만 확정하고 중복 확정은 `INVALID_REQUEST`, report 불일치는 `REPORT_NOT_FOUND`로 처리한다.
- 더 높은 version이 존재하는 오래된 candidate는 `REPORT_VERSION_CONFLICT`로 거부해 낮은 version이 current를 덮어쓰지 못하게 한다.
- candidate TTL은 기준값이 없어 `Q-008`로 분리하고 이번 slice에서 임의 만료 정책을 넣지 않았다.
- 기존 `D-017` 중복 번호를 해소하기 위해 candidate 임시 저장 결정을 `D-022`로 정리했다.

### Implementation

- `MeetingReport`에 nullable `confirmedAt`과 immutable `confirmed`/`withoutCurrent` transition을 추가했다.
- domain confirm은 기존 current confirmed report를 모두 `current=false`로 바꾸고 대상 report만 `CONFIRMED/current=true`로 저장한다.
- lifecycle service는 인증과 `requireEditAccess`를 domain 변경 전에 적용한다.
- Frontend candidate card는 Backend confirm API를 호출하고 confirmed/version/current/loading/error 상태를 반영한다.

### Verification

- Passed: `cd backend && ./gradlew test`
- Passed: `cd frontend && npm run build` (기존 500 kB 초과 bundle warning 유지)
- Passed: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai`, 24 regression tests
- Passed: `cd ai && ./.venv/bin/python -m compileall app`
- Passed: `git diff --check`
- Passed: Backend service test에서 current report 교체, version 1/2 보존, 중복·stale 확정 거부, 다른 meeting report 거부, VIEWER 사전 차단을 검증했다.
- Passed: Backend `18080` public API smoke에서 edit 권한 사용자의 없는 report는 `404 REPORT_NOT_FOUND`, 비권한 사용자는 report 조회 전에 `403 MEETING_ACCESS_DENIED`를 반환했다.
- Not run: public 성공 confirm E2E. 현재 외부 API로 transcript/candidate를 주입할 수 없어 supported 생성·확정 성공은 Backend service 통합 테스트로 검증했다.

### Remaining Boundary

- `Q-008` candidate TTL 결정과 만료/정리 작업
- report manual update, version history 조회/복원, Markdown/PDF/DOCX download
- PostgreSQL repository transaction과 partial unique index 실제 적용 검증
- `REPORT_CONFIRMED` persistent audit log

## M025 Task Candidate Backend Route and TaskCard Confirmation

### Contract and Decision

- public 생성/조회/확정 route와 internal `/api/internal/meeting-ai/extract-tasks` 계약을 추가했다.
- 후보 생성은 회의 편집 권한, 조회는 회의 읽기 권한, TaskCard 확정은 회의 편집 권한과 active SpaceMember를 모두 요구한다.
- `TaskCandidate`는 `CANDIDATE`, `CONFIRMED`, `DISMISSED` 상태를 사용하고 후보당 `TaskCard.sourceCandidateId`는 unique다.
- candidate TTL은 기준값이 없어 `Q-009`로 분리했으며 이번 slice에서는 상태와 중복만 검증한다.

### Implementation

- AI strict endpoint는 `transcript`, `report`, `decision`, `actionItem`만 허용하고 다른 project/meeting source를 `AI_CONTEXT_FORBIDDEN`으로 거부한다.
- Backend는 권한 검증 후 transcript와 current confirmed report를 canonical context로 조립하고, source 근거가 있는 supported 결과만 in-memory `TaskCandidate`로 저장한다.
- active participant 표시 이름이 active SpaceMember와 정확히 일치할 때만 `suggestedAssigneeId`를 연결한다.
- 후보 확정은 제목, 설명, 담당자, 마감일, 상태를 검증하고 candidate 상태 전이와 TaskCard 생성을 하나의 synchronized domain operation으로 처리한다.
- 생성/조회 응답은 `canConfirm` capability를 반환하고, true일 때만 active SpaceMember 담당자 선택지를 제공한다. 회의 게스트와 VIEWER에는 Space 멤버 목록을 노출하지 않는다.
- Frontend는 `canConfirm`이 false면 후보 편집/등록을 비활성화하고 유효한 `assigneeId`만 확정 요청에 사용한다.
- Frontend Report Agent는 AI 직접 호출과 로컬 등록을 제거하고 Backend 생성/조회/확정 API, 재진입 후보 복원, loading/error, 제목·설명·담당자·마감일 편집을 사용한다.
- target PostgreSQL 기준선으로 `V6__create_task_candidates_cards.sql`을 추가했다.

### Verification

- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests`, 29 tests
- Passed: `cd ai && ./.venv/bin/python -m compileall app tests`
- Passed: `cd backend && ./gradlew test`
- Passed: `cd frontend && npm run build` (기존 500 kB 초과 bundle warning 유지)
- Passed: `git diff --check`
- Passed: AI test에서 project/meeting/type allowlist, no-source LLM 미호출, provider `503`을 검증했다.
- Passed: Backend test에서 edit 권한 선차단, candidate 저장/조회, source allowlist, 담당자 매핑, VIEWER 확정 거부, guest 확정 거부, invalid assignee, 중복 확정을 검증했다.
- Passed: Backend `18080` 실제 public API smoke에서 source 없는 owner 생성 `200 unsupported/context-only`, 생성/조회 `canConfirm=true`, active SpaceMember 담당자 1명, 없는 후보 확정 `404 TASK_CANDIDATE_NOT_FOUND`, 무인증 조회 `401`을 확인했다.
- Not run: V6 migration 실제 PostgreSQL 적용. 로컬 DB profile datasource가 구성되지 않아 후속 data verification이 필요하다.
- Not run: STT가 존재하는 public supported 생성과 성공 confirm E2E. 현재 외부 API로 transcript를 주입할 수 없어 AI/Backend service test로 검증했다.

### Remaining Boundary

- `Q-009` candidate TTL 결정, 만료 검증과 정리 작업
- 후보 제외 API와 일반 Kanban 카드 CRUD/목록의 Backend 전환
- PostgreSQL repository transaction과 V6 unique/FK 제약 실제 적용 검증
- `AI_REQUESTED`, `TASK_CANDIDATE_CONFIRMED`, `TASK_CARD_CHANGED` persistent audit log

## M026 AI Provider Safety

### Scope and Decision

- `requirements/performance.md`의 prototype 기준에 따라 OpenAI 호출은 기본 30초, 보고서 생성은 60초 timeout을 사용한다.
- 설정 누락, provider HTTP/connection 오류, timeout, 잘못된 응답은 public/internal 경로 모두 `503 AI_PROVIDER_UNAVAILABLE` 고정 응답으로 정규화한다.
- provider raw body, 연결 사유, 환경변수 이름은 클라이언트 응답에 포함하지 않는다.
- AI `HTTPException`과 validation 오류는 `{code, message, fieldErrors, traceId}` 공통 body를 반환하며 현재 AI traceId는 `null`이다.
- 생성 요청 자동 재시도는 중복 과금 가능성이 있고 idempotency 보장이 없어 제외했다.
- API request/response 데이터 모델, ERD, RAG scope 변경은 없다.
- `feature-implementation-comparison.md`를 현재 권한 기반 AI 통합 prototype 상태로 갱신하고 상세 기준 문서로의 경계를 명시했다.

### Verification

- Passed: `cd ai && ./.venv/bin/python -m compileall app tests`
- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests -v`, 35 tests
- Passed: HTTP 오류 detail, connection reason, 환경변수 이름 비노출 테스트
- Passed: validation/provider 오류의 `{code, message, fieldErrors, traceId}` 공통 body 테스트
- Passed: OpenAI 기본 30초와 보고서 60초 timeout 전달 테스트
- Passed: `git diff --check`

### Remaining Boundary

- internal API 서비스 인증은 Backend credential/header 연동이 필요한 shared contract 작업이다.
- PostgreSQL/pgvector retriever와 embedding worker는 Data/Backend 영속 저장소가 선행되어야 한다.
- 실제 STT 기반 context 연동은 STT 저장·조회 계약과 권한 필터 구현이 선행되어야 한다.
- persistent `AI_REQUESTED` audit와 token budget 자동 축소는 별도 후속 milestone이다.

## M027 Local PostgreSQL and pgvector Foundation

### Design

- 공유된 Flyway V1~V6은 수정하지 않고 누락 schema와 제약을 V7~V9 forward migration으로 추가했다.
- 로컬 DB는 다른 프로젝트 PostgreSQL과 격리된 `pgvector/pgvector:pg16` 컨테이너와 host `5434`를 사용한다.
- 회의당 `MeetingTranscript` 하나가 `PENDING/PROCESSING/COMPLETED/FAILED`, `retentionUntil`, `legalHold`, `purgedAt`을 관리하고 segment는 기존 `meetingId` FK를 유지한다.
- `SourceReference`는 DB table이 아닌 API 논리 모델로 결정했다. report/task는 `sourceIds`, transcript chunk는 `chunk_source_segments`로 근거를 보존한다.
- `requirements/glossary.md`의 물리 이름도 `response.sources`, `source_ids`, `chunk_source_segments`로 맞췄으며 `requirements/INDEX.md` 라우팅 변경은 필요하지 않다.
- 비동기 재색인은 `EmbeddingJob`, `generation`, `isActive`, `replacedAt`으로 추적한다. embedding model/차원/vector index는 `Q-010` 결정 전까지 강제하지 않는다.

### Changes

- `compose.local.yml`: PostgreSQL 16 + pgvector, named volume, health check, host `5434` 기본값을 추가했다.
- V7: `auth_identities`, `auth_sessions`, `space_invitations`, `meeting_invitations`, `meeting_rooms`와 token/status/partial unique 제약을 추가했다.
- V8: retention 기본값/enum, `meeting_transcripts`, `domain_terms`, `audit_logs`와 보존 정리 index를 추가했다.
- V9: `embedding_jobs`와 chunk generation/active 교체 metadata/index를 추가했다.
- Spring Boot JDBC starter와 `application-local.yml`을 추가했다. `local` profile을 기본 profile로 지정해 Docker Compose DB와 DataSource/Flyway를 기본 활성화하고, `db` profile은 환경변수 기반 DataSource를 사용한다.
- README에 로컬 DB 실행, Flyway 적용, 중지 명령과 환경변수 경계를 추가했다.

### Verification

- Passed: `docker compose -f compose.local.yml config`
- Passed: `meetingmind-postgres-local` health check, PostgreSQL 16.14, pgvector 0.8.5
- Passed: 빈 DB에 Flyway V1~V9 최초 적용, 9개 migration 모두 success
- Passed: Flyway 재실행에서 schema version 9 up-to-date 확인
- Passed: 24개 도메인 table 생성, `DAYS_30` retention 기본값, 핵심 check constraint 6개와 partial index 3개 조회
- Passed: `cd backend && ./gradlew test`
- Passed: `git diff --check`

### Local Profile Refinement

- `SPRING_PROFILES_ACTIVE=local`은 기본적으로 `jdbc:postgresql://localhost:5434/meetingmind`, 사용자 `meetingmind`를 사용한다.
- Compose와 local profile은 동일한 `MEETINGMIND_DB_*` 이름, 사용자, 비밀번호, port 기본값을 공유하며 Spring 표준 datasource 환경변수로 Backend만 별도 override할 수 있다.
- Passed: `SPRING_PROFILES_ACTIVE=local SERVER_PORT=18080 ./gradlew bootRun`, Hikari PostgreSQL 연결, Flyway v9 up-to-date
- Passed: `GET http://127.0.0.1:18080/api/workspace` -> `200`
- Passed: profile 미지정 `./gradlew bootRun`, default `local` 자동 적용, Tomcat `8080` 기동, `GET http://127.0.0.1:8080/api/workspace` -> `200`
- `./gradlew bootRun`은 기본 `local` profile로 Docker PostgreSQL에 연결한다. Docker 없이 실행할 별도 in-memory profile은 현재 제공하지 않는다.
- Gradle `test` task는 `test` profile을 명시해 DataSource/Flyway를 비활성화한다. 따라서 단위/컨텍스트 테스트는 Docker 실행 여부와 독립적이고, 실제 schema는 별도 Compose/Flyway 검증으로 확인한다.
- Passed: `meetingmind-db` 중지 상태에서 `cd backend && ./gradlew test`
- `db` profile은 배포/CI용으로 분리하고 datasource URL/username/password 환경변수를 필수로 요구한다.

### Remaining Boundary

- T210: Auth/Workspace/STT in-memory/file 저장소의 PostgreSQL repository 및 transaction 전환
- Q-010: embedding model과 vector 차원 확정 후 `vector(n)` 및 HNSW/IVFFlat index migration
- T211: embedding worker와 권한 필터된 pgvector retriever 연결
- 보존 만료 정리 scheduler와 `legalHold` 운영 API
