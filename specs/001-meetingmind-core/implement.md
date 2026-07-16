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
| 사용자 | Codex | T063-T064, T222-T228, T231 | 로컬 PostgreSQL/pgvector, 전사 보존, 누락 schema, embedding generation migration과 검증 |

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
- 2026-07-09: `MeetingParticipant.accessStatus` canonical 값은 `ACTIVE`, `REVOKED`로 status-values, data-model, ERD, contracts에 반영했다. SpaceMember 제거 시 MeetingParticipant 처리 정책은 2026-07-10 M027 보정에서 프로젝트 권한 제거와 회의 단독 권한 유지를 분리하는 방향으로 갱신했다.
- 2026-07-09: HOST 일시 퇴장, 회의 종료, 마지막 HOST 회수/강등/삭제 금지 정책을 확정했다. `ADMIN`은 서비스 전체 운영자가 아니라 SpaceRole의 프로젝트 관리자임을 용어집과 결정 로그에 명시했다.
- 2026-07-09: 회의 삭제 권한은 기본 `OWNER`/`HOST` 전용으로 확정하고, `ADMIN` 삭제는 명시적 예외 정책이 있을 때만 허용하도록 정책/권한/API 계약에 반영했다.
- 2026-07-09: `AuthIdentity.provider` 표기를 `local`, `google`로 통일했다. ERD의 로컬 인증 provider 제약도 같은 기준으로 맞췄다.
- 2026-07-09: T039/T040/T094 구현 전에 사용할 `test-matrix.md`를 추가했다. 요구사항의 성공/실패 기준을 Space access, Meeting access, HOST 보호, SpaceMember 제거, LiveKit token 발급 단위 테스트 케이스로 분해했다.
- 2026-07-09: T102 Backend 영향도 점검을 수행했다. 현재 Auth token 발급은 요구사항 기준과 정합하지만, legacy `/api/livekit/token`은 아직 인증 사용자와 회의 권한을 확인하지 않고 request body의 `identity`/`roomName`을 신뢰한다. 이 gap은 T094에서 target `/api/v1/meetings/{meetingId}/livekit-token`로 전환하며 닫는다.
- 2026-07-09: T039/T040 선행 slice로 `backend/src/main/java/com/meetingmind/demo/authz/**` 권한 policy 계층을 추가했다. `SpaceAccessPolicy`는 active `SpaceMember`와 `OWNER`/`ADMIN` 멤버 관리 권한을 default-deny로 검증하고, `MeetingAccessPolicy`는 `ACTIVE` participant, `OWNER`/`ADMIN` override, `OWNER`/`HOST` 삭제, 마지막 active `HOST` 보호, LiveKit 접근 상태 차단을 검증한다. SpaceMember 제거에 따른 회의 접근 처리는 M027 domain mutation에서 관리한다.
- 2026-07-09: 마지막 active `HOST` 보호 실패 code `LAST_ACTIVE_HOST_REQUIRED`를 공통 오류 계약과 Meeting participant 변경 계약에 추가했다.
- 2026-07-09: T035 Backend 구조 조사를 수행했다. 현재 backend는 JPA/DB 없이 Auth의 `InMemoryAuthStore`, service, controller, 단위 테스트 패턴을 사용하므로 Space/Meeting 도메인도 같은 in-memory repository/service 경계로 시작한다.
- 2026-07-09: T036/T037로 `backend/src/main/java/com/meetingmind/demo/domain/**` 최소 도메인 record와 `InMemoryWorkspaceStore`, `WorkspaceDomainService`를 추가했다. Space 생성은 생성자를 `OWNER` SpaceMember로 등록하고, 회의 생성은 `OWNER`/`ADMIN`만 허용하며 생성자를 `HOST` MeetingParticipant로 등록한다. 추가 참여자 처리는 M027 보정 이후 SpaceMember 여부와 프로젝트 접근권 생성을 분리한다.
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
- 2026-07-10: M018 ACL/칸반 보강 준비로 `SpaceMember.spaceRole` local state를 추가하고, 회의 생성 form이 제목/일시/참여자 후보를 받아 `MeetingParticipant` local state로 연결되도록 했다. `/project-overview` 회의 삭제는 회차 입력 확인 절차를 거치며, ACL 패널은 owner/admin override, default-deny 대상, 마지막 active HOST 보호를 UI와 local update handler에서 표시/차단한다. 칸반은 검색/담당자/상태 필터, 카드 상세 편집, `sourceCandidateId` 표시를 추가했다. `cd frontend && npm run build`, `git diff --check`는 통과했다. T133-T135는 Project AI source 분리, 접근 가능 회의 목록 필터, 감사 로그/Backend gap 정리가 남아 open으로 유지한다.
- 2026-07-10: T137-T144로 M018 Project Workspace Frontend를 frontend local state 기준 완료 처리했다. Project AI 패널은 `projectKnowledge[]`와 접근 가능한 `meetings[]` 후보를 분리하고, 공식 Project Knowledge / Meeting record source count와 unsupported/확인 불가 copy를 표시한다. 접근 회수되어 ACTIVE participant가 없는 회의는 회의 목록/Project AI meeting source 후보에서 제외한다. TeamMembersPage는 Space invitation과 Meeting invitation copy를 분리하고, `OWNER`/`ADMIN`/`MEMBER` 표시/변경, owner 제거 제한, 멤버 제거 시 해당 Space의 MeetingParticipant `REVOKED` 전환, 마지막 active HOST 제거 방지, owner transfer 확인 문구(`TRANSFER OWNER`)와 기존 owner 강등 role 선택을 local flow로 구현했다. Negative permission smoke 결과: default-deny 안내/empty source copy 표시, 회수 즉시 Project AI meeting source 제외, owner/admin override 표시, 마지막 active HOST role/access/member removal 차단, owner transfer 확인 누락 시 disabled 상태를 확인했다. Route smoke: 승인된 local dev server `http://127.0.0.1:5173/`에서 `/spaces`, `/project-overview`, `/team-members`가 HTTP 200을 반환했다. Verification: `cd frontend && npm run build`, `git diff --check` 통과. Backend/API gap은 실제 MeetingParticipant/Invitation persistence, SpaceMember role/remove API, owner transfer transaction, Kanban persistence, Project AI backend 권한 선필터/context 조립, AuditLog 저장이며 다음 권한 매트릭스 backend 구현에서 닫는다.
- 2026-07-10: T146으로 M019 source-aware AI/report/task candidate type과 client 경계를 추가했다. `chatMeetingAi`, `generateReportCandidate`, `extractTaskCandidates`, report list/confirm/update/download, task candidate fetch/confirm 함수가 target contract 이름으로 분리되어 있다.
- 2026-07-10: T148-T150으로 `MeetingAiPage`를 `/api/meeting-ai/chat` request shape로 전환하고 `sources[]`, `unsupported` 표시를 추가했다. `ReportAgentPage`에는 회의록 candidate 생성/확정 local flow, task candidate 추출/등록 전 편집/등록 승인 local flow, backend gap 안내를 추가했다. T151-T153 완료 기준 중 current confirmed version 전환, Markdown export 버튼, M018 칸반 state와 `sourceCandidateId` 연계는 아직 후속 작업이다.
- 2026-07-10: T158로 backend 권한 매트릭스 구현 전 상세 요구와 계약을 재확인했다. 기준 문서는 `requirements/permissions.md`, `requirements/functional-requirements-detail.md`의 FR-ACL/FR-PERM/FR-OWN, `contracts/space-api.md`, `contracts/meeting-api.md`, `contracts/ai-api.md`, `test-matrix.md`다. M020은 SpaceMember role/remove API, SpaceMember 제거 시 프로젝트 권한 제거와 회의 단독 권한 유지 분리, 마지막 active `HOST` 보호의 실제 participant mutation 적용, owner transfer local transaction flow, MeetingParticipant add/update/revoke API, Project AI context 후보 backend 선필터, audit 최소 event 또는 gap 명시 순서로 진행한다. Baseline verification은 `cd backend && ./gradlew test` 통과했다. 첫 sandbox 실행은 Gradle lock file 권한 문제로 실패했고 승인 후 재실행해 통과했다. 작업 branch는 `backend-permission-matrix`이며 M018 frontend 완료 commit은 `120c6a6 feat: complete project workspace frontend`다. `.specify/memory/session-handoff.md`는 작업 전부터 unstaged 변경이 있어 이번 backend 문서/구현 범위에서 제외한다.
- 2026-07-10: T205-T211로 backend 권한 매트릭스 runtime을 in-memory domain/store/API에 연결했다. `SpaceAccessPolicy.requireOwnerManagement`를 추가하고, `SpaceController`에 SpaceMember 목록/role 변경/제거, owner transfer, Project AI context candidate endpoint를 연결했다. `MeetingController`는 MeetingParticipant list/add/update(revoke 포함)를 제공한다. `WorkspaceDomainService`는 OWNER 전용 SpaceMember role/remove, 제거 시 member MeetingParticipant를 `GUEST`로 전환해 회의 단독 권한을 유지, participant revoke/role 변경의 마지막 active `HOST` 보호, owner transfer 확인 문자열(`TRANSFER OWNER`)과 기존 owner `ADMIN`/`MEMBER` 강등, Project AI `projectKnowledge[]`/accessible `meetings[]` 후보 선필터를 수행한다. AuditLog는 DB 전환 전 최소 in-memory `AuditEvent`로 role 변경, member removal, participant change, owner transfer의 actor/target/before/after/timestamp를 남긴다. Negative permission coverage는 owner/admin/member 권한 차단, participant revoke 즉시 차단, SpaceMember 제거 후 Project AI 차단/회의 접근 유지, 마지막 active HOST 보호, owner transfer 확인 누락, Project AI inaccessible meeting 제외를 `WorkspaceDomainServiceTest`로 고정했다. Verification: `cd backend && ./gradlew test` 통과, `git diff --check` 통과.
- 2026-07-10: 사용자 의도 확인에 따라 회의 참가자 권한과 프로젝트 전체 접근권을 더 엄격히 분리했다. `MeetingParticipant`는 기본적으로 특정 회의 접근권만 만들며 SpaceMember 또는 프로젝트 접근권을 생성하지 않는다. 프로젝트 전체 접근권은 Space owner가 SpaceMember/Space invitation으로 명시 부여한 경우에만 생긴다. 회의 생성의 추가 참여자는 SpaceMember가 아니어도 `GUEST` participant로 등록 가능하고, SpaceMember 제거 시 기존 회의 participant는 `GUEST`로 전환되어 회의 ACL 범위 접근만 유지된다.
- 2026-07-11: T212-T215로 사용자-facing 회의 참여를 URL/코드 참가 신청과 HOST 승인 흐름으로 전환했다. 회의 생성은 UUID 기반 32자리 `joinCode`와 URL을 반환하고, `POST /api/v1/meetings/join-requests`는 meetingId 없이 code 또는 URL만 받아 meeting을 조회해 `PENDING` 신청을 만든다. JoinRequest에는 코드 원문을 복제 저장하지 않는다. active HOST 또는 Space OWNER/ADMIN override만 목록 조회와 승인/거절이 가능하고, 승인 시 기본 `VIEWER` participant를 만든 뒤 SpaceMember 여부에 따라 `member`/`guest`를 결정한다. 승인 전에는 participant와 회의 접근권이 없으며 SpaceMember는 생성하지 않는다. 기존 participant 직접 추가 API는 운영상 ACL 조정용으로 유지하고 `MEETING_INVITATION` target 계약은 superseded 처리했다. URL/code, invalid code, duplicate pending, existing participant, viewer 승인 거부, 순수 HOST 승인, ADMIN override 승인, approve/reject replay, member/guest 분기와 controller response를 테스트했다. Verification: `cd backend && ./gradlew test` 64건 통과, `git diff --check` 통과.
- 2026-07-11: M028 persistence gap은 `meetings.join_code_hash` unique lookup과 `meeting_join_requests` table/partial unique pending index다. 현재 in-memory prototype은 Meeting에 raw joinCode를 보관한다. 당시 Frontend target type/client는 기존 MeetingInvitation 경계를 사용했으며, 이 gap은 2026-07-12 M029에서 JoinRequest 화면/client로 해소했다.
- 2026-07-12: T216 조사 결과 `TeamMembersPage`에는 SpaceRole 조회/변경/제거, `ProjectOverviewPage`에는 MeetingParticipant role/accessStatus와 default-deny/override 표시가 있다. 그러나 `LiveMeetingPage`는 인증만 확인한 뒤 고정된 HOST 권한 문구로 prejoin을 허용하고, JoinRequest code/url 입력 화면과 M028 API client는 없다. 또한 기존 local 승인 handler는 회의 신청자를 SpaceMember로 추가해 회의 단독 권한 의도와 충돌한다. M029에서 `/meeting-access`, Backend ACL access probe, meeting-only 승인 semantics를 순서대로 구현한다.
- 2026-07-12: T217-T221로 Frontend meeting access surface를 M028 계약에 연결했다. `/meeting-access`는 URL/code 신청, PENDING 표시, participant 조회 기반 접근 재확인, HOST/OWNER/ADMIN의 pending 신청 조회·승인·거절을 제공한다. `/live-meeting`은 meetingId와 Backend participant access probe가 성공하기 전 media/prejoin을 노출하지 않고, `/live-room`은 legacy 무인가 token endpoint 대신 Bearer token을 포함한 `/api/v1/meetings/{meetingId}/livekit-token`을 사용한다. Project/Workspace meeting link에는 meetingId를 전달하고, 로그인 redirect는 invite query를 보존한다. ProjectOverview 회의 목록과 Project AI meeting source는 현재 사용자 participant 또는 OWNER/ADMIN override 기준으로 계산하며, 회의 생성/ACL/상태/삭제 control도 현재 role에 따라 제한한다. 회의 생성 form의 직접 참가자 지정은 제거했다. TeamMembers local 회의 승인도 SpaceMember를 만들지 않고 VIEWER guest participant만 생성하며, SpaceMember 제거 시 기존 meeting participant는 REVOKED가 아니라 guest로 유지하도록 수정했다. Verification: `cd frontend && npm run build` 통과, 승인된 local dev server `http://127.0.0.1:5173/meeting-access` HTTP 200, `git diff --check` 통과. 중간 build 1회는 제거한 participant state의 orphan 초기화 호출 때문에 실패했고 해당 호출 제거 후 재실행해 통과했다. Browser 스킬로 in-app browser 연결을 시도했으나 available browser 목록이 비어 있어 desktop/mobile visual smoke는 실행하지 못했다.
- 2026-07-14: T222로 CI hardening gap을 조사하고 M030/T223-T232 실행 순서를 추가했다. 현재 CI는 `main` push/PR의 Backend test, Frontend build, AI compile/unit만 수행하며 `dev` push, Backend `bootJar`, PostgreSQL V1~V6 실제 migration, Backend/AI image, Frontend lint/unit/Playwright, secret/image scan, GitHub Summary와 final gate가 없다. migration V4는 `vector` extension을 생성하므로 pgvector 지원 PostgreSQL service image를 사용한다. `main`은 stable final check를 required로 설정한 PR-only 보호 규칙을 적용하고, `dev`는 우선 push CI를 실행하는 통합 브랜치로 둔다. Branch protection은 workflow check가 원격에 생성된 뒤 관리자 권한으로 적용한다. 현재 Docker daemon 연결 실패로 local container smoke는 미실행했고, `gh` 미인증 및 GitHub App private repo 접근 실패로 원격 보호 상태도 확인하지 못했다. API/ERD/data-model 변경 영향은 없고 `git diff --check`는 통과했다.

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
- `TeamMembersPage.tsx`는 SpaceMember role/owner transfer와 회의 참가 신청을 분리해 표시한다. 실제 Backend 신청 조회/검토는 M022의 `/meeting-access?meetingId=...` 관리 화면이 담당하고, TeamMembers local 요청은 mock fallback 검증용이다.
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
- Passed: `cd backend && ./gradlew test` after M028 meeting join request approval flow, total 64 backend tests
- Passed: `git diff --check` after M028 meeting join request approval flow
- Passed: `cd backend && ./gradlew test` and `cd frontend && npm run build` after integrating M027-M029 permission and meeting access work onto `origin/dev`
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
- M018 Backend/API 후속: MeetingParticipant/Invitation persistence, SpaceMember role/remove API, owner transfer transaction, Kanban persistence, Project AI backend 권한 선필터/context 조립, AuditLog 저장
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

## M030 Local PostgreSQL and pgvector Foundation

### Design

- 원격에 공유된 Flyway V1~V9은 수정하지 않고 최신 MeetingJoinRequest와 joinCodeHash를 V10 forward migration으로 추가했다.
- 로컬 DB는 다른 프로젝트 PostgreSQL과 격리된 `pgvector/pgvector:pg16` 컨테이너와 host `5434`를 사용한다.
- 회의당 `MeetingTranscript` 하나가 `PENDING/PROCESSING/COMPLETED/FAILED`, `retentionUntil`, `legalHold`, `purgedAt`을 관리하고 segment는 기존 `meetingId` FK를 유지한다.
- `SourceReference`는 DB table이 아닌 API 논리 모델로 결정했다. report/task는 `sourceIds`, transcript chunk는 `chunk_source_segments`로 근거를 보존한다.
- `requirements/glossary.md`의 물리 이름도 `response.sources`, `source_ids`, `chunk_source_segments`로 맞췄으며 `requirements/INDEX.md` 라우팅 변경은 필요하지 않다.
- M030 시점의 비동기 재색인은 `EmbeddingJob`, `generation`, `isActive`, `replacedAt`으로 추적했고 embedding model/차원/vector index는 열어 두었다. 해당 Q-010은 아래 M033 준비에서 결정했다.

### Changes

- `compose.local.yml`: PostgreSQL 16 + pgvector, named volume, health check, host `5434` 기본값을 추가했다.
- V7: `auth_identities`, `auth_sessions`, `space_invitations`, 기존 `meeting_invitations`, `meeting_rooms`와 token/status/partial unique 제약을 추가했다. 공유된 checksum은 유지한다.
- V8: retention 기본값/enum, `meeting_transcripts`, `domain_terms`, `audit_logs`와 보존 정리 index를 추가했다.
- V9: `embedding_jobs`와 chunk generation/active 교체 metadata/index를 추가했다.
- V10: `meeting_join_requests`, `meetings.join_code_hash`와 pending/review/lookup 제약을 추가했다. 기존 `meeting_invitations`는 공유 migration 호환을 위해 물리 테이블만 유지하고 사용자-facing 계약에서는 사용하지 않는다.
- Spring Boot JDBC starter와 `application-local.yml`을 추가했다. `local` profile을 기본 profile로 지정해 Docker Compose DB와 DataSource/Flyway를 기본 활성화하고, `db` profile은 환경변수 기반 DataSource를 사용한다.
- README에 로컬 DB 실행, Flyway 적용, 중지 명령과 환경변수 경계를 추가했다.

### Verification

- Passed: `docker compose -f compose.local.yml config`
- Passed: `meetingmind-postgres-local` health check, PostgreSQL 16.14, pgvector 0.8.5
- Passed: 빈 DB에 Flyway V1~V10 최초 적용, 10개 migration 모두 success
- Passed: 기존 로컬 schema version 9에서 V7 checksum 변경 없이 V10 forward upgrade
- Passed: Flyway 재실행에서 schema version 10 up-to-date 확인
- Passed: 25개 도메인 table, `meeting_join_requests`, `meetings.join_code_hash`, pgvector 0.8.5, pending unique와 review 상태 제약 조회
- Passed: `cd backend && ./gradlew test`
- Passed: `cd frontend && npm run build`
- Passed: `git diff --check`

### Local Profile Refinement

- `SPRING_PROFILES_ACTIVE=local`은 기본적으로 `jdbc:postgresql://localhost:5434/meetingmind`, 사용자 `meetingmind`를 사용한다.
- Compose와 local profile은 동일한 `MEETINGMIND_DB_*` 이름, 사용자, 비밀번호, port 기본값을 공유하며 Spring 표준 datasource 환경변수로 Backend만 별도 override할 수 있다.
- Passed: `SPRING_PROFILES_ACTIVE=local SERVER_PORT=18080 ./gradlew bootRun`, Hikari PostgreSQL 연결, Flyway v10 up-to-date
- Passed: `GET http://127.0.0.1:18080/api/workspace` -> `200`
- Passed: profile 미지정 `SERVER_PORT=18080 ./gradlew bootRun`, default `local` 자동 적용, `GET http://127.0.0.1:18080/api/workspace` -> `200`; 기존 8080 Backend는 중단하지 않았다.
- `./gradlew bootRun`은 기본 `local` profile로 Docker PostgreSQL에 연결한다. Docker 없이 실행할 별도 in-memory profile은 현재 제공하지 않는다.
- Gradle `test` task는 `test` profile을 명시해 DataSource/Flyway를 비활성화한다. 따라서 단위/컨텍스트 테스트는 Docker 실행 여부와 독립적이고, 실제 schema는 별도 Compose/Flyway 검증으로 확인한다.
- Passed: `meetingmind-db` 중지 상태에서 `cd backend && ./gradlew test`
- `db` profile은 배포/CI용으로 분리하고 datasource URL/username/password 환경변수를 필수로 요구한다.

### Remaining Boundary

- T229: Auth/Workspace/STT in-memory/file 저장소의 PostgreSQL repository 및 transaction 전환
- T230: embedding worker와 권한 필터된 pgvector retriever 연결
- 보존 만료 정리 scheduler와 `legalHold` 운영 API

## M031 CI Quality and Supply Chain Gates

### Baseline Audit and Planning

- CI trigger/concurrency/최소 권한과 Backend/Frontend/AI, PostgreSQL Migration, Playwright, Container Images, Secret Scan, `CI Gate` job 구현을 확인했다.
- 충돌은 `.gitignore`, `backend/build.gradle`, `tasks.md`에서 해결했다. PostgreSQL local/test profile과 CI dependency 고정, M030 data milestone과 M031 CI milestone을 모두 보존했다.
- Gitleaks는 44개 커밋에서 `backend/.env` 4건, `ai/.env.example` 1건을 탐지했다. OpenAI key 규칙 3건과 generic API key 규칙 2건이며 secret 값과 전체 hash는 기록하지 않았다.
- OpenAI/LiveKit 기존 credential은 공급자에서 폐기·재발급됐다. 여러 원격 공유 브랜치의 강제 재작성을 피하기 위해 폐기된 5건의 exact fingerprint만 `.gitleaksignore`에 등록하고 신규 secret 차단은 유지한다.

### Verification

- Passed: `cd backend && ./gradlew test bootJar`
- Passed: `cd frontend && npm run lint && npm run test && npm run build`; lint 오류 0건/기존 경고 8건, unit 6건
- Passed: `cd ai && python3 -m compileall app tests && python3 -m unittest discover -s tests`; 35 tests
- Passed: conflict 0건, `git diff --check`, `git diff --cached --check`
- Passed: OpenAI/LiveKit 기존 credential 폐기·재발급 확인 후 `.gitleaksignore` exact fingerprint 5건 적용, `gitleaks git . --redact --no-banner` 0건
- Passed: 격리된 `pgvector/pgvector:0.8.2-pg16-bookworm` PostgreSQL 16에서 `MigrationIntegrationTest`; Flyway V1~V10과 `vector` extension
- Passed: `meetingmind-backend:ci`, `meetingmind-ai:ci` build와 content digest 생성; 두 image 모두 `meetingmind` non-root 사용자와 예상 entrypoint 확인
- Passed: Trivy 0.72.0 HIGH/CRITICAL scan; Backend OS/JAR 0건, AI OS/Python package 0건
- Passed: `cd frontend && npm run test:e2e`; Chromium 로그인, active HOST prejoin 허용, unknown meeting default-deny 2건
- Fixed: Trivy 0.72.0 Linux 64-bit archive에 32-bit checksum이 지정된 오류를 공식 64-bit checksum으로 교정했다. Gitleaks 8.30.1 Linux x64 checksum도 공식 release와 대조했다.
- Hardened: 모든 GitHub Action을 공식 major ref가 가리키는 commit SHA로 고정했다.
- Initial remote result: PR #29의 Backend, Frontend, AI, PostgreSQL Migration, Playwright, Container Images는 성공했다. Secret Scan의 과거 5건 때문에 `CI Gate`만 연쇄 실패했다.
- Passed: exact fingerprint 적용 후 PR #29 재실행에서 Backend, Frontend, AI, PostgreSQL Migration, Playwright, Container Images, Secret Scan과 최종 `CI Gate`가 모두 성공했다.
- Blocked: `main` branch protection API는 private repository의 현재 GitHub 요금제에서 `403 Upgrade to GitHub Pro or make this repository public`를 반환했다. Pro 업그레이드 또는 공개 전환 전에는 required `CI Gate`, PR-only, force-push/삭제 금지를 적용할 수 없다.

### Remaining Work

- T244/T245: GitHub Pro 업그레이드 또는 공개 저장소 전환 후 `main` protection 적용과 최종 closeout

## M032 Backend PostgreSQL Runtime Persistence

### Design

- `AuthStore`와 `WorkspaceStore` port로 service의 concrete in-memory 의존을 제거했다.
- `test` profile은 기존 in-memory adapter를 유지하고 기본 `local` 및 배포용 `db` profile은 Spring JDBC adapter를 사용한다.
- 기존 V1~V10 schema가 현재 관계형 domain 계약을 수용하므로 migration, ERD 관계, vector schema는 변경하지 않았다.
- join code는 생성 응답에서만 원문을 반환하고 PostgreSQL에는 SHA-256 hash만 저장한다.
- Backend owner는 관계형 원천 데이터와 ACL 선필터를 담당한다. embedding provider/model, vector 차원/index, `EmbeddingJob`/`EmbeddingChunk` runtime과 `ai/app/rag.py`는 별도 AI/RAG owner 경계로 남겼다.

### Changes

- Auth: user/identity/session JDBC round-trip, signup/login/google/refresh/logout transaction, refresh row lock과 rotation을 연결했다.
- Workspace: Space/member/owner, Meeting/participant/join request, speaker/transcript segment를 JDBC로 저장·조회한다.
- Artifacts: report/decision/action/sourceIds, task candidate/card, ProjectKnowledge metadata와 AuditLog를 기존 JSONB/schema 계약으로 저장한다.
- Transactions: Space/Meeting row lock 순서를 두고 owner transfer, participant mutation, join approval/reject, report version/current, task candidate confirm과 audit를 원자적으로 처리한다.
- AI context: Project AI meeting 후보를 PostgreSQL query에서 active SpaceRole과 MeetingParticipant ACL로 선필터한다. Meeting AI는 DB transcript/report source를 기존 internal contract로 전달한다.
- Profile wiring: in-memory adapter는 `test`, JDBC adapter는 `local`/`db`에서만 등록한다.

### Verification

- Passed: `cd backend && ./gradlew test bootJar`
- Passed: host `5434` local DB에서 `JdbcAuthStoreIntegrationTest`, `JdbcWorkspaceStoreIntegrationTest`; refresh rotation, joinCode hash, ACL/guest, transcript/report/task/knowledge/audit JSONB round-trip
- Passed: member는 active participant 회의만, OWNER는 전체 회의를 Project AI 후보로 조회하고 meeting guest는 Project AI를 거부하는 PostgreSQL 선필터 negative test
- Passed: 임시 빈 `pgvector/pgvector:pg16` DB(host `55432`)에서 Flyway V1~V10 최초 적용과 Auth/Workspace JDBC integration test
- Passed: 기본 `local` profile Backend 기동, Hikari/Flyway v10 확인, signup `200`, Backend 재시작 후 같은 계정 login `200`; 검증 계정/세션은 이후 삭제했다.
- Passed: `git diff --check`
- Not run: Frontend/AI 검증. 이번 변경은 Backend persistence와 문서만 수정하고 frontend/AI/vector owner 파일은 변경하지 않았다.

### Remaining Boundary

- legacy `/api/stt` streaming session과 transcript file prototype은 실제 STT pipeline 계약이 Future Draft라 이번 관계형 artifact persistence에서 제외했다.
- T230 embedding worker, model/dimension/index, pgvector similarity query와 semantic retriever는 별도 AI/RAG 담당 범위다.
- `Q-008`, `Q-009` candidate TTL과 report history/export는 여전히 후속 결정이 필요하다.
## M034 Grounded PostgreSQL RAG Preparation

### Decisions

- Q-010을 `text-embedding-3-small`, 1536차원, cosine exact search로 닫았다. 권한 선필터 후 후보 5,000개 초과 또는 검색 p95 1초 지속 초과 전에는 approximate index를 추가하지 않는다.
- vector cosine 후보와 `pg_trgm` 후보를 RRF로 결합한다. Meeting AI는 5개, Project AI는 8개 근거를 기본값으로 사용한다.
- Backend는 권한을 평가해 Meeting 단일 scope 또는 Project allowed meeting scope를 만들고, AI는 역할을 재판단하지 않고 DB query에서 범위를 강제한다.
- `EmbeddingChunk.scope`는 query mode가 아니라 source 소유 범위다. meeting artifact와 ProjectKnowledge를 중복 임베딩하지 않는다.
- Target AI는 관련도 gate와 structured citation 검증을 사용한다. 근거 없는 chat/report/task 결과는 public 응답 또는 candidate 저장에 사용하지 않는다.
- 인덱스 갱신은 fine-tuning이 아니라 source별 비동기 재임베딩이다. ProjectKnowledge 변경, transcript 완료, 발화자명 변경, current confirmed report 변경이 trigger이며 삭제/보관/보존 만료는 즉시 제외한다.
- PostgreSQL `embedding_jobs`를 durable queue로 사용하고 별도 broker는 도입하지 않는다. grounding은 완료된 T253 Backend persistence 위에서 먼저 진행하고 worker/retriever는 V12 migration 이후 통합한다.
- 운영 baseline은 원문 비노출 로그, p95 검색/응답 지연, unsupported reason, citation 검증 실패, embedding queue age와 final failure다.

### Document Impact

- Updated: `clarify.md`, `plan.md`, `contracts/ai-api.md`, `data-model.md`, `erd.md`, `tasks.md`, shared handoff.
- Requirements impact: 기존 FR-MBOT/FR-PBOT, NFR-AI, NFR-AZ, NFR-DATA, 성능 기준을 구체화했으며 `requirements/*` 기준선 자체는 변경하지 않았다.
- ERD/data-model impact: target `vector(1536)`, EmbeddingJob source XOR/generation unique와 retry/lease metadata를 문서화했다. 실제 V1-V10 migration은 수정하지 않고 T267의 V12 forward migration으로 분리했다.
- API impact: T264 문서 준비 시 Target 응답에 nullable `unsupportedReason`과 citation 검증 규칙을 추가했고, 당시 Current Prototype 구현은 유지했다. 실제 응답 구현은 아래 T265에서 반영했다.

### Verification

- Passed: 관련 requirements, AI contract, data model, ERD, existing V9 job schema 영향 비교.
- Passed: `git diff --check`, stale Q-010/scope 표현 검색, T264-T276 task ID 중복 검사.
- Not run: code tests. 이번 작업은 구현 변경 없는 shared contract/task 준비다.

### T265 Grounding Implementation

- `ai/app/grounding.py`에 공통 evidence gate와 `NO_EVIDENCE`, `LOW_RELEVANCE`, `MODEL_UNSUPPORTED`, `UNVERIFIED_OUTPUT` 판정을 추가했다.
- in-memory lexical 검색 점수를 `0.0~1.0`으로 정규화하고 prototype threshold `0.30` 미만이면 provider를 호출하지 않는다.
- 용어 설명, Meeting AI, Project AI provider 응답을 `supported`, `answer`, `sourceIds` 구조로 검증한다. citation 누락이나 allowlist 밖 ID가 있으면 답변과 source를 폐기한다.
- 보고서 decision/action item과 task candidate는 유효한 source ID가 있는 항목만 유지한다. 검증 가능한 저장성 항목이 없으면 candidate를 만들지 않고 `UNVERIFIED_OUTPUT`으로 반환한다.
- public/internal 응답에 nullable `unsupportedReason`을 추가하고 supported 응답의 `sources[]`는 실제 citation만 반환한다.
- API request, ERD, 관계형 data model은 바꾸지 않았다. 현재 Backend가 직접 전달하는 report/task source는 검색 점수가 없어 evidence 존재 gate만 적용하고, model별 semantic threshold는 T260에서 보정한다.

### T265 Verification

- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests -v`, 43 tests.
- Passed: 근거 0건/낮은 관련도 provider 미호출, missing/forged citation 차단, provider unsupported, report/task 무근거 항목 제거.
- Passed: `cd ai && ./.venv/bin/python -m compileall app tests`.
- Passed: `git diff --check`.

### T266 Structured Outputs And Untrusted Context

- Responses API request가 선택적으로 `text.format`을 받도록 확장하고, grounded answer, report, task candidate에 strict JSON Schema를 연결했다.
- 모든 object schema는 `additionalProperties=false`이고 모든 property를 required로 선언했다. nullable report/task 필드는 `string|null`, 저장성 상태는 `candidate` enum으로 제한한다.
- 용어 설명, Meeting AI, Project AI, report, task provider 경로는 strict schema를 사용한다. 기존 `/api/meeting-ai/ask` plain-text 호출에는 format을 추가하지 않았다.
- source context는 `AiSource` JSON 배열로 직렬화한다. developer instruction은 source의 text/title/speaker 안에 포함된 명령이나 역할 변경 요청을 실행하지 않고 사실 데이터로만 사용하도록 명시한다.
- API public response, ERD, 관계형 data model과 Backend request 계약은 변경하지 않았다.

### T266 Verification

- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests -v`, 48 tests.
- Passed: strict/closed/required schema 재귀 검증, Responses API request body의 `text.format`, 5개 provider call site format 전달, legacy plain-text 비영향.
- Passed: source 내 지시문과 JSON 경계 이탈 문자열이 원문 보존된 JSON data로 직렬화되고 internal relevance score는 provider context에서 제외됨을 검증.
- Passed: `cd ai && ./.venv/bin/python -m compileall app tests`.
- Passed: `git diff --check`.

### T267-T272, T268 PostgreSQL RAG Integration

- V12 forward migration은 기존 V1~V10 checksum을 유지하면서 `pg_trgm`, `vector(1536)`, source XOR/generation unique, retry/lease 필드와 exact/trigram index를 추가했다. 후보 임계값을 넘기 전까지 HNSW는 만들지 않는다.
- ProjectKnowledge 생성·수정·복원, MeetingTranscript 완료, 발화자/회의명 변경, current confirmed report 전환은 source row 변경과 같은 transaction에서 다음 EmbeddingJob generation을 만든다. segment insert와 candidate/draft report 편집은 job을 만들지 않는다. ProjectKnowledge archive와 전사 purge는 기존 chunk를 즉시 비활성화하고 purge된 transcript source link도 제거한다.
- AI worker는 `FOR UPDATE SKIP LOCKED`와 lease로 작업을 선점하고 1분/5분/15분 retry 뒤 final failure를 기록한다. 최신 generation만 기존 active chunk와 원자적으로 교체하며 stale generation과 실패 generation은 이전 active chunk를 유지한다.
- Meeting worker snapshot은 최신 `meeting_speakers` 이름, transcript window, current confirmed report/decision/action을 사용한다. ProjectKnowledge는 `PUBLISHED`이고 미삭제인 원천만 chunk로 만든다.
- Meeting/Project chat Backend service는 raw source 조립 대신 `AiSearchScopeResolver`가 만든 `spaceId + meetingId` 또는 `spaceId + allowedMeetingIds`만 AI gateway에 전달한다. 권한 거부는 gateway 호출 전에 끝난다.
- AI SQL은 active chunk와 `COMPLETED` job만 대상으로 exact cosine 20개와 `pg_trgm` 20개를 RRF로 결합한다. ProjectKnowledge publish/delete와 MeetingTranscript purge 상태도 방어적으로 다시 확인한다. Meeting 단일 범위, ProjectKnowledge와 허용 회의 union, 빈 allowed list와 cross-space/meeting 차단을 DB 통합 테스트로 검증했다.
- 모든 `/api/internal/*`는 `X-MeetingMind-Service-Token`을 검증한다. Backend의 네 AI gateway client는 같은 환경변수에서 헤더를 추가하고 빈 credential은 전송하지 않는다.

### T267-T272, T268 Verification

- Passed: 임시 PostgreSQL 16 DB에서 Flyway V1~V10 적용, legacy V10 job 삽입, V11 soft-delete 적용 후 V12 upgrade와 version 1~12 history 확인.
- Passed: `vector(1536)`, `vector`/`pg_trgm`, exact cosine/trigram query, source trigger generation과 non-trigger negative case.
- Passed: 별도 V1~V12 DB에서 deterministic 1536차원 provider로 worker generation 1→2→3/4 stale swap, source segment link, Meeting/Project scope와 cross-scope 차단 통합 테스트.
- Passed: `cd ai && ./.venv/bin/python -m compileall app tests`.
- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests -v`, 60 tests 중 DB 환경변수 기반 1건은 기본 실행에서 skip; 같은 테스트를 별도 PostgreSQL DSN으로 실행해 통과했다.
- Passed: `cd backend && ./gradlew test`와 PostgreSQL `MigrationIntegrationTest --rerun-tasks`.
- Migration reconciliation: Meeting soft delete를 `V11`로 유지하고 vector/job migration을 `V12`로 승격했다. 기존 branch-local `V11 finalize vector search jobs` history가 있는 DB는 Flyway repair 대상이 아니며, data dump 후 새 DB에서 V1~V12를 다시 적용해야 한다.
- Passed: `docker compose -f compose.local.yml config`, `docker compose -f compose.local.yml --profile ai config`, `git diff --check`.
- Not run: 실제 OpenAI embedding/chat credential을 사용한 과금 발생 E2E와 한국어 30~50개 평가/검색 p95 측정. T275에서 수행한다.

### Remaining Boundary

- legacy STT streaming session/file은 아직 `meeting_transcripts.status=COMPLETED`를 JDBC로 기록하지 않는다. DB trigger/worker는 준비됐지만 실제 회의 종료 이벤트 연결은 STT owner와 통합해야 한다.
- T274 Frontend `unsupportedReason`, T263 실제 provider 기반 정확도·성능·Backend-to-AI HTTP 검증이 남아 있다.
- M034 attachment RAG는 MeetingMessage/Attachment 계약과 Backend 저장이 완료된 뒤 진행한다.

### T273 AI Observability

- Backend 요청의 안전한 `X-Request-ID`를 응답과 네 AI gateway 호출에 전파하고 오류 응답 `traceId`와 연결했다.
- AI API, PostgreSQL 검색, embedding job/queue에 JSON 구조 로그를 적용했다. 질문, STT, 답변과 credential은 허용 필드에 포함하지 않는다.
- 검색 지연/결과 수/검색 범위, source·unsupported·citation failure, job 처리 시간/실패 코드, queue pending/processing/failed/oldest age를 기록한다.
- 초기 알림 기준은 검색 p95 1초, unsupported 30%, citation failure 5%, pending age 300초 또는 failed 1건, gateway/provider 실패율 5%다. 실제 threshold 조정은 T275 측정 이후 수행한다.

### T273 Verification

- Passed: `cd ai && ./.venv/bin/python -m compileall app tests`와 Docker PostgreSQL DSN을 사용한 AI 62 tests. 질문·검색 결과·provider 상세 비노출, 검색/queue 구조 로그와 generation 교체를 검증했다.
- Passed: `cd backend && ./gradlew test`, PostgreSQL `MigrationIntegrationTest`와 `JdbcWorkspaceStoreIntegrationTest`; RequestTrace 정규화와 네 AI gateway의 `X-Request-ID` 전달을 검증했다.
- Passed: Compose 기본/AI profile config, Frontend lint 오류 0건/unit 6건/build, Playwright 4건, `git diff --check`.
- Not run: 실제 OpenAI credential을 사용하는 과금 E2E, 한국어 평가 질의와 검색 p95 측정, 운영 모니터링 시스템의 실제 alert 발화. T275에서 수행한다.

## M035 Meeting Chat Text Attachment RAG Preparation

### Decision

- 회의 채팅 첨부파일은 MVP에서 텍스트 임베딩만 사용한다. TXT, Markdown, 텍스트 추출 가능한 PDF는 기존 `text-embedding-3-small` 1536차원 공간에 저장한다.
- 이미지 파일과 이미지 전용 PDF는 현재 검색 대상에서 제외한다. visual embedding, OCR/Vision 설명과 원본 기반 멀티모달 답변은 별도 확장 milestone에서 결정한다.
- 현재 실시간 회의 화면에는 영속 채팅/첨부파일 도메인과 업로드 API가 없다. AI extractor보다 MeetingMessage/Attachment 계약 및 Backend 저장이 선행되어야 한다.

### Document Impact

- Updated: `clarify.md`, `plan.md`, `contracts/ai-api.md`, `tasks.md`, shared handoff.
- Deferred: Attachment API, ERD, data model과 requirements 변경은 T266 shared contract에서 함께 처리한다.
- M034 impact: 기존 grounding과 pgvector 기반 구현은 그대로 진행할 수 있으며 이미지 vector 컬럼이나 모델은 V12에 추가하지 않는다.

### Verification

- Passed: 현재 LiveRoom, embedding chunk/job schema, 업로드 요구사항과 권한 문서의 선행 경계 비교.
- Passed: `git diff --check`, Q-011/D-039 반영 검색, T277-T284 task ID 중복 검사.
- Not run: code tests. 이번 변경은 첨부파일 검색 정책과 후속 task 준비만 포함한다.

## M036 Frontend Workspace Persistence Hydration

### Implementation

- Backend에 `GET /api/v1/spaces/{spaceId}/meetings`를 추가하고 기존 Space membership 및 Meeting ACL 필터를 적용했다. `status/from/to` query는 enum·ISO-8601·범위 순서를 서버에서 검증하며, 실제 active participant가 없는 OWNER/ADMIN override 결과는 권한 role을 가장하지 않고 `myRole=null`로 반환한다.
- 로그인 후 레거시 화면 snapshot과 별도로 Space, 접근 가능한 Meeting, SpaceMember를 target API에서 읽어 workspace catalog와 프로젝트 상태를 복원한다. 일부 하위 조회가 실패하면 `Workspace API partial`로 표시하고 mock 회의를 영속 Space에 섞지 않는다.
- Space/Meeting 생성 API 실패 시 local fallback ID로 phantom 항목을 만들지 않는다. 생성 폼은 요청 중 중복 제출을 막고 성공한 경우에만 닫거나 입력값을 초기화하며 실패 메시지를 유지한다.
- 이름 기반 local state의 현재 경계를 보호하기 위해 UI에서는 같은 이름의 Space 중복 생성을 거부한다. 전체 상태 key를 `spaceId`로 전환하는 작업은 별도 구조 변경으로 남긴다.

### Document Impact

- Updated: `plan.md`, `contracts/meeting-api.md`, `tasks.md`, `implement.md`.
- `myRole` nullable은 MeetingParticipant/SpaceRole 관계를 바꾸지 않는 조회 projection 계약이므로 `data-model.md`와 `erd.md` 변경은 없다.
- requirements 기준의 Space membership, Meeting ACL, OWNER/ADMIN override 규칙은 변경하지 않았다.

### Verification

- Passed: `cd backend && ./gradlew test`.
- Passed: Docker PostgreSQL 16.14 + pgvector 0.8.5에서 `JdbcWorkspaceStoreIntegrationTest`; 재조회된 Meeting 목록과 member ACL을 검증했다.
- Passed: `cd frontend && npm run lint`, 오류 0건과 기존 경고 8건.
- Passed: `cd frontend && npm run test`, 6 tests.
- Passed: `cd frontend && npm run build`; 기존 500 kB 초과 bundle warning만 유지됐다.
- Passed: Playwright Space/Meeting 새로고침 복원 및 프로젝트 생성 500 실패 시 phantom 미생성 2 tests.
- Passed: `git diff --check`.
## M033 Meeting CRUD PostgreSQL End-to-End

### Design and Contract

- FR-MREG-01/04/05/06/07, FR-ACL-07을 기준으로 목록·상세·수정·삭제 권한과 canonical 상태 전이를 확정했다.
- 수정은 OWNER/ADMIN/active HOST, 삭제는 OWNER/active HOST만 허용한다. ADMIN 단독 삭제는 기본 거부한다.
- `SCHEDULED` 삭제는 `CANCELED` 전환과 soft-delete metadata를 같은 transaction에 기록하고, `IN_PROGRESS` 삭제는 `409 MEETING_ALREADY_PROCESSING`, `ENDED` 삭제는 상태를 유지한 soft delete로 결정했다.
- hard purge, restore, grace period는 보존·감사 정책이 추가로 필요하므로 후속 범위로 남겼다.

### Changes

- V11 forward migration에 `meetings.deleted_at`, `deleted_by` FK/check와 active 조회 partial index를 추가했다. 공유 V1~V10은 수정하지 않았다.
- `WorkspaceStore`의 in-memory/JDBC adapter에 meeting update/soft delete를 추가하고, 목록·상세·Project AI 후보·Meeting AI source 조회에서 삭제 row를 제외했다.
- `WorkspaceDomainService`에 ACL-filtered list/detail, title/schedule/status update, row-locked delete/audit transaction을 구현했다.
- `GET /api/v1/spaces/{spaceId}/meetings`, `GET/PATCH/DELETE /api/v1/meetings/{meetingId}`와 DTO를 구현했다.
- Frontend target Space는 Backend 회의 목록으로 legacy meeting을 교체하며 생성·수정·삭제 뒤 반드시 재조회한다. API 실패 시 local-only 성공 mutation을 수행하지 않는다.
- ProjectOverview의 loading/error/권한 control과 상태 표기, 삭제 확인을 연결하고, 고정 높이 flex에서 운영 카드와 칸반이 겹치던 레이아웃을 수정했다.
- Playwright Backend/Frontend port와 Backend API base URL을 환경변수로 분리해 기존 개발 서버를 중단하지 않고 격리 E2E를 실행할 수 있게 했다. cross-origin target 실행에 필요한 CORS `PATCH`도 허용했다.

### Verification

- Passed: `cd backend && ./gradlew test`
- Passed: local PostgreSQL `5434`에서 `JdbcWorkspaceStoreIntegrationTest`; update round-trip, deleted metadata, 목록/Project AI/Meeting AI 제외
- Passed: 임시 빈 PostgreSQL `55432`에서 `MigrationIntegrationTest`; Flyway V1~V11 전체 적용. 기존 local V10 schema의 V11 forward upgrade와 재검증도 통과했다.
- Passed: `cd frontend && npm run test`; 2 files, 9 tests
- Passed: `cd frontend && npm run lint`; 오류 0건, 기존 경고 8건
- Passed: `cd frontend && npm run build`; bundle size 경고 외 성공
- Passed: `PLAYWRIGHT_BACKEND_PORT=18081 npm run test:e2e`; 로그인, HOST prejoin/default-deny, 회의 생성→수정→삭제와 서버 목록 0건, 총 3건
- Passed: local profile Backend `18082` + PostgreSQL real API smoke; signup→Space→create→list/detail→patch 후 Backend 재시작, login→수정값 재조회→delete→목록 0건→상세 404→Meeting AI 404
- Cleanup: real API smoke에서 생성한 고유 account/session/Space/Meeting/audit row만 검증 후 transaction으로 제거했다.
- Passed: `git diff --check`
- Recovered validation input error: 첫 JDBC 재실행은 수동으로 잘못 넣은 DB 비밀번호 때문에 실패했고, `compose.local.yml`의 `meetingmind_local` 기준으로 재실행해 통과했다. 코드 실패는 아니었다.

### Closeout Boundary

- `.specify/memory/session-handoff.md`는 병합된 팀 공통 기준만 기록한다는 규칙에 따라 아직 병합되지 않은 현재 브랜치 상태를 추가하지 않았다.
- soft-deleted meeting의 hard purge, restore, grace period와 운영자 조회 API는 후속 milestone에서 보존·감사 요구와 함께 결정한다.
- AI/RAG 파일과 vector migration은 수정하지 않았다. 후속 retriever도 M033의 `deleted_at is null` 관계형 선필터 결과만 사용해야 한다.

## M037 Meeting CRUD Frontend Target Completion

### Design and Changes

- M033 완료 범위와 FR-MREG/FR-ACL/FR-CAL 요구를 대조해 M037을 별도 Frontend milestone로 추가했다. Backend·AI·migration은 수정하지 않았다.
- target Space는 `GET /spaces/{spaceId}/members`로 stable userId를 조회하고, 회의 생성 시 선택한 Space member를 `participantUserIds`로 전달한다. 사용자-facing 신규 참여는 URL/코드 참가 신청 흐름과 분리했다.
- target 회의 목록을 읽은 뒤 `GET /meetings/{meetingId}`와 `GET /meetings/{meetingId}/participants`를 함께 조회한다. 상세 응답의 title/status/schedule/myRole과 participant 목록의 stable participant id를 meetingId 기반 state에 저장한다.
- 실제 Backend 상세 DTO가 participant id를 계약의 `id`가 아닌 `participantId`로 반환하는 차이를 E2E에서 발견했다. Frontend는 mutation ID가 계약과 일치하는 participant 목록 endpoint를 사용해 우회했으며 Backend DTO/계약 정규화는 후속이다.
- participant 추가, role 변경, `ACTIVE`/`REVOKED` 변경은 target POST/PATCH API를 호출하고 성공 뒤 목록·상세를 재조회한다. 마지막 active HOST는 UI에서 예방하고 Backend 409 정책을 최종 기준으로 유지한다.
- 회의 상태 control은 `SCHEDULED -> IN_PROGRESS/CANCELED`, `IN_PROGRESS -> ENDED`와 현재 상태만 제공한다. 제목·예정 일시는 `SCHEDULED`에서만 수정한다.
- 생성 응답의 joinCode/joinUrl은 생성 권한자에게 현재 Frontend 메모리에서만 표시하며 새 인증 성공과 프로젝트 삭제 시 제거한다.
- 캘린더는 이미 ACL-filtered된 target Space meeting 목록을 사용한다. 생성 권한, 초기 참여자, loading/error, 성공 후 목록·캘린더 동시 갱신과 meetingId route를 연결했다.

### Verification

- Passed: `cd frontend && npm run test`; 2 files, 11 tests
- Passed: `cd frontend && npm run lint`; 오류 0건, 기존 경고 8건
- Passed: `cd frontend && npm run build`; bundle size 경고 외 성공
- Passed: `PLAYWRIGHT_BACKEND_PORT=18083 npm run test:e2e`; 로그인, HOST prejoin/default-deny, 회의 생성→참가 코드 표시→수정→삭제, 캘린더 생성/이동, guest participant 상세→EDITOR 변경→REVOKED 회수, 총 4건
- Passed: `git diff --check`
- Recovered: 첫 격리 E2E는 Backend CORS 허용 범위 밖 Frontend port `15174` 때문에 실패했다. 사용자 서버가 없는 것을 확인하고 허용된 Frontend `5173`과 격리 Backend `18083` 조합으로 재실행했다.
- Recovered: guest 사용자를 workspace domain에 동기화하지 않은 초기 fixture와 상세 DTO의 participant id 필드 차이를 확인해 fixture와 participant 목록 조회 경계를 교정했다.

### Remaining Boundary

- `GET /api/v1/calendar/events` Backend runtime과 알림은 아직 없으며 캘린더는 Space별 ACL-filtered meeting 목록을 read model로 사용한다.
- FR-MREG-01 description과 FR-CAL-04 종료 일시는 현재 Meeting API/data model에 없어 임의 추가하지 않았다.
- 초기 참여자 선택은 조회 가능한 SpaceMember로 제한된다. 비멤버 guest 직접 검색/추가는 사용자 검색 또는 invitation 계약이 생긴 뒤 연결한다.
- Playwright에서 기존 `WorkspaceHomePage` list key React warning이 관찰됐지만 M034 변경과 무관하고 동작/검증 실패는 아니다.
- 공용 `.specify/memory/session-handoff.md`는 병합된 공통 기준만 기록하므로 아직 병합되지 않은 M034 상태를 추가하지 않았다.

## M038 Workspace JPA Migration

### T297-T298 Implementation

- Auth의 `AuthStore`와 `JdbcAuthStore`는 변경하지 않았다. non-auth Workspace와 Backend가 저장하는 AI artifact의 domain model 자체에 JPA mapping을 두고 `JpaWorkspacePersistence`가 이를 직접 영속화한다. Auth FK는 `user_id` scalar 값으로 유지했으며 `@ManyToOne<User>`와 cascade를 추가하지 않았다.
- `spring-boot-starter-data-jpa`를 추가하고 `open-in-view=false`, `ddl-auto=validate`, UTC JDBC time zone을 설정했다. schema 변경은 계속 Flyway만 담당한다.
- Space, Meeting/ACL, Transcript, Report, Task, ProjectKnowledge, AuditLog table의 domain entity mapping을 추가했다. 별도 `*Entity`와 domain record 간 변환은 제거했다. `embedding_jobs` worker와 `embedding_chunks.embedding vector(1536)` hybrid retrieval은 D-040에 따라 JPA mapping에서 제외하고 AI native SQL/JDBC 경계를 유지한다.

### Verification

- Passed after direct domain entity conversion: `cd backend && ./gradlew test`.
- Passed after direct domain entity conversion: `CI_POSTGRES_URL=jdbc:postgresql://localhost:5434/meetingmind_runtime_8080 CI_POSTGRES_USER=meetingmind CI_POSTGRES_PASSWORD=meetingmind_local ./gradlew test --tests com.meetingmind.demo.domain.JdbcWorkspaceStoreIntegrationTest --tests com.meetingmind.demo.domain.SttTranscriptFlowIntegrationTest`; JPA reload, join-code hash, ACL, transcript completion, segment persistence, and embedding-job trigger were verified.
- Passed: `cd backend && ./gradlew test` with the existing DataSource-free `test` profile.
- Passed: temporary empty PostgreSQL database on the local pgvector container. `db` profile applied Flyway V1~V12, initialized Hibernate `EntityManagerFactory` with validation, and returned `GET /api/workspace` on port 18084. The temporary database and server were removed after verification.
- Blocked (separate local integration issue): the existing `meetingmind` database has a V11 checksum mismatch (`applied -396214114`, current `-2043882333`), so its default `local` profile boot is correctly rejected by Flyway. No `repair` was run because migration history must not be rewritten without the integration owner's decision.

### Remaining Boundary

- Auth는 계속 JDBC `AuthStore`를 사용한다. `WorkspaceStore`의 Space, Meeting/ACL, transcript, report/task, knowledge, audit는 `JpaWorkspaceStore`와 `JpaWorkspacePersistence`로 전환했고, vector retrieval/worker SQL은 JPA 대상이 아니다.
- Cloud STT provider callback, target DB lifecycle, LiveKit Egress deployment E2E를 검증했다. OpenAI embedding provider의 실제 한국어 검색 품질과 외부 latency 평가는 별도 T301 잔여 작업이다.

### T299 Workspace JPA Persistence

- `JdbcWorkspaceStore`는 Auth와 JDBC round-trip 검증용 helper로 남기고 Spring bean에서는 제거했다. `local`/`db` profile은 `JpaWorkspaceStore`를 주입하며, scalar user FK와 Flyway schema owner 경계를 유지한다.
- `JpaWorkspacePersistence`는 Space, Meeting/ACL, join request, speaker, transcript segment, report/task, ProjectKnowledge, audit 도메인 엔티티를 직접 write/read한다. `MeetingTranscript`도 JPA entity로 저장한다. Report의 decision/action row만 별도 child entity로 유지하며 조회 시 `MeetingReport`에 조립한다.
- PostgreSQL integration에서 Space/Meeting/ACL, join code hash, transcript/report/task/knowledge/audit reload와 Project AI ACL 선필터를 검증했다.

### T300 STT Persistence Lifecycle

- target `POST /api/v1/meetings/{meetingId}/transcription/start`는 HOST 또는 Space manager 권한을 확인한 뒤 `MeetingTranscript=PROCESSING`을 저장하고 LiveKit egress STT 세션을 시작한다. `stop`은 해당 회의 세션만 종료하며 `GET /dialogue`는 현재 저장된 segment를 반환한다. legacy `/api/stt/*`는 호환용으로 유지했다.
- provider callback의 text는 target meeting일 때 `MeetingSpeaker`와 `TranscriptSegment`로 즉시 저장한다. target session에는 파일 기반 transcript 복사본을 남기지 않아 retention/ACL 경계를 우회하지 않는다.
- 같은 meeting의 다중 track callback은 meeting row lock 안에서 순번을 계산한다. 완료 전환은 `meeting_transcripts` trigger를 통해 `TRANSCRIPT_COMPLETED` embedding job을 하나 만든다. provider 오류는 원문을 저장하지 않고 `FAILED`와 일반화된 실패 사유만 기록한다.
- `SttStreamClientFactory`가 Clova client 생성만 담당하도록 분리했다. production은 `ClovaNestStreamClientFactory`를 사용하고, PostgreSQL integration test는 동일 registry callback을 호출하는 fake stream client를 주입해 provider network 없이 lifecycle을 검증한다.

### Verification

- Passed: `cd backend && ./gradlew test`.
- Passed: temporary PostgreSQL database on the local pgvector container with `JdbcWorkspaceStoreIntegrationTest`; Flyway V1~V12, JPA adapter reload, ACL negative case, `PROCESSING -> COMPLETED`, segment 2건 저장, `TRANSCRIPT_COMPLETED` embedding job 1건 생성을 검증했다. Temporary database was dropped after the test.
- Passed: 같은 Flyway V12 temporary database에서 `AI_TEST_DATABASE_URL=... python -m unittest tests.test_embedding_repository.PostgresEmbeddingRepositoryIntegrationTest`; deterministic 1536-dimension embedding worker가 transcript-completed job을 처리하고 최신 generation 교체, Meeting/Project scope, 빈 allowed meeting list, cross-space 차단, purge 후 검색 제외를 검증했다. Temporary database was dropped after the test.
- Passed: temporary PostgreSQL database에서 `SttTranscriptFlowIntegrationTest`; fake provider callback text 2건이 실제 JPA `MeetingSpeaker`/`TranscriptSegment`로 저장되고, session close 이후 dialogue `COMPLETED` 및 `TRANSCRIPT_COMPLETED` job 1건을 검증했다.
- Passed: 같은 Flyway V12 temporary database에서 `AI_TEST_DATABASE_URL=... python -m unittest discover -s tests`; 63 tests 통과. `test_stt_rag_performance`는 200개 STT segment -> worker -> Meeting scope retrieval 100회를 실행했고 deterministic provider 기준 local p95 `8.85 ms`를 기록했다.
- Passed: `CLOVA_SPEECH_SECRET`로 Cloud STT gRPC 설정 handshake와 실제 Korean PCM 전사를 확인했다. 48 kHz WebSocket 입력을 server resampler가 16 kHz PCM으로 전달한 legacy smoke에서 65개의 `transcription` callback이 반환됐다. provider `responseType=["config"]` ACK는 전사 text가 아니므로 저장하지 않고, 마지막 PCM frame을 `epFlag=true`로 전송한 뒤 stream 종료를 기다리도록 client를 보정했다.
- Passed: `RUN_CLOVA_STT_SMOKE=true` opt-in integration test에서 실제 16 kHz PCM을 Cloud STT client로 실시간 전송했다. 실제 callback이 JPA `TranscriptSegment`에 저장되고 target dialogue가 `COMPLETED`, `TRANSCRIPT_COMPLETED` embedding job이 정확히 1건인 것을 검증했다. 기본 `./gradlew test`에서는 비용과 secret 노출 방지를 위해 이 test가 skip된다.
- Passed: `git diff --check`.
- Passed: actual LiveKit egress smoke. valid LiveKit Cloud credential과 임시 ngrok `PUBLIC_WS_BASE_URL` 환경에서 Chromium client가 audio track을 publish했고, target start가 Egress를 생성한 뒤 Cloud STT callback transcript 60건을 저장했다. Egress SDK에는 signalling용 `ws(s)` URL이 아닌 API용 `http(s)` URL이 필요하므로 `LiveKitEgressService`에서 이를 정규화하고 unit test로 고정했다.
- Hardened: target Egress WebSocket 종료는 마지막 audio flush 후 `MeetingTranscript=COMPLETED`로 종결하고, legacy STT session은 수동 조회 호환을 위해 유지한다. Egress stop 실패는 `FAILED`로 종결한 뒤 `503 STT_PROVIDER_UNAVAILABLE`를 반환해 재시작을 막는 무한 `PROCESSING` 상태를 남기지 않는다. transcription 시작 시 `MEETING_TRANSCRIPTION_STARTED` audit event를 기록한다.
- Not run: OpenAI embedding provider의 실제 한국어 retrieval quality 및 p95 latency. `T275`의 API key 기반 평가와 성능 측정이 선행되어야 T301을 완료할 수 있다.

### T302 Live STT Target API Integration

- LiveRoom의 STT 시작, 종료, 자막 polling을 legacy `/api/stt/*`에서 인증·Meeting ACL이 적용되는 `/api/v1/meetings/{meetingId}/transcription/*`와 `/dialogue`로 전환했다.
- `GET /dialogue`는 `PROCESSING` 중에도 즉시 저장된 `TranscriptSegment`를 반환한다. 이는 실시간 자막 표시와 저장 모델을 같은 Meeting scope/ACL 경계에 둔다.
- legacy `/api/stt/*` controller는 기본 runtime에서 제외하고, 수동 smoke가 필요한 경우에만 `legacy-stt` profile을 명시적으로 활성화한다. target STT의 LiveKit Egress WebSocket 경로는 이 profile과 무관하게 유지한다.
- legacy `/api/livekit/token` controller도 기본 runtime에서 제외하고, request body를 신뢰하는 기존 token 발급을 확인해야 하는 경우에만 `legacy-livekit` profile을 명시적으로 활성화한다.
- 회의 상세 응답에서 영속화 후 복원할 수 없고 생성 시에만 전달해야 하는 `joinCode`/`roomCode`를 제거했다. 생성 응답의 `joinCode`/`joinUrl` 계약은 유지한다.
- Frontend는 제품 시간대 `Asia/Seoul`을 명시하고 중복된 `MeetingDetailResponse` 선언을 제거했다. 데이터 모델과 ERD의 관계/필드 변경은 없으므로 갱신하지 않았다.

### T302 Verification

- Passed: `cd backend && ./gradlew test`; `PROCESSING` transcript의 target dialogue 조회와 기존 STT lifecycle/controller 회귀를 포함한다.
- Passed: `cd backend && ./gradlew test --tests com.meetingmind.demo.MeetingMindApplicationTest`; 기본 context에는 legacy STT controller가 없고 `legacy-stt` 명시 profile에서만 등록됨을 검증했다.
- Passed: 같은 context test에서 legacy LiveKit controller도 기본 context에 없고 `legacy-livekit` 명시 profile에서만 등록됨을 검증했다.
- Passed: `cd frontend && TZ=UTC npm test -- --run`; 3 files, 15 tests. STT target API client의 Bearer auth와 시간대 독립 포맷을 검증했다.
- Passed: `cd frontend && npm run build`; bundle size warning 외 성공.
- Passed: `cd ai && python3 -m compileall app tests`, `git diff --check`, legacy frontend STT endpoint 및 `roomCode` 참조 검색.
