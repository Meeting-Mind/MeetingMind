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
- Kanban 상태는 `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `DONE`을 사용한다. 새 drag-and-drop dependency는 추가하지 않고 기존 React/DOM 이벤트나 명시 이동 버튼 중 작은 구현을 선택한다.
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
- 2026-07-21: 반복되는 테스트 fixture false positive를 막기 위해 `.gitleaks.toml`에 좁은 allowlist를 추가했다. 허용 범위는 BFF 테스트용 fake `AUTH_USER_ID` UUID와 고정 dummy Token Vault key fixture에 한정하며, 실제 `.env`/provider secret은 계속 차단한다.

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
- Passed: `.gitleaks.toml` fixture allowlist 적용 후 `gitleaks git . --redact --no-banner` 전체 이력 70 commits scan이 0건으로 통과했다.
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
- Frontend Meeting/Project AI는 `unsupportedReason`을 근거 부족/관련도 부족/근거 검증 실패로 표시하고, provider 오류는 기존 API error로 분리한다. 실제 provider 기반 정확도·성능·Backend-to-AI HTTP 검증 결과는 T275에 기록했다.
- M034 attachment RAG는 MeetingMessage/Attachment 계약과 Backend 저장이 완료된 뒤 진행한다.

### T275 Korean Grounded Provider Evaluation

- `RUN_OPENAI_GROUNDED_EVAL=true ./.venv/bin/python -m unittest tests.test_openai_grounded_evaluation`을 실제 OpenAI credential로 실행했다.
- 평가기는 `BackendMeetingAiChatRequest`의 internal handler에 단일 회의 source를 전달한다. 근거 있음 15건은 실제 인용 source ID를, 근거 없음 15건은 무관한 source를 사용해 DB fallback 없이 evidence gate와 structured citation을 함께 검증한다.
- 결과: false-supported `0%`, supported answer `100%`, citation 정확도 `100%`, provider-inclusive p95 `3,788.00 ms` (총 30건, 2026-07-21). 이 지연은 provider를 포함하며 SR-007의 PostgreSQL retrieval p95와 별도다.
- Backend의 `HttpMeetingAiGatewayClientTest`는 Core가 service token과 request ID를 포함해 `/api/internal/meeting-ai/chat`을 호출하고 응답을 역직렬화하는 local HTTP integration을 검증한다. BFF public Meeting/Project AI route는 Core downstream으로만 프록시된다.

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

### T274 Frontend Unsupported Reason

- Meeting AI와 Project AI 응답 타입은 nullable `unsupportedReason`을 보존한다. `NO_EVIDENCE`, `LOW_RELEVANCE`, `MODEL_UNSUPPORTED`, `UNVERIFIED_OUTPUT`은 정상 grounded 결과로 구분해 보여주며, HTTP provider 오류는 기존 error state로 유지한다.
- Passed: `cd frontend && npm run test -- --run && npm run build`, `git diff --check`.

## M035 Meeting Chat Text Attachment RAG Preparation

### Decision

- 회의 채팅 첨부파일은 MVP에서 텍스트 임베딩만 사용한다. TXT, Markdown, 텍스트 추출 가능한 PDF는 기존 `text-embedding-3-small` 1536차원 공간에 저장한다.
- 이미지 파일과 이미지 전용 PDF는 현재 검색 대상에서 제외한다. visual embedding, OCR/Vision 설명과 원본 기반 멀티모달 답변은 별도 확장 milestone에서 결정한다.
- 현재 실시간 회의 화면에는 영속 채팅/첨부파일 도메인과 업로드 API가 없다. AI extractor보다 MeetingMessage/Attachment 계약 및 Backend 저장이 선행되어야 한다.

### Document Impact

- Updated: `clarify.md`, `plan.md`, `contracts/ai-api.md`, `tasks.md`, shared handoff.
- Deferred: Attachment API, ERD, data model과 requirements 변경은 T278 shared contract에서 함께 처리한다.
- M034 impact: 기존 grounding과 pgvector 기반 구현은 그대로 진행할 수 있으며 이미지 vector 컬럼이나 모델은 V12에 추가하지 않는다.

### Verification

- Passed: 현재 LiveRoom, embedding chunk/job schema, 업로드 요구사항과 권한 문서의 선행 경계 비교.
- Passed: `git diff --check`, Q-011/D-039 반영 검색, T277-T284 task ID 중복 검사.
- Not run: code tests. 이번 변경은 첨부파일 검색 정책과 후속 task 준비만 포함한다.

### T278 Attachment Contract and Storage Domain

- M035 attachment storage/RAG 설계와 구현은 현재 전달 범위에서 제외했다. 이전 future draft는 재개 시점의 요구사항, 최신 migration version, storage provider 정책을 기준으로 다시 확정한다.
- Meeting ACL이 upload/list/download/RAG에 그대로 적용된다. 삭제·보존 만료·Meeting soft delete는 chunk를 먼저 inactive 처리하고 object cleanup은 retry 가능한 비동기로 남긴다. Project AI는 Backend가 전달한 `allowedMeetingIds` 안의 READY attachment만 검색한다.
- Updated: `requirements/{glossary,permissions,policies,status-values}.md`, `clarify.md` D-044/Q-012, API contract index/new attachment contract, AI contract, ERD/data model/test matrix/tasks.
- Verification: `git diff --check` pending after the full worktree verification. No production code, migration, or object storage credential was added; T279-T283 implement the contract and run AT-001~AT-005.

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
- Cloud STT provider callback, target DB lifecycle, LiveKit Egress deployment E2E와 OpenAI embedding/RAG provider smoke를 검증했다.
- AI API와 embedding worker는 process 환경변수, `ai/.env`, 루트 `.env`, `backend/.env` 우선순위로 같은 설정을 읽고 `OPEN_AI_KEY`를 `OPENAI_API_KEY` 별칭으로 처리한다. 실제 OpenAI embedding 호출은 `RUN_OPENAI_EMBEDDING_SMOKE=true`일 때만 수행하는 별도 smoke test로 분리했다.
- `RUN_OPENAI_RAG_INTEGRATION=true` opt-in test는 별도 migrated PostgreSQL에 한국어 STT fixture를 입력하고 실제 OpenAI worker로 색인한 뒤, vector 차원, Project allowed-meeting 범위, 빈 allowed/cross-space negative case, PostgreSQL hybrid retrieval p95를 확인한다. fixture는 검증 종료 시 삭제한다.
- Passed: `RUN_OPENAI_EMBEDDING_SMOKE=true`로 실제 OpenAI embedding 단건 호출이 통과했다.
- Passed: `RUN_OPENAI_RAG_INTEGRATION=true`로 전용 PostgreSQL 평가 DB에서 실제 `text-embedding-3-small` provider를 실행했다. 한국어 STT fixture가 worker를 통해 `vector(1536)`으로 저장되고, Project allowed meeting만 반환하며 빈 allowed list와 cross-space 검색이 차단됨을 확인했다. PostgreSQL hybrid retrieval 100회 p95는 `13.23 ms`였다.
- Passed: `RUN_OPENAI_GROUNDED_EVAL=true`로 한국어 grounded provider 평가가 통과했다. false-supported `0%`, supported answer `100%`, citation 정확도 `100%`, provider-inclusive p95 `3,788.00 ms`.
- Remaining: Backend-to-AI HTTP end-to-end에서 provider 호출을 포함한 전체 사용자 흐름 latency 평가는 별도 운영 smoke로 남긴다.

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
- Passed: 2026-07-21 `RUN_CLOVA_STT_SMOKE=true`로 macOS 한국어 TTS에서 만든 16 kHz PCM을 실제 CLOVA STT에 전송했고, `meetingmind_stt_smoke` 전용 DB에서 transcript `COMPLETED`, segment 저장, `TRANSCRIPT_COMPLETED` embedding job 1건을 재검증했다.
- Passed: `git diff --check`.
- Passed: actual LiveKit egress smoke. valid LiveKit Cloud credential과 임시 ngrok `PUBLIC_WS_BASE_URL` 환경에서 Chromium client가 audio track을 publish했고, target start가 Egress를 생성한 뒤 Cloud STT callback transcript 60건을 저장했다. Egress SDK에는 signalling용 `ws(s)` URL이 아닌 API용 `http(s)` URL이 필요하므로 `LiveKitEgressService`에서 이를 정규화하고 unit test로 고정했다.
- Passed: 2026-07-21 실제 LiveKit Cloud credential로 RoomService `ListRooms` smoke가 HTTP 200을 반환했다. participant token은 출력하지 않고 요청 메모리에서만 생성했다.
- Hardened: target Egress WebSocket 종료는 마지막 audio flush 후 `MeetingTranscript=COMPLETED`로 종결하고, legacy STT session은 수동 조회 호환을 위해 유지한다. Egress stop 실패는 `FAILED`로 종결한 뒤 `503 STT_PROVIDER_UNAVAILABLE`를 반환해 재시작을 막는 무한 `PROCESSING` 상태를 남기지 않는다. transcription 시작 시 `MEETING_TRANSCRIPTION_STARTED` audit event를 기록한다.
- Not run: Browser에서 LiveKit 입장부터 STT 종료, Meeting AI 질문까지 이어지는 전체 사용자 흐름 latency 측정은 별도 운영 smoke로 남긴다.

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
- Passed: `cd frontend && TZ=UTC npm test -- --run`; STT target API client, 시간대 독립 포맷과 CI의 축소 ICU locale에서도 일관된 한국어 오전/오후 표기를 검증했다.
- Passed: `cd frontend && npm run build`; bundle size warning 외 성공.
- Passed: `cd ai && python3 -m compileall app tests`, `git diff --check`, legacy frontend STT endpoint 및 `roomCode` 참조 검색.

## BFF/Auth Architecture Evolution Reference

## M042 Report History Safety and Term E2E

### Changes

- 회의록 이력의 선택 상태를 편집/저장 대상 `currentReportId`와 분리했다. 이력 항목은 Markdown 다운로드 대상으로만 선택하며, 선택으로 로컬 보고서 본문이나 저장 대상이 바뀌지 않는다.
- meetingId가 바뀌면 candidate, 이력, 선택 report 상태를 함께 초기화하고, 새 회의의 candidate 조회 결과가 비어 있으면 이전 candidate를 명시적으로 제거한다.
- Playwright에서 로그인된 Browser가 BFF를 거쳐 등록 DomainTerm 설명 endpoint를 호출하고 `local-glossary` 응답을 받는 경로를 추가했다. 등록어 경로는 AI gateway가 없어도 성공해야 한다.

### Verification

- Passed: `cd frontend && npm run test`; 4 files, 23 tests.
- Passed: `cd frontend && npm run build`; 기존 500 kB 초과 bundle warning만 유지됐다.
- Passed: `PLAYWRIGHT_BACKEND_PORT=18086 PLAYWRIGHT_BFF_PORT=18087 PLAYWRIGHT_FRONTEND_PORT=5174 npm run test:e2e -- --grep 'calendar and domain dictionary mutations'`; 등록어 수정 후 Browser가 BFF -> Core 용어 설명 endpoint에서 `local-glossary` 응답을 받았다.
- Passed: `git diff --check`.

## M039 Workspace Artifact CRUD API

- 2026-07-20: Space 상세 조회, 수정/삭제, Space invitation, 일반 TaskCard CRUD, report 목록/수정/이력/Markdown download를 Core API와 BFF allowlist에 연결했다. Space 상세는 active SpaceMember와 Meeting ACL로 upcoming meeting, confirmed report, open TaskCard를 선필터한다. Space 삭제와 TaskCard 삭제는 soft delete이며, 진행 중 회의가 있는 Space 삭제는 거부한다. invitation token 원문은 SHA-256 hash만 저장하고 생성 응답에서 한 번만 반환하며, 초대 이메일과 인증 이메일이 일치해야 수락할 수 있다. report 수동 수정은 기존 version을 변경하지 않고 새 `DRAFT` version을 만든다.
- 2026-07-20: PDF/DOCX export는 생성 방식을 아직 결정하지 않아 `501 REPORT_EXPORT_NOT_SUPPORTED`로 명시했고, 현재는 UTF-8 Markdown attachment만 지원한다. ReportAgent는 다운로드 Blob과 선택한 format으로 파일명을 만들며, ProjectOverview의 project/task mutation, TeamMembers의 이메일 초대 생성, ReportAgent의 report save/download는 target Space에서 Backend response를 사용한다. mock fallback Space는 기존 local UX를 유지한다.
- 2026-07-20: 리뷰 보완으로 `V15__add_space_updated_at.sql`을 추가해 Space PATCH 응답의 `updatedAt`을 영속화했다. PostgreSQL migration integration test는 V13~V15 적용과 `task_cards.deleted_at`, `spaces.updated_at`을 검증한다. Verification: `cd backend && ./gradlew test`, `cd bff && ./gradlew test`, `cd frontend && npm run build`, `git diff --check` 통과.
- 2026-07-20: Browser Space invitation 수락/거절 화면을 추가했다. 초대 생성은 `/space-invitations/{spaceId}/{invitationId}#token={token}` 링크를 복사하며, token은 로그인 복귀 경로에도 fragment로만 보존된다. 수락 화면은 fragment를 읽은 뒤 주소창에서 즉시 제거하고 authenticated accept/decline request body로만 전송한다. Backend API/schema 변경은 없다.
- 2026-07-20: Space TaskCard 조회는 카드의 Space scope를 유지하되, 연결된 Meeting을 읽을 수 없는 사용자의 `meetingId`와 `sourceCandidateId`를 `null`로 마스킹한다. 이를 통해 meeting source metadata가 Space 권한만으로 노출되지 않도록 했다.
- 2026-07-20: `SpaceInvitation`도 Workspace JPA 경계로 전환했다. `local`/`db` profile의 `JpaWorkspaceStore`는 invitation 생성·조회·상태 변경을 `JpaWorkspacePersistence`로 직접 처리하며 더 이상 JDBC delegate에 위임하지 않는다.
- 2026-07-20: TeamMembers의 role 변경, 멤버 제거, owner transfer는 target Space에서 기존 BFF API를 호출한 뒤 `GET /spaces/{spaceId}/members`로 목록을 재조회한다. API 실패 시 target Space의 local member state를 변경하지 않으며 데모 Space만 기존 local UX를 유지한다.
- 2026-07-20: PostgreSQL/JPA integration test는 invitation 수락 뒤 `SpaceInvitation=ACCEPTED`와 `SpaceMember` 생성을, Task 삭제 뒤 `deletedAt`과 active 조회 제외를, report 수정 뒤 새 `DRAFT` version을 확인한다. BFF proxy test는 Task DELETE route가 Core downstream으로 전달되는 것을 확인한다.
- Verification: `cd backend && ./gradlew test`, `cd bff && BFF_REDIS_INTEGRATION=true BFF_REDIS_PORT=6380 ./gradlew test`, `cd frontend && npm run test -- --run && npm run build`, `git diff --check`.
- 2026-07-20: Frontend API client는 Space invitation/member role/Task create-update-delete/report PATCH를 same-origin BFF cookie와 CSRF token으로만 보내고 Browser Authorization header를 만들지 않는 것을 unit test로 고정했다. Playwright는 실제 Browser→BFF→Core에서 invitation 생성, member role 변경, Task 생성/삭제 성공을 확인했고, report PATCH allowlist는 인증된 BFF 세션에서 Core `404 MEETING_NOT_FOUND`까지 전달됨을 확인했다. 실제 report 수정 성공은 transcript와 AI candidate fixture가 필요한 별도 AT/report lifecycle 통합 환경에서 검증한다.

## M040 Calendar and Domain Dictionary Target Completion

- 2026-07-20: `GET /api/v1/calendar/events`를 Core와 BFF allowlist에 추가했다. 요청은 필수 ISO-8601 `from`/`to`와 선택 `spaceId`를 받고, Space membership 확인 뒤 각 Meeting의 ACL을 다시 적용한다. 종료 시각은 Meeting의 `scheduledEndAt`을 반환하며 실제 회의 종료 `endedAt`과 구분한다. Frontend calendar는 현재 월/주/일 표시 범위와 선택 Space로 API를 조회하며, 일정 생성 성공 뒤 같은 범위를 다시 읽는다.
- 2026-07-20: Space-scoped DomainTerm CRUD를 Core JPA/in-memory store profile, BFF allowlist, `/terms` 관리 화면에 연결했다. 조회는 Space member, 등록·수정·archive/restore는 OWNER/ADMIN으로 제한한다. 활성 용어는 trim 및 대소문자 무시 unique 제약을 사용하고, 변경은 `DOMAIN_TERM_CHANGED` audit event로 남긴다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.CalendarServiceTest --tests com.meetingmind.demo.domain.DomainTermServiceTest --tests com.meetingmind.demo.controller.SpaceControllerTest`, `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`, `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build` passed. Frontend build has the existing >500 kB chunk-size warning only.
- 2026-07-20: 격리된 Playwright runtime(`Backend 18086`, `BFF 18087`, `Frontend 5174`)에서 owner가 DomainTerm을 생성·수정하고 duplicate `409` 오류를 확인했다. 같은 Browser session이 `GET /api/v1/calendar/events`를 BFF 경유로 조회하고 생성된 meeting을 캘린더에 표시하는 것을 검증했다.

## M041 Meeting Term Explanation Integration

- 2026-07-20: `POST /api/v1/meetings/{meetingId}/terms/explain`을 추가했다. Backend는 인증과 Meeting ACL을 먼저 검증한 뒤 현재 meeting의 Space에서 active DomainTerm을 exact case-insensitive lookup한다. 등록어는 `local-glossary` source를 즉시 반환해 AI gateway/LLM을 호출하지 않는다.
- 2026-07-20: 미등록어는 내부 `POST /api/internal/meeting-ai/explain-term`에 Space/Meeting scope와 term만 전달한다. AI는 해당 single meeting의 PostgreSQL RAG `transcript`/`decision` source에서 evidence gate를 통과할 때만 provider를 호출하며, 근거가 없으면 `unsupported=true`로 끝낸다.
- 2026-07-20: LiveRoom의 하드코딩 STT keyword 목록을 제거했다. 자막에서 120자 이하 텍스트를 선택하면 용어 설명 패널이 source 또는 unsupported 상태를 표시한다. 응답 경쟁 상태는 request id로 차단한다.
- 2026-07-20: ReportAgent는 candidate와 공식 report history를 별도로 조회해 candidate 확정 뒤 version/current 목록을 갱신하고, 선택한 version을 Markdown으로 다운로드한다. 태스크 후보는 title/description/assignee/dueDate 검토 뒤 TaskCard confirm API로 등록하며, 성공 후 해당 Space 칸반으로 이동할 수 있다.
- Verification: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai.ExplainTermTest && ./.venv/bin/python -m compileall app tests`; `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.WorkspaceCrudServiceTest --tests com.meetingmind.demo.domain.MeetingTermExplanationServiceTest --tests com.meetingmind.demo.service.HttpMeetingAiGatewayClientTest`; `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `PLAYWRIGHT_BACKEND_PORT=18086 PLAYWRIGHT_BFF_PORT=18087 PLAYWRIGHT_FRONTEND_PORT=5174 npm run test:e2e -- --grep 'calendar and domain dictionary mutations'`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M043 Task Candidate Dismissal

- 2026-07-20: `POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/dismiss`를 추가했다. active SpaceMember이면서 해당 회의 편집 권한이 있는 사용자만 같은 meeting의 `CANDIDATE`를 `DISMISSED`로 전이할 수 있다. 후보와 sourceIds는 보존하며 확정 또는 기존 제외 후보는 다시 전이할 수 없다.
- Core는 `TASK_CANDIDATE_DISMISSED` audit event를 남기고, BFF route allowlist와 ReportAgent의 `등록 제외` action을 연결했다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.TaskCandidateServiceTest`; `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M044 Project Knowledge Management

- 2026-07-20: 기존 `ProjectKnowledge` JPA 모델과 pgvector 재색인 trigger를 재사용해 Space-scoped 목록, 상세, 등록, 수정, archive API를 추가했다. 목록은 `PUBLISHED`와 `deletedAt=null`만 반환하고 title/content keyword 및 type filter를 지원한다. source meeting metadata는 별도 Meeting ACL을 통과한 사용자에게만 보인다.
- OWNER/ADMIN만 등록·수정·archive할 수 있다. 등록과 수정은 `PENDING` embedding 상태로 전환되어 PostgreSQL trigger가 비동기 job을 생성하며, archive는 이력을 보존하고 Project AI 후보에서 제거한다. Frontend는 목록 preview만으로 수정하지 않고 detail endpoint로 원문을 읽은 후 저장한다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.WorkspaceDomainServiceTest`; `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M045 Report Version Preview and Restore

- 2026-07-20: report version 상세 조회와 restore endpoint를 추가했다. restore는 과거 행을 수정하지 않고 해당 title, summary, markdown, decision, action item을 복사한 새 `DRAFT` version을 만든다. 기존 current confirmed report는 유지한다.
- ReportAgent는 version을 선택하면 상세 Markdown을 별도 preview로 읽고, 현재 편집 대상이 아닌 과거 version만 새 초안으로 복원할 수 있다. history 선택은 현재 editor state를 바꾸지 않는다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.WorkspaceCrudServiceTest`; `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M046 Candidate TTL Enforcement

- 2026-07-20: Q-008/Q-009를 생성 시각 기준 7일로 결정했다. 만료된 MeetingReport `CANDIDATE`는 확정하거나 그 후보에서 새 draft를 만들 수 없고, 만료된 TaskCandidate는 TaskCard로 확정할 수 없다. 두 경우 모두 `409 CANDIDATE_EXPIRED`를 반환한다.
- 별도 `EXPIRED` status나 물리 삭제는 추가하지 않았다. 후보, 근거 source, 생성 이력은 보존하며 태스크 후보 제외는 만료 후에도 허용한다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.TaskCandidateServiceTest --tests com.meetingmind.demo.domain.WorkspaceCrudServiceTest`; `git diff --check` passed.

## M047 Dashboard Summary Target Integration

- 2026-07-20: `GET /api/v1/dashboard`를 추가했다. 사용자 Space membership을 먼저 확인하고, 각 Space의 Meeting ACL을 통과한 회의만 오늘(`Asia/Seoul`) 집계와 회의록 활동에 사용한다. 미완료 TaskCard는 Space scope로 집계하되 연결 Meeting을 읽을 수 없으면 source 식별자를 마스킹한다.
- 최근 활동은 현재 영속 read model에서 신뢰할 수 있는 Space 변경, Task 변경, 읽을 수 있는 회의록 생성 시각으로만 구성한다. 모든 audit event를 조회하는 별도 read model은 추가하지 않았다.
- BFF allowlist와 Frontend 대시보드 홈을 연결했다. target summary가 실패할 때는 기존 홈 mock/legacy summary를 유지하며, target API 부분 실패로 표시한다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.WorkspaceDomainServiceTest`; `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M048 Project AI Personal Conversation History

- 2026-07-20: `ProjectAiMessage`와 V16 migration으로 Project AI 대화를 Space+사용자 단위로 저장한다. 성공한 chat의 USER/ASSISTANT 쌍만 저장하며 `GET /api/v1/spaces/{spaceId}/ai/history`는 active SpaceMember 자신의 최신 50개만 시간순으로 반환한다.
- 다음 Project AI 요청은 최근 10개 turn을 비신뢰 대화 문맥으로만 AI에 전달한다. Backend는 요청마다 현재 Space/Meeting 접근 범위를 다시 계산하고, AI는 이력을 source/citation 또는 권한 판단 근거로 쓰지 않도록 strict prompt를 적용한다.
- BFF Core allowlist와 ProjectOverview 초기 이력 조회를 연결했다. history read 실패는 새 채팅을 막지 않으며, gateway/provider 실패는 이력을 저장하지 않는다.
- Verification: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai && ./.venv/bin/python -m compileall app tests`; `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.ProjectAiServiceTest --tests com.meetingmind.demo.MigrationIntegrationTest`; `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M049 Report AI Conversation Edit

- 2026-07-20: `POST /api/v1/meetings/{meetingId}/reports/{reportId}/ai-edits`를 추가했다. 편집 권한을 먼저 확인한 뒤 대상 report가 현재 meeting에 속하는지 검증하고, 기존 report를 수정하지 않는 새 `CANDIDATE` version을 생성한다. 본문이 없는 기존 report는 summary를 비신뢰 편집 문맥으로 사용한다.
- Backend는 현재 meeting transcript와 confirmed report의 decision/action만 AI source로 조립한다. AI는 `instruction`과 기존 report 본문을 비신뢰 문맥으로 분리하며, 새 사실과 citation은 이번 single-meeting source에서만 검증한다. 근거 또는 검증된 citation이 없으면 candidate를 저장하지 않는다.
- BFF Core allowlist와 ReportAgent 대화 입력을 연결했다. Frontend API client는 same-origin cookie와 CSRF token만 사용하며 Browser Authorization header를 보내지 않는다.
- Verification: `cd backend && ./gradlew cleanTest test --tests com.meetingmind.demo.domain.ReportCandidateServiceTest`; `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai && ./.venv/bin/python -m compileall app tests`; `cd bff && ./gradlew cleanTest test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M050 Kanban Drag State Transition

- 2026-07-20: ProjectOverview의 TaskCard를 browser native drag-and-drop으로 상태 컬럼 사이에 이동할 수 있게 했다. drop은 새 상태일 때만 기존 `onMoveProjectTask` callback을 호출하므로, target Space에서는 기존 BFF/Core `PATCH /spaces/{spaceId}/tasks/{taskId}` 권한·입력 검증 경로를 그대로 사용한다.
- 기존 상태 select를 제거하지 않아 pointer를 사용할 수 없는 사용자는 동일한 상태 변경을 계속 수행할 수 있다. Task 편집 중인 카드는 draggable을 끈다.
- Verification: `cd frontend && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M051 Kanban Labels and Priority

- 2026-07-20: 일반 TaskCard에 `LOW`, `MEDIUM`, `HIGH` 우선순위와 문자열 라벨 목록을 추가했다. 기존 카드와 입력 생략 시 `MEDIUM`/빈 목록을 사용한다.
- 2026-07-20: `V17__add_task_card_priority_labels.sql`은 `task_cards.priority`와 PostgreSQL `text[] labels`를 추가한다. JPA와 JDBC store 모두 우선순위·라벨을 저장하고 조회하며, create/update 입력은 최대 10개, trim 후 1~40자, 대소문자 무시 중복 불가를 검증한다.
- 2026-07-20: GET/POST/PATCH TaskCard 계약과 ProjectOverview 편집 화면을 갱신했다. 기존 native drag-and-drop은 status만 변경하고, priority와 labels는 카드 편집에서 수정한다.
- Verification: `cd backend && ./gradlew cleanTest test --tests com.meetingmind.demo.domain.WorkspaceCrudServiceTest`; `CI_POSTGRES_URL=jdbc:postgresql://localhost:5434/meetingmind_v17_verify CI_POSTGRES_USER=meetingmind CI_POSTGRES_PASSWORD=meetingmind_local ./gradlew cleanTest test --tests com.meetingmind.demo.MigrationIntegrationTest --tests com.meetingmind.demo.domain.JdbcWorkspaceStoreIntegrationTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M052 Meeting Schedule Details

- 2026-07-20: `Meeting`에 nullable `description`과 필수 `scheduledEndAt`을 추가했다. `scheduledEndAt`은 실제 회의 종료 시각인 `endedAt`과 별도 필드로 유지하며, V18 migration은 기존 행에 시작 시각 + 1시간을 backfill하고 DB check constraint로 종료 시각이 시작 이후임을 보장한다.
- 생성·수정 API, 목록·상세·calendar 응답, JPA/JDBC/in-memory store와 ProjectOverview 생성·수정 폼을 같은 계약으로 갱신했다. 서버는 시작/종료 범위를 검증하고, 기존 내부 생성·수정 호출은 호환성을 위해 1시간 기본 종료 시각을 사용한다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.controller.SpaceControllerTest --tests com.meetingmind.demo.controller.MeetingControllerTest --tests com.meetingmind.demo.domain.CalendarServiceTest --tests com.meetingmind.demo.domain.WorkspaceDomainServiceTest`; `CI_POSTGRES_URL=jdbc:postgresql://localhost:5434/meetingmind_v18_verify CI_POSTGRES_USER=meetingmind CI_POSTGRES_PASSWORD=meetingmind_local ./gradlew cleanTest test --tests com.meetingmind.demo.MigrationIntegrationTest --tests com.meetingmind.demo.domain.JdbcWorkspaceStoreIntegrationTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build`; `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M053 Report DOCX Export

- 2026-07-20: 기존 report download ACL과 BFF route를 재사용해 `format=docx`를 지원했다. DOCX는 서버가 보유한 report title과 Markdown(없으면 summary) 텍스트만 생성하며, Browser는 same-origin BFF cookie 경로로 Blob을 내려받는다.
- Apache POI `poi-ooxml`은 DOCX 생성에만 사용했다. 당시 PDF는 배포 가능한 한글 폰트 번들·라이선스와 렌더링 전략이 없어 `501 REPORT_EXPORT_NOT_SUPPORTED`을 반환했으며, 이후 M055에서 OFL 글꼴을 embed하는 방식으로 구현했다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.controller.MeetingReportControllerTest --tests com.meetingmind.demo.controller.MeetingControllerTest --tests com.meetingmind.demo.domain.CalendarServiceTest --tests com.meetingmind.demo.domain.WorkspaceDomainServiceTest`; `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M054 In-app Meeting Reminders

- 2026-07-20: WorkspaceHome의 상단 알림 버튼이 기존 `GET /api/v1/calendar/events`를 향후 24시간 범위로 별도 조회해 `SCHEDULED` 회의 최대 5개를 표시한다. 새 notification 저장소나 권한 우회는 만들지 않았고, API가 적용하는 Space membership과 Meeting ACL 결과만 사용한다.
- 알림 조회 실패는 화면의 캘린더·대시보드 흐름을 막지 않으며 빈 알림 상태로 처리한다. push/email 같은 비동기 외부 발송은 delivery provider, 수신 동의, 재시도·중복 방지 정책이 필요한 별도 범위다.
- Verification: `cd frontend && npm run test -- --run src/api/workspace.test.ts && npm run build` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M055 Report PDF Export

- 2026-07-20: `format=pdf`를 기존 Meeting ACL download endpoint와 BFF route에 추가했다. PDFBox가 `NanumGothic-Regular.ttf`를 PDF에 embed하므로 운영 이미지와 개발 시스템 글꼴에 의존하지 않는다. 글꼴 원본과 SIL Open Font License 1.1 전문은 `backend/src/main/resources/fonts/`에 포함한다.
- Frontend Report Agent에서 같은 cookie-authenticated Blob 경로로 PDF 다운로드를 제공하며, Markdown은 `.md`, DOCX/PDF는 대응 확장자로 저장한다.
- Verification: `MeetingReportControllerTest`가 PDF magic header, attachment filename, 한글 제목과 본문의 PDF text extraction을 확인한다. `frontend/src/api/workspace.test.ts`가 PDF BFF route 요청을 확인한다.

## M056 Dashboard Latest Reports

- 2026-07-20: `GET /api/v1/dashboard`에 `latestReports`를 추가했다. 사용자에게 현재 접근 가능한 meeting만 먼저 계산한 뒤, 각 meeting의 current `CONFIRMED` report만 확정 시각 내림차순 최대 5건으로 집계한다. candidate, draft, 이전 version은 대시보드 최신 보고서에 포함하지 않는다.
- WorkspaceHome은 최신 확정 회의록의 제목, 원본 회의, version, 확정일을 표시하고 해당 Report Agent로 이동한다. target summary가 없을 때는 빈 상태를 표시하며 mock 보고서를 최신 확정본으로 위장하지 않는다.
- Verification: `WorkspaceDomainServiceTest`는 private meeting report의 owner-only 노출을, `DashboardControllerTest`는 API field mapping을 검증한다.

## M057 Landing Flow Alignment

- 2026-07-21: LandingPage를 실제 MeetingMind 사용자 흐름으로 교체했다. 첫 화면은 회의 생성 또는 참가, 자막과 회의록 검토, Space 지식과 태스크 활용의 3단계만 설명한다.
- 제품 미리보기는 회의, 자막, 확정 회의록, 태스크, Project AI 출처를 읽기 전용 흐름 카드로 보여준다. 데스크톱에서는 각 카드 열이 느리게 순환하고 hover 시 멈추며, 모바일과 reduced-motion 설정에서는 정적으로 표시한다. 예시 데이터가 실제 결과가 아님을 명시했고, 가짜 후기와 legacy 화면 링크를 제거했다.
- 직접 이동 CTA는 `/spaces`와 `/meeting-access`만 사용한다. Meeting AI와 Project AI의 검색 범위, 회의별 접근 제어, 출처 기반 답변, 보관 정책을 화면에 반영했다.
- 권한·출처·보관 정책 카드는 공용 `FlippingCard`로 앞면 요약과 뒷면 세부 기준을 제공한다. hover/focus는 미리보기, click은 고정 토글이며 keyboard의 Enter/Space와 `aria-pressed`를 지원한다.
- 제품 흐름 미리보기의 상태 라벨에는 공용 `PrismRevealText`를 적용했다. 초기 표시 순간에만 파랑 계열이 텍스트를 지나가며, reduced-motion 설정에서는 즉시 정적 텍스트로 표시한다.
- 비교 검토용 `/opening-preview`를 추가했다. 기존 `/` LandingPage는 변경하지 않았으며, 첫 화면은 `MeetingMind` 제품명·가치 문구와 회의 대화가 프로젝트 지식으로 축적되는 생성 이미지로 구성한다. 아래 스크롤에는 회의-보고서-프로젝트 지식 흐름, 기록이 정리된 지식으로 이어지는 생성 이미지, Meeting AI/Project AI의 접근 범위와 출처 기준을 제공한다. 가짜 제품 화면과 스크롤 안내 문구는 제거했다.
- 첫 화면 제품명에는 `motion/react` 기반 `MorphingText`를 적용했다. 각 글자는 blur·scale·수직 이동으로 들어오고, 값 변경 시 같은 위치의 글자가 `layoutId`로 이어진다. 컨테이너는 전체 텍스트의 `aria-label`을 제공하고 각 글자는 보조 기술에서 숨긴다. reduced-motion에서는 애니메이션을 생략한다.
- 제품명 아래 문구는 `회의를 기록합니다.`, `결정을 정리합니다.`, `태스크를 연결합니다.`, `프로젝트 지식으로 남깁니다.`를 3초마다 순환한다. reduced-motion에서는 첫 문구를 고정한다.
- 랜딩(`/`)과 비교 검토용 오프닝(`/opening-preview`)의 색상은 파랑·흰색 계열로 통일했다. route, CTA, 앵커, 제품 흐름 문구는 유지하고 버튼 색 대비와 8px 작업 표면 반경을 함께 정리했다.
- Verification: `cd frontend && npm run build && npm run lint`, desktop/mobile 수동 화면 검토, `git diff --check` passed. Vite의 기존 500 kB chunk-size warning과 기존 lint warning 7건 외 새 오류는 없다.

## M058 Frontend Product Architecture Design

- 2026-07-21: 전면 프론트 리팩토링 구현 전 설계 기준선을 `frontend-refactor-plan.md`에 추가했다. 현재 `App.tsx` route 구조, `ProjectOverviewPage.tsx`와 `ReportAgentPage.tsx`의 과밀 책임, `WorkspaceSidebar.tsx`의 legacy query navigation, `types.ts`의 target status enum, `requirements/permissions.md`, `requirements/status-values.md`를 기준으로 작성했다.
- 추가 범위는 State Model, Navigation Architecture, Interaction Guideline, Permission UX, Information Hierarchy, Design Token Plan, AppShell Architecture, Component Architecture, Refactoring Priority, Implementation Guardrails다.
- 현재 frontend는 React Router, `motion`, vanilla CSS 기반이며 Tailwind와 shadcn/ui는 설치되어 있지 않다. 따라서 design token은 Tailwind/shadcn으로 이식 가능한 목표값으로만 정의했고, 실제 dependency 도입은 후속 task에서 별도 결정하도록 남겼다.
- UI 구현, CSS 수정, Tailwind 설정 변경, 컴포넌트 수정은 하지 않았다.
- Verification: 현재 route/type/page 구조와 기존 `frontend-refactor-plan.md` target route를 대조했고, `git diff --check` passed.

## M059 Frontend Common State Components

- 2026-07-21: Refactoring Priority 1번만 구현했다. `DataState`, `ConfirmDialog`, `StatusBadge`, `RoleBadge`와 barrel export를 `frontend/src/components/common/`에 추가했다.
- `DataState`는 loading, empty, error, forbidden, not-found, conflict, session-expired를 구분한다. 기본 문구는 `frontend-refactor-plan.md`의 화면 상태와 오류 흐름을 따른다.
- `ConfirmDialog`는 destructive 또는 권한 영향 action에 재사용할 수 있도록 default/danger variant, loading lock, Escape close, dialog aria 속성을 제공한다.
- `StatusBadge`는 현재 `types.ts`의 Meeting, Transcript, Report, Task, Invitation, JoinRequest, Participant, DomainTerm status와 문서 상태 모델에 나온 published/archive/attachment 계열 상태를 표시한다.
- `RoleBadge`는 Space role과 Meeting role을 합치지 않고 별도 badge로 표시한다. `REVOKED` access status는 회수 상태로 분리한다.
- 기존 화면 연결, route 변경, API 변경, 인증 변경, 비즈니스 로직 이동은 하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only.

## M060 Frontend AppShell Foundation

- 2026-07-21: Refactoring Priority 2번 기준으로 `frontend/src/components/layout/`에 `ProtectedRoute`, `AppShell`, layout export와 CSS를 추가했다. `ProtectedRoute`는 `App.tsx` inline 함수에서 그대로 분리했고, 로그인 요청과 원래 주소 복귀 동작은 유지했다.
- `AppShell`은 sidebar, topbar, content slot만 가진 최소 구조다. 새 상태관리나 route 변경 없이 `/spaces`의 기존 `WorkspaceSidebar`, topbar, 본문 section을 감싸는 공통 골격으로 사용했다.
- `WorkspaceHomePage`는 기존 비즈니스 로직, API 호출, 회의 생성 흐름, 알림 panel 동작을 유지한 채 바깥 wrapper만 `AppShell`로 교체했다. 이 단계에서는 TeamMembers, Terms, ProjectOverview 같은 legacy page는 옮기지 않았다.
- `/common-components-preview`도 같은 `AppShell` 위로 옮겼다. 좌측 섹션 nav, 상단 breadcrumb와 진입 action을 추가해 공통 컴포넌트 검수 화면이 실제 앱 탐색 패턴을 따르도록 맞췄다.
- `common.css`의 버튼/다이얼로그 인터랙션은 `emil-design-eng` 기준에 맞춰 조정했다. hover는 fine pointer에서만 작동하고, active scale은 `0.97`, dialog enter는 `scale(0.96)` + opacity로 시작한다.
- 비즈니스 로직, API 계약, 인증 구조, 기존 주요 route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Frontend build has the existing >500 kB chunk-size warning only, lint has the existing 7 warnings only.

## M061 AppShell Visual Foundation

- 2026-07-21: 새로 설치한 `design-taste-frontend`와 `redesign-existing-projects` 기준을 현재 vanilla CSS/React 구조에 적용해 `/spaces`의 AppShell 작업 표면을 점검했다. 제품 기준은 `frontend-refactor-plan.md`의 AppShell slot, blue/white token, current location과 next action 표시 원칙을 따랐다.
- `frontend/src/components/layout/AppShell.tsx`를 sidebar/content slot만 가진 공통 레이아웃으로 추가하고, `frontend/src/components/layout/ProtectedRoute.tsx`로 인증 보호 경계를 분리했다. `WorkspaceHomePage`의 기존 sidebar와 본문은 해당 셸에 연결했다. API 호출, 인증 판정, 권한, route, 상태 소유 로직은 변경하지 않았다.
- AppShell 기반 화면의 전역 token을 blue/white 작업 표면으로 통일하고, 기존 `0.75` 전역 transform scale을 `1`로 되돌렸다. `body.app-theme`와 document overflow는 브라우저 기본 반응형 흐름을 사용하도록 평면 배경·자동 스크롤로 정리했다. 이는 레이아웃 표시 규칙만 바꾸며 API, 인증, 권한, route, 데이터 구조는 변경하지 않는다.
- `/spaces`에 한해 248px sidebar, 독립 content scroll, 8px 작업 표면, blue/white token, keyboard focus, hover/active feedback, reduced-motion 규칙을 적용했다. 기존 Sidebar 메뉴와 프로젝트 생성 modal의 동작은 유지했다.
- 이번 단계에서는 TeamMembers, Terms, ProjectOverview, Meeting 화면을 새 셸로 옮기지 않았다. 다음 Sidebar 단계에서 메뉴 정보 구조와 role/context 표시를 별도로 정리한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. 기존 500 kB chunk-size warning과 기존 lint warning 7건 외 새 오류는 없다.

## M062 Workspace Sidebar Navigation

- 2026-07-21: `WorkspaceSidebar`의 기존 query href, active/disabled 메뉴, 프로젝트 생성 callback과 modal 동작을 유지한 채 Sidebar 정보를 워크스페이스 생성, 탐색, 현재 작업공간, 권한 범위 안내로 나눴다.
- 실제 구독 상태를 조회하지 않는 `구독이 곧 만료됩니다` 문구는 제거했다. 대신 현재 project/회의 지식이 접근 가능한 권한 범위 안에서 표시된다는 안내를 둬서 사용자의 위치와 데이터 범위를 설명한다. 새 route, API, 권한 판정은 추가하지 않았다.
- `/spaces`의 AppShell에서는 blue/white 작업 표면과 8px radius, keyboard focus, reduced-motion 규칙을 사용한다. 작은 화면에서는 프로젝트 생성·현재 작업공간 보조 정보는 접고 핵심 탐색만 유지한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. 기존 Vite chunk-size warning과 lint warning 7건 외 새 오류는 없다.

## M063 Spaces Workspace Home

- 2026-07-21: `/spaces`의 기존 API-driven 상태와 callback을 유지하면서 화면 제목과 설명을 추가하고, 프로젝트 수 요약, 오늘 회의, 최근 활동, 미완료 작업, 최신 확정 회의록, 프로젝트 검색·목록, 캘린더 순으로 정보 우선순위를 정리했다. 이는 `frontend-refactor-plan.md`의 Spaces 정보 계층을 실제 화면에 반영한 것이다.
- 검색·필터 결과가 없을 때 필터 초기화 action을 제공하고, 최근 활동과 미완료 작업이 없을 때 성공을 가장하지 않는 빈 상태 문구를 표시한다. 데이터가 없는 경우를 위해 새 API나 mock 데이터를 추가하지 않았다.
- `/spaces` AppShell에 blue/white 작업 표면, 8px work-surface radius, 제한된 hover lift, keyboard focus, mobile grid 재배치, reduced-motion 규칙을 적용했다. 링크, API, 인증, 권한, route, 회의 생성·캘린더 상태 흐름은 변경하지 않았다.
- Visual check: 인증 세션 없이 `/spaces`에 접근하면 기존 로그인 요구 모달로 보호되며, 보호 우회나 mock 성공을 추가하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. 기존 Vite chunk-size warning과 lint warning 7건 외 새 오류는 없다.

## M064 Project Home Shell

- 2026-07-21: 설계 문서의 Project Home route인 `/project-overview`를 기존 query 기반 주소와 함께 유지하면서 `AppShell` 안으로 옮겼다. Sidebar는 현재 프로젝트명을 표시하고 `프로젝트 개요` 메뉴를 활성 상태로 보여준다.
- 프로젝트 제목·상태·설명, 다음 회의, 회차별 회의 흐름, 회의 운영/ACL, 칸반, Project AI, 공식 Project Knowledge의 기존 기능과 상태 흐름은 삭제하거나 mock으로 바꾸지 않았다. CSS 계층만 blue/white 작업 표면, 8px work-surface radius, 권한 범위 표시, keyboard focus 기준으로 정리했다.
- 다음 회의와 접근 가능한 회의가 없을 때 기존 안내 상태를 유지하며, Project AI에는 Project Knowledge와 접근 가능한 Meeting record 범위를 계속 표시한다. 회의, ACL, task, AI API와 callback 계약은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. 기존 Vite chunk-size warning과 lint warning 7건 외 새 오류는 없다.

## M065 Meetings Surface

- 2026-07-21: Project Home의 회의 영역에 접근 가능한 전체·예정·진행 중·완료 건수 요약을 추가하고, `Meetings` kicker와 `회의 목록과 상태` 제목으로 목적을 분명히 했다. 상태 수는 기존 `accessibleMeetings`에서 계산하며 별도 API나 mock을 추가하지 않았다.
- 회의 운영 영역의 생성·수정·상태 변경·삭제 확인·ACL 부여/회수와 참가 코드 결과는 기존 callback과 권한 disable 조건을 유지한 채 blue/white compact form으로 정리했다. 전체보기 모달에는 검색 결과 없음 상태를 추가했다.
- 회의 상태 badge, default-deny 안내, 마지막 active HOST 보호, Meeting AI/Project AI 범위 로직은 변경하지 않았다. 작은 화면에서는 생성/수정 폼과 modal 목록을 한 열로 전환하고 focus outline을 유지한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. 기존 Vite chunk-size warning과 lint warning 7건 외 새 오류는 없다.

## M066 Meeting Context and Prejoin

- 2026-07-21: 별도 Meeting Detail route가 없는 현재 route 계약을 유지하면서 `/live-meeting`을 회의 컨텍스트와 Prejoin 경계 화면으로 정리했다. 회의 제목·예정 시각·현재 사용자·Meeting role/OWNER/ADMIN override·접근 결과를 우선 표시한다.
- 영상 미리보기, 마이크/카메라 토글, 장치 재설정, 참가자 대기 상태, 시작 전 체크리스트, 입장/참가 신청 이동은 기존 JSX와 state/callback을 유지하고 blue/white 정보 패널과 집중형 영상 무대로 스타일만 변경했다.
- 접근 확인 중/거부 상태와 미디어 권한 실패 상태를 성공 상태와 분리했다. `sessionStorage` prejoin payload와 `/live-room` 이동 계약은 변경하지 않았다. 새 route/API/mock 성공 처리는 추가하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. 기존 Vite chunk-size warning과 lint warning 7건 외 새 오류는 없다.

## M067 Live Meeting Surface

- 2026-07-21: 현재 `/live-room` route와 `LiveRoomPage`의 실제 흐름을 기준으로 LiveMeetingLayout을 정리했다. 상단에는 회의 제목, LiveKit 연결 상태, 참가자 수, 나가기 action을 두고, 중앙에는 영상 무대와 참가자 목록, 하단에는 마이크·카메라·화면 공유·Meeting AI 제어를 배치했다. 우측에는 실시간 자막, 선택 용어 설명, 검색을 작업 패널로 고정했다.
- 연결 상태는 기존 `connectionStateLabel` state를 그대로 사용해 `연결 중`, `실시간 연결됨`, `재연결 중`, `연결 종료`, `연결 실패`를 사용자에게 노출한다. 자막이 없거나 검색 결과가 없는 경우 기존 빈 상태를 유지하며, 실제 STT/API 성공을 mock으로 표시하지 않는다.
- `LiveRoomPage`의 LiveKit token/connect/disconnect, participant snapshot, STT polling/start/stop, 용어 설명, bookmark, Meeting AI notice, 나가기 route와 오류 처리는 변경하지 않았다. 변경은 연결 상태·참가자 수의 표시와 접근성 label, scoped CSS에 한정했다.
- 데스크톱은 영상과 자막의 2열, 1200px 이하에서는 세로 흐름, 680px 이하에서는 모바일 제어 2열로 전환한다. video surface는 짙은 navy, 작업 패널은 blue/white token과 8px radius를 사용하며 reduced-motion에서 transition/animation을 끈다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Build의 기존 Vite 500 kB chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M068 Transcript Surface

- 2026-07-21: 현재 route 구조에 독립 Transcript route가 없고, 실제 전사 원문은 `/meeting-ai?meetingId=...`의 왼쪽 영역에 있으므로 route/API를 추가하지 않고 해당 화면을 Transcript 중심으로 정리했다. 원문을 주 콘텐츠로 두고, 현재 회의 검색 범위·결정사항·Action Item을 순서대로 배치했다.
- Meeting AI는 오른쪽 보조 패널로 유지하고, 기존 `meetingId` query, `chatMeetingAi` 호출, source 표시, unsupported/오류 메시지, 추천 질문과 입력 동작은 변경하지 않았다. 전사·결정사항·Action Item은 기존 `WorkspaceData` 값만 사용한다.
- 전사 영역에 의미 있는 heading, list/listitem, section label, AI loading `aria-live`를 추가했다. 기존 시각용 placeholder line은 제거하고 현재 데이터가 실제로 확인 가능한 기록임을 설명하는 상태 문구로 바꿨다. 이로써 미확인 전사나 AI 성공을 시각적으로 가장하지 않는다.
- blue/white 작업 화면, 8px surface radius, compact typography, desktop 2열과 1000px 이하 세로 흐름, 620px 이하 발화 정보 재배치, focus-visible과 reduced-motion을 적용했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Build의 기존 Vite 500 kB chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M069 Report Workspace Surface

- 2026-07-21: 현재 `/report-agent`의 실제 보고서 편집 화면을 기준으로 공식 보고서 본문을 왼쪽 주 콘텐츠로 고정하고, 보고서 편집 Agent·회의록 candidate·버전 이력·태스크 후보를 오른쪽 검토 패널로 정리했다. 보고서 흐름에서 사용자가 먼저 확인해야 하는 본문과 확정 전 검토 작업을 분리한 것이다.
- 기존 `reportState`, `reportCandidate`, `reportHistory`, `taskCandidates` 상태를 그대로 사용해 `공식 회의록`, `확정 전 candidate`, `편집 중` 상태를 표시한다. 상태 배지는 저장·확정 로직을 새로 만들거나 기존 상태를 추측하지 않는다.
- `updateMeetingReport`, `confirmMeetingReport`, `restoreMeetingReport`, `downloadMeetingReport`, AI 편집, 태스크 후보 추출·확정·제외와 기존 query route/API는 변경하지 않았다. 보고서 본문, Agent, candidate panel에 aria label과 focus-visible 규칙을 추가했다.
- blue/white 작업 화면, 8px surface radius, compact document table, 데스크톱 2열·1120px 이하 세로 전환·720px 이하 문서 메타 재배치, reduced-motion을 적용했다. 기존 모달·이력 선택·다운로드 UI의 동작은 유지했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Build의 기존 Vite 500 kB chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M070 Task Candidate Review Surface

- 2026-07-21: 별도 Task Candidates route가 없는 현재 구조에서 `/report-agent` 우측 검토 패널의 태스크 후보 영역을 명확한 작업 표면으로 정리했다. `Task candidates / 태스크 후보 검토` heading을 추가하고 회의 근거 기반 범위를 표시했다.
- 후보가 없을 때는 성공이나 후보 0건을 단정하지 않고, `회의 근거에서 후보를 추출하면 내용을 검토한 뒤 칸반에 등록할 수 있습니다`라는 다음 행동 안내를 표시한다. 후보가 있는 경우 검토 대기·등록 승인·등록 제외 상태를 색과 상태 문구로 구분한다.
- 후보별 source ID, 제목·설명·담당자·마감일 입력, 칸반 등록·등록 제외·칸반에서 보기 action과 `canConfirmTaskCandidates` disabled 조건은 기존 그대로 유지했다. API, callback, route와 데이터 구조는 변경하지 않았다.
- `aria-label`을 후보 카드와 검토 영역에 추가하고 blue/white 작업 표면, 8px radius, 후보 상태별 색, focus-visible을 적용했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Build의 기존 Vite 500 kB chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M071 Meeting AI Surface

- 2026-07-21: 기존 `/meeting-ai?meetingId=...`의 Meeting AI 보조 패널을 현재 회의 전용 검색 범위가 먼저 보이는 구조로 정리했다. 고정 회차 문구는 제거하고 `현재 회의 전용 · Project 전체 미포함`으로 표시해 실제 회의와 어긋나는 안내를 방지했다.
- 각 AI 답변의 source를 `근거` 목록으로 표시하고, 근거 부족 응답은 `관련도 부족` 또는 `근거 없음` 상태로 분리했다. 답변 대화는 `role=log`, 근거는 list/listitem, 회의 ID 누락·API 오류는 live alert로 접근성 정보를 보강했다.
- `chatMeetingAi` 호출, `meetingId` 검증, source 변환, unsupported reason 매핑, 추천 질문·입력·로딩 동작은 변경하지 않았다. 실제 근거가 없는 답변을 성공으로 표현하는 mock 데이터나 새 API를 추가하지 않았다.
- AI 패널은 blue/white 작업 표면, 범위 강조, 근거 라벨, 오류 상태와 disabled control을 사용하며 기존 Transcript 화면과 함께 반응형으로 동작한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Build의 기존 Vite 500 kB chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M072 Project AI Surface

- 2026-07-21: 현재 `/project-overview` 우측 Project AI 영역의 실제 흐름을 기준으로 검색 범위를 먼저 표시하도록 정리했다. `Project Knowledge`는 공식 지식만, `Meeting record`는 Backend가 접근 가능한 회의만 선필터한다는 내용을 scope panel에 유지·강조했다.
- 답변 대화는 log로 표시하고 source tag를 `근거` 목록으로 구분했다. `unsupportedReason`이 있는 답변은 근거 부족 상태로 시각 구분하고, 검색 데이터 미준비·API 오류·답변 생성 중 상태를 live message로 전달한다.
- `chatProjectAi`, `fetchProjectAiHistory`, `projectAiAvailable`, Backend 권한 선필터 전제, Project Knowledge CRUD, 기존 route/callback은 변경하지 않았다. 프론트에서 권한 범위를 새로 계산하거나 검색 범위를 넓히지 않았다.
- Project AI 질문 입력 placeholder를 접근 가능한 회의와 공식 지식 기준으로 명확히 하고, disabled control과 focus-visible을 추가했다. blue/white 작업 표면과 기존 AppShell을 유지했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` passed. Build의 기존 Vite 500 kB chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M073 Project Knowledge Surface

- 2026-07-21: 현재 route 구조에는 독립 Knowledge 화면이 없고 공식 Project Knowledge가 `/project-overview` 우측 패널에 있으므로, 이 패널을 Knowledge 단계의 실제 화면으로 정리했다. `/terms`의 Domain Dictionary는 용어사전이라는 별도 기능과 API 흐름을 가지므로 이번 단계에서는 수정하지 않았다.
- Project Knowledge 종류를 사람이 읽는 `직접 등록`, `결정`, `회의록`, `외부 자료`로 표시하고 embedding 상태를 `검색 가능`, `처리 중`, `처리 대기`, `처리 실패`로 구분했다. 상태 값은 기존 `ProjectKnowledgeItem.embeddingStatus`를 그대로 사용하며 프론트에서 처리 성공을 추측하지 않는다.
- 공식 지식 목록에 접근 가능한 empty state와 다음 행동을 추가하고, OWNER/ADMIN에게만 등록·편집 폼을 노출했다. 일반 멤버에게는 공식 지식의 관리 주체를 설명한다. 기존 `projectKnowledge`, `onCreateProjectKnowledge`, `onUpdateProjectKnowledge`, `onDeleteProjectKnowledge`, `fetchProjectKnowledgeDetail`과 권한 조건은 변경하지 않았다.
- 폼에 종류·제목·내용의 명시적 label을 추가하고, 항목별 편집·삭제 accessible name, list/listitem, status와 focus-visible을 보강했다. blue/white 작업 표면, 8px work-surface radius, compact spacing, reduced-motion 규칙을 기존 Project Home 스타일과 맞췄다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check`를 실행한다. Build의 기존 Vite chunk-size warning과 lint warning 6건은 기준선으로 기록한다.

## M074 Members Surface

- 2026-07-21: 현재 `/team-members?spaceId={spaceId}`가 실제 Members 화면이므로 기존 route와 query 계약을 유지하면서 `AppShell`과 프로젝트 Sidebar 안으로 옮겼다. target `/spaces/{spaceId}/members` 전환은 route/API 변경 없이 후속 alias 작업으로 남겼다.
- 화면 순서를 `멤버 요약·Space 초대 → 초대 방식 → Meeting 참가 승인 대기 → Owner 이양 → 멤버 Directory`로 정리했다. 이는 프로젝트 접근을 먼저 준비하고, 회의 접근 승인과 소유권 변경 같은 고위험 작업을 분리한 정보 계층이다.
- `projectMembers`, `pendingRequests`, `inviteMeta`, `onCreateSpaceInvitation`, `onApproveRequest`, `onRejectRequest`, `onRemoveMember`, `onTransferOwner`, `onUpdateMemberRole`의 상태·callback·권한 조건은 그대로 유지했다. 프론트에서 권한을 새로 계산하거나 API 성공을 추측하지 않는다.
- OWNER/ADMIN/MEMBER role, 활성·부재 상태, 회의 참가 승인과 Space 초대의 차이를 별도 문구와 badge로 표시했다. Owner 이양은 기존 확인 문구와 활성 멤버 조건을 유지하고, 제거·역할 변경은 기존 disabled 조건을 유지했다.
- 초대·승인·Owner 이양·Directory에 명시적 label, `aria-label`, `role=list/table`, live error/empty state, keyboard focus를 추가했다. blue/white 작업 표면, 8px work-surface radius, desktop/tablet/mobile 재배치, reduced-motion을 적용했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check` 통과. `/team-members?spaceId=space-1`는 Vite index `200`을 반환하며, 보호 화면의 실제 데이터·권한 검증은 로그인 세션이 필요해 별도 E2E에서 확인한다. 기존 Vite chunk-size warning과 lint warning 6건을 기준선으로 기록했다.

## M075 Project Settings Surface

- 2026-07-21: 현재 별도 Settings route가 없고 `/project-overview`에서 설정 모달을 여는 구조이므로, route/API를 추가하지 않고 이 모달을 Project Settings surface로 정리했다. target `/spaces/{spaceId}/settings` 전환은 별도 라우팅 작업으로 남겼다.
- 설정 화면의 정보 순서를 `프로젝트 정보 설명 → 권한 안내 → 이름·설명 수정 → 저장 상태·오류 → 프로젝트 삭제 위험 영역`으로 고정했다. 사용자는 수정 대상과 위험 작업을 먼저 구분할 수 있다.
- `onUpdateProject`, `onDeleteProject`, `projectTitle`, `projectDescription`, 프로젝트명 확인 문구와 기존 저장·삭제 callback은 유지했다. `meetingMutationLoading`과 `meetingMutationError`를 기존 상태 표시로 사용해 저장·삭제 중과 실패를 성공처럼 보이지 않게 했다.
- OWNER/ADMIN이 아니면 기본 정보 입력을 비활성화하고 수정 권한을 설명하며, 프로젝트 삭제는 OWNER만 활성화했다. 최종 권한 판정은 기존 서버/API에 맡기고, 프론트는 보조 안내만 제공한다.
- 설정 모달을 blue/white 작업 표면, 8px radius, 명시적 label, focus-visible, 모바일 높이 제한, reduced-motion으로 정리했다. 위험 영역은 별도 `Danger zone`으로 분리했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check`를 실행한다. 기존 Vite chunk-size warning과 lint warning 6건은 기준선으로 기록한다.

## M076 Landing Visual Compliance Pass

- 2026-07-21: 새로 설치한 `.agents/skills/design-taste-frontend/SKILL.md`의 audit 기준을 현재 MeetingMind 랜딩에 적용했다. 실제 computed style에서 활성화된 gradient 3건(`body.landing-theme`, `.landing-hero`, `.landing-preview-ai-card`)을 확인하고, blue/white 업무 화면과 충돌하지 않도록 각각 평면 배경으로 바꿨다.
- 랜딩 JSX, CTA 링크, route, API/auth/business logic은 변경하지 않았다. body는 `#f8fbff`, hero는 `#f8fbff`, AI preview는 `#f5f9ff`를 사용해 제품 preview를 구분하되 장식성 gradient와 불필요한 시각 노이즈를 제거했다.
- Browser audit 결과 390px에서 `bodyScrollWidth=390`, page width=390, overflow 0건, active background image 0건이었다. 1440px에서도 `bodyScrollWidth=1440`, interactive element unlabeled 0건, active background image 0건이며 H1→H2→H3 heading 순서를 확인했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test`, `git diff --check`를 실행한다. 기존 Vite chunk-size warning과 lint warning 6건은 기준선으로 기록한다.

## M077 Frontend Route Boundary

- 2026-07-21: `App.tsx`에 섞여 있던 `Routes` 선언과 보호 route 조립을 `frontend/src/routes/AppRoutes.tsx`로 이동했다. `ComponentProps` 기반 route prop type을 사용해 page callback 계약을 중복 선언하지 않았다.
- 기존 path, `ProtectedRoute`의 로그인 요청·원래 주소 복귀, page data, API callback, 인증·권한 판정은 그대로 유지했다. App은 인증 bootstrap, workspace 상태·mutation controller와 공통 세션 UI를 연결하고, route 선언은 별도 경계가 담당한다.
- Verification: `/`에서 랜딩 heading과 이름 없는 interactive element 0건, 비인증 `/spaces`에서 `/` 복귀와 로그인 모달을 browser로 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check`를 통과했으며 기존 lint warning 6건과 Vite chunk-size warning만 남았다.

## M078 Frontend Workspace Controller Boundary

- 2026-07-21: `App.tsx`에 있던 workspace 초기 데이터, API read loader, refresh callback, read/mutation 상태를 `frontend/src/hooks/useWorkspaceController.ts`로 이동했다. 공유 타입과 순수 workspace 변환·표시 helper는 `frontend/src/app/workspaceTypes.ts`와 `frontend/src/app/workspaceModel.ts`에 두어 route/page 조립과 데이터 경계를 분리했다.
- 기존 workspace API 호출, auth session 전달, route path, page callback, permission 판정은 변경하지 않았다. mutation handler는 동작 위험을 낮추기 위해 이번 단계에 남겨두고 다음 controller 분리 작업의 대상으로 기록한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check`를 실행한다. build 성공, 테스트 36개 통과, 새 lint 오류 없이 기존 warning 6건을 유지하는 것을 기준으로 한다.

## M079 Frontend Workspace Mutation Boundary

- 2026-07-21: 새로 설치한 `.agents/skills/design-taste-frontend/SKILL.md`의 component boundary와 page-thin 원칙을 적용해 `App.tsx`의 workspace mutation handler를 `frontend/src/hooks/useWorkspaceMutations.ts`로 이동했다. 프로젝트·회의·참여자·태스크·Project Knowledge·Space 멤버·초대 mutation을 한 경계에서 조립하고 App은 auth, workspace controller, route props만 연결한다.
- API 호출, mock fallback, optimistic state update, 권한 확인, 오류 문구, 반환값과 route 이동은 이동 전 동작을 그대로 유지했다. `handleUpdateMeeting`처럼 hook 내부에서만 사용하는 함수는 App 외부로 노출하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check`를 실행한다. build 성공, 테스트 36개 통과, 새 lint 오류 없이 기존 warning 6건을 유지하는 것을 기준으로 한다.

## M080 Meeting AI AppShell Integration

- `MeetingAiPage`의 기존 자체 셸을 공통 `AppShell`과 `WorkspaceSidebar` 조합으로 연결했다.
- `project`, `spaceId` query context를 사이드바 링크에 전달하고, 회의 제목을 현재 작업공간 컨텍스트로 표시한다.
- 기존 meetingId 기반 AI 요청, 범위 안내, transcript, source 표시, 로딩/오류 상태는 변경하지 않았다.
- `AppRoutes`에서 기존 프로젝트 생성 mutation을 사이드바에 전달해 공통 탐색에서 생성 동작이 끊기지 않도록 했다.
- 근거: `design-taste-frontend`의 page-thin 및 consistent navigation 원칙, 제품 문서의 Meeting AI current-meeting scope 원칙.

## M081 Report AppShell Integration

- `ReportAgentPage`의 기존 보고서 작업 프레임을 공통 `AppShell`과 `WorkspaceSidebar` 안에 배치한다.
- `project`, `spaceId`, `meetingId` query context와 기존 `Meeting AI` 이동 링크를 유지한다.
- 보고서 생성·수정·확정·복원·다운로드·태스크 후보 승인 API와 상태 처리는 변경하지 않는다.
- 근거: 보고서가 회의 후속 흐름의 공식 문서 표면이므로 현재 회의/프로젝트 위치를 공통 탐색에서 계속 보여줘야 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/report-agent?meetingId=meeting-1&project=Product%20Ops&spaceId=space-1`는 `/`와 로그인 모달로 복귀했고, 가로 overflow가 없었다. 기존 Vite chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M082 Domain Terms AppShell Integration

- `DomainTermsPage`를 구형 `workspace-catalog-shell`에서 공통 `AppShell`과 `WorkspaceSidebar` 조합으로 이동했다.
- 기존 `spaceId`/`project` 선택, 용어 조회·등록·수정·보관 API, OWNER/ADMIN 관리 권한 판정은 유지했다.
- 실제 동작이 없는 알림 버튼을 제거해 dead control을 없애고 용어사전 작업에 집중시켰다.
- 공통 `DataState`를 로딩·빈 목록·오류·참여 프로젝트 없음 상태에 적용하고 오류 시 재시도 action을 제공했다.
- 근거: 공통 navigation과 작업공간 위치를 모든 보호 화면에서 동일하게 유지하고, 동작하지 않는 UI를 노출하지 않는 UX 원칙.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/terms?spaceId=space-1`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. 기존 Vite chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M083 Live Meeting Prejoin State Badges

- 회의 입장 전 접근 확인 상태를 공통 `StatusBadge`로, 확인된 Meeting role을 `RoleBadge`로 표시한다.
- 기존 `accessCheckState`, `accessRole`, default-deny 문구, 카메라·마이크 권한 처리, 입장 route와 sessionStorage 기록은 변경하지 않는다.
- 근거: 권한 UX에서 접근 상태와 역할을 같은 시각 언어로 표현해 사용자가 입장 가능 여부와 권한 범위를 즉시 구분하도록 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/live-meeting?meetingId=meeting-1&spaceId=space-1`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. 기존 Vite chunk-size warning과 lint warning 6건 외 새 오류는 없다.

## M084 Live Room State and Accessibility

- 실시간 연결 상태를 `StatusBadge`, 참가자 역할을 `RoleBadge`로 표시한다.
- 자막 검색 input에 명시적 accessible label을 추가하고, 기존 `meetingAi` prop은 `_meetingAi`로 명시해 미사용 경고만 정리한다.
- LiveKit room 연결, STT start/stop, 참가자 제어, 자막 검색·북마크·용어 설명 동작은 변경하지 않는다.
- 근거: 집중형 Live 레이아웃에서도 연결 상태와 역할은 즉시 읽혀야 하며, 자막 검색은 키보드·스크린리더 사용자가 목적을 알 수 있어야 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/live-room?meetingId=meeting-1`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. `_meetingAi` 정리로 lint warning은 6건에서 5건으로 줄었고, 나머지는 기존 경고다.

## M085 Meeting Access State Badges

- `MeetingAccessPage`의 신청 상태를 `StatusBadge`, 확인된 Meeting role을 `RoleBadge`로 표시한다.
- 기존 join code 제출, access 재확인, HOST 승인·거절, pending/denied/allowed 상태와 권한 범위 문구는 유지한다.
- 근거: 참가 신청과 회의 데이터 접근은 서로 다른 권한 단계이므로 상태와 역할을 분리해 보여줘야 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/meeting-access?meetingId=meeting-1`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. 새 lint 오류 없이 기존 warning 5건을 유지한다.

## M086 Space Invitation State Badge

- Space 초대 응답 화면에 수락·거절·처리 중·실패·응답 대기 상태를 공통 `StatusBadge`로 표시한다.
- 초대 fragment token 제거, 수락·거절 API, 완료 후 Space 목록 이동과 오류 문구는 변경하지 않는다.
- 근거: 초대 응답은 되돌릴 수 없는 접근 권한 변경이므로 현재 처리 결과를 버튼과 분리해 명확히 보여줘야 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/space-invitations/space-1/invite-1#token=test`는 `/`와 로그인 모달로 복귀했고 fragment token은 제거됐다. 기존 Vite chunk-size warning과 lint warning 5건 외 새 오류는 없다.

## M087 Project Overview Status Language

- 프로젝트 개요의 Space role, 프로젝트 진행 상태, 회의 상태, Project Knowledge embedding 상태를 공통 `RoleBadge`/`StatusBadge`로 표시한다.
- 기존 회의 목록, Project AI, Knowledge CRUD, 권한 조건, route와 API callback은 변경하지 않는다.
- 근거: 프로젝트 홈은 회의→보고서→태스크→지식 흐름의 중심이므로 사용자가 상태와 권한을 같은 시각 언어로 빠르게 비교할 수 있어야 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/project-overview?spaceId=space-1`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. 기존 Vite chunk-size warning과 lint warning 5건 외 새 오류는 없다.

## M088 Members Status Language

- 멤버 디렉터리의 Space role을 `RoleBadge`, 활성/부재 상태를 `StatusBadge`로 표시한다.
- 초대 생성, 회의 참가 승인, Owner 이양, Space role 변경, 멤버 제거 callback과 disabled 권한 조건은 변경하지 않는다.
- 근거: Members 화면은 권한 관리 화면이므로 역할과 현재 접근 상태를 이름만으로 읽게 하지 않고, 공통 권한 시각 언어로 비교 가능하게 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/team-members?spaceId=space-1`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. 기존 Vite chunk-size warning과 lint warning 5건 외 새 오류는 없다.

## M089 Workspace Home Status Language

- 오늘 회의와 캘린더 회의 상태를 공통 `StatusBadge`로 표시했다.
- 프로젝트 검색, 정렬·필터, 캘린더 조회·범위 선택, 회의 생성과 링크 이동은 변경하지 않는다.
- 근거: 워크스페이스 홈은 여러 프로젝트의 상태를 비교하는 화면이므로 상태 표현을 통일해 다음 행동을 빠르게 판단하게 한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 비인증 `/spaces`는 `/`와 로그인 모달로 복귀했고 가로 overflow가 없었다. 기존 Vite chunk-size warning과 lint warning 5건 외 새 오류는 없다.

## M090 Space Target Route and Layout

- `frontend/src/components/layout/SpaceLayout.tsx`를 추가해 Project Home의 AppShell과 Space sidebar를 하나의 layout 경계로 묶었다. `ProjectOverviewPage`는 route의 `:spaceId`를 우선 사용하고, 기존 query 기반 `/project-overview` 주소는 계속 지원한다.
- `/spaces/:spaceId` target route를 `AppRoutes`에 연결하고, `/spaces` 프로젝트 카드와 Space sidebar의 프로젝트 개요 링크가 target route를 사용하도록 변경했다. `WorkspaceSpace.href`도 같은 canonical 주소로 맞췄다.
- target Space가 없거나 접근 가능한 Space 목록에 없는 경우 `DataState(notFound)`를 표시하고 `/spaces`로 돌아갈 수 있게 했다. API 호출, 권한 판정, mutation, 인증 흐름은 변경하지 않았다.
- UX 근거: Space ID를 URL에 고정하면 새로고침과 deep link에서도 현재 프로젝트 문맥을 잃지 않는다. 존재하지 않는 Space를 빈 화면으로 남기지 않아 다음 행동을 명확히 한다.
- Product 근거: 회의·태스크·지식·AI가 모두 Space 안에서 해석된다는 제품 원칙을 route와 layout에 반영했다.
- 유지보수 근거: legacy query alias를 제거하지 않고 `SpaceLayout` 경계만 추가해 기존 화면 기능과 후속 하위 route 분리를 동시에 지원한다.
- Verification: browser에서 `/spaces/space-1`의 not-found 상태와 overflow 없음, `/project-overview?spaceId=space-1` alias 상태를 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. lint는 기존 warning 5건, build는 기존 Vite chunk-size warning만 남았다.

## M091 Project Home Information Hierarchy

- target `/spaces/:spaceId`는 `ProjectHomePage`로 연결해 프로젝트 상태 요약, 다음 회의, 열린 작업, 최근 회의, 최신 확정 회의록, Project AI 진입만 우선 표시한다. 이는 문서의 Project Home 정보 우선순위인 "현재 상태 → 다음 행동 → 최근 결과"를 따른다.
- 기존 `ProjectOverviewPage`의 회의 CRUD, ACL, 칸반, Project Knowledge, Project AI, 설정 기능은 `/project-overview?spaceId=...` compatibility 화면에 유지한다. 새 화면에서 `전체 운영 화면`, `전체 보기`, `칸반 열기`, `Project AI 열기`가 해당 기능으로 연결되며, legacy 화면에 anchor를 추가했다.
- API 호출, 인증·권한 판정, mutation handler, 데이터 구조는 변경하지 않았다. dashboard latest report와 현재 Space task/meeting/knowledge 상태가 없을 때는 빈 상태로 표시해 mock 성공처럼 보이지 않게 했다.
- UX 근거: 첫 진입 화면에서 모든 운영 도구를 동시에 보여주지 않고 사용자의 다음 행동을 먼저 제시해 인지 부하를 줄인다.
- Product 근거: 회의 → 회의록 → 태스크 → 지식 → AI 흐름의 현재 위치를 프로젝트 단위 요약으로 보여준다.
- 유지보수 근거: 요약 화면과 기존 조작 화면을 분리해 이후 Calendar, Meetings, Tasks, AI, Knowledge target route를 독립적으로 옮길 수 있다.
- Verification: browser에서 target not-found 상태와 `/` landing 회귀, 가로 overflow 없음, 활성 gradient 없음 확인. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space 데이터가 없는 계정이라 populated Project Home 카드의 시각 검증은 보류했다.

## M092 Project Meetings Target Surface

- `/spaces/:spaceId/meetings`에 `ProjectMeetingsPage`를 추가했다. Project Home에서 회의 목록을 분리하고 제목·회차·상태 검색, 상태 필터, 접근 가능한 회의 목록, 상태 배지와 회의 화면 진입을 제공한다.
- `SpaceLayout`과 Sidebar에 현재 `회의` 메뉴를 연결했다. 회의 생성과 ACL은 아직 legacy 운영 화면이 담당하므로 `회의 만들기`와 Project Home 링크는 `/project-overview?spaceId=...#project-meetings` compatibility surface로 이동한다.
- 회의 데이터가 없거나 Space가 유효하지 않으면 각각 empty/not-found 상태를 표시한다. API 호출, 회의 접근 권한, mutation, Live/Report route는 변경하지 않았다.
- UX 근거: 회의 찾기와 회의 생성은 서로 다른 작업이므로 Project Home의 요약과 분리하고, 검색·상태 필터를 목록 상단에 두어 탐색 비용을 줄였다.
- Product 근거: Space 안에서 회의가 독립된 업무 단위라는 제품 흐름을 URL과 navigation에 반영했다.
- 유지보수 근거: 목록 surface는 조회와 navigation에 집중하고, 기존 full operation 화면을 조작 경계로 유지해 후속 Meeting Layout/Meeting Detail 분리가 가능하다.
- Verification: browser에서 `/spaces/space-1/meetings` not-found 상태, overflow 없음, 활성 gradient 없음 확인. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없는 계정이라 populated 목록의 시각 검증은 보류했다.

## M093 Project Context Routes for Members and Terms

- `TeamMembersPage`와 `DomainTermsPage`가 `useParams`의 `spaceId`를 query보다 우선 사용하도록 연결했다. `AppRoutes`에 `/spaces/:spaceId/members`, `/spaces/:spaceId/terms`를 추가하고 legacy `/team-members`, `/terms`를 유지했다.
- Project sidebar의 멤버·용어 링크는 Space context가 있을 때 target route를 사용한다. 유효하지 않은 target Space는 첫 Space로 자동 대체하지 않고 not-found 또는 empty 상태로 표시한다.
- 초대, Owner 이양, role 변경·제거, 용어 조회·등록·수정·보관 API와 권한 판정은 변경하지 않았다.
- UX 근거: 권한 관리와 용어는 현재 프로젝트 문맥 없이는 안전하게 해석할 수 없으므로 URL과 화면 모두에서 Space를 고정한다.
- Product 근거: Space가 회의·지식·멤버의 상위 경계라는 제품 원칙을 navigation에 반영했다.
- 유지보수 근거: 기존 alias를 보존한 채 params 우선 규칙을 공통화해 이후 하위 route migration을 단계적으로 진행할 수 있다.
- Verification: browser에서 target/legacy members·terms 주소의 상태와 overflow 없음 확인. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 기존 lint warning 5건과 Vite chunk-size warning만 남았다.

## M094 Project AI Target Surface

- `/spaces/:spaceId/ai`에 `ProjectAiPage`를 추가했다. 기존 `chatProjectAi`와 `fetchProjectAiHistory` API를 사용해 질문·이력·응답·모델 표시를 제공하며, `SpaceLayout`과 Project AI navigation을 사용한다.
- 화면에 `Project Knowledge + 접근 가능한 회의` 검색 범위, 권한 선필터 안내, source tag, 근거 없음/unsupported, loading, provider 오류를 구분해 표시한다. project role과 공식 지식 목록도 함께 보여준다.
- 기존 ProjectOverview의 Project AI 조작은 compatibility 화면에 남겨 API·권한·mutation 흐름을 보존했다. 새 target 화면은 해당 화면을 직접 대체하기 위해 API 계약을 변경하지 않는다.
- UX 근거: AI 답변보다 먼저 검색 범위와 근거 정책을 보이면 사용자가 프로젝트 전체를 검색한다고 오해하지 않는다.
- Product 근거: Project AI는 Space의 공식 지식과 접근 가능한 회의만 검색한다는 핵심 제품 원칙을 별도 URL과 화면에 고정했다.
- 유지보수 근거: Meeting AI와 Project AI의 route·scope·source UI를 분리해 이후 각각의 source viewer와 deep link를 독립적으로 확장할 수 있다.
- Verification: browser에서 `/spaces/space-1/ai` not-found 상태, overflow 없음, 활성 gradient 없음 확인. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 chat/history/source interaction은 보류했다.

## M095 Project Tasks Target Surface

- `/spaces/:spaceId/tasks`에 `ProjectTasksPage`를 추가해 TODO, IN_PROGRESS, IN_REVIEW, DONE 상태와 태스크 검색·상태 필터, 담당자·마감일·우선순위 표시를 제공한다.
- 카드 클릭과 `태스크 관리 열기`는 기존 `/project-overview?spaceId=...#project-tasks`로 연결한다. 기존 drag, create, edit, delete mutation은 legacy 화면에 남겨 API와 권한 경계를 보존했다.
- Space role, empty/not-found, session 범위 안내를 표시하고 mock 데이터를 실제 성공으로 위장하지 않는다.
- UX 근거: 프로젝트 홈의 열린 작업 요약과 상세 칸반을 분리해 상태 비교와 카드 조작의 목적을 나눴다.
- Product 근거: 회의에서 확정된 Action Item이 Project Tasks로 이어지는 제품 흐름을 별도 URL과 보드로 표현했다.
- 유지보수 근거: 조회 surface와 기존 mutation surface를 먼저 분리해 추후 TaskCard editor/drag interaction을 독립 컴포넌트로 옮길 수 있다.
- Verification: browser에서 `/spaces/space-1/tasks` not-found 상태, overflow 없음, 활성 gradient 없음 확인. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated board/filter interaction은 보류했다.

## M096 Project Knowledge Target Surface

- `/spaces/:spaceId/knowledge`에 `ProjectKnowledgePage`를 추가했다. 공식 지식의 유형, 제목, 미리보기, source 연결, embedding 상태를 표시하고 제목·내용·유형 검색과 embedding 상태 필터를 제공한다.
- 지식 등록·수정·보관은 기존 `/project-overview?spaceId=...#project-knowledge` compatibility surface로 연결한다. legacy 화면에 Knowledge anchor를 추가했으며, 기존 API·OWNER/ADMIN 판정을 변경하지 않았다.
- UX 근거: Project Knowledge를 일반 회의 기록과 분리하고 embedding 상태를 보여줘 AI 검색에 실제로 사용 가능한 기준인지 판단하게 한다.
- Product 근거: Project AI가 공식 최신 상태를 기준으로 답해야 한다는 제품 원칙을 별도 Knowledge route에 반영했다.
- 유지보수 근거: 조회/필터 surface와 편집 mutation surface를 분리해 editor drawer와 source viewer를 후속 단계에서 독립적으로 옮길 수 있다.
- Verification: browser에서 `/spaces/space-1/knowledge` not-found 상태, overflow 없음, 활성 gradient 없음 확인. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated knowledge/filter interaction은 보류했다.

- 2026-07-16: Core의 `sessionStorage + Bearer` Auth는 현재 구현과 제한된 rollback을 위한 legacy compatibility로 분류했다. 목표 Browser-BFF/Auth Service, Redis session, encrypted Token Vault, AWS EKS/LiveKit Cloud 설계와 구현 작업은 `../002-bff-auth-msa/**`에서 관리한다. 이 기록 변경은 Core code/schema를 수정하거나 기존 migration을 되돌리지 않는다.
- 2026-07-18: `../002-bff-auth-msa` T035에서 Core 요청 경로에 deterministic legacy/target access resolver를 연결했다. target 검증 실패는 legacy로 재시도하지 않고, 유효 target UUID `sub`는 V13 `users.auth_user_id`로 기존 문자열 업무 User를 찾는다. BFF 전용 `/internal/v1/users/projection`은 target Core JWT subject, deterministic resource ID와 workload identity를 모두 검증해 신규 User를 멱등 upsert한다. V13 필드만 사용하므로 migration/ERD 관계 추가는 없으며 Backend 전체 테스트, 실제 PostgreSQL projection insert/update/conflict와 bootJar를 검증했다.
- 2026-07-21: 사용자 결정으로 회의 채팅 텍스트 첨부파일 RAG(M035)를 이번 전달 범위에서 제외했다. upload API, extractor, retriever, Frontend, AT-001~AT-005 검증은 후속 단계에서 다시 계획한다.

## M097 Meeting Detail and MeetingLayout

- `useMeetingContext`가 기존 `fetchMeetingDetail`과 `fetchMeetingParticipants`를 통해 target Meeting의 상세와 참가자 권한을 읽는다. route의 `spaceId`와 API가 반환한 `detail.spaceId`가 다르면 결과를 차단해 다른 프로젝트의 회의가 화면에 섞이지 않도록 했다.
- `MeetingLayout`을 추가해 Project sidebar 아래에 회의 breadcrumb, 상태, Space role, Meeting role, 회의 메뉴, 현재 회의 범위 안내를 고정했다. Transcript와 태스크 후보 전용 화면은 후속 milestone에서 연결한다.
- `MeetingDetailPage`는 회의 결과 흐름, 다음 행동, 참가자별 Meeting role과 접근 상태를 표시한다. API·인증·권한·mutation 계약은 변경하지 않았고, 프로젝트 미존재, loading, API error, 참가자 empty 상태를 `DataState`로 구분했다.
- UX 근거: 회의 상세에서 사용자가 현재 Space와 Meeting을 동시에 인지하고, 회의 → 회의록 → 후속 업무의 다음 행동을 바로 선택할 수 있어야 한다.
- Product 근거: MeetingMind의 회의는 독립된 기록 단위가 아니라 회의록과 태스크로 이어지는 프로젝트 업무 컨텍스트다. Meeting role과 Space role을 합치지 않고 별도 표시해 권한 범위를 오해하지 않게 했다.
- 유지보수 근거: API read boundary를 hook으로 분리하고 layout/page를 분리해 이후 prejoin, transcript, report, task candidate 화면을 같은 Meeting context 안으로 순차 이동할 수 있다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 로그인 계정에 접근 가능한 Space가 없어 `/spaces/space-1/meetings/meeting-1`은 명시적 not-found 상태로 확인했으며 가로 overflow 없음, 활성 gradient 0건을 확인했다.

## M098 Meeting Target Route Connections

- `/spaces/:spaceId/meetings/:meetingId` target route를 추가하고 Project Home과 Project Meetings의 실제 Meeting ID 링크가 이 상세 화면으로 먼저 진입하도록 변경했다. ID가 없는 legacy fallback 데이터는 임의 ID를 만들지 않고 기존 compatibility 주소를 유지한다.
- `/spaces/:spaceId/meetings/:meetingId/live/prejoin`, `/live`, `/report`, `/ai` target route를 추가했다. 기존 `LiveMeetingPage`, `LiveRoomPage`, `ReportAgentPage`, `MeetingAiPage`는 path parameter를 우선 읽고, 기존 query parameter도 계속 읽는다.
- Meeting API 요청, LiveKit/STT 동작, 보고서·AI mutation, 인증과 권한 판단은 변경하지 않았다.
- UX 근거: 프로젝트에서 회의를 선택했을 때 사용자가 곧바로 실시간 화면이나 legacy report로 튀지 않고, 상태와 권한을 확인하는 Meeting context를 먼저 거치게 한다.
- Product 근거: 모든 회의 산출물은 동일한 Meeting ID에 귀속되어야 하며, target URL이 이를 명시해야 transcript·report·task·AI 확장의 기준점이 된다.
- 유지보수 근거: path/query dual-read를 사용해 기존 링크와 새 deep link를 동시에 지원하고, 향후 legacy alias redirect를 별도 단계로 옮길 수 있다.
- Verification: target prejoin/live/report/AI 경로를 브라우저에서 직접 열어 path가 유지되고 가로 overflow가 없음을 확인했다. 실제 회의가 없어 API 오류·접근 차단 상태가 노출되었으며, fake success는 표시되지 않았다. build/lint/test/diff check도 통과했다.

## M099 Meeting Task Candidate Review

- `useMeetingTaskCandidates`가 기존 `fetchTaskCandidates`, `extractTaskCandidates`, `confirmTaskCandidate`, `dismissTaskCandidate`를 사용해 조회·추출·수정·확정·제외 상태를 관리한다. API 요청 형식, 후보 상태, `canConfirm` 결과는 변경하지 않았다.
- `/spaces/{spaceId}/meetings/{meetingId}/tasks`에 `MeetingTaskCandidatesPage`를 추가하고 `MeetingLayout`의 태스크 후보 메뉴를 실제 target route로 연결했다. 후보 제목·설명·담당자·마감일을 확정 전에만 편집할 수 있고, 확정된 후보는 같은 Space의 Project Tasks로 이동한다.
- 후보, 칸반 등록, 등록 제외, loading, error, empty 상태를 분리했다. `canConfirm=false`인 사용자는 후보를 읽을 수 있지만 입력과 확정/제외 동작은 비활성화하며, 회의 HOST/EDITOR에게 요청하도록 안내한다.
- UX 근거: AI가 만든 실행 항목을 자동 등록하지 않고 회의 근거를 확인하는 검토 단계를 둬 잘못된 작업 생성과 권한 오해를 줄인다. 결과 상태와 다음 행동을 카드 안에 함께 배치해 검토 후 바로 칸반으로 이동할 수 있게 한다.
- Product 근거: 회의록에서 나온 Action Item이 프로젝트 업무로 이어지는 흐름을 별도 Meeting surface로 고정한다. 후보는 현재 Meeting 범위에서만 만들어지고, 확정 이후에만 Project Tasks에 포함된다는 제품 경계를 명시한다.
- 유지보수 근거: 태스크 후보 API 상태를 전용 hook으로 분리해 기존 `ReportAgentPage`의 보고서 편집 로직과 결합하지 않았다. 이후 보고서·AI 화면을 MeetingLayout에 옮겨도 후보 상태와 mutation 경계를 재사용할 수 있다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. browser에서 `/spaces/space-1/meetings/meeting-1/tasks`의 명시적 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. 실제 Space가 없는 계정이라 populated 후보 편집/확정 interaction은 보류했다.

## M100 Meeting Result Shell

- `MeetingContextLayout`을 추가해 target report와 Meeting AI가 먼저 실제 Meeting 상세·참가자 API를 확인하고, Space ID 불일치나 접근 실패를 `DataState`로 처리하도록 했다. 기존 `MeetingLayout`의 breadcrumb, Space/Meeting role, 회의 scope 문구와 navigation을 두 결과 화면에 공유한다.
- `ReportAgentPage`와 `MeetingAiPage`에 `embedded` 표시 모드를 추가했다. target route에서는 기존 AppShell을 중첩하지 않고 MeetingLayout 안에 본문을 렌더링하며, legacy query route에서는 기존 AppShell과 WorkspaceSidebar를 그대로 사용한다.
- 보고서 편집·버전·다운로드·확정, Meeting AI 질문·source·근거 부족·오류 상태의 API와 local state 동작은 변경하지 않았다.
- UX 근거: 회의록과 AI를 별도 제품처럼 보여주지 않고 같은 Meeting 결과 흐름 안에 배치해 사용자가 현재 회의와 검색 범위를 잃지 않게 한다. 결과 화면에 진입하기 전에 권한과 Space 경계를 확인해 잘못된 회의 결과를 노출하지 않는다.
- Product 근거: Meeting → Transcript → Report/Task → Meeting AI의 산출물이 하나의 회의 컨텍스트에 귀속된다는 제품 원칙을 URL과 layout으로 고정한다. Meeting AI는 여전히 현재 회의만 검색하고, Report는 해당 회의 산출물만 편집한다.
- 유지보수 근거: wrapper와 embedded mode를 사용해 기존 호환 화면을 건드리지 않고 target migration을 진행했다. 이후 Live/Prejoin도 같은 context wrapper로 옮길 수 있다.
- Verification: target report/AI 경로에서 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 browser로 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없는 계정이라 populated embedded 화면은 보류했다.

## M101 Meeting Preparation Context Gate

- `/spaces/{spaceId}/meetings/{meetingId}/live/prejoin`에 `MeetingPrejoinRoutePage`를 추가해 실제 Meeting 상세 API가 반환한 `spaceId`를 먼저 확인한다. Space가 없거나 API 접근이 실패하면 전용 입장 화면 대신 명시적인 not-found/error 상태를 표시한다.
- 실제 회의 context가 확인된 경우에만 기존 `LiveMeetingPage`를 전용 LiveMeetingLayout으로 렌더링한다. 기존 legacy `/live-meeting` 주소는 기존 fallback 동작과 별도 경계를 유지한다.
- target prejoin에서는 live meeting detail 요청 실패를 `strictApi` 오류 상태로 처리해 임시 fallback 데이터를 성공 화면처럼 표시하지 않는다. 카메라·마이크 점검, participant role 조회, LiveKit 진입 코드는 변경하지 않았다.
- UX 근거: 회의 입장 전에 URL의 프로젝트와 실제 회의 소속을 확인하면 다른 프로젝트의 장치·접근 화면이 보이는 혼동을 줄인다. 실시간 화면은 일반 작업 화면보다 집중도가 중요하므로 전용 layout을 유지한다.
- Product 근거: Live는 승인된 단일 Meeting에만 접근하는 경계이며, prejoin은 회의 시작 전 권한·장치 확인 단계다. target API 실패를 성공으로 표시하지 않아 회의 접근 신뢰성을 지킨다.
- 유지보수 근거: route context gate와 기존 prejoin UI를 분리해 LiveKit/STT business logic을 건드리지 않았다. 이후 Live room에도 동일한 target context gate를 적용할 수 있다.
- Verification: browser에서 target prejoin의 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 장치 점검 및 LiveKit 입장 상호작용은 보류했다.

## M102 Meeting Live Room Context Gate

- `/spaces/{spaceId}/meetings/{meetingId}/live`에 `MeetingLiveRoutePage`를 추가해 실제 Meeting 상세·참가자 API와 Space 소속을 먼저 확인한다. Space가 없거나 접근에 실패하면 전용 Live room 대신 명시적인 not-found/error 상태를 표시한다.
- 실제 Meeting context가 확인된 경우에만 기존 `LiveRoomPage`를 전용 LiveMeetingLayout으로 렌더링한다. target Live room의 `strictApi` 경계는 live meeting 상세 요청 실패 시 fallback 데이터를 성공 화면으로 표시하지 않도록 처리하며, legacy `/live-room` compatibility 동작은 유지한다.
- LiveKit token 요청·연결·참가자 상태·STT 시작/종료·자막 폴링·용어 설명·회의 제어 API와 권한 판정은 변경하지 않았다.
- UX 근거: 실시간 회의는 일반 프로젝트 화면보다 집중도가 높으므로 실제 회의와 프로젝트 경계를 먼저 확인해 잘못된 회의실 진입을 막는다. 연결 실패를 임시 참가자 화면으로 보여주지 않아 사용자가 현재 상태를 오해하지 않게 한다.
- Product 근거: Live room은 승인된 단일 Meeting의 실시간 발화·참가자·STT 범위만 다루는 경계다. target URL과 context gate가 이 범위를 고정해 Meeting → Transcript/Report/Task 흐름과 분리되지 않도록 한다.
- 유지보수 근거: route gate와 기존 LiveKit/STT 화면을 분리해 실시간 business logic과 API 계약을 보존하고, compatibility alias를 유지한 채 canonical route를 단계적으로 전환할 수 있다.
- Verification: browser에서 target Live room의 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 LiveKit 연결·STT·자막 상호작용은 보류했고 임시 Live room 성공은 표시되지 않았다.

## M103 Project Calendar Target Surface

- `/spaces/{spaceId}/calendar`에 `ProjectCalendarPage`를 추가해 `fetchCalendarEvents`의 실제 Space 일정만 월간 그리드에 표시한다. 일정이 없으면 empty, 조회 중이면 loading, API 실패면 retry 가능한 error 상태를 사용한다.
- 캘린더 일정의 회의 링크는 `/spaces/{spaceId}/meetings/{meetingId}` canonical Meeting Detail로 연결하고, Space role과 프로젝트 breadcrumb를 표시한다. 기존 `/spaces`의 캘린더·회의 생성 운영 화면은 compatibility surface로 유지한다.
- 회의 일정 API, 인증, Space 접근 범위와 회의 상세 route 계약은 변경하지 않았다.
- UX 근거: 프로젝트 홈의 요약 캘린더와 일정 판단 화면을 분리해 날짜 비교에 필요한 공간을 확보하고, 일정에서 바로 회의 context로 이동할 수 있게 했다.
- Product 근거: 캘린더는 Space에 속한 회의의 시간 흐름을 보여주는 탐색 계층이며, 사용자가 접근할 수 있는 일정만 표시되어야 한다.
- 유지보수 근거: target page가 calendar read API와 상태 표시를 독립적으로 소유하고, 기존 생성·mutation surface와 분리해 이후 주/일 보기 확장을 가능하게 했다.
- Verification: browser에서 `/spaces/space-1/calendar`의 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated 일정과 회의 링크 interaction은 보류했다.

## M104 Project Settings Target Surface

- `/spaces/{spaceId}/settings`에 `ProjectSettingsPage`를 추가해 프로젝트 이름·설명 수정, 권한·소유권 관리 진입, 삭제 위험 구역을 분리한다.
- 기존 `onUpdateProject`와 `onDeleteProject` mutation handler를 그대로 사용한다. OWNER/ADMIN만 수정할 수 있고 OWNER만 삭제할 수 있으며, 삭제는 공통 `ConfirmDialog`를 거친다. Owner 이양은 기존 Members target 화면으로 연결한다.
- API, 인증, Space role 판정, Owner transfer 계약과 project mutation business logic은 변경하지 않았다.
- UX 근거: 일반 정보 수정과 되돌릴 수 없는 삭제를 같은 시각적 무게로 섞지 않고, 권한이 없는 사용자는 비활성 입력과 설명으로 이유를 이해하게 한다.
- Product 근거: 프로젝트 설정은 Space 자체의 정체성과 수명주기를 관리하는 계층이며, 회의별 권한과 소유권을 혼동하지 않도록 Members 화면을 별도 진입점으로 유지한다.
- 유지보수 근거: 설정 page는 기존 mutation callback만 조합하고 공통 ConfirmDialog·StatusBadge를 사용해 API layer와 UI 책임을 분리한다. legacy Project Overview의 기존 운영 surface는 보존한다.
- Verification: browser에서 `/spaces/space-1/settings`의 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated 저장·삭제 interaction은 보류했다.

## M105 Account Settings Target Surface

- `/settings`, `/settings/account`, `/settings/security`를 `AccountSettingsPage`로 연결해 인증된 `AuthSession`의 표시 이름·이메일·계정 상태와 세션 만료 정보를 보여준다. `AuthSessionControls`에서도 계정 설정으로 직접 이동할 수 있다.
- 현재 기기 로그아웃은 기존 `onLogout`을 사용하고, 모든 기기 로그아웃은 기존 `AllDeviceLogoutModal`과 `onLogoutAll` 재인증 흐름을 사용한다. 프로필 변경·이미지 저장 API가 없는 상태에서 저장 성공을 가장하지 않고 준비 상태를 안내한다.
- 인증 세션, 로그아웃 API, 재인증·토큰 폐기 정책과 backend 계약은 변경하지 않았다.
- UX 근거: 계정 정보와 프로젝트 권한을 분리해 사용자가 개인 세션 관리와 Space 운영 설정을 혼동하지 않게 한다. 모든 기기 로그아웃처럼 영향 범위가 큰 동작은 기존 확인·재인증 흐름으로 진입시킨다.
- Product 근거: MeetingMind의 계정은 여러 Space와 회의 접근을 가로지르는 전역 주체이므로, 세션 상태를 프로젝트 화면과 분리해 현재 인증 범위와 다음 행동을 명확히 한다.
- 유지보수 근거: AppRoutes는 기존 인증 callback만 주입하고, AccountSettingsPage는 새 인증·프로필 API를 만들지 않는다. 프로필 API가 추가되면 표시 영역에 동일한 read/write 경계를 연결할 수 있다.
- Verification: 인증된 local session으로 `/settings/account`를 browser에서 확인했고 계정 표시명·이메일·세션 정보, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 모든 기기 로그아웃은 세션 폐기 부작용 때문에 실행하지 않았다. lint 오류는 없고 기존 warning 5건과 Vite chunk-size warning만 남았다.

## M106 Project Meetings Create Surface

- `ProjectMeetingsPage`의 회의 만들기 action을 target route 안에 연결했다. 제목은 필수이고 설명·시작·종료 일시는 선택이며 기존 `onCreateMeeting` callback을 호출한다. OWNER/ADMIN이 아니면 버튼을 비활성화하고 이유를 설명한다.
- `ProjectHomePage`의 회의 만들기와 열린 작업 링크를 canonical Project Meetings/Tasks route로 연결하고, 더 이상 target 홈의 기본 action이 전체 legacy 운영 화면으로 이동하지 않도록 했다. 기존 compatibility route 자체와 API·mutation 계약은 유지한다.
- UX 근거: 회의 목록을 확인한 뒤 같은 화면에서 생성해야 context switching이 줄어든다. 권한 없는 사용자는 action을 숨기지 않고 생성 불가 사유를 바로 확인할 수 있다.
- Product 근거: Project Home의 역할은 프로젝트 상태 요약이고, 회의 생성·필터·목록 운영은 Project Meetings가 소유해야 한다. 회의 생성 후 Meeting Detail로 이어지는 canonical 흐름을 유지한다.
- 유지보수 근거: 새 API나 별도 mutation을 만들지 않고 AppRoutes가 기존 callback과 loading/error 상태를 주입한다. 생성 UI는 target page에만 두어 레거시 `ProjectOverviewPage`의 큰 상태 묶음과 결합하지 않는다.
- Verification: browser에서 `/spaces/space-1/meetings`의 명시적 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated 생성 dialog와 권한 interaction은 보류했다. lint 오류는 없고 기존 warning 5건과 Vite chunk-size warning만 남았다.

## M107 Project Tasks CRUD Surface

- `ProjectTasksPage`에 기존 `onCreateProjectTask`, `onMoveProjectTask`, `onDeleteProjectTask`를 연결했다. target 화면에서 제목·설명·담당자·우선순위·마감일을 입력해 생성하고, 카드에서 상태를 변경하며, 삭제는 공통 `ConfirmDialog`를 거친다.
- OWNER/ADMIN만 생성·상태 변경·삭제할 수 있고, 그 외 사용자는 Space 범위와 제한 사유를 확인한다. target 페이지는 더 이상 태스크 운영을 위해 legacy `ProjectOverviewPage`로 이동하지 않는다.
- API 계약, task status/priority 모델, 권한 판정, mutation 구현은 변경하지 않았다.
- UX 근거: 칸반을 보는 화면에서 상태 변경과 삭제가 바로 가능해야 불필요한 context switching과 작업 위치 상실을 줄인다. 위험한 삭제는 확인 단계로 분리했다.
- Product 근거: 회의에서 확정된 Action Item은 Project Tasks에서 지속 관리되어야 하므로 target route가 실제 운영 책임을 가져야 한다.
- 유지보수 근거: page는 기존 callback만 사용하고, `AppRoutes`에서 필요한 mutation 경계를 명시적으로 주입한다. 레거시 화면은 호환 주소로 남기되 target 작업 흐름과 분리했다.
- Verification: browser에서 `/spaces/space-1/tasks`의 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated 생성·상태 변경·삭제 interaction은 보류했다. lint 오류는 없고 기존 warning 5건과 Vite chunk-size warning만 남았다.

## M108 Project Knowledge CRUD Surface

- `ProjectKnowledgePage`에 기존 `onCreateProjectKnowledge`, `onUpdateProjectKnowledge`, `onDeleteProjectKnowledge`를 연결했다. target 화면에서 공식 지식을 등록·수정하고, 삭제는 공통 `ConfirmDialog`를 거친다. 유형과 embedding 상태를 같은 카드에서 확인한다.
- OWNER/ADMIN만 관리할 수 있고, 다른 사용자는 Space 공식 출처와 제한 사유를 확인한다. target 페이지는 더 이상 지식 관리를 위해 legacy `ProjectOverviewPage`로 이동하지 않는다.
- API 계약, embedding 상태 모델, Project AI 검색 범위, 권한 판정과 mutation 구현은 변경하지 않았다.
- UX 근거: Project AI가 참고하는 공식 지식은 검색 화면에서 바로 관리해야 출처와 embedding 상태를 확인한 뒤 수정할 수 있다. 삭제 영향은 확인 단계에서 명시한다.
- Product 근거: Knowledge는 회의 결과를 다음 업무에 연결하는 프로젝트 자산이므로 Project context 안에서 지속 관리되어야 한다.
- 유지보수 근거: 기존 callback을 AppRoutes에서 주입하고 target page가 UI 상태만 소유한다. 새 API나 별도 상태 저장소를 만들지 않아 legacy와 target 간 계약이 갈라지지 않는다.
- Verification: browser에서 `/spaces/space-1/knowledge`의 프로젝트 not-found 상태, 가로 overflow 없음, 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 실제 Space가 없어 populated 등록·수정·삭제 interaction은 보류했다. lint 오류는 없고 기존 warning 5건과 Vite chunk-size warning만 남았다.

## M109 Canonical Alias and Mock Boundary

- 실제 Space/Meeting ID를 알고 있는 내부 이동을 canonical `/spaces/...` target으로 정리했다. Project AI의 Knowledge 이동, workspace의 회의·보고서 이동, Live cancel, 보고서의 태스크 보드 이동은 더 이상 legacy 운영 화면을 기본 목적지로 사용하지 않는다. 전역 화면에서 Space를 특정할 수 없는 링크는 `/spaces`로 보낸다.
- `AppRoutes`의 legacy query alias는 기존 주소를 유지하되 `spaceId`와 `meetingId`가 있는 경우 canonical target으로 redirect한다. `WorkspaceHomePage`는 workspace API가 mock fallback 상태일 때 임시 데이터를 성공 화면으로 표시하지 않고 retry 가능한 오류 상태를 보여준다.
- API 계약, 인증, 권한 판정, mutation과 legacy route 자체는 변경하지 않았다. canonical target 전환은 내부 탐색 경계만 조정한다.
- UX 근거: 사용자가 실제 Space/Meeting context를 알고 이동할 때 legacy query 화면으로 되돌아가지 않게 해 현재 위치와 다음 행동을 유지한다. 데이터가 없을 때 임시 성공 화면을 보여주지 않아 실제 업무 상태와 데모 상태를 혼동하지 않게 한다.
- Product 근거: MeetingMind의 기본 객체 단위는 Space와 Meeting이며, 보고서·AI·태스크·지식은 해당 context 아래에서 열려야 한다. canonical URL이 이 제품 구조를 탐색에도 반영한다.
- 유지보수 근거: redirect와 mock 차단을 route/page 경계에 두고 API layer와 business logic은 그대로 유지했다. legacy 외부 링크는 계속 동작하므로 단계적 migration이 가능하다.
- Verification: Project AI, workspace meeting/report, Live cancel, report task board 내부 이동과 legacy alias redirect, `/spaces` mock fallback 경계를 browser로 확인했다. 대상 화면 가로 overflow 없음과 활성 gradient 0건을 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. lint 오류는 없고 기존 warning 5건과 Vite chunk-size warning만 남았다.

## M110 Target Data Boundary

- `WorkspaceDataSource`에 `loading`을 추가하고 `TargetDataGate`를 만들어 target 화면이 초기 mock 또는 legacy snapshot을 성공 데이터처럼 렌더링하지 않도록 했다. Workspace API 성공과 partial 성공만 target content를 허용하며, loading·legacy/mock 실패는 명시적인 상태와 재시도를 제공한다.
- `SpaceLayout`, `MeetingLayout`, Workspace Home, Project target pages, Meeting target pages, Members/Terms target routes, Live prejoin/live route에 동일한 경계를 연결했다. 기존 legacy 화면은 호환용으로 유지하고, API 호출·인증·권한·상태 모델은 변경하지 않았다.
- UX 근거: 사용자는 잠깐 보이는 임시 프로젝트나 회의 정보를 실제 데이터로 오해할 수 있으므로, 데이터 준비 전에는 위치와 상태만 표시하고 업무 content를 숨긴다. 실패 시 retry를 제공해 다음 행동을 명확히 한다.
- Product 근거: Space와 Meeting은 실제 권한 범위의 기준 객체다. 실제 API가 확인되지 않은 상태에서 프로젝트·회의·AI·권한 정보를 보여주지 않는 것이 제품의 접근 제어와 근거 기반 원칙에 맞다.
- 유지보수 근거: 페이지마다 중복된 mock guard를 만들지 않고 layout/gate 경계에 상태 정책을 모았다. 이후 도메인 API 분리나 client cache 도입 시에도 target 성공 조건을 한 곳에서 유지할 수 있다.
- Verification: `/spaces/space-1` target에서 임시 데이터가 성공 화면으로 표시되지 않는 상태, 가로 overflow 없음, 활성 gradient 0건을 browser로 확인했다. `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. lint 오류는 없고 기존 warning 5건과 Vite chunk-size warning만 남았다.

## M111 Target Report and Meeting AI Data Boundary

- `MeetingContextLayout`이 실제 `MeetingDetailResponse`를 render callback으로 하위 target 화면에 전달하도록 확장했다. target Report/Meeting AI는 route query의 샘플 프로젝트·회의 값을 성공 데이터로 사용하지 않고 실제 Meeting 제목·상태·참여자 범위를 기준으로 표시한다.
- target Report는 Backend report list/detail 응답이 있을 때만 제목·요약·본문을 채우고, report가 없으면 중립적인 빈 상태와 candidate 생성 안내를 표시한다. target AI는 실제 chat 응답 전까지 legacy transcript·decision·Action Item·chat sample을 표시하지 않으며, 근거 없는 report 편집 결과도 만들지 않는다. legacy `/report-agent`, `/meeting-ai` 화면은 기존 호환 동작을 유지한다.
- API 계약, 인증, 권한, mutation, 상태 모델은 변경하지 않았다. report detail은 기존 `fetchMeetingReportDetail`을 사용하고 Meeting AI 질문은 기존 `chatMeetingAi`를 사용한다.
- UX 근거: 회의 context가 확인되기 전의 샘플 결과는 사용자가 실제 회의 기록으로 오해할 수 있다. 데이터가 없을 때 빈 상태를 보여주고 다음 행동을 안내하면 현재 상태와 근거 범위를 정확히 이해할 수 있다.
- Product 근거: MeetingMind의 회의록·AI는 특정 Meeting의 권한 범위와 근거에 종속된다. 실제 회의의 결과와 출처만 보여주는 것이 회의 지식 자산의 신뢰성을 보장한다.
- 유지보수 근거: target과 legacy를 prop으로 분리하고 API layer를 재사용해 기존 외부 링크와 프로토타입 화면을 깨지 않으면서 단계적 migration을 가능하게 했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. lint 오류는 없고 기존 경고 5건과 Vite chunk-size warning만 남았다.

## M112 App and API Boundaries

- `App.tsx`에 흩어져 있던 legacy seed map을 `app/initialWorkspaceState.ts`로 이동해 App이 초기 상태 정의를 직접 소유하지 않도록 했다. 인증·route bootstrap과 기존 mutation callback 조립은 유지했다.
- BFF 요청, JSON/blob 응답, 403 CSRF reset, error message 변환을 `api/client.ts`로 분리했다. 기존 구현은 `api/legacyWorkspace.ts`에 보존하고 `api/workspace.ts`는 compatibility facade로 바꿔 legacy API 계약과 `/api/workspace` snapshot을 유지한다.
- `api/{spaces,dashboard,calendar,meetings,meetingAccess,live,transcripts,reports,tasks,ai,knowledge,terms}.ts` 도메인 경계를 추가하고 target hook/page와 mutation 호출부의 import를 해당 모듈로 전환했다. 주요 함수 구현은 각 도메인 모듈로 이동했고, `legacyWorkspace.ts`에는 legacy snapshot만 남겼다. `workspace.ts` facade는 기존 caller를 위한 호환 export로 유지한다.
- UX 근거: 화면은 도메인 책임을 기준으로 읽을 수 있어야 하며, target page가 하나의 거대한 API 파일을 탐색하지 않아도 된다. App에서 임시 데이터 정의를 분리하면 실제 상태와 화면 bootstrap의 경계가 명확해진다.
- Product 근거: Space, Meeting, Report, AI, Task, Knowledge는 서로 다른 사용자 흐름과 권한 범위를 가진다. API 호출도 같은 업무 단위로 읽혀야 scope 누락을 줄일 수 있다.
- 유지보수 근거: 공통 client를 한 곳에서 유지해 CSRF/error 처리 중복을 없애고, domain import를 먼저 고정해 실제 구현 이동이 호출부 변경 없이 가능하도록 했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. lint 오류와 warning은 없고 Vite chunk-size warning만 남았다.

## M113 Design Token and CSS Boundary

- 공통 앱 토큰을 `frontend/src/styles/tokens.css`로 분리했다. 배경·표면·텍스트·선·accent·success·danger·radius·shadow·motion 기준을 한 곳에서 관리하고, `main.tsx`가 앱 스타일보다 먼저 로드한다.
- 랜딩 스타일의 사용되지 않는 generic selector가 보호 화면에 영향을 주지 않도록 `html:has(body.landing-theme)`, `.landing-page a`, `.landing-page section`, `.landing-page h1~p`, `.landing-page button/input/textarea` 범위로 제한했다. 앱은 기존 `app-theme` body scope를 유지한다.
- `app.css`에 남아 있던 보라색 primary/neutral 값을 `--app-accent`, `--app-accent-strong`, `--app-accent-soft`, `--app-text`, `--app-muted`, `--app-line`으로 치환해 앱의 파랑·흰색 제품 톤을 통일했다. API, route, 인증, 권한, 상태 모델은 변경하지 않았다.
- UX 근거: 화면마다 다른 색 체계와 랜딩 전역 selector는 현재 위치와 상태의 의미를 흔들고, 보호 화면의 기본 여백을 예상하지 못하게 만든다. 토큰과 scope를 분리하면 상태 표현과 layout이 예측 가능해진다.
- Product 근거: MeetingMind는 회의·보고서·태스크·Knowledge를 하나의 업무 흐름으로 연결하므로, primary action과 scope 상태가 같은 색 의미를 공유해야 한다. 랜딩의 공개 제품 설명이 내부 업무 화면의 의미를 덮어쓰면 안 된다.
- 유지보수 근거: 토큰을 별도 파일로 두면 이후 shadcn/ui 또는 컴포넌트 스타일을 도입할 때 색·motion 기준을 한 곳에서 조정할 수 있고, 랜딩과 앱 CSS의 변경 경계를 분리할 수 있다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. build는 기존 Vite chunk-size warning만 남겼고 lint warning은 0건이다. browser에서 `/`, `/spaces`, `/spaces/space-1`, `/spaces/space-1/meetings/meeting-1`, `/spaces/space-1/tasks`, `/spaces/space-1/knowledge`, `/spaces/space-1/ai`, `/settings/account`를 확인했다. 실제 Space가 없는 세션에서는 target route가 `NOT FOUND`를 표시했으며 모든 경로의 가로 overflow와 활성 gradient는 0건이었다. landing `390x844` viewport에서도 가로 overflow가 없었다.

## M114 Project Home PageHeader

- 목표: Project Home에서 현재 위치, 프로젝트 이름, Space role, 다음 행동을 하나의 공통 header 계층으로 읽게 한다.
- 수정 파일: `frontend/src/components/common/PageHeader.tsx`, `frontend/src/components/common/index.ts`, `frontend/src/components/common/common.css`, `frontend/src/pages/ProjectHomePage.tsx`.
- 구현: `PageHeader`가 breadcrumb, eyebrow, title, description, meta, actions를 표시하도록 만들고, Project Home의 기존 역할 badge와 `다음 회의 열기`/`회의 만들기`/`프로젝트 설정` 링크를 그대로 주입했다. API 호출, route target, 권한 판정, 데이터 계산은 변경하지 않았다.
- UX 근거: 프로젝트 화면에서 사용자가 현재 Space와 페이지 목적을 먼저 확인한 뒤 다음 회의나 설정으로 이동해야 하므로, 경로·제목·role·CTA를 같은 시선 흐름에 배치했다. breadcrumb는 전체 프로젝트 목록으로 돌아가는 상위 경로를 제공한다.
- Product 근거: MeetingMind의 기본 업무 단위는 Space이며 Project Home은 회의·태스크·보고서·Project AI로 들어가는 허브다. 헤더가 이 Space context와 다음 행동을 함께 보여줘야 회의 결과가 다음 업무로 이어진다.
- 유지보수 근거: 페이지별로 반복되던 header markup을 공통 컴포넌트로 분리해 이후 Project Meetings, Knowledge, AI 화면에서도 같은 정보 계층을 재사용할 수 있다. 렌더링 전용 컴포넌트라 API와 상태 소유 경계를 침범하지 않는다.
- 영향 범위: Project Home 한 화면과 공통 header 스타일만 변경했다. 기존 legacy route, target route, API, 인증, 권한, 상태 모델은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1`의 not-found 경계, `NOT FOUND` heading, 프로젝트 목록 이동, 가로 overflow 없음(`false`)을 확인했다. 접근 가능한 Space를 임의로 생성하지 않아 populated header의 시각 검증은 보류했다.

## M115 Project Meetings PageHeader

- 목표: Project Meetings에서 현재 Space와 회의 목록의 목적, 현재 사용자의 Space role, 회의 생성 가능 여부를 한 번에 읽게 한다.
- 수정 파일: `frontend/src/pages/ProjectMeetingsPage.tsx`.
- 구현: 기존 header markup을 공통 `PageHeader`로 교체하고 `/spaces` → 현재 Space → 회의 breadcrumb, `Meetings` eyebrow, Space `RoleBadge`, 회의 생성/프로젝트 홈 action을 연결했다. 검색·상태 필터, `onCreateMeeting` mutation, 권한 비활성화, 오류·empty 상태와 기존 route target은 유지했다.
- UX 근거: 회의 목록에서는 사용자가 먼저 어느 프로젝트의 회의인지 확인하고, 자신이 생성할 수 있는지 판단한 뒤 검색·필터를 사용해야 한다. 경로와 role을 CTA보다 앞에 두어 권한 없는 사용자의 오류 시도를 줄인다.
- Product 근거: 회의는 Space 안에서만 해석되고, 회의록·태스크·AI로 이어지는 업무 흐름의 시작점이다. Project Meetings의 상위 context를 명시하면 잘못된 프로젝트에서 회의를 만들거나 찾는 혼동을 줄인다.
- 유지보수 근거: Project Home과 Project Meetings가 같은 `PageHeader` API를 사용해 이후 Knowledge, AI, Members 화면도 같은 정보 계층으로 전환할 수 있다. 페이지는 API/state 소유 없이 기존 props와 handler만 연결한다.
- 영향 범위: Project Meetings header와 공통 컴포넌트 사용만 변경했다. API, 인증, 권한 판정, 상태 모델, 검색/필터 동작, route 계약은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/meetings`의 `NOT FOUND` 경계를 확인했고 desktop `1280px`, mobile `390px` 모두 가로 overflow가 없었다. 접근 가능한 Space를 임의로 생성하지 않아 populated 목록·생성 dialog의 시각 검증은 보류했다.

## M116 Project AI Scope and Source Hierarchy

- 목표: Project AI에서 현재 Space, 검색 범위, 답변 근거를 질문 입력보다 먼저 이해할 수 있게 한다.
- 수정 파일: `frontend/src/pages/ProjectAiPage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 header를 `PageHeader`로 교체하고 Space breadcrumb·role badge·Project AI 설명을 연결했다. AI 응답의 source tag를 `근거` 라벨 아래에 배치하고 대화 log에 `aria-busy`를 연결했다. 모바일에서는 scope/source column이 대화보다 먼저 표시되도록 순서를 조정했다.
- UX 근거: Project AI는 일반 채팅이 아니라 권한 범위가 제한된 검색 화면이다. 사용자가 질문 전에 검색 범위와 근거 정책을 확인해야 다른 프로젝트나 비공식 자료가 검색된다고 오해하지 않는다.
- Product 근거: Project AI는 현재 Space의 공식 Project Knowledge와 접근 가능한 회의만 검색해야 하며, 근거가 없으면 추정하지 않는다. Scope와 citation을 화면의 고정 정보로 드러내 제품의 신뢰 원칙을 반영했다.
- 유지보수 근거: PageHeader와 기존 `StatusBadge`/source tag 구조를 재사용했고, AI client·history hook·권한 선필터·unsupported 응답 처리는 수정하지 않았다. CSS는 기존 Project AI surface 범위에만 추가했다.
- 영향 범위: Project AI 한 화면의 layout, 정보 표현, 접근성 attribute만 변경했다. API, 인증, 권한, route, 상태 모델, 질문 동작은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/ai`의 not-found 경계, desktop `1280px`와 mobile `390px` 가로 overflow 없음, 실제 Space 부재 시 fake AI message 미표시를 확인했다. populated Project AI 답변과 citation interaction은 실제 접근 가능한 Space가 없어 보류했다.

## M117 Project Knowledge PageHeader

- 목표: Project Knowledge에서 현재 Space, 공식 출처의 의미, Space role, 지식 등록 가능 여부를 한 번에 읽게 한다.
- 수정 파일: `frontend/src/pages/ProjectKnowledgePage.tsx`.
- 구현: 기존 header markup을 `PageHeader`로 교체하고 `/spaces` -> 현재 Space -> Knowledge breadcrumb, `Official source` eyebrow, Space RoleBadge, `지식 등록`/`프로젝트 홈` action을 연결했다. 설명에 embedding 처리 상태가 검색 가능 여부에 영향을 준다는 정보를 추가했다. 검색·embedding 상태 필터, CRUD mutation, 삭제 ConfirmDialog, 권한 비활성화와 기존 route는 유지했다.
- UX 근거: Knowledge는 일반 문서 목록이 아니라 Project AI가 참조하는 공식 기준이다. 사용자가 목록을 보기 전에 Space context와 공식 출처의 의미, 현재 권한을 확인해야 잘못된 기준을 등록하거나 삭제하지 않는다.
- Product 근거: MeetingMind는 회의 기록과 Project Knowledge를 구분하고, Project AI는 공식 Knowledge와 접근 가능한 회의만 검색한다. header에서 공식 source와 embedding 기준을 드러내 제품의 신뢰/검색 계약을 반영했다.
- 유지보수 근거: Project Home/Meetings/AI와 같은 `PageHeader` API를 재사용해 페이지별 breadcrumb/title/action 중복을 줄였다. API, state, permission 계산과 mutation 경계는 기존 page props/handlers에 남겼다.
- 영향 범위: Project Knowledge 한 화면의 header 정보 계층만 변경했다. API, 인증, 권한 판정, route, 상태 모델, CRUD 동작은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/knowledge`의 not-found 경계, desktop `1280px`와 mobile `390px` overflow 없음, 실제 Space 부재 시 fake Knowledge 미표시를 확인했다. populated Knowledge 카드와 CRUD dialog는 접근 가능한 Space가 없어 보류했다.

## M118 Members Information Hierarchy

- 목표: Members에서 현재 Space, 관리 목적, 멤버 운영 상태, 초대 진입을 먼저 이해하게 한다.
- 수정 파일: `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 멤버 개요와 초대 panel을 공통 `PageHeader`로 연결하고 `/spaces` -> 현재 Space -> Members breadcrumb, `Access control` eyebrow, Space 초대 action, 전체/활성/부재/승인 대기 요약을 추가했다. 초대·회의 승인·오너 이양·멤버 검색/필터·역할 변경·제거 handler와 기존 route/API/상태 계산은 유지했다.
- UX 근거: Members는 단순 주소록이 아니라 Space 접근 제어 화면이다. 사용자가 대상 Space와 현재 운영 상태를 확인한 뒤 초대, 승인, 소유권, 역할 관리로 내려가도록 시선 순서를 고정했다.
- Product 근거: MeetingMind의 Space membership은 Project Knowledge·회의·AI 접근의 기반이고, Meeting join request는 Space membership과 별개다. header와 초대 영역에서 Space context를 먼저 고정해 두 권한 범위를 혼동하지 않게 했다.
- 유지보수 근거: Project Home/Meetings/AI/Knowledge와 같은 `PageHeader` API를 재사용해 header markup과 responsive 규칙을 통일했다. 페이지의 API callback, role 계산, mutation loading/error 경계는 기존 코드에 남겼다.
- 영향 범위: Members 한 화면의 layout과 정보 표현만 변경했다. 초대, 승인/거절, owner transfer, role update, member removal, 검색/필터, legacy alias와 target route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/members`의 not-found 경계, desktop `1280px`와 mobile `390px` overflow 없음, 실제 Space 부재 시 fake Members 미표시를 확인했다. populated 멤버 표와 초대/승인/오너 이양 interaction은 접근 가능한 Space가 없어 보류했다.

## M119 Project Settings Information Hierarchy

- 목표: Project Settings에서 현재 Space와 role, 기본 정보 변경, 권한 관리, 위험 작업의 순서를 명확히 한다.
- 수정 파일: `frontend/src/pages/ProjectSettingsPage.tsx`.
- 구현: 기존 settings header를 `PageHeader`로 교체하고 프로젝트 목록 -> 현재 Space -> Settings breadcrumb, `Project settings` eyebrow, 현재 Space `RoleBadge`, 프로젝트 홈 action을 연결했다. 설명에서 기본 정보·권한·삭제 작업의 차이를 명시했다. 저장 form, OWNER/ADMIN 권한 계산, 멤버 관리 링크, 삭제 `ConfirmDialog`, API callback과 route는 유지했다.
- UX 근거: 설정 화면에서는 사용자가 먼저 어느 프로젝트를 변경하는지와 자신의 역할을 확인해야 한다. 그 다음 일반 변경과 되돌릴 수 없는 삭제를 분리해 실수를 줄인다.
- Product 근거: Space가 MeetingMind의 권한 경계이며, 프로젝트 정보와 멤버/소유권은 같은 Space context에서 관리된다. header의 breadcrumb와 role 표시가 이 제품 구조를 드러낸다.
- 유지보수 근거: Project Home/Meetings/AI/Knowledge/Members와 같은 `PageHeader` API를 재사용해 breadcrumb, title, role, action의 구현 차이를 줄였다. 비즈니스 handler와 permission state는 페이지 내부에 그대로 남겼다.
- 영향 범위: Project Settings header와 정보 표현만 변경했다. API, 인증, 권한 판정, 저장/삭제 동작, ConfirmDialog, route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/settings`의 not-found 경계, desktop `1280px`와 mobile `390px` overflow 없음, 실제 Space 부재 시 fake 설정과 Danger zone 미표시를 확인했다. populated 저장 form과 삭제 ConfirmDialog는 접근 가능한 Space가 없어 보류했다.

## M120 Account Settings Information Hierarchy

- 목표: 전역 Account Settings에서 계정 context, 현재 세션 상태, 프로필 정보, 보안 action을 한 번에 이해하게 한다.
- 수정 파일: `frontend/src/pages/AccountSettingsPage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 account header를 `PageHeader`로 교체하고 워크스페이스 -> 계정 설정 breadcrumb, `Account settings` eyebrow, 활성 세션 `StatusBadge`, 워크스페이스 이동 action을 연결했다. 기존 프로필·보안/세션 surface와 현재 기기/모든 기기 로그아웃 action, 재인증 modal은 유지했다. PageHeader의 전역 화면 여백과 mobile 여백만 account scope로 추가했다.
- UX 근거: 계정 설정은 특정 Space에 속하지 않으므로 프로젝트 탐색과 분리된 전역 context가 먼저 보여야 한다. 세션 상태를 header에 두고 프로필과 보안을 이어 배치해 사용자가 계정 정보와 위험 action을 구분한다.
- Product 근거: MeetingMind는 프로젝트 권한과 계정/세션 보안을 별도 계층으로 운영한다. 워크스페이스 이동과 세션 상태를 header에서 분리해 Space role과 계정 상태를 혼동하지 않게 했다.
- 유지보수 근거: Project/Meeting 화면과 같은 `PageHeader` API를 재사용해 global route도 breadcrumb/title/action/status 표현을 통일했다. 인증 session, logout callback, 재인증 modal의 책임은 변경하지 않았다.
- 영향 범위: Account Settings의 header layout과 spacing만 변경했다. 인증, 세션 만료, 로그아웃/재인증, route, modal 동작은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/settings/account`의 PageHeader·프로필·보안/세션 정보 표시, desktop `1280px`와 mobile `390px` overflow 없음, 로그아웃 action 미실행 상태를 확인했다. 세션을 폐기하는 로그아웃 상호작용은 실행하지 않았다.

## M121 Project Tasks PageHeader

- 목표: Project Tasks에서 현재 Space, 사용자의 role, 태스크 관리 목적과 주요 action을 같은 정보 계층에서 이해하게 한다.
- 수정 파일: `frontend/src/pages/ProjectTasksPage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 전용 header를 `PageHeader`로 교체하고 프로젝트 목록 -> 현재 Space -> Tasks breadcrumb, `Action items` eyebrow, Space `RoleBadge`, 태스크 생성/프로젝트 홈 action을 연결했다. 기존 태스크 검색·상태 필터·칸반 이동·생성·삭제 `ConfirmDialog`, 권한 비활성화와 route는 유지했다. 사용하지 않는 Project Tasks 전용 header CSS를 제거하고 공통 반응형 header 규칙을 사용했다.
- UX 근거: 태스크를 조작하기 전에 사용자가 어느 Space의 업무인지와 현재 권한을 확인해야 한다. 경로·목적·role을 action과 같은 헤더에 두어 잘못된 프로젝트에서 생성하거나 권한 없는 변경을 시도하는 혼동을 줄였다.
- Product 근거: MeetingMind의 회의 결과는 Space 단위 태스크로 이어진다. Project Tasks가 Space context와 `태스크 만들기` action을 함께 보여줘 회의에서 확정된 일이 다음 업무로 연결되는 제품 흐름을 강화한다.
- 유지보수 근거: Project Home/Meetings/AI/Knowledge/Members/Settings/Account와 같은 `PageHeader` API를 재사용해 페이지별 header 중복을 줄였다. API 호출, 상태 계산, 권한 판정, mutation handler는 기존 페이지 경계에 남겼다.
- 영향 범위: Project Tasks의 header markup과 전용 header CSS만 변경했다. API, 인증, 권한, route, 상태 모델, 검색/필터/칸반 CRUD 동작은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/tasks`를 mobile `390px`와 desktop `1280px`로 확인했으며 현재 접근 가능한 Space가 없어 의도한 not-found 경계가 표시되고 가로 overflow는 없었다. populated board, 생성 dialog, 상태 변경/삭제 interaction은 실제 Space가 없어 보류했고 fake 태스크를 표시하지 않았다.

## M122 Project Calendar PageHeader

- 목표: Project Calendar에서 현재 Space, 사용자의 role, 일정 화면의 목적과 회의 목록 이동을 같은 정보 계층에서 이해하게 한다.
- 수정 파일: `frontend/src/pages/ProjectCalendarPage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 전용 header를 `PageHeader`로 교체하고 프로젝트 목록 -> 현재 Space -> 캘린더 breadcrumb, `Schedule` eyebrow, Space `RoleBadge`, 회의 목록 action을 연결했다. 기존 월 이동·오늘 이동·ACL-filtered 일정 조회·회의 상세 링크·빈 상태·오류 재시도와 route는 유지했다. 사용하지 않는 Calendar 전용 header CSS를 제거하고 공통 반응형 header 규칙을 사용했다.
- UX 근거: 일정 화면에서는 사용자가 어느 Space의 일정인지 먼저 확인한 뒤 월을 이동하거나 회의를 선택해야 한다. 경로·목적·role을 달력 도구보다 앞에 배치해 context switching과 권한 범위 혼동을 줄였다.
- Product 근거: MeetingMind의 Calendar는 접근 가능한 회의만 Space 시간 흐름으로 보여주는 운영 화면이다. Space context와 `회의 목록` 이동을 함께 노출해 일정 확인에서 회의 업무로 자연스럽게 이어지게 했다.
- 유지보수 근거: Project Tasks와 동일한 `PageHeader` API와 responsive override를 재사용해 프로젝트 하위 화면의 헤더 구현 차이를 줄였다. `fetchCalendarEvents`, 날짜 계산, 상태 경계와 route 책임은 변경하지 않았다.
- 영향 범위: Project Calendar의 header markup과 전용 header CSS만 변경했다. API, 인증, 권한, route, 일정 조회, 월 이동, 빈/오류 상태는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/calendar`를 mobile `390px`와 desktop `1280px`로 확인했으며 현재 접근 가능한 Space가 없어 의도한 not-found 경계가 표시되고 가로 overflow는 없었다. populated 달력과 회의 링크 interaction은 실제 Space가 없어 보류했고 fake 일정을 표시하지 않았다.

## M123 Meeting Transcript Information Hierarchy

- 목표: Meeting Transcript에서 회의 context는 상위 `MeetingLayout`이 책임지고, 본문은 전사 목적·처리 상태·검색·발화 목록에 집중하게 한다.
- 수정 파일: `frontend/src/styles/app.css`.
- 구현: Transcript 내부 header의 카드 border/background/shadow를 제거하고 간결한 section header로 낮췄다. 상위 MeetingLayout의 회의명·breadcrumb·role·status·meeting nav와 본문 전사 상태를 중복하지 않도록 시각 계층을 분리했으며, 640px 이하에서는 전사 검색 toolbar가 세로로 쌓이도록 유지했다. 전사 API, 검색 상태, Meeting AI route, loading/error/empty 경계는 변경하지 않았다.
- UX 근거: 사용자는 먼저 회의 context를 확인하고 그 다음 전사 검색과 발화 내용을 읽는다. 같은 화면 안에서 회의 header와 전사 header를 같은 무게의 카드로 반복하지 않아 정보 과부하와 context switching을 줄였다.
- Product 근거: Transcript는 Meeting AI가 현재 회의 근거를 확인하는 작업 surface이지 별도 회의가 아니다. MeetingLayout에서 회의 범위와 권한을 고정하고 본문에서는 전사 처리 상태와 발화 탐색을 우선해 회의 -> 전사 -> Meeting AI 흐름을 명확히 했다.
- 유지보수 근거: 기존 `MeetingLayout`, `DataState`, `StatusBadge`, dialogue hook과 route를 재사용했다. 변경은 Transcript scope CSS에 한정되어 API·상태·권한 계약을 건드리지 않는다.
- 영향 범위: Meeting Transcript의 내부 header 표현과 모바일 toolbar layout만 변경했다. 전사 데이터·검색·권한·Meeting AI 이동·loading/error/empty 동작은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/meetings/meeting-1/transcript`를 mobile `390px`와 desktop `1280px`로 확인했으며 현재 접근 가능한 Space가 없어 의도한 not-found 경계가 표시되고 가로 overflow는 없었다. populated 전사 목록과 검색 interaction은 실제 Space가 없어 보류했고 fake 전사를 표시하지 않았다.

## M124 Meeting Report Context Header

- 목표: Meeting Report의 상단 작업 영역이 내부 구현명 대신 사용자가 이해하는 `회의록` context와 실제 문서 제목을 먼저 보여주게 한다.
- 수정 파일: `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/styles/app.css`.
- 구현: Report 작업 헤더의 `report-agent / ...` 표기를 `회의록` label과 `reportView.title` 기반 문서 제목으로 교체했다. 저장 상태, Meeting AI 이동, Markdown/DOCX/PDF 다운로드, 회의록 저장 action은 그대로 유지했고 모바일에서는 문서 제목이 헤더 너비를 넘지 않도록 ellipsis와 100% 폭 규칙을 적용했다.
- UX 근거: 사용자는 보고서를 편집할 때 내부 컴포넌트 이름이 아니라 현재 문서와 저장 상태를 확인해야 한다. 상위 MeetingLayout의 회의 위치·권한·상태와 Report의 문서 context를 분리해 중복과 인지 부담을 줄였다.
- Product 근거: 회의는 보고서로 확정되고, 보고서는 태스크·Knowledge·AI로 이어지는 공식 기록이다. `회의록`과 실제 제목을 작업 헤더에 고정해 공식 기록을 편집하는 화면임을 명확히 했다.
- 유지보수 근거: 기존 report state, API callback, route, embedded data boundary를 그대로 사용하고 표시용 markup/CSS만 변경했다. fake report fallback을 추가하지 않았으며 실제 report detail이 없을 때의 빈 상태 경계를 유지했다.
- 영향 범위: Report 작업 헤더의 텍스트 계층과 반응형 폭만 변경했다. 보고서 조회·수정·confirm·restore·download·AI 편집·task candidate mutation은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/meetings/meeting-1/report`를 mobile `390px`와 desktop `1280px`로 확인했으며 현재 접근 가능한 Space가 없어 not-found 경계가 표시되고 fake `AI 자동 생성` 내용은 표시되지 않았으며 overflow도 없었다. populated report interaction은 실제 Space가 없어 보류했다.

## M125 Meeting Task Candidates Information Hierarchy

- 목표: Meeting Task Candidates에서 회의 context는 상위 `MeetingLayout`이 책임지고, 본문은 후보 검토 기준·권한·검토 대기열에 집중하게 한다.
- 수정 파일: `frontend/src/styles/app.css`.
- 구현: 후보 화면 내부 header의 카드 border/background/shadow를 제거하고 간결한 검토 section으로 낮췄다. 상위 MeetingLayout의 회의명·breadcrumb·role·status·meeting nav와 후보 화면의 검토 목적을 분리했으며, 700px 이하에서는 재추출 action이 세로 흐름으로 내려가도록 유지했다. 후보 API, 권한 판정, 편집·확정·제외 handler, loading/error/empty 경계는 변경하지 않았다.
- UX 근거: 후보는 회의에서 나온 실행 항목을 검토한 뒤 프로젝트 칸반으로 보내는 중간 단계다. 회의 context를 반복하는 큰 header보다 검토 기준과 권한 안내를 먼저 읽게 해야 확정 전 상태와 등록 가능 여부를 오해하지 않는다.
- Product 근거: MeetingMind의 회의 -> 태스크 흐름은 근거 확인과 사용자 확정을 거쳐야 한다. 화면의 검토 기준·현재 회의 범위·Meeting role 안내를 독립 surface로 유지해 자동 추출 결과가 곧 공식 태스크라는 오해를 막았다.
- 유지보수 근거: 기존 `MeetingLayout`, `StatusBadge`, `RoleBadge`, task candidate hook과 route를 재사용하고 CSS scope만 조정했다. 데이터·mutation·권한 계약은 그대로 유지된다.
- 영향 범위: Meeting Task Candidates의 내부 header와 mobile layout만 변경했다. 후보 조회·추출·필드 수정·칸반 등록·제외·권한 제한·route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/meetings/meeting-1/tasks`를 mobile `390px`와 desktop `1280px`로 확인했으며 현재 접근 가능한 Space가 없어 not-found 경계가 표시되고 가로 overflow는 없었다. populated 후보 interaction은 실제 Space가 없어 보류했고 fake 후보를 표시하지 않았다.

## M126 Meeting AI Context Boundary

- 목표: target Meeting AI에서 회의 위치·권한·상태는 상위 `MeetingLayout` 하나가 책임지고, 본문은 현재 회의 범위·전사·근거·질문에 집중하게 한다.
- 수정 파일: `frontend/src/pages/MeetingAiPage.tsx`.
- 구현: `embedded` target에서는 내부 `meeting-ai-page-header`를 렌더링하지 않도록 분기하고, legacy `/meeting-ai`에서는 기존 topbar를 그대로 유지했다. 기존 `chatMeetingAi` 호출, `meetingId` 검사, source/unsupported/error 표시, 추천 질문과 입력 상태는 변경하지 않았다.
- UX 근거: 같은 회의 제목과 상태가 MeetingLayout과 Meeting AI 내부 topbar에 반복되면 사용자가 두 개의 context를 가진 것으로 오해할 수 있다. target에서는 상위 탐색과 권한 context를 한 곳에 두고, AI 본문은 범위·근거·질문에 집중시켰다.
- Product 근거: Meeting AI는 프로젝트 전체를 검색하는 기능이 아니라 현재 회의만 답하는 보조 도구다. 단일 Meeting context header를 사용하면 회의 -> 전사/근거 -> 질문 흐름과 scope 경계가 명확해진다.
- 유지보수 근거: embedded/legacy route의 표현만 분리하고 API·hook·state·route 계약은 그대로 재사용했다. target data boundary가 제공하는 실제 Meeting이 없을 때 기존 not-found 경계를 유지하며 fake AI content를 추가하지 않았다.
- 영향 범위: target Meeting AI의 중복 header 렌더링만 변경했다. AI API, 인증, 권한, source filtering, unsupported/error 처리, legacy compatibility route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/meetings/meeting-1/ai`를 mobile `390px`와 desktop `1280px`로 확인했으며 현재 접근 가능한 Space가 없어 not-found 경계가 표시되고 overflow는 없었다. 실제 질문/source interaction은 실제 Space가 없어 보류했으며 fake 답변을 표시하지 않았다.

## M127 Domain Terms PageHeader

- 목표: Domain Terms에서 현재 프로젝트, 사용자의 Space role, 용어사전 목적과 프로젝트 선택을 검색/CRUD보다 먼저 이해하게 한다.
- 수정 파일: `frontend/src/pages/DomainTermsPage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 전용 header를 `PageHeader`로 교체하고 프로젝트 목록 -> 현재 Space -> 용어사전 breadcrumb, `Domain dictionary` eyebrow, Space `RoleBadge`, 프로젝트 선택 selector를 연결했다. 검색·상태 필터·등록/수정·활성/비활성 처리·권한 안내·empty/error 상태와 target/legacy route는 유지했다. 사용하지 않는 전용 header CSS를 제거하고 공통 반응형 header 규칙을 사용했다.
- UX 근거: 용어는 프로젝트 문맥에 따라 의미가 달라지므로 사용자가 먼저 어느 Space를 편집하는지와 권한을 확인해야 한다. selector를 action 영역에 유지해 프로젝트 전환을 숨기지 않으면서, 검색과 CRUD가 그 다음 단계로 읽히게 했다.
- Product 근거: MeetingMind의 용어사전은 회의 기록과 AI 검색에서 프로젝트별 의미를 맞추는 지식 기반이다. Space context와 role을 고정해 잘못된 프로젝트의 용어를 수정하거나 AI 근거 범위를 오해하는 일을 줄였다.
- 유지보수 근거: Project 화면과 동일한 `PageHeader`/`RoleBadge` API를 사용해 breadcrumb·title·selector·role 표현을 통일했다. terms API, query parameter 선택 로직, mutation state와 route 계약은 변경하지 않았다.
- 영향 범위: Domain Terms의 header markup과 전용 header CSS만 변경했다. 검색·필터·용어 CRUD·권한·empty/error 처리와 target/legacy route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces/space-1/terms`를 mobile `390px`와 desktop `1280px`로 확인했으며 PageHeader와 empty/not-found 경계가 표시되고 가로 overflow는 없었다. 실제 Space가 없어 populated 용어 목록과 CRUD interaction은 보류했고 fake 용어를 표시하지 않았다.

## M128 Workspace Home PageHeader

- 목표: Workspace Home에서 현재 전역 context, 제품의 업무 목적, 참여 프로젝트 규모를 오늘 회의·최근 활동보다 먼저 이해하게 한다.
- 수정 파일: `frontend/src/pages/WorkspaceHomePage.tsx`, `frontend/src/styles/app.css`.
- 구현: 기존 Workspace Home header를 `PageHeader`로 교체하고 워크스페이스 breadcrumb, `Workspace` eyebrow, 업무 목적 제목/설명, 프로젝트 수 meta를 연결했다. 기존 데이터 source 표시, 알림 panel, 오늘 회의, 최근 활동, action items, 최신 보고서, 프로젝트 목록, 캘린더와 callback은 유지했다. 사용하지 않는 전용 header CSS를 제거하고 공통 header 규칙을 사용했다.
- UX 근거: 전역 대시보드에서는 사용자가 어느 범위의 작업을 보고 있는지와 이 화면에서 무엇을 얻는지 먼저 알아야 한다. 프로젝트 수를 title 옆 상태 정보로 두고, 오늘 해야 할 일과 최근 활동을 바로 아래에 배치해 scanning 순서를 고정했다.
- Product 근거: MeetingMind의 핵심 흐름은 여러 Space의 회의 결과를 다음 업무로 연결하는 것이다. Workspace Home의 역할을 프로젝트·회의 집계와 다음 행동의 허브로 명확히 해 Space 상세 화면과 구분했다.
- 유지보수 근거: 기존 `AppShell`, `TargetDataGate`, dashboard/calendar API와 state 계산을 그대로 두고 표시용 header만 `PageHeader`로 통일했다. 페이지가 API나 비즈니스 로직을 새로 소유하지 않는다.
- 영향 범위: Workspace Home의 header markup과 전용 header CSS만 변경했다. 오늘 회의·알림·검색·필터·캘린더·프로젝트/회의 생성 route와 callback은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. 인증된 local browser에서 `/spaces`를 mobile `390px`와 desktop `1280px`로 확인했으며 PageHeader, empty 상태, overflow 없음이 확인됐다. 현재 local session 데이터가 empty여서 populated dashboard 카드와 알림/캘린더 interaction은 보류했고 fake 프로젝트·회의를 표시하지 않았다.

## M129 Frontend Route and Responsive Audit

- 목표: 전체 canonical/legacy route가 설계된 AppShell·SpaceLayout·MeetingLayout·특수 Live/Access/Invitation layout 경계를 유지하면서 상태·접근성·반응형 기준을 만족하는지 확인한다.
- 수정 파일: `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: 코드 변경 없이 `AppRoutes`와 각 page/layout의 route ownership을 다시 확인했다. Workspace/Project/Meeting 화면은 공통 shell과 PageHeader/MeetingLayout을 사용하고, Live/Prejoin/Meeting Access/Space Invitation은 실시간 장치·접근 승인·초대 token이라는 별도 context를 가져 전용 layout을 유지하는 것으로 결정했다. 전체 23개 주요 route를 browser에서 확인했다.
- UX 근거: 모든 화면을 같은 header로 만들면 Meeting live controls, invite resolution, access approval처럼 사용자 목표와 보안 상태가 다른 화면의 context가 약해진다. 공통 navigation이 필요한 업무 화면은 통일하고, 상태 중심 특수 화면은 전용 layout을 유지하는 것이 discoverability와 error prevention에 맞다.
- Product 근거: MeetingMind는 Workspace -> Space -> Meeting의 업무 계층과 Live/Access/Invitation의 운영 경계를 함께 가진다. route별 context를 보존해야 권한 범위와 실시간 상태를 잘못 해석하지 않는다.
- 유지보수 근거: route alias와 기존 API/상태/권한 경계를 변경하지 않고 browser 검증만 수행해 통합 위험을 늘리지 않았다. 이후 populated Space가 제공되면 같은 route matrix에서 CRUD·LiveKit·STT·AI source interaction을 확장 검증할 수 있다.
- 영향 범위: 코드 변경은 없고 검증 문서만 갱신했다. 모든 route target, API, 인증, 권한, 상태 모델, special layout을 유지했다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint`, `cd frontend && npm run test -- --run`, `git diff --check` 통과. browser에서 주요 23개 route를 mobile `390px`와 desktop `1280px`로 열어 overflow 0건, main landmark 존재, 접근 불가/empty 상태에서 fake 성공 데이터 미표시를 확인했다. populated Space가 없어 실제 CRUD·LiveKit·STT·AI source interaction은 운영 계정 검증으로 남겼다.

## M130 온프레 AI Provider 전환 PoC

- 목표: 기존 FastAPI AI 서버, prompt, citation validation, PostgreSQL/pgvector retrieval, embedding worker generation/swap을 유지하면서 OpenAI 직접 호출만 provider factory 경계로 감싼다. 환경변수만으로 OpenAI와 local OpenAI-compatible text/embedding provider를 전환할 수 있게 한다.
- 수정 파일: `backend/src/main/resources/application.yml`, `backend/src/test/java/com/meetingmind/demo/MeetingMindApplicationTest.java`, `backend/src/test/java/com/meetingmind/demo/service/HttpAiGatewayClientEndpointTest.java`, `README.md`, `ai/.env.example`, `ai/onprem.env.example`, `ai/Dockerfile`, `ai/README.md`, `ai/app/text_generation_provider.py`, `ai/app/embedding_provider.py`, `ai/app/embedding_worker.py`, `ai/app/main.py`, `ai/app/observability.py`, `ai/onprem_poc_smoke.py`, `ai/onprem_poc_validate.py`, `ai/onprem_poc_run.sh`, `ai/onprem_poc_prepare_eval_db.sh`, `ai/tests/test_embedding_worker.py`, `ai/tests/test_meeting_ai.py`, `ai/tests/test_text_generation_provider.py`, `ai/tests/test_provider_boundary.py`, `ai/tests/test_onprem_compose_wiring.py`, `ai/tests/test_onprem_poc_*.py`.
- 구현: `TextGenerationProvider`와 `EmbeddingProvider` factory 및 provider 중심 `call_text_generation` entrypoint를 추가하고 `AI_TEXT_PROVIDER`, `AI_EMBEDDING_PROVIDER`로 `openai`/`local-openai-compatible`을 선택하게 했다. 기존 OpenAI provider는 유지했고, local provider는 OpenAI-compatible `responses` 또는 `chat-completions` text API와 `/embeddings` API를 사용한다. Meeting AI, Project AI, report, task, prompt, JSON parsing, citation validation, RAG query, chunk schema, worker claim/load/complete/swap 로직은 재사용했다. Backend AI gateway client와 internal endpoint 계약은 변경하지 않았고, `meetingmind.ai.base-url`은 기존 기본값을 유지한 채 `MEETINGMIND_AI_BASE_URL`로 명시 설정할 수 있게 yml에 선언했다. `compose.local.yml`의 `ai` profile에는 FastAPI `meetingmind-ai` 서비스와 `meetingmind-ai-worker`가 같은 provider/vector env를 받도록 연결하고, 기존 `/health`를 사용하는 `meetingmind-ai` healthcheck를 추가해 로컬 실행도 환경변수 전환 경계를 따른다. `ai/onprem.env.example`에는 최종 smoke/validator gate가 요구하는 local provider, streaming, retrieval, threshold env를 모았다. `onprem_poc_run.sh`는 첫 번째 인자 또는 `ONPREM_POC_ENV_FILE`로 명시 env file의 `KEY=VALUE`, `export KEY=VALUE`, 단순 quoted value를 로드한 뒤 smoke와 validator를 실행하고, 이미 export된 shell 환경변수를 env file 값보다 우선한다. Env loader는 shell identifier 형식 key만 허용하고 명령 치환을 실행하지 않으며, wrapper는 외부 `ONPREM_POC_MIN_STARTED_AT` 값을 신뢰하지 않고 이번 실행 시작 시각을 validator 호출에 주입해 오래된 result JSON을 최종 gate에 쓰지 못하게 한다. AI Docker image에는 `onprem_poc_smoke.py`, `onprem_poc_validate.py`, `onprem_poc_run.sh`, `onprem_poc_prepare_eval_db.sh`를 포함해 컨테이너 안에서도 같은 smoke/validator gate와 평가 DB 준비 절차를 실행할 수 있게 했다.
- 관측/검증: local streaming chat-completions 경로에서 `ttftMs`, `totalMs`, `tokensPerSecond`, token count/estimate를 수집하고 provider 완료 로그와 smoke metric에 남긴다. `/health`에는 provider id, local base URL configured 여부와 local-compatible 판정, internal service token configured 여부, API style, stream 여부, response format mode, embedding/vector dimension 양수 일치, DB configured 여부만 노출하고 token/base URL/DSN/secret 원문은 노출하지 않는다. Provider alias인 `local`, `openai-compatible`은 health/smoke config에서 `local-openai-compatible`으로 canonicalize해 validator 기준과 맞춘다. ASGI HTTP boundary 테스트는 실제 FastAPI app을 호출해 `/health` provider config와 `/api/internal/{meeting-ai/chat,meeting-ai/explain-term,project-ai/chat,meeting-ai/generate-report,meeting-ai/extract-tasks}` service-token 인증, trace header, response shape가 유지되는지 확인한다. `/health`와 smoke safe config 테스트는 validator의 민감 필드명 denylist 기준도 함께 적용해 `token`, `base_url`, `database_url`, `dsn` 같은 raw secret/endpoint 키가 결과 키로 다시 생기지 않게 고정한다. Backend `MeetingMindApplicationTest`는 `MEETINGMIND_AI_BASE_URL`과 `AI_INTERNAL_SERVICE_TOKEN` placeholder가 Spring context의 AI gateway beans까지 주입되는지 검증한다. Backend gateway 테스트는 Meeting/term, Project AI, report, task gateway client가 `MEETINGMIND_AI_BASE_URL`로 만든 internal endpoint와 `AI_INTERNAL_SERVICE_TOKEN` header, trace header, 기존 request/response shape를 유지하는지 local HTTP server로 검증한다. `onprem_poc_smoke.py`는 네트워크 호출 전 final smoke local provider 강제, local text/embedding base URL 절대 http(s) URL, api.openai.com/userinfo/query/fragment 차단, final smoke용 streaming chat-completions 설정, placeholder가 아닌 model, embedding/vector dimension, required retrieval DB 설정, `AI_INTERNAL_SERVICE_TOKEN` 설정 여부를 preflight로 확인한 뒤 provider probe, embedding probe, retrieval latency, Meeting AI, Project AI, report, task, unsupported guard, permission guard를 실행하고, retrieval scope는 `ONPREM_POC_PROJECT_ID`와 `ONPREM_POC_ALLOWED_MEETING_IDS` raw env를 직접 읽어 미설정 상태를 기본값으로 감추지 않는다. 결과 JSON에는 schema version, UTC 시작/완료 시각, 전체 duration, preflight flag를 `run` metadata로 남긴다. `ONPREM_POC_PREFLIGHT_ONLY=true`일 때는 provider/RAG 호출 없이 safe config만 출력해 env file을 먼저 확인할 수 있고, 이 모드에서만 placeholder 모델명을 허용한다. `onprem_poc_validate.py`는 preflight-only 결과를 최종 결과로 인정하지 않으며, 최종 결과 JSON의 run metadata, wrapper 실행 시작 시각 이후 생성된 result 여부, local provider, local base URL 구성과 local-compatible 판정, model 구성, placeholder가 아닌 실제 모델명, internal service token 설정 여부, result JSON 민감 필드명 부재, text provider probe의 실제 JSON parse/shape, text provider probe와 Meeting/Project/Report/Task generation 응답에서 관측한 모델명의 설정 모델 일치, embedding provider 응답에서 관측한 모델명의 설정 모델 일치, generation response format mode와 config 일치, stream option과 embedding dimensions option boolean safe config, streaming 기반 0 이상 TTFT, 각 scenario의 전체 소요 시간 `durationMs`, provider/retrieval metric의 scenario duration 상한과 run duration 일관성, embedding provider metric, retrieval 측정/source 반환/scope config, summary scenario count/failed scenario와 retrieval requirement를 포함한 metrics 재계산 값의 일치, metrics scenario 중복/unknown 방지, citation/JSON parsing success, hallucination proxy, permission `403` guard를 gate로 판정한다. Optional TTFT와 tokens/sec threshold는 provider probe뿐 아니라 Meeting AI, Project AI, report, task generation scenario 전체에 적용한다.
- 재사용 근거: Backend/Frontend API 계약은 변경하지 않았다. AI service의 legacy `call_openai_text` test hook과 internal endpoint shape, 기존 source scope validator, PostgreSQL/pgvector repository, embedding job queue/generation/swap, `embedding_chunks vector(1536)` 경계는 유지했다. Dimension mismatch와 local provider api.openai.com 또는 invalid base URL은 provider 초기화와 smoke preflight에서 실패시키고, 실패 메시지는 기존 worker 재색인/swap 경로와 schema migration을 먼저 사용하라고 안내한다. `test_provider_boundary.py`는 provider module 밖의 직접 provider HTTP 호출을 금지해 service/router/RAG 비즈니스 로직이 `TextGenerationProvider`와 `EmbeddingProvider` 경계를 우회하지 못하게 한다.
- Verification: `docker compose -f compose.local.yml --profile ai config --quiet`, `cd backend && ./gradlew test`, `cd backend && ./gradlew test --tests com.meetingmind.demo.MeetingMindApplicationTest`, `cd backend && ./gradlew test --tests com.meetingmind.demo.service.HttpAiGatewayClientEndpointTest`, `cd backend && ./gradlew test --tests com.meetingmind.demo.service.HttpMeetingAiGatewayClientTest --tests com.meetingmind.demo.service.HttpAiGatewayClientEndpointTest`, `cd backend && ./gradlew test --tests com.meetingmind.demo.MeetingMindApplicationTest --tests com.meetingmind.demo.service.HttpAiGatewayClientEndpointTest --tests com.meetingmind.demo.service.HttpMeetingAiGatewayClientTest`, `docker build -q ai` (`sha256:ed1de1c80a7f076e02e33dc2824a7f939d144a5e6d5387e47435d8336c21d5a5`), `docker run --rm sha256:ed1de1c80a7f076e02e33dc2824a7f939d144a5e6d5387e47435d8336c21d5a5 python -m compileall app onprem_poc_smoke.py onprem_poc_validate.py`, `docker run --rm sha256:ed1de1c80a7f076e02e33dc2824a7f939d144a5e6d5387e47435d8336c21d5a5 test -x /app/onprem_poc_run.sh -a -x /app/onprem_poc_prepare_eval_db.sh`, `docker run --rm sha256:ed1de1c80a7f076e02e33dc2824a7f939d144a5e6d5387e47435d8336c21d5a5 ./onprem_poc_run.sh /tmp/missing-onprem.env` exit 2 확인, `cd ai && ./.venv/bin/python -m unittest discover -s tests` 185건 통과(8 skipped), `cd ai && ./.venv/bin/python -m unittest tests.test_provider_url tests.test_onprem_poc_smoke tests.test_text_generation_provider tests.test_embedding_worker` 55건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_provider_url` 2건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_provider_boundary` 1건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_compose_wiring` 2건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_frontend_boundary` 1건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_run_script` 6건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_smoke` 29건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_validate` 32건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_validate tests.test_onprem_poc_smoke tests.test_onprem_poc_run_script` 68건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_text_generation_provider` 9건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai.FastApiHttpBoundaryTest` 6건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai.HealthTest tests.test_meeting_ai.FastApiHttpBoundaryTest` 11건 통과, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_smoke tests.test_meeting_ai.HealthTest tests.test_meeting_ai.FastApiHttpBoundaryTest` 40건 통과, `cd ai && ./.venv/bin/python -m compileall app/provider_url.py tests/test_provider_url.py tests/test_onprem_poc_smoke.py tests/test_text_generation_provider.py tests/test_embedding_worker.py`, `cd ai && ./.venv/bin/python -m compileall tests/test_onprem_poc_run_script.py`, `cd ai && ./.venv/bin/python -m compileall onprem_poc_smoke.py tests/test_onprem_poc_smoke.py tests/test_onprem_poc_run_script.py`, `cd ai && ./.venv/bin/python -m compileall app onprem_poc_smoke.py onprem_poc_validate.py tests/test_onprem_poc_smoke.py tests/test_meeting_ai.py`, `cd ai && ./.venv/bin/python -m compileall app onprem_poc_smoke.py onprem_poc_validate.py tests/test_onprem_poc_postgres_integration.py`, `bash -n ai/onprem_poc_run.sh ai/onprem_poc_prepare_eval_db.sh`, `set -a && source ai/onprem.env.example && set +a && test "$AI_TEXT_PROVIDER" = local-openai-compatible && test "$AI_TEXT_STREAM" = true && test "$ONPREM_POC_REQUIRE_RETRIEVAL" = true`, `cd ai && ONPREM_POC_PREFLIGHT_ONLY=true ONPREM_POC_RESULT_PATH=/tmp/meetingmind-preflight-wrapper-run.json ./onprem_poc_run.sh ./onprem.env.example` 통과 및 `run.resultSchemaVersion: 2`, `preflightOnly: true`, `internalServiceTokenConfigured: true` safe config 출력 확인, `cd ai && ./onprem_poc_run.sh ./onprem.env.example`는 placeholder 모델명 preflight error로 exit 1 확인, `git diff --check -- ai/app/provider_url.py ai/tests/test_provider_url.py ai/tests/test_onprem_poc_smoke.py ai/tests/test_text_generation_provider.py ai/tests/test_embedding_worker.py`, `git diff --check -- ai/onprem_poc_smoke.py ai/tests/test_onprem_poc_smoke.py ai/tests/test_onprem_poc_run_script.py`, `git diff --check -- ai/tests/test_onprem_poc_smoke.py ai/tests/test_meeting_ai.py`, `git diff --check -- backend/src/main/resources/application.yml backend/src/test/java/com/meetingmind/demo/MeetingMindApplicationTest.java backend/src/test/java/com/meetingmind/demo/service/HttpAiGatewayClientEndpointTest.java README.md ai compose.local.yml specs/001-meetingmind-core/implement.md specs/001-meetingmind-core/tasks.md` 통과. 로컬 `meetingmind-postgres-local`에 빈 평가 DB `meetingmind_onprem_eval_0722_1`, `meetingmind_onprem_eval_0722_2`, `meetingmind_onprem_eval_0722_3`를 만들고 Backend migration V1~V18을 적용했다. `ONPREM_POC_EVAL_DATABASE_NAME=meetingmind_onprem_eval_script_0722 ./ai/onprem_poc_prepare_eval_db.sh`로 helper의 Docker `psql` fallback을 검증했고, 같은 DB에서 `RUN_ONPREM_POC_POSTGRES_INTEGRATION=true AI_TEST_DATABASE_URL=postgresql://meetingmind:meetingmind_local@localhost:5434/meetingmind_onprem_eval_script_0722 ./.venv/bin/python -m unittest tests.test_onprem_poc_postgres_integration` 2건 통과. `AI_TEST_DATABASE_URL=postgresql://meetingmind:meetingmind_local@localhost:5434/meetingmind_onprem_eval_0722_1 ./.venv/bin/python -m unittest tests.test_embedding_repository.PostgresEmbeddingRepositoryIntegrationTest` 통과. `RUN_ONPREM_POC_POSTGRES_INTEGRATION=true AI_TEST_DATABASE_URL=postgresql://meetingmind:meetingmind_local@localhost:5434/meetingmind_onprem_eval_0722_3 ./.venv/bin/python -m unittest tests.test_onprem_poc_postgres_integration` 2건 통과해 mock OpenAI-compatible HTTP text/embedding provider가 run metadata를 포함한 최종 result shape로 validator 판정을 수행하고, 기존 worker, 실제 pgvector retrieval probe, 9개 smoke scenario와 validator 판정을 확인했다.
- Remaining boundary: 실제 vLLM/TGI/NIM 등 운영 local LLM endpoint와 실제 local embedding endpoint는 아직 제공되지 않았다. 따라서 T410은 완료 처리하지 않으며, `RUN_ONPREM_AI_POC_SMOKE=true` 결과 JSON이 실제 provider와 실제 PostgreSQL/pgvector DB에서 `onprem_poc_validate.py`를 통과해야 Day 2/3 온프레 PoC 완료로 본다.

## M131 Figma Make Space Invitation Integration

- 목표: canonical `/space-invitations/{spaceId}/{invitationId}#token=...` 화면에서 Make mock 수락/거절 상태를 제거하고 실제 Space invitation accept/decline API로 전환한다.
- 수정 파일: `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md`.
- 구현: `InvitationResponse`가 fragment의 `token`만 읽고 URL에서 즉시 제거한 뒤 `acceptSpaceInvitation`, `declineSpaceInvitation`을 호출하도록 바꿨다. 현재 로그인 계정 이메일, `spaceId`, `invitationId`, 처리 상태 badge를 실제 응답 흐름에 맞게 표시하고, 수락/거절 완료 후 `/spaces` 이동 링크를 제공한다. token이 없거나 API가 실패하면 mock 성공 대신 명시적 오류 상태를 보여준다.
- UX 근거: Space 초대 응답은 로그인 계정과 token 검증 결과를 바로 보여줘야 잘못된 계정으로 수락하는 실수를 줄일 수 있다. 완료 전에는 수락/거절 action만 노출하고, 완료 후에는 목록 복귀만 열어 상태를 단순화했다.
- Product 근거: MeetingMind는 Space 초대 token을 query/global state에 남기지 않고 API body에만 전달해야 한다. 권한 없는 사용자나 만료 token을 UI에서 성공처럼 보이면 안 되므로 실제 API 결과를 그대로 표시했다.
- 유지보수 근거: 이미 존재하는 `frontend/src/api/spaces.ts` 경계를 재사용했고 별도 mock adapter를 만들지 않았다. 기존 Make layout은 유지하고 데이터/행동만 target contract로 바꿨다.
- 영향 범위: `/space-invitations/:spaceId/:invitationId` 화면의 초대 응답 흐름만 변경했다. 다른 Space/Meeting route, 인증 구조, BFF same-origin/CSRF 정책은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint` 통과. lint는 `frontend/src/App.tsx`의 기존 unused warning 13건만 유지했고 새 오류는 없었다.

## M132 Figma Make Project Settings Integration

- 목표: `/spaces/{spaceId}/settings`의 Make mock 저장/삭제/토글을 제거하고 실제 Space detail/update/delete 계약에 맞춰 동작하게 한다.
- 수정 파일: `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md`.
- 구현: `ProjectSettings`가 `ShellOutletContext`의 `spaceDetail`, `spaceLoading`, `spaceError`, `reloadSpace`를 사용하도록 바꿨다. 이름/설명 폼은 `PATCH /api/v1/spaces/{spaceId}`에 연결했고, OWNER/ADMIN만 수정 가능하게 했다. 삭제는 `DELETE /api/v1/spaces/{spaceId}`에 연결했고 OWNER만 허용한다. 서버 계약이 없는 `Auto-confirm Reports`, `Live STT in Meetings`는 읽기 전용 상태로 내리고, archive action도 unavailable로 명시했다. `403`, `404`는 권한/존재 오류 문구로 분리했다.
- UX 근거: 프로젝트 설정 화면에서는 수정 가능한 항목과 아직 서버가 없는 항목을 섞어 성공처럼 보이면 안 된다. 실제로 저장 가능한 정보는 폼으로 유지하고, 미구현 항목은 읽기 전용으로 낮춰 기대를 조정했다.
- Product 근거: Space 수정/삭제는 role 기반 보호가 핵심이므로 최종 권한 판단은 backend 응답을 사용해야 한다. OWNER만 삭제 가능하다는 정책을 화면과 API 오류 양쪽에서 일관되게 드러냈다.
- 유지보수 근거: AppShell이 이미 소유한 `fetchSpaceDetail` 결과와 reload callback을 재사용해 중복 fetch를 만들지 않았다. `updateSpace`, `deleteSpace`, `ApiRequestError`만 연결해 최소 변경으로 끝냈다.
- 영향 범위: `/spaces/:spaceId/settings` 화면의 프로젝트 정보 저장/삭제와 미구현 옵션 표시만 변경했다. 다른 프로젝트 화면, AI, LiveKit, 멤버 관리, 인증 구조는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint` 통과. lint는 `frontend/src/App.tsx`의 기존 unused warning 13건만 유지했고 새 오류는 없었다.

## M133 Figma Make Account Settings Integration

- 목표: `/settings`의 Make mock profile/notification/security 저장 흐름을 제거하고 실제 세션 정보와 logout/logout-all API만 연결한다.
- 수정 파일: `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md`.
- 구현: `AccountSettings`가 `AuthContext`의 실제 session을 사용해 표시 이름, 이메일, 계정 상태, idle/absolute expiry, remember-me 상태를 렌더링하도록 바꿨다. 현재 기기 로그아웃은 `logoutCurrentSession()`, 모든 기기 로그아웃은 `AllDeviceLogoutModal` + `logoutAllDevices()`에 연결했고 성공 시 세션을 비우고 `/login`으로 이동한다. 프로필 수정, 알림 설정, 비밀번호 변경/계정 삭제는 서버 계약이 없으므로 읽기 전용 안내로 내렸다.
- UX 근거: 계정 설정에서 저장되지 않는 form과 토글을 그대로 두면 사용자가 성공을 기대하게 된다. 서버가 있는 세션 보안 action만 활성화하고 나머지는 unavailable로 명시해 오해를 줄였다.
- Product 근거: MeetingMind의 계정 보안은 same-origin 세션과 재인증 기반 모든 기기 로그아웃이 핵심이다. 세션 만료 시각과 현재 브라우저 종료 action을 직접 보여줘 계정 보안 흐름을 명확히 했다.
- 유지보수 근거: 기존 `logoutCurrentSession`, `logoutAllDevices`, `AllDeviceLogoutModal`, `AuthContext`를 재사용했고 새로운 API 형식을 만들지 않았다. Make layout은 유지하면서 mock 성공 처리만 제거했다.
- 영향 범위: `/settings` 화면의 세션 표시와 로그아웃 action만 변경했다. 인증 구조, BFF CSRF 정책, 다른 Space/Meeting route는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint` 통과. lint는 `frontend/src/App.tsx`의 기존 unused warning 13건만 유지했고 새 오류는 없었다.

## M134 Figma Make Terms Dictionary Integration

- 목표: `/spaces/{spaceId}/terms`의 정적 용어 목록과 local add/detail 상태를 제거하고 실제 용어 사전 CRUD API로 전환한다.
- 수정 파일: `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md`.
- 구현: `TermsDictionary`가 `fetchDomainTerms`, `createDomainTerm`, `updateDomainTerm`, `archiveDomainTerm`를 사용하도록 바꿨다. 목록은 실제 `ACTIVE` 용어만 불러오고 검색은 현재 로드된 목록에서 수행한다. 선택된 용어는 상세 패널에서 실제 term/definition을 편집할 수 있으며, OWNER/ADMIN만 저장/보관할 수 있다. 기존 mock category/full/usedIn 필드는 제거하고, 대신 실제 `status`와 `updatedAt`을 보여준다. 서버 없는 필드를 성공처럼 보이지 않게 했다.
- UX 근거: 용어 사전은 AI 근거 해석에 직접 쓰이므로 프로젝트별 실제 용어만 보여야 한다. category나 referenced-in 같은 가짜 보조정보보다 현재 정의와 수정 가능 여부를 우선 배치하는 편이 업무 화면에 맞다.
- Product 근거: MeetingMind는 프로젝트 공식 용어를 Project AI와 Meeting AI의 해석 기준으로 사용한다. 따라서 mock 용어를 노출하면 안 되고, 최종 근거는 backend의 Space 권한과 term store를 따라야 한다.
- 유지보수 근거: 이미 존재하는 `terms` API 모듈과 `ShellOutletContext`의 Space role을 재사용했다. 새 상태 관리나 별도 페이지를 만들지 않고 기존 Make split layout 안에서 데이터와 mutation만 교체했다.
- 영향 범위: `/spaces/:spaceId/terms` 화면의 목록, 상세, 등록, 수정, 보관 흐름만 변경했다. 다른 Space/Meeting/AI route, 인증 구조, BFF 정책은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint` 통과. lint는 `frontend/src/App.tsx`의 기존 unused warning 13건만 유지했고 새 오류는 없었다.

## M135 Figma Make Project Knowledge CRUD Integration

- 목표: `/spaces/{spaceId}/knowledge`의 조회 전용 그래프 화면에 실제 지식 생성/수정/보관 흐름을 추가한다.
- 수정 파일: `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md`.
- 구현: `ProjectKnowledge`가 `createProjectKnowledge`, `updateProjectKnowledge`, `deleteProjectKnowledge`를 사용하도록 확장했다. 그래프와 폴더 구조는 유지한 채 좌측 패널에 `Add Knowledge` action을 추가했고, 우측 패널에서 새 지식 생성 또는 선택된 지식의 title/content 편집과 보관을 수행한다. OWNER/ADMIN만 mutation을 실행할 수 있고 MEMBER는 조회만 가능하다. 빈 상태에서도 생성 버튼을 바로 제공하며, 저장/보관 후에는 목록과 상세를 다시 불러와 embedding 상태와 최신 내용을 유지한다.
- UX 근거: Project Knowledge는 단순 시각화가 아니라 공식 지식 관리 화면이므로 조회 전용 상태에 머물면 작업 흐름이 끊긴다. 같은 패널 안에서 생성/수정/보관을 처리해 그래프 문맥을 잃지 않게 했다.
- Product 근거: MeetingMind의 Project AI는 공식 Project Knowledge를 검색 대상으로 삼는다. 따라서 mock 노드가 아니라 실제 backend 지식만 등록/수정/보관되어야 하며, 권한 없는 사용자가 수정 성공처럼 보이면 안 된다.
- 유지보수 근거: 기존 knowledge API 모듈과 `ShellOutletContext`의 Space role을 재사용했고, 별도 페이지나 상태 라이브러리를 추가하지 않았다. 기존 그래프 빌더와 상세 fetch를 유지하면서 mutation 경계만 추가했다.
- 영향 범위: `/spaces/:spaceId/knowledge`의 생성/수정/보관과 empty/saving/error UI만 변경했다. AI 검색, 다른 Space/Meeting route, 인증 구조는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint` 통과. lint는 `frontend/src/App.tsx`의 기존 unused warning 13건만 유지했고 새 오류는 없었다.

## M136 Figma Make Meeting Actions Cleanup

- 목표: `/spaces/{spaceId}/meetings`와 `/spaces/{spaceId}/meetings/{meetingId}`에 남아 있던 mock action을 실제 동작 또는 명시적 unavailable 상태로 정리한다.
- 수정 파일: `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md`.
- 구현: `MeetingList`의 `Schedule Meeting` 버튼을 실제 `createMeeting` mutation에 연결했고, 제목/설명/시작/종료 시간을 받는 생성 모달을 추가했다. 생성 성공 시 목록을 다시 불러오고 새 회의 상세로 이동한다. `Filter` 버튼은 no-op 상태를 없애고 제목/호스트/상태 기준의 실제 로컬 필터 패널로 교체했다. `MeetingOverview`의 `Invite` 버튼은 현재 canonical meeting invitation API가 없으므로 disabled 상태와 안내 문구로 내렸다.
- UX 근거: 눌러도 아무 일도 일어나지 않는 action은 업무 화면에서 신뢰를 떨어뜨린다. 생성 가능한 기능은 바로 저장되게 하고, 아직 서버 계약이 없는 기능은 unavailable로 명시해 성공 오해를 막았다.
- Product 근거: 회의 생성은 transcript/report/task 흐름의 시작점이므로 실제 backend mutation으로 이어져야 한다. 반면 회의 이메일 초대는 현재 `userId` 기반 participant grant 수준만 존재해 end-user invitation flow로 보이면 안 된다.
- 유지보수 근거: 기존 `createMeeting`, `fetchMeetings`, `fetchMeetingDetail` API 모듈만 재사용했고 새 상태 관리 계층은 추가하지 않았다. 회의 목록/상세 레이아웃은 유지하면서 action 경계만 바꿨다.
- 영향 범위: 회의 목록 상단 action, 필터 상태, 회의 상세 참가자 패널 상단 안내만 변경했다. transcript/report/task/live/AI route와 인증 구조는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run lint` 통과. build는 기존 Vite chunk size warning만 출력했고, lint는 `frontend/src/App.tsx`의 기존 unused warning 13건만 유지했다.

## M137 Live STT False Failure Root Cause Fix

- 목표: 실시간 회의 재입장 또는 중복 시작 시 backend가 이미 전사를 진행 중이어도 frontend가 `STT Failed`로 잘못 표시하는 문제를 근본적으로 제거한다.
- 수정 파일: `frontend/src/api/client.ts`, `frontend/src/App.tsx`, `frontend/src/pages/LiveRoomPage.tsx`, `frontend/src/api/workspace.test.ts`, `specs/001-meetingmind-core/implement.md`.
- 구현: BFF/API 에러 파싱에서 backend `code`를 `ApiRequestError`에 보존하도록 바꿨다. `LiveMeeting`과 `LiveRoomPage`는 `409 TRANSCRIPTION_ALREADY_PROCESSING`를 실패가 아니라 기존 전사 세션 재사용 상태로 처리한다. 또 두 화면 모두 `fetchMeetingDialogue()`의 `status`를 STT 상태의 진실값으로 사용해, polling 결과가 `PROCESSING` 또는 `COMPLETED`면 active/connected로 되돌리고 `FAILED`일 때만 실패 상태를 유지한다.
- UX 근거: 사용자는 이미 자막이 생성 중인데 상단 배지에 `STT Failed`가 보이면 시스템이 망가졌다고 판단한다. backend 실제 상태와 UI 상태를 맞춰야 혼란이 반복되지 않는다.
- Product 근거: MeetingMind의 live transcript는 회의 중 핵심 신뢰 지표다. 같은 회의에 대한 재접속이나 track 재발행이 전사 실패처럼 보이면 STT 기반 회의 경험 전체 신뢰가 무너진다.
- 유지보수 근거: 호출부마다 예외 copy를 덧붙이지 않고 공통 API 에러 객체와 live 상태 판단 지점만 수정했다. 이후 같은 backend 오류 코드를 다른 화면에서도 재사용할 수 있다.
- 영향 범위: live meeting 두 구현의 STT 상태 배지/배너와 transcription start 오류 처리만 변경했다. 인증, 라우팅, backend API 계약은 바꾸지 않았다.
- Verification: `cd frontend && npx vitest run src/api/workspace.test.ts`, `cd frontend && npm run build`.

## M138 Instant Meeting Room Reuse Backend

- 목표: Space마다 같은 회의방을 반복 사용하되, transcript/report/task/Meeting AI 데이터는 회차별 `meetingId`로 계속 분리한다.
- 수정 파일: `backend/src/main/java/com/meetingmind/demo/{controller/SpaceController.java,domain/Meeting.java,domain/WorkspaceStore.java,domain/WorkspaceDomainService.java,domain/InMemoryWorkspaceStore.java,domain/JdbcWorkspaceStore.java,domain/JpaWorkspaceStore.java,service/MeetingLiveKitTokenService.java,dto/CreateInstantMeetingResponse.java}`, `backend/src/main/resources/db/migration/V19__add_meeting_room_code.sql`, `backend/src/test/java/com/meetingmind/demo/{controller/SpaceControllerTest.java,domain/MeetingLiveKitTokenServiceTest.java}`, `specs/001-meetingmind-core/contracts/{meeting-api.md,live-stt-api.md}`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/tasks.md`.
- 구현: `meetings.room_code`를 추가하고, `POST /api/v1/spaces/{spaceId}/instant-meetings`가 `space-room-{spaceId}`를 재사용 room code로 가진 `IN_PROGRESS` 회의를 즉시 생성하도록 했다. 생성자는 자동으로 `HOST` participant가 된다. LiveKit token 발급은 `meeting.roomCode ?? meetingId`를 roomName으로 사용하도록 바꿨다. 이로써 같은 Space 회의방 반복 입장과 회차별 `meetingId` 분리를 동시에 유지한다.
- UX 근거: 사용자는 매번 회의를 새로 만들지 않고 바로 같은 프로젝트 회의방에 들어가길 원한다. 반면 transcript/report/task는 회차 경계가 분리되어야 검색과 회의록이 섞이지 않는다.
- Product 근거: MeetingMind는 회의방 자체보다 회의 결과물의 프로젝트 지식화를 다룬다. 따라서 room 재사용은 허용하되 Meeting AI와 회의 산출물 scope는 기존 `meetingId` 단위를 유지해야 한다.
- 유지보수 근거: 새 room 테이블 없이 `meetings.room_code`와 기존 `meetingId` 계약을 재사용했다. 기존 scheduled meeting은 `roomCode`가 없으면 기존 `meetingId` roomName fallback을 써서 회귀를 줄였다.
- 영향 범위: backend meeting 생성/LiveKit roomName 결정 경로와 관련 계약 문서만 변경했다. frontend route, AI scope 로직, 인증 구조는 이번 범위에 포함하지 않았다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.controller.SpaceControllerTest --tests com.meetingmind.demo.domain.MeetingLiveKitTokenServiceTest`.

## M139 Target Meeting Report Lifecycle Alignment

- 목표: target `/spaces/:spaceId/meetings/:meetingId/report` 화면이 실제 `CANDIDATE -> DRAFT -> CONFIRMED` 회의록 상태와 API 결과만 표시하도록 정리한다.
- 수정 파일: `frontend/src/pages/ReportAgentPage.tsx`, `specs/001-meetingmind-core/{tasks,implement}.md`.
- 구현: report candidate 생성, detail 조회, AI edit candidate, 수동 본문 저장, 확정, 버전 선택/복원, 다운로드를 기존 report API로 연결했다. 저장되지 않은 자동 저장 표시와 local AI apply/revert, mock commit 목록을 제거했다. 태스크 후보 편집/등록 UI는 보고서 화면에서 제거하고 canonical Meeting Task Candidate route 링크로 분리했다.
- UX 근거: 회의록 후보와 공식 회의록, 태스크 후보는 서로 다른 상태와 다음 행동을 가진다. 한 화면에서 가짜 변경과 태스크 등록을 함께 보여주면 보고서가 실제로 저장되거나 확정된 것으로 오해할 수 있다.
- Product 근거: 보고서는 현재 회의 근거로 생성되고 사용자가 검토 후 확정하는 공식 기록이다. 태스크 후보는 별도 검토 후에만 칸반으로 이어져야 한다.
- 유지보수 근거: 새 API나 상태 라이브러리를 추가하지 않고 기존 `reports` API client와 이미 존재하는 Meeting Task Candidate route를 재사용했다. target route의 data boundary를 하나의 report detail 상태로 제한했다.
- 영향 범위: target/legacy ReportAgentPage의 보고서 작업 화면만 변경했다. Backend API, BFF, 인증, 권한 계약, Task Candidate API는 변경하지 않았다.
- Verification: `cd frontend && npm run build` 성공, `cd frontend && npm run test -- --run` 5 files/38 tests 통과, `cd frontend && npm run lint` 오류 0건. lint에는 기존 `frontend/src/App.tsx`의 unused warning 11건만 남았고, build에는 기존 Vite chunk-size warning만 출력됐다. `git diff --check` 통과. 사용자 요청에 따라 인증된 브라우저의 candidate 생성/AI 수정/저장/확정 실동작은 사용자 수동 테스트로 남긴다.

## M140 Knowledge RAG Cluster Graph

- 목표: Knowledge 화면을 문서 type hub가 아닌 권한 필터된 RAG source의 의미 클러스터 노드로 전환한다.
- 결정: cluster는 별도 DB entity로 저장하지 않는다. active embedding generation을 source centroid로 집계한 read model이며, Core가 먼저 `allowedMeetingIds`를 계산한 뒤 AI가 SQL scope에 강제한다.
- 계약: `GET /api/v1/spaces/{spaceId}/knowledge/graph`의 node/edge/cluster projection을 `contracts/knowledge-api.md`, `data-model.md`, `erd.md`에 추가했다.
- 구현: Core `KnowledgeGraphService`가 인증 사용자 기준으로 기존 `AiSearchScopeResolver.projectScope()`를 호출해 active SpaceMember와 `allowedMeetingIds`를 선필터한다. Core는 AI internal `/api/internal/knowledge/graph`에 이 scope만 전달하며, BFF는 public graph route를 Core로만 프록시한다. AI repository는 active/COMPLETED embedding을 source centroid로 집계하고, project knowledge 또는 허용 meeting source만 SQL 조건으로 읽어 similarity edge와 connected-component cluster를 계산한다. Frontend Knowledge 화면은 source-level node/edge와 cluster folder를 표시하며 meeting node는 읽기 전용, 공식 knowledge node만 기존 CRUD detail 화면을 연다.
- 권한/보안: raw chunk, embedding vector, 권한 밖 meeting ID는 응답에 포함하지 않는다. source metadata의 meeting link도 Core가 허용한 meeting 범위에서만 AI에 도달한다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.service.HttpAiGatewayClientEndpointTest --tests com.meetingmind.demo.MeetingMindApplicationTest`, `cd bff && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`, `cd ai && ./.venv/bin/python -m unittest tests.test_knowledge_graph tests.test_meeting_ai.FastApiHttpBoundaryTest -v`, `cd ai && ./.venv/bin/python -m compileall app`, `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint` 통과했다. 로컬 pgvector DB에서 빈 Space 대상으로 `PostgresEmbeddingRepository.knowledge_graph()`를 read-only 실행해 `nodes=0 edges=0`을 확인했다. Frontend lint에는 기존 `App.tsx` unused warning 11건, build에는 기존 Vite chunk-size warning만 남았다. 인증된 populated Space의 브라우저 시각 검증은 사용자 수동 테스트로 남긴다.

## M141 Active Calendar and Kanban Interaction Fix

- 목표: 활성 legacy 화면의 Calendar query 계약 오류와 Kanban 카드 이동 누락을 고친다.
- 구현: `ProjectCalendar`이 월 시작과 다음 달 시작을 `Date#toISOString()`으로 전송하도록 수정했다. `ProjectTasks`에는 브라우저 native drag event를 추가해 TODO, IN_PROGRESS, IN_REVIEW, DONE 컬럼 drop이 기존 `PATCH /api/v1/spaces/{spaceId}/tasks/{taskId}`의 status-only request를 호출하도록 연결했다. 저장 성공 뒤 `loadTasks()`로 서버 상태를 재조회하며 실패 시 카드 위치는 바꾸지 않고 오류를 표시한다.
- UX 근거: 카드 이동은 상태 select와 같은 상태 변경이어야 하며, 실패한 이동을 완료처럼 보이면 안 된다. ISO-8601 instant는 시간대에 따라 조회 범위가 달라지는 Calendar API 계약을 안정적으로 지킨다.
- Product 근거: FR-KAN-04의 상태 컬럼 이동을 활성 화면에서 제공하고, meeting schedule은 Space 범위의 실제 시간 흐름으로 조회한다.
- 유지보수 근거: 새 dependency나 API를 추가하지 않고 기존 native DOM event와 task/calendar client를 재사용했다.
- 영향 범위: `frontend/src/App.tsx`의 활성 legacy Calendar와 Kanban interaction만 변경한다. backend, BFF, 권한, route 계약은 변경하지 않는다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run` 39건 통과, `cd frontend && npm run lint` 오류 0건, `git diff --check` 통과. 기존 `App.tsx` unused warning 11건과 Vite chunk-size warning만 남았다.

## M142 Task Review Status and Overview Completion

- 목표: 칸반의 `In Review` 열을 실제 TaskCard 상태로 저장하고, Project Overview의 열린 태스크를 완료 처리한다.
- 계약/데이터: `TaskCardStatus`와 `task_cards.status` check constraint에 `IN_REVIEW`를 추가했다. 기존 Flyway migration은 수정하지 않고 V20 forward migration으로 제약을 교체한다. Kanban API, data model, ERD, 상태 기준선도 같은 enum을 사용한다.
- 구현: 활성 legacy `ProjectTasks`의 In Review column은 기존 status PATCH를 drop target으로 사용한다. Overview의 각 열린 태스크 check control은 event propagation을 멈춘 뒤 같은 PATCH에 `DONE`을 전송하고, 성공하면 Space detail을 다시 읽어 카드와 완료율을 갱신한다. 실패 시 상태를 낙관적으로 바꾸지 않고 오류만 표시한다.
- 권한: 새로운 권한 예외는 만들지 않는다. 기존 active SpaceMember task update 권한을 서버가 그대로 검증한다.
- Verification: `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.WorkspaceCrudServiceTest`, 새 빈 PostgreSQL DB에서 `CI_POSTGRES_URL=jdbc:postgresql://127.0.0.1:5434/meetingmind_migration_v20_test CI_POSTGRES_USER=meetingmind CI_POSTGRES_PASSWORD=... ./gradlew test --tests com.meetingmind.demo.MigrationIntegrationTest`, `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check`를 통과했다. Frontend lint는 기존 `App.tsx` unused warning 11건만 남고 오류는 없다.

## M143 Knowledge Graph Viewport Fix

- 문제: Knowledge 그래프 노드는 1000px 기준 좌표를 사용하지만 SVG에 viewBox가 없어, 폭이 좁은 중앙 패널에서는 노드가 화면 밖에 렌더링됐다.
- 구현: SVG에 `viewBox="0 0 1000 700"`와 `preserveAspectRatio="none"`을 지정해 기존 노드/edge 좌표를 현재 패널 폭과 높이에 맞춰 표시한다. Knowledge 목록과 생성/상세 패널은 각 경계의 드래그 핸들로 조절하며, 목록은 200~420px, 우측 패널은 240~420px 범위로 제한하고 중앙 그래프는 남은 너비를 사용한다. 데이터, RAG scope, API 계약은 변경하지 않는다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `git diff --check`.

## M144 Report Generation Readiness

- 문제: 전사가 없는 회의에서도 Report 화면이 생성 버튼을 보여 실제 연동이 되지 않은 것처럼 보였고, 활성 `App.tsx` 라우트는 API 기반 Report 페이지 대신 Make 정적 mock을 렌더링하고 있었다.
- 구현: 활성 `/spaces/{spaceId}/meetings/{meetingId}/report` 라우트를 API 기반 `ReportAgentPage`로 교체했다. Report 화면이 `GET /api/v1/meetings/{meetingId}/dialogue`의 상태와 확정 세그먼트 수를 조회한다. `COMPLETED`이고 비어 있지 않은 전사에서만 생성 버튼을 활성화하며, 처리 중·실패·빈 전사는 현재 이유를 표시한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `git diff --check`.

## M145 Meeting Report Review Clarity

- 목표: 실제 API 기반 Meeting Report 화면에서 전사 준비, 후보/초안/확정 상태, 근거, 공식 확정 행동을 명확히 한다.
- 구현: `ReportAgentPage`는 공통 `StatusBadge`로 현재 report와 transcript 상태를 헤더에 표시하고 transcript 화면으로 이동할 수 있게 했다. report source ID는 편집 본문 하단 Evidence 영역에 표시한다. 후보 확정은 공통 `ConfirmDialog`를 거치며, CONFIRMED report는 저장 action을 노출하지 않는다. 중복된 candidate 생성 action은 review panel에서 제거하고 빈 상태의 `Generate report`를 유일한 생성 action으로 유지했다.
- UX 근거: 회의록은 전사 근거를 바탕으로 생성되고 검토 후 공식 기록이 된다. 상태를 여러 곳에서 반복하거나 즉시 확정시키는 UI는 실제 저장/확정 여부를 오해하게 만든다.
- Product 근거: `CANDIDATE -> DRAFT -> CONFIRMED` 상태 모델과 회의별 공식 report 1개 원칙을 UI가 그대로 따라야 한다. source ID는 아직 transcript 시간대 deep link 계약이 없으므로 식별자로만 표시한다.
- 유지보수 근거: 기존 reports/transcripts API와 공통 상태·확인 컴포넌트를 재사용했다. API, 권한, route, 데이터 모델은 변경하지 않았다.
- 영향 범위: `/spaces/:spaceId/meetings/:meetingId/report`의 실제 API 화면과 report 전용 CSS만 변경한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check` 통과. lint는 기존 `App.tsx` unused warning 12건만 유지했고 새 오류·warning은 없다. build는 기존 Vite chunk-size warning만 출력됐다.

## M146 Meeting Report Workspace Design Pass

- 목표: API 기반 Meeting Report를 보고서 편집과 검토가 분명한 업무형 화면으로 정리한다.
- 구현: report 전용 CSS에서 8px 기반의 surface radius, blue/white token, compact action, 문서형 입력 표면, 360px review panel을 적용했다. 1080px 이하에서는 review panel이 본문 아래로 이동하고, 700px 이하에서는 header와 상태/action 영역이 세로로 재배치된다. 보라색 gradient와 과도한 radius는 report scope에서 제거했다.
- UX 근거: 회의록 화면의 우선순위는 보고서 본문, 전사 근거, 검토/확정이다. 시각 장식보다 상태와 다음 행동을 가까이 두고, 좁은 화면에서는 편집 흐름을 우선하는 편이 인지 부담을 줄인다.
- Product 근거: 화면은 CANDIDATE/DRAFT/CONFIRMED 상태와 전사 기반 근거를 명확히 보여야 하며, 이 디자인 변경은 해당 동작·권한·API 결과를 바꾸지 않는다.
- Figma skill note: 기존 Figma Make URL은 `/make/` 형식이라 Figma MCP의 node/variable inspection 대상이 아니다. `figma-use` 및 `figma-generate-design` 지침을 따라 현재 API 화면 구조·기존 token·공통 component를 기준으로 디자인을 적용했다.
- 영향 범위: `frontend/src/styles/app.css`의 `.report-agent-*` selector와 responsive rule만 변경한다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check` 통과. 테스트는 5 files/39 tests 통과했고, lint는 기존 `App.tsx` unused warning 12건만 유지했다. build는 기존 Vite chunk-size warning만 출력됐다.

## M147 Report CSS Module Extraction

- 목표: 누적된 `app.css`에서 현재 API 기반 Meeting Report의 전용 스타일을 별도 stylesheet로 분리한다.
- 구현: `styles/report.css`를 만들고 report history, `.report-agent-*`, `.report-workspace-page` 및 task candidate review selector를 이동했다. 현재 API 화면을 덮는 design pass는 파일의 마지막에 두고, `main.tsx`가 `app.css` 다음에 `report.css`를 import해 cascade 순서를 보존한다.
- 유지보수 근거: Report route의 시각 책임을 한 파일로 모아 selector 탐색과 변경 범위를 줄인다. 앱 전역 scale selector와 Meeting AI와 함께 쓰는 반응형 selector만 `app.css`에 남겨 다른 화면의 layout 회귀를 막는다.
- 영향 범위: stylesheet import와 최신 Report CSS 위치만 바뀌며 API, 컴포넌트, route, 권한, 데이터는 변경하지 않는다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check` 통과. 테스트는 5 files/39 tests 통과했고, lint는 기존 `App.tsx` unused warning 12건만 유지했다. build는 기존 Vite chunk-size warning만 출력됐다.

## M148 Meeting Report State-focused Workspace

- 목표: 전사 준비 전의 빈 화면, 생성된 보고서, AI 수정 요청의 다음 행동을 한 화면에서 명확히 한다.
- 구현: 전사가 준비되지 않은 경우 강조된 transcript 상태 badge와 transcript 이동 링크를 표시하고, 보고서가 없을 때는 아이콘·설명·단일 primary `Generate report` action을 제공한다. 생성된 실제 API report는 markdown heading/list를 안전한 read-only 문서 구조로 표시하며, `Edit report`를 누른 경우에만 기존 저장용 입력 폼을 연다. AI 수정 패널에는 실제 report가 있을 때만 선택 가능한 3개 예시 prompt를 제공한다.
- UX 근거: 빈 상태에서 현재 원인과 다음 행동을 함께 보여주고, 생성 후에는 textarea보다 읽기 쉬운 문서가 먼저 보이도록 해야 회의록의 검토와 확정 흐름이 명확해진다.
- Product 근거: 보고서는 전사 근거로 생성된 candidate를 검토한 뒤 공식 기록으로 확정하는 산출물이다. 임의 mock report를 만들지 않고 실제 report API 응답만 렌더링해 성공 상태를 가장하지 않는다.
- 유지보수 근거: 기존 report/transcript API, `StatusBadge`, `ConfirmDialog`와 CSS scope를 재사용했다. API, route, 인증, 권한, 데이터 모델은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check` 통과. 테스트는 5 files/39 tests 통과했고, lint는 기존 `App.tsx` unused warning 12건만 유지했다. build는 기존 Vite chunk-size warning만 출력됐다.

## M149 Meeting Report Header and Review Panel Boundaries

- 목표: transcript 실패 상태와 report review/AI 입력의 상태 경계를 좁은 우측 panel에서도 명확히 보인다.
- 구현: `Transcript unavailable` badge를 amber warning pill로 표시하고, report 상태 메시지는 기존 muted text로 유지했다. AI 입력은 `flex: 1` field wrapper와 shrink되지 않는 Send button으로 구성해 우측 panel에서 잘리지 않게 했으며, report가 없을 때 disabled input wrapper에 안내 tooltip을 제공한다. Review 영역은 아이콘이 있는 empty state를 사용하고, report candidate가 없으면 task candidate 이동 link를 disabled presentation으로 표시한다.
- UX 근거: 실패 원인은 상단에서 빠르게 식별해야 하고, 비활성 입력과 빈 review 영역은 왜 행동할 수 없는지 보여줘야 한다. 좁은 panel에서 전송 action이 잘리면 사용자는 동작 가능 여부를 판단할 수 없다.
- 유지보수 근거: `StatusBadge`와 기존 route를 재사용하고 Report 전용 CSS만 추가했다. API, route, 인증, 권한, 데이터 모델은 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check` 통과. 테스트는 5 files/39 tests 통과했고, lint는 기존 `App.tsx` unused warning 12건만 유지했다. build는 기존 Vite chunk-size warning만 출력됐다.

## M150 ConfirmDialog Modal Rendering

- 문제: `ConfirmDialog`는 modal DOM과 `.mm-confirm-dialog*` stylesheet를 이미 갖고 있었지만 `common.css`가 entry에서 import되지 않아 title, description, action이 일반 text처럼 출력됐다. 또한 dialog가 Report frame 안에 있어 scale/overflow 영향을 받을 수 있었다.
- 구현: `main.tsx`가 공통 `common.css`를 token 다음에 import하도록 연결했다. `ConfirmDialog`는 `createPortal(..., document.body)`로 overlay를 root 밖에 렌더링하고, backdrop 클릭과 Escape 키는 busy 상태가 아닐 때 cancel 처리한다. 공통 action 영역은 오른쪽 정렬로 고정했고, 중복된 `Confirm action` label은 제거했다.
- 영향 범위: 같은 공통 dialog를 사용하는 화면도 정상 modal styling과 stacking context를 공유한다. report confirm API, route, 인증, 권한, 상태 전이는 변경하지 않았다.
- Verification: `cd frontend && npm run build`, `cd frontend && npm run test -- --run`, `cd frontend && npm run lint`, `git diff --check` 통과. 테스트는 5 files/39 tests 통과했고, lint는 기존 `App.tsx` unused warning 12건만 유지했다. build는 기존 Vite chunk-size warning만 출력됐다.

## M151 Meeting Report Review Workspace

- 목표: 실제 API 기반 회의록을 문서 열람, AI 수정 제안, 검토와 공식 확정의 순서로 분리한다.
- 구현: header는 프로젝트/회의/회의록 breadcrumb, 실제 회의 일시와 참가자 수, report/transcript 상태, `전사 보기`, 더보기 메뉴, 상태별 단일 주요 action으로 구성했다. `CANDIDATE`, `DRAFT`, `CONFIRMED`는 한국어 상태로 표시하며, report 본문은 heading/list/bold만 안전하게 읽기 전용 markdown 문서로 렌더링한다. 근거는 raw source UUID 대신 `전사 근거 N개`와 canonical transcript 이동 링크만 표시한다.
- AI 편집: AI 패널을 `AI 편집 도우미`로 바꾸고 실제 report가 있을 때만 예시 요청을 제공한다. AI 수정 결과는 즉시 현재 문서에 반영하지 않고, 이전/제안 내용 비교 후 사용자가 `보고서에 적용` 또는 `취소`를 선택하게 했다. 생성된 candidate 자체는 기존 API가 서버에 저장하므로, 취소는 현재 화면의 적용만 취소한다.
- 검토/반응형: 우측 검토 패널은 실제 source 수와 markdown에서 읽은 결정/액션 항목 수만 사용한다. 후보가 없으면 task review 이동을 비활성 상태로 보여준다. 700px 이하에서는 `보고서`와 `AI 편집` 탭으로 한 번에 한 작업만 표시하고 주요 action을 하단에 고정한다.
- UX 근거: 회의록의 공식 확정과 AI 제안 적용은 서로 다른 결과를 낳으므로, 초안 편집 제안을 확정 흐름과 분리해야 사용자가 저장·확정 상태를 오해하지 않는다. 좁은 화면에서는 문서와 AI를 동시에 압축하기보다 작업 맥락을 분리하는 편이 읽기와 입력을 보존한다.
- Product 근거: 보고서는 전사 근거를 바탕으로 생성·검토·확정되는 공식 회의 기록이며, AI는 문서의 수정 제안을 보조할 뿐 무단으로 확정하지 않는다.
- 유지보수 근거: 기존 `reports`/`transcripts` API와 target route, 공통 ConfirmDialog를 유지하고 Report 전용 stylesheet에 화면 책임을 모았다. Backend, BFF, 인증, 권한, 데이터 모델은 변경하지 않았다.
- 계약 제한: 현재 `sourceIds`에는 timestamp, speaker, excerpt가 없어 문장 단위 source preview나 해당 시점 deep link를 만들지 않았다. report route에는 Task Candidate 상세가 없어 담당자·기한·완료 상태를 표시하지 않았다. 선택 영역만 AI에 보내는 API와 candidate discard API도 없으므로 각각 전체 보고서 수정 요청과 화면상 적용 취소만 지원한다.
- Verification: `cd frontend && npm run build` 성공, `cd frontend && npm run test -- --run` 5 files/39 tests 통과, `cd frontend && npm run lint` 오류 0건, `git diff --check` 통과. lint에는 기존 `frontend/src/App.tsx` unused warning 12건, build에는 기존 Vite chunk-size warning만 남았다. browser에서는 인증 세션이 없어 target report route가 로그인 화면으로 보호되는 것까지 확인했으며, 실제 보고서 데이터의 시각/상호작용 검증은 사용자 수동 테스트로 남긴다.

## M040 Knowledge Graph Contract Expansion

- 범위: 기존 Knowledge Graph API의 additive 확장 계약, read model/ERD 경계, Topic 및 Participant 정책
- 변경 파일: `contracts/knowledge-api.md`, `data-model.md`, `erd.md`, `clarify.md`, `tasks.md`
- 결정: 기존 `clusters`, `edges`, `generatedAt` 응답은 유지하고 `nodes`, richer edges/clusters, filters, partial/truncated 상태와 node detail endpoint를 추가 계약으로 정의한다.
- 권한: Backend가 SpaceMember와 MeetingParticipant를 먼저 계산하고 요청 meetingIds는 `allowedMeetingIds`와 교집합한다. detail 조회도 동일 권한을 재검사한다.
- 개인정보: Participant node는 정책 확정 전 기본 제외한다. Topic은 Phase 1에서 서버 파생 결과로만 제공한다.
- 구현 상태: 문서 단계만 완료했다. Backend/AI/Frontend 코드는 다음 단계에서 계약 기준으로 구현한다.
- 검증: 신규 섹션과 D-047/D-048, T423-T426 존재 확인 및 `git diff --check`를 실행했다. 코드 테스트와 빌드는 문서 전용 변경이므로 실행하지 않았다.

## T427 Backend Knowledge Graph Filters

- 변경 파일: `backend/src/main/java/com/meetingmind/demo/controller/KnowledgeGraphController.java`, `backend/src/main/java/com/meetingmind/demo/service/KnowledgeGraphService.java`
- 구현: 기존 graph route에 `meetingIds`와 `nodeTypes` query를 추가했다. Backend가 계산한 `allowedMeetingIds`와 요청 회의 ID를 교집합한 뒤 AI gateway에 전달하며, 응답에서도 보이는 node/edge를 다시 필터링한다.
- 보안: 클라이언트 필터를 권한 경계로 사용하지 않는다. 지원하지 않는 node type은 `400 INVALID_GRAPH_FILTER`로 거부한다. Participant는 계약대로 타입만 예약하고 현재 응답에 임의로 노출하지 않는다.
- API 영향: 기존 query 없는 호출과 기존 응답 필드는 유지한다. 새 query는 additive다.
- 검증: `cd backend && ./gradlew compileJava` 성공, `cd backend && ./gradlew test --tests com.meetingmind.demo.service.HttpAiGatewayClientEndpointTest` 성공. `git diff --check`는 관련 변경 검증을 계속 유지한다.

## T428 AI Knowledge Graph Response Extension

- 변경 파일: `ai/app/main.py`
- 구현: 기존 `clusters`, `edges`, `generatedAt`를 유지하면서 flat `nodes`와 nodeType,
  entityId, connectionCount, clusterIds, edge id/type/weight, cluster nodeIds/nodeCount/
  colorKey, filters/truncated metadata를 추가했다.
- 범위: source-level centroid를 그대로 사용하고 raw chunk를 반환하지 않는다. Participant는
  현재 source mapping에 포함하지 않는다.
- 호환성: 기존 `meeting` source type도 허용해 기존 unit test와 저장 데이터 입력을 보존한다.
- 검증: `cd ai && ./.venv/bin/python -m unittest tests.test_knowledge_graph` 2건 통과,
  `python -m compileall app/main.py tests/test_knowledge_graph.py` 통과,
  `git diff --check` 통과.

## T429 Frontend Knowledge Graph Contract Integration

- 변경 파일: `frontend/src/types.ts`, `frontend/src/api/knowledge.ts`, `frontend/src/App.tsx`
- 구현: 확장 graph response의 flat `nodes`, node/cluster metadata를 optional 타입으로 추가하고, 응답에 flat nodes가 있으면 이를 우선 렌더링한다. 구 응답은 cluster nodes fallback으로 계속 지원한다.
- 필터: API client에 `meetingIds`/`nodeTypes` query 전달을 추가했다. 권한 판단은 Backend에 남기고 Frontend는 표시·탐색 목적의 필터만 전달한다.
- 영향: 기존 Knowledge graph 레이아웃과 folder/type/similarity cluster UI는 유지하며 인증·권한·데이터 저장 로직은 변경하지 않았다.
- 검증: `cd frontend && npm run build` 성공. Vite chunk-size warning만 기존과 같이 남았다.

## M041 Semantic Color Token Consolidation

- 목표: 기존 MeetingMind 파랑 accent를 유지하면서 공통 색상 의미를 토큰으로 분리해 버튼, 링크, 상태 배지, 선택 영역의 색상 규칙을 재사용한다.
- 변경 파일: `frontend/src/styles/tokens.css`, `frontend/src/components/common/common.css`, `frontend/src/styles/app.css`.
- 구현: accent subtle/border/text/hover/active, link, selection, info, success, warning, danger 토큰을 추가했다. 공통 primary button hover, breadcrumb hover, info/positive/warning status 및 role badge가 semantic token을 사용하도록 연결했고, `app.css`의 반복되는 accent/text/background/status 색상값을 같은 토큰으로 치환했다.
- 영향 범위: 기존 layout, route, API, 인증, 권한, 상태 전이는 변경하지 않았다. 개별 의미가 다른 나머지 특수 색상은 기존 값을 유지했다.
- UX 근거: 동일 의미의 action과 상태가 화면마다 다른 색으로 보이지 않도록 공통 컴포넌트의 의미 토큰을 단일화했다.
- 검증: `git diff --check` 통과, `cd frontend && npm run build` 성공. 기존 Vite chunk-size warning만 남았다.

## M042 Workspace and Profile Images

- 변경 파일: Core Space/Auth model 및 controller, BFF proxy route, Frontend `App.tsx`/session/space API client, V23 migration, API·ERD·data model contract 문서.
- 구현: 프로필과 Space 대표 이미지는 `multipart/form-data`로 Core에 업로드하고, DB에는 delivery URL만 저장한다. 파일은 JPEG/PNG/WebP, 최대 5MB로 제한한다. Space 대표 이미지 업로드는 OWNER/ADMIN만 가능하며 프로필 수정은 현재 사용자만 가능하다.
- 변경 결정: S3 연동은 폐기하고 로컬 파일 저장소(`MEETINGMIND_IMAGE_UPLOAD_DIR`, 기본 `.local-uploads/images`)를 사용한다. 이미지는 `/api/v1/assets/images/{profiles|spaces}/{ownerId}/{filename}` 경로로 제공하며 BFF proxy를 경유한다.
- 검증: `cd backend && ./gradlew compileJava`, `./gradlew test --tests com.meetingmind.demo.controller.SpaceControllerTest`, `cd bff && ./gradlew compileJava && ./gradlew test --tests com.meetingmind.bff.proxy.ProxyRouteRegistryTest`, `cd frontend && npm run build`, `git diff --check`를 통과했다. Frontend는 기존 Vite chunk-size warning만 남았다.

## M043 AI Reliability Harness and Operational Verification Planning

- 목표: 요구사항 정의서 기준으로 AI 기능의 운영 검증 범위를 먼저 고정한다. 범위는 Meeting AI, Project AI, AI Report, Task extraction, Terms Dictionary, RAG retrieval, STT/LiveKit smoke, 외부 provider 장애 대응, Prometheus/Grafana 관측이다.
- 변경 파일: `specs/001-meetingmind-core/ai-harness-strategy.md`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 결정: AI 하네스는 권한 선필터, scope envelope, evidence gate, citation validation, prompt injection guard, token budget, provider failure normalization, log redaction을 독립 검증 단위로 둔다. STT/LiveKit/OpenAI/RAG 실제 smoke는 provider credential이 필요한 opt-in 검증으로 분리한다.
- 현재 근거: `test-matrix.md`의 SR-005, SR-007, SR-008은 STT/RAG/provider 품질의 기존 검증 근거로 유지한다. BFF `DownstreamGuard`, Backend/Core `AiGatewayGuard`, BFF/Backend Prometheus endpoint, AI `/metrics` 기준선은 코드와 테스트로 확인됐다.
- 남은 작업: AH-001~AH-014 자동 테스트, SMK-003~SMK-005 smoke, 외부 API provider failure execution matrix, STT gateway/AI provider worker guard 보강, Grafana dashboard/provisioning 추가.
- 검증: 문서 전용 변경으로 backend/frontend/ai build는 실행하지 않는다. Markdown/diff sanity만 실행한다.

## T435.1 AI Harness Unit Coverage

- 변경 파일: `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: Project AI에서 `allowedMeetingIds=[]`가 전체 회의 허용으로 확장되지 않고 Postgres RAG request에 빈 tuple로 전달되는지 테스트했다. source context limit은 지정 개수만 유지하고 순서를 바꾸지 않는지 검증했다. supported 응답 관측 로그가 질문 원문, source 원문, answer 원문을 포함하지 않는지도 추가로 고정했다.
- 범위: AI runtime behavior는 변경하지 않고 기존 harness 정책의 회귀 테스트만 추가했다.
- 검증: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai` 71건 통과.

## T435.2 AI Harness Scope Rejection and Report Context Limit

- 변경 파일: `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: Project AI 요청에서 `allowedMeetingIds=[]`인데 회의 source가 직접 제공되면 `AI_CONTEXT_FORBIDDEN`으로 거부되는지 테스트했다. AI Report 생성은 provider에 전달하는 untrusted source context를 첫 12개 source로 제한하는지 검증했다.
- 범위: AI runtime behavior는 변경하지 않고, 빈 회의 권한 범위와 report context budget이 후속 변경으로 넓어지지 않도록 회귀 테스트만 추가했다.
- 검증: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai` 73건 통과.

## T436.1 Operational Smoke Runbook

- 변경 파일: `specs/001-meetingmind-core/operational-smoke-runbook.md`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: STT/LiveKit/AI Report/RAG smoke를 기본 local deterministic check와 provider opt-in check로 분리했다. AI unit/on-prem HTTP smoke, PostgreSQL-backed STT/report/AI service tests, `RUN_CLOVA_STT_SMOKE=true` provider smoke, AI on-prem/OpenAI-compatible final smoke, product E2E manual flow를 같은 runbook에 정리했다.
- 결정: provider credential, PCM sample, public callback URL, local OpenAI-compatible provider endpoint는 기본 CI 요구사항이 아니다. 실제 provider smoke는 opt-in으로만 실행하고, 실행 결과는 runbook의 execution record template에 기록한다.
- 안전 기준: provider failure는 normalized error와 trace ID 중심으로 기록하고, prompt/STT/report/answer/API key/LiveKit token 원문은 로그나 smoke output에 남기지 않는다. `allowedMeetingIds=[]`일 때 meeting source를 검색하지 않는 기존 AI harness 기준도 smoke failure handling rule에 연결했다.
- 검증: `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_http_smoke`는 현재 provider HTTP env가 없어 1건 skip으로 정상 종료했다. `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_smoke tests.test_onprem_poc_validate tests.test_onprem_poc_run_script` 69건 통과, `cd ai && ./.venv/bin/python -m compileall app onprem_poc_smoke.py onprem_poc_validate.py tests/test_onprem_poc_smoke.py tests/test_onprem_poc_validate.py tests/test_onprem_poc_run_script.py` 통과, `git diff --check -- specs/001-meetingmind-core/operational-smoke-runbook.md specs/001-meetingmind-core/test-matrix.md specs/001-meetingmind-core/tasks.md specs/001-meetingmind-core/implement.md` 통과.

## T437 External API Reliability Policy

- 변경 파일: `specs/001-meetingmind-core/contracts/external-reliability.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: Google OAuth, Auth JWKS, LiveKit, Soniox/OpenAI/Clova STT, Backend/Core -> AI, AI text generation/embedding provider, PostgreSQL/pgvector, Redis, SMTP에 대한 timeout, retry 허용 범위, fallback, 사용자 메시지, 로그 금지 항목을 표로 고정했다.
- 근거: BFF는 `DownstreamGuard`와 `DownstreamHttpClient`로 bulkhead/circuit open/half-open probe를 이미 사용한다. Backend/Core는 `application.yml`의 `jwks-request-timeout=2s`, `HttpMeetingAiGatewayClient`/`HttpProjectAiGatewayClient`/`HttpKnowledgeGraphGatewayClient`의 `30s`, `HttpReportAiGatewayClient`/`HttpTaskAiGatewayClient`의 `60s`, `HttpTranscriptionGateway`의 `10s`, AI `text_generation_provider.py` timeout 설정을 기준으로 문서를 작성했다.
- 결정: 근거 없음/저품질은 `200 unsupported=true`, provider timeout/connection/malformed output은 `503 AI_PROVIDER_UNAVAILABLE`로 정규화한다. mutation은 자동 retry하지 않고, background embedding job만 backoff retry를 허용한다. provider 원문 오류, prompt, transcript, answer, token, secret은 사용자 응답과 로그에 노출하지 않는다.
- 후속 gap: Prometheus/Grafana 노출과 dashboard는 `T439` 후속이다.
- 검증: `git diff --check -- specs/001-meetingmind-core/contracts/external-reliability.md specs/001-meetingmind-core/tasks.md specs/001-meetingmind-core/test-matrix.md specs/001-meetingmind-core/implement.md` 통과.

## T438 Backend/Core AI Gateway Guard

- 변경 파일: `backend/src/main/java/com/meetingmind/demo/service/AiGatewayGuard.java`, `backend/src/main/java/com/meetingmind/demo/service/AiGatewayGuardPolicy.java`, `backend/src/main/java/com/meetingmind/demo/service/AiGatewayGuardRejectedException.java`, `backend/src/main/java/com/meetingmind/demo/service/HttpMeetingAiGatewayClient.java`, `backend/src/main/java/com/meetingmind/demo/service/HttpProjectAiGatewayClient.java`, `backend/src/main/java/com/meetingmind/demo/service/HttpReportAiGatewayClient.java`, `backend/src/main/java/com/meetingmind/demo/service/HttpTaskAiGatewayClient.java`, `backend/src/main/java/com/meetingmind/demo/service/HttpKnowledgeGraphGatewayClient.java`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/meetingmind/demo/service/AiGatewayGuardTest.java`, `backend/src/test/java/com/meetingmind/demo/service/HttpMeetingAiGatewayClientTest.java`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: Backend/Core -> AI internal HTTP 경계에 공통 `AiGatewayGuard`를 추가했다. guard는 semaphore bulkhead, 연속 실패 임계치, open duration, half-open probe를 제공한다. Meeting/Project/Report/Task/Knowledge Graph gateway client는 같은 정책 키를 사용하고, 회로가 열린 경우에도 기존 `AiGatewayException` 경로로 정규화해 상위 service의 `503 AI_PROVIDER_UNAVAILABLE` 또는 `503 KNOWLEDGE_GRAPH_UNAVAILABLE` 매핑을 유지한다.
- 설정: `meetingmind.ai.guard.max-concurrent`, `meetingmind.ai.guard.failure-threshold`, `meetingmind.ai.guard.open-duration` 기본값 `16`, `3`, `30s`를 추가했다.
- 검증: `cd backend && ./gradlew test --tests com.meetingmind.demo.service.AiGatewayGuardTest --tests com.meetingmind.demo.service.HttpMeetingAiGatewayClientTest` 통과.
- 남은 제약: 이번 범위는 Backend/Core -> AI gateway 경계만 보호한다. STT gateway bulkhead/circuit과 AI service 내부 provider worker 분리는 별도 작업으로 남는다.

## T439 Prometheus and Observability Baseline

- 변경 파일: `bff/build.gradle`, `bff/src/main/resources/application.yml`, `bff/src/main/java/com/meetingmind/bff/observability/DownstreamGuardMetrics.java`, `bff/src/main/java/com/meetingmind/bff/config/ProxyConfiguration.java`, `bff/src/main/java/com/meetingmind/bff/proxy/DownstreamGuard.java`, `bff/src/main/java/com/meetingmind/bff/proxy/DownstreamHttpClient.java`, `bff/src/test/java/com/meetingmind/bff/BffHealthEndpointTest.java`, `bff/src/test/java/com/meetingmind/bff/proxy/DownstreamGuardTest.java`, `backend/build.gradle`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/meetingmind/demo/BackendActuatorEndpointTest.java`, `ai/requirements.txt`, `ai/app/observability.py`, `ai/app/repository.py`, `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/contracts/observability.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: BFF와 Backend에 Prometheus registry와 endpoint exposure를 추가했다. BFF `DownstreamGuard`는 service별 rejection/opened/open gauge를 기록한다. AI는 `/metrics` endpoint를 노출하고 endpoint duration/source count, provider duration/token usage/failure, RAG retrieval duration/result count, embedding queue gauge를 기록한다.
- 기준: Grafana panel 기준은 `contracts/observability.md`에 고정했다. STT/LiveKit custom metric은 이번 범위에서 패널 요구만 정의하고 구현은 후속으로 남겼다.
- 보안: metric label과 로그에 prompt, transcript 원문, answer 원문, secret, token, DSN을 넣지 않는다.
- 남은 제약: Backend STT/LiveKit custom metric, Grafana dashboard json/provisioning, Prometheus scrape config는 아직 미구현이다.
- 후속 UI 결정: Space Overview의 `Knowledge Indexed`는 운영 의미가 약하므로 화면 지표에서 제외하고, Space 단위 `AI Usage/quota` 지표로 대체한다. 이 값은 frontend 계산으로 만들지 않고 Backend/AI 집계 API로 노출한다. 최소 필드는 `limit`, `totalRequests`, `totalInputTokens`, `totalOutputTokens`, `usagePercent`, `meetingAiRequests`, `projectAiRequests`, `reportAiRequests`다.

## M043 Requirement Matrix Tightening

- 변경 파일: `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: 기존 `Authz and LiveKit Access` 중심 문서를 요구사항 기반 전사 검증 매트릭스로 재정렬했다. 문서 앞단에 현재 상태 표를 추가해 권한, AI scope, STT/LiveKit smoke, AI Report -> Knowledge, guest/ACL negative, 외부 API resilience, observability를 한 번에 읽을 수 있게 했다.
- 결정: 현재 완료로 강하게 말할 수 있는 범위는 `권한/LiveKit access 자동화`, `AI harness 일부 자동화`, `Prometheus endpoint 기준선`, `external reliability policy 문서화`까지다. `SMK-003~SMK-005`, `AH 전항목 자동화`, `Grafana provisioning`, `STT/LiveKit custom metric`은 아직 완료로 표기하지 않는다.
- 이유: 지금 필요한 것은 새 smoke를 과장해서 완료 처리하는 것이 아니라, 요구사항 정의서 기준으로 어디까지 자동화되었고 어디가 수동/미완료인지 흔들림 없이 보이게 만드는 것이다.
- 검증: `git diff --check -- specs/001-meetingmind-core/test-matrix.md specs/001-meetingmind-core/implement.md` 통과.

## T435.3 AI Harness Provider Contract Drift Fix

- 변경 파일: `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: AI unittest가 오래된 2-value provider mock `(text, model)`과 현재 runtime 3-value contract `(text, model, usage)`가 섞여 있던 문제를 정리했다. `ai/app/main.py`는 forward-ref union import 오류 없이 테스트에서 import 가능하도록 정리했고, `ai/tests/test_meeting_ai.py`는 report/task/project/meeting harness mock을 모두 최신 contract에 맞췄다.
- 원인: provider usage metric이 runtime에는 이미 추가되었는데, 테스트 fixture와 일부 응답 모델 annotation이 이전 계약에 머물러 있었다. 그 결과 unittest가 실제 harness 검증 전에 import/runtime unpack error로 먼저 깨졌다.
- 검증: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai` 76건 통과. `AH-008`, report untrusted context, task context limit, provider error normalization, log redaction 회귀가 함께 검증된다.

## T436.2 Local Deterministic Smoke Re-run

- 변경 파일: `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: `operational-smoke-runbook.md` 기준으로 provider key 없이 돌릴 수 있는 deterministic smoke를 다시 실행해 현재 기준선을 확인했다.
- 실행 결과:
  - `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai` -> 76건 통과
  - `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_http_smoke` -> 1건 skip, 정상 종료
  - `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.MeetingReportLifecycleServiceTest` -> 통과
  - `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.ProjectAiServiceTest` -> 통과
- 판단: `SMK-001` 로컬 자동화 기준선은 유지된다. `SMK-002~SMK-005`는 여전히 provider/env/browser 수동 검증이 필요하므로 완료 처리하지 않는다.

## V119.1 Requirement Verification Sync

- 변경 파일: `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/operational-smoke-runbook.md`, `specs/001-meetingmind-core/contracts/external-reliability.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: 요구사항 ID별 상태표를 현재 실행 결과에 맞게 다시 맞췄다. `SMK-001`은 2026-07-25 local deterministic PASS로 기록했고, `SMK-002~SMK-005`는 provider/env/browser 의존성이 남아 있는 opt-in/manual 영역으로 유지했다. 외부 API 장애 대응 정책은 문서 규칙만 남기지 않고 BFF/Backend/AI의 실제 runtime class와 test 파일까지 매핑했다.
- 판단: 지금 단계에서 완료라고 말할 수 있는 것은 문서/자동화 기준선 정합성이다. 실제 운영 smoke 전부 완료는 아니다. 따라서 `V119` 본체는 계속 pending이고, 문서 동기화 하위 작업만 완료 처리했다.
- 검증: `git diff --check -- specs/001-meetingmind-core/test-matrix.md specs/001-meetingmind-core/operational-smoke-runbook.md specs/001-meetingmind-core/contracts/external-reliability.md specs/001-meetingmind-core/tasks.md specs/001-meetingmind-core/implement.md` 통과.

## V119.2 Guard and Metrics Evidence Sync

- 변경 파일: `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/contracts/external-reliability.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: 문서가 여전히 `T438` 이전 계획 상태를 일부 유지하고 있던 부분을 정리했다. Backend/Core -> AI guard가 이미 runtime에 반영되어 있다는 점과, BFF/Backend Prometheus endpoint 검증이 실제 테스트로 통과한다는 점을 현재 상태표와 정책 문서에 다시 연결했다.
- 검증:
  - `cd backend && ./gradlew test --tests com.meetingmind.demo.service.AiGatewayGuardTest --tests com.meetingmind.demo.BackendActuatorEndpointTest`
  - `cd bff && ./gradlew test --tests com.meetingmind.bff.BffHealthEndpointTest --tests com.meetingmind.bff.proxy.DownstreamGuardTest`
  - `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai`
- 판단: 이제 남은 큰 검증 공백은 `SMK-002~005` 실행 증적, `AH-009` token budget 자동화, Grafana/STT-LiveKit 관측 보강이다. 문서 기준선과 구현 사이의 드리프트는 이 범위에서 정리됐다.

## V119.3 Local Runtime Smoke Entry Fix

- 변경 파일: `scripts/run-ai.sh`, `scripts/run-local-stack.sh`, `specs/001-meetingmind-core/operational-smoke-runbook.md`, `specs/001-meetingmind-core/implement.md`.
- 구현: provider smoke 진입 전제였던 local runtime 실행 경로를 정리했다. `scripts/run-ai.sh`는 project virtualenv의 `ai/.venv/bin/uvicorn`을 우선 사용하도록 바꿨고, `scripts/run-local-stack.sh`는 AI까지 같이 올리도록 보완했다.
- 원인: 기존 AI 실행 스크립트는 system `python3 -m uvicorn`에 의존했다. 이 상태에서는 개발 환경에 따라 AI 서버가 뜨지 않거나, 8000 포트에 남은 stale listener 때문에 smoke가 false negative로 보일 수 있었다.
- 판단: 이 수정은 `SMK-002~005` 완료가 아니라 smoke 진입 조건 정리다. 남은 것은 실제 provider/browser/manual 증적 채우기다.

## M152 NonProd V2 AI Container Security Remediation

- 원인: `ai/Dockerfile`의 가변 `python:3.12-slim`은 현재 Python 3.12.13 / Debian 13.6 trixie로 해석된다. 실제 NonProd V2 ARM64 tag의 OS 패키지는 Debian 저장소 최신 버전이었지만 Amazon Inspector의 ECR 지원 Debian 범위는 11/12라서 trixie가 지원 경계 밖에 있었다. ECR finding의 `glibc`, `perl`, `sqlite3`, `util-linux`, `diffutils`는 OS package이고 FastAPI/Starlette/Uvicorn/psycopg가 원인이 아니었다.
- 구현: base를 `python:3.12.13-slim-bookworm@sha256:d50fb7611f86d04a3b0471b46d7557818d88983fc3136726336b2a4c657aa30b`으로 고정했다. 이 multi-arch index의 Linux/ARM64 child는 `sha256:c18c7a910432dde3311fc54d02e5d5220f3ebe26fec43ff15745982863dd7b3b`이다. 임시 컨테이너의 `apt-get -s upgrade`에 적용 대상이 없어 apt upgrade layer를 추가하지 않았고 `ai/requirements.txt`는 변경하지 않았다.
- ARM64 산출물: `docker build --platform linux/arm64 --provenance=false --no-cache --tag meetingmind-ai:security-after ai`가 성공했고 single child digest는 `sha256:2f8aa4212b745b4259b55f4e4ebfc3881aada8b85f4ded0469b5eb306ebe5161`이다. `linux/arm64`, Debian 12 bookworm, `meetingmind` uid/gid 999, 약 64.5 MB를 확인했다.
- 검증: `docker build --check --platform linux/arm64 ai`는 warning 0건, 컨테이너 `python -m compileall app onprem_poc_smoke.py onprem_poc_validate.py` 통과, 컨테이너 Python 3.12에서 `python -m unittest discover -s tests -v`는 189건 중 182건 통과·외부 PostgreSQL/OpenAI 필요 7건 skip으로 성공했다. `trivy image --exit-code 1 --ignore-unfixed --no-progress --scanners vuln --severity HIGH,CRITICAL meetingmind-ai:security-after`는 OS/Python 모두 0건으로 통과했다.
- ECR 반증 결과: 사용자가 같은 single ARM64 child digest를 ECR에서 조회한 결과 `status=COMPLETE`, CRITICAL 3/HIGH 5/MEDIUM 4/LOW 1로 실패했다. CRITICAL은 `perl 5.36.0-7+deb12u3`의 `CVE-2026-57433`, `CVE-2026-12087`, `CVE-2026-13221`이다. 따라서 `--ignore-unfixed` local pass는 ECR gate를 대표하지 못하며 Bookworm 전환은 최종 해결책이 아니다.
- 패키지 경계: Bookworm의 `perl-base`, `coreutils`, `util-linux`는 Essential/required이고 security repository에 위 CRITICAL 전체 fixed version이 없다. 강제 purge, sid/forky package 혼합, scanner metadata 삭제는 지원 가능성과 실제 보안을 훼손하므로 사용하지 않는다.
- Alpine 사전 조사: 공식 `python:3.12.13-alpine3.24@sha256:6d43704baacd1bfbe7c295d7f13079d5d8104ed33568873133f8fc69980419df`의 Linux/ARM64 child는 `sha256:900229622a576409d52f7a66b24cf441415a828d7e503b0107bf56452a4e44ac`이다. base Trivy 전체 scan은 CRITICAL/HIGH 0이고 pip MEDIUM 4/LOW 1만 남았다. 현재 requirements는 ARM64 musllinux binary wheel만으로 dry-run 설치가 성공했다.
- T434 구현: `ai/Dockerfile`을 pinned `python:3.12.13-alpine3.24@sha256:6d43704baacd1bfbe7c295d7f13079d5d8104ed33568873133f8fc69980419df`로 전환하고 `apk add --no-cache bash`, `addgroup -S`, `adduser -S -G meetingmind -h /app`만 추가했다. `ai/app/**`와 `ai/requirements.txt`는 변경하지 않았다.
- T434 산출물: `docker build --check --platform linux/arm64 ai`는 warning 0건, `docker build --platform linux/arm64 --provenance=false --no-cache --tag meetingmind-ai:security-alpine ai`가 성공했다. local single-platform image digest는 `sha256:c3ee717afe7da78823d13779bbda0956834fe815c7f7f7ab1c29209dd6dd45be`, size는 36,487,931 bytes이며 `linux/arm64`, Alpine 3.24.1, Python 3.12.13, `meetingmind` uid 100/gid 101을 확인했다. `/bin/bash`가 존재하고 `perl` command가 없음을 확인했다.
- T435 검증: 컨테이너 native import(`psycopg`, `uvloop`, `httptools`, `pydantic_core`, `yaml`), `python -m compileall app onprem_poc_smoke.py onprem_poc_validate.py`, `bash -n onprem_poc_run.sh onprem_poc_prepare_eval_db.sh`가 통과했다. `python -m unittest discover -s tests -v`는 189건 중 182건 통과·7건 skip으로 성공했다. skip은 기존대로 `AI_TEST_DATABASE_URL` 미설정 3건, on-prem PostgreSQL 통합 조건 미충족 2건, OpenAI 외부 호출 opt-in 미설정 3건, 합계 7건이다.
- T436 구현/검증: `.github/workflows/ci.yml`의 AI image scan만 `--ignore-unfixed`를 제거하고 다른 이미지 scan 명령은 유지했다. `trivy image --exit-code 1 --no-progress --scanners vuln --severity HIGH,CRITICAL meetingmind-ai:security-alpine`는 Alpine OS와 Python package 모두 0건으로 종료했고 `git diff --check`도 통과했다. Trivy의 Alpine 3.24 EOL 목록 경고는 보안 finding이 아닌 informational warning이며 scan gate는 성공했다.
- T437 ECR 결과: 사용자 승인 후 local image `meetingmind-ai:security-alpine`을 ECR `meetingmind-nonprod-v2-ai`에 immutable tag `c3ee717afe7da78823d13779bbda0956834fe815c7f7f7ab1c29209dd6dd45be`로 push했다. 수정 commit을 만들지 않은 상태였으므로 tag는 push 대상 single ARM64 image의 full config/image digest를 사용했다. ECR returned manifest/child digest는 로컬과 같은 `sha256:c3ee717afe7da78823d13779bbda0956834fe815c7f7f7ab1c29209dd6dd45be`이고 media type은 `application/vnd.docker.container.image.v1+json`이다. ECR reported image size는 36,485,437 bytes다.
- T437 ECR scan: `describe-image-scan-findings`를 tag가 아닌 returned child digest로 조회했으며 `status=COMPLETE`, `description=The scan was completed successfully.`, `findings=[]`, `findingSeverityCounts={}`를 반환했다. 따라서 ECR basic scan 기준 CRITICAL/HIGH 0이며 MEDIUM/LOW도 0이다.
- T438 closeout: T434~T438을 완료 처리했다. 로컬 Trivy는 `--ignore-unfixed` 없이 HIGH/CRITICAL 0, ECR child scan은 COMPLETE/0/0으로 일치했다. secret 출력/기록은 없었고 `ai/app/**`, `ai/requirements.txt`, Backend/Frontend/BFF/STT, Terraform, ECS/runtime 설정은 변경하지 않았다. 기존 사용자 변경은 보존했고 commit/push는 수행하지 않았다.

## V119.4 SMK-002 Local Tier Evidence and Merge Regression Fix

- 변경 파일: `backend/src/main/resources/application.yml`, `backend/src/test/java/com/meetingmind/demo/service/SttTranscriptFlowIntegrationTest.java`, `scripts/run-local-stack.sh`, `specs/001-meetingmind-core/operational-smoke-runbook.md`, `specs/001-meetingmind-core/implement.md`.
- 배경: `SMK-002` 진입을 준비하면서 dev 병합 직후의 backend 기동 경로와 STT 전사 지속성 검증을 실제로 실행했다. 문서상 근거로 지정돼 있던 검증이 실제로는 한 번도 실행된 적 없음을 확인했다.
- 회귀 1 (backend 기동 불가): dev 병합(`1b4dffc`)이 `backend/src/main/resources/application.yml`에 `management:` 블록을 중복 생성했다. dev는 파일 상단에 `include: health`만 있는 블록을 추가했고, 이 브랜치는 `T439`에서 하단에 `include: health,info,prometheus`와 `endpoints.access.default`, `prometheus.access`를 포함한 블록을 갖고 있었다. 두 삽입 위치가 겹치지 않아 git이 충돌 없이 양쪽을 모두 남겼고, 같은 document 안에 같은 key가 두 번 생겨 SnakeYAML `DuplicateKeyException`으로 Spring context 자체가 로드되지 않았다. dev 블록은 이 브랜치 블록의 진부분집합이므로 상단 dev 블록을 제거하고 `T439` 블록만 남겼다.
- 회귀 1 발견 지연 이유: 8080에 떠 있던 backend 프로세스는 병합 이전에 기동된 것이라 병합된 설정을 읽지 않았다. 따라서 실행 중인 서비스만 보면 정상으로 보였고, 새로 기동하는 순간에만 실패하는 상태였다.
- 회귀 2 (SMK-002 근거 미실행): `SttTranscriptFlowIntegrationTest`는 `@Primary` `SttProvider` 후보가 둘이 되어 `NoUniqueBeanDefinitionException`으로 context 로드에 실패하는 상태였다. `ConfiguredSttProvider`가 `b327508`에서 `@Primary`를 얻었고 테스트도 fake를 `@Primary`로 등록했기 때문이다. 이 테스트는 `@EnabledIfEnvironmentVariable(CI_POSTGRES_URL)`로 게이트되어 있고 지금까지 그 env가 설정된 실행이 없었기 때문에 계속 skip으로 넘어가 깨진 사실이 드러나지 않았다. runbook은 이 테스트를 `SMK-002` local 근거로 지정하고 있었으므로, 해당 근거는 실제로 존재하지 않았다.
- 회귀 2 수정 방식: 테스트 fake의 `@Primary`를 제거하고 `STT_PROVIDER=fake-clova` system property로 `ConfiguredSttProvider`가 fake를 선택하게 했다. `DotenvConfig.optional`이 system property를 최우선으로 읽으므로 운영과 같은 provider 선택 경로를 그대로 통과하며, `@Primary`를 우회하지 않는다.
- `SMK-002` local tier 실행 결과 (실제 PostgreSQL, docker `meetingmind-postgres-local`, host port 5434):
  - `SttTranscriptFlowIntegrationTest` -> 1건 실행/0 skip/통과. transcript `COMPLETED` 전이, segment 2건 순서 및 speaker 보존, `embedding_jobs`의 `TRANSCRIPT_COMPLETED` 1건 enqueue를 확인했다. enqueue는 `V12__finalize_vector_search_jobs.sql`의 `meeting_transcript_embedding_job_trigger`가 수행하므로 실제 DB trigger 경로까지 검증된다.
  - `MeetingLiveKitTokenServiceTest` -> 5건 실행/0 skip/통과. LiveKit token 발급 시 room/identity/만료와 권한 거부 분기를 확인했다. 단 이는 mock 기반이므로 실제 LiveKit 서버 접속 근거는 아니다.
  - `BackendActuatorEndpointTest` 2건, `CoreHealthEndpointTest` 1건 통과로 회귀 1 수정이 `T439` prometheus 노출과 dev가 추가한 health 검증을 동시에 만족함을 확인했다.
- `run-local-stack.sh` 보완: runbook이 요구하던 "smoke 진입 전 포트 점유 확인"이 스크립트에 구현돼 있지 않았다. backend/ai/bff/frontend 포트를 기동 전에 모두 확인해 점유 시 점유 프로세스를 출력하고 중단하도록 했고, `nohup` 직후 생존 여부까지 확인해 포트 bind 실패나 venv 누락으로 즉시 죽은 경우를 성공으로 보고하지 않게 했다. 기존 프로세스를 자동으로 종료하지는 않는다.
- 남은 `SMK-002` 공백: provider tier는 여전히 미완료다. runbook이 지정한 opt-in 검증은 `ClovaSttTranscriptSmokeIntegrationTest`(`RUN_CLOVA_STT_SMOKE`) 하나인데 이는 `clova-nest`를 대상으로 한다. 반면 실제 runtime 기본 provider는 `ConfiguredSttProvider`의 `soniox-realtime`이고 fallback은 `openai-realtime`이다. 현재 환경에는 Soniox/OpenAI 키가 있고 Clova 키는 없으므로, 문서가 지정한 근거는 실행할 수 없고 실제로 실행될 provider에는 대응 smoke가 없다. 이 불일치를 먼저 정리해야 `SMK-002`를 닫을 수 있다.
- 판단: 이번 범위로 `SMK-002`의 local tier 근거는 처음으로 실제 확보됐고, 병합으로 들어온 기동 불가 회귀도 제거됐다. 그러나 LiveKit 실제 입장과 provider STT 전사 증적은 확보하지 못했으므로 `SMK-002` 본체와 `V119`는 계속 pending으로 둔다.
- 검증: `cd backend && CI_POSTGRES_URL=... ./gradlew test --tests com.meetingmind.demo.domain.SttTranscriptFlowIntegrationTest --tests com.meetingmind.demo.domain.MeetingLiveKitTokenServiceTest --tests com.meetingmind.demo.BackendActuatorEndpointTest --tests com.meetingmind.demo.CoreHealthEndpointTest` 통과, `zsh -n scripts/run-local-stack.sh` 통과, 점유 포트 상태에서 preflight가 exit 1로 중단함을 확인했다.

## T441/T442 Isolated Test DB and DB-gated Verification Recovery

- 변경 파일: `scripts/run-db-tests.sh`, `backend/src/test/java/com/meetingmind/demo/MigrationIntegrationTest.java`, `backend/src/test/java/com/meetingmind/demo/domain/JdbcWorkspaceStoreIntegrationTest.java`, `specs/001-meetingmind-core/{tasks,implement}.md`.
- 계기: PR #56의 CI `PostgreSQL Migration` job이 실패했다. `V119.4`에서 세운 가설("skip 뒤에 깨진 검증이 더 있다")이 CI에서 먼저 확인된 것이다.
- 회귀 1 (`MigrationIntegrationTest`): `7a3f70d`이 `V24__create_ai_usage_events.sql`을 추가했지만 이 테스트의 하드코딩된 단정을 갱신하지 않았다. `migrationsExecuted == 13`과 `containsExactly("1".."23")`이 V24로 각각 14와 "24" 포함으로 바뀌어야 했다. dev CI(79da6dd)는 V24가 없어 green이었으므로 이 브랜치가 유발한 회귀다. 로컬에서는 `CI_POSTGRES_URL` 미설정으로 계속 skip되어 드러나지 않았다.
- 회귀 2 (`JdbcWorkspaceStoreIntegrationTest`): `completesTranscriptAndEnqueuesOneEmbeddingJob`이 회의 VIEWER의 전사 시작에 `MEETING_ACCESS_DENIED`를 기대했으나 실제는 `TRANSCRIPTION_ALREADY_PROCESSING`이었다. 원인은 `88effad`이 `requireTranscriptManagement`를 `requireParticipantManagement`에서 `requireReadAccess`로 의도적으로 완화한 것이다("Any active meeting participant may contribute to the shared transcript" 주석 명시). 즉 코드가 아니라 테스트가 stale했다. 이 테스트도 DB-gated로 어디서도 실행되지 않아 방치돼 있었다.
- 회귀 2 수정 방식: 기대값만 바꾸면 음성 권한 커버리지가 사라지므로, VIEWER는 완화된 정책대로 `TRANSCRIPTION_ALREADY_PROCESSING`을 받도록 고치고, 회의 밖 사용자(outsider)로 `MEETING_ACCESS_DENIED` 음성 검증을 새로 추가해 권한 경계 자체는 계속 검증하게 했다.
- `T441` 산출물: `scripts/run-db-tests.sh`. CI `PostgreSQL Migration` job과 같은 `pgvector/pgvector:0.8.2-pg16-bookworm`을 쓰고, dev용 `meetingmind-postgres-local`(5434)과 분리된 `meetingmind-postgres-test`(5435)를 사용한다. `MigrationIntegrationTest`가 pristine DB를 요구하므로(재실행 시 `expected: 10 but was: 0`) 매 실행마다 남은 연결을 끊고 database를 drop/create한다. 실수로 5434를 지정하면 거부한다.
- `T442` 실행 결과: Backend 206건 실패 0, **skip 10건 -> 1건**. 이전까지 한 번도 실행되지 않던 DB-gated 9건(`MigrationIntegrationTest` 1, `JdbcWorkspaceStoreIntegrationTest` 4, `JdbcAuthStoreIntegrationTest` 3, `SttTranscriptFlowIntegrationTest` 1)이 모두 실행/통과한다. 남은 skip 1건은 provider credential이 필요한 `ClovaSttTranscriptSmokeIntegrationTest`이며 `T440` 범위다.
- 판단: `V119.4`에서 제기한 "증적을 쌓기 전에 증적 경로가 실제로 도는지 먼저 확인한다"는 순서 원칙이 실제로 회귀 2건을 찾아냈다. 두 건 모두 skip 때문에 장기간 은폐돼 있었고, 하나는 이 브랜치가 유발한 것이었다. BFF skip 6건은 `T442.1`로 남긴다.
- 검증: `./scripts/run-db-tests.sh --console=plain` -> Backend 206건/실패 0/skip 1. 스크립트 재실행 시에도 pristine 리셋으로 `MigrationIntegrationTest`가 반복 통과한다.

## T442.2 Migration Test Brittleness Removal

- 변경 파일: `backend/src/test/java/com/meetingmind/demo/MigrationIntegrationTest.java`, `specs/001-meetingmind-core/{tasks,implement}.md`.
- 계기: `T441/T442`에서 V24 누락으로 깨진 단정을 `13 -> 14`, 버전 목록에 `"24"` 추가로 고쳤는데, 이는 증상만 없앤 수정이었다. 기대 버전 목록이 하드코딩돼 있어 마이그레이션을 추가할 때마다 두 곳을 손으로 갱신해야 하고, 갱신을 잊으면 **정상적인 마이그레이션 추가가 CI 실패로 나타난다**. V24가 정확히 그 사례였다.
- 구현: 기대 버전 목록을 classpath의 `db/migration` 실제 파일에서 유도하도록 바꿨다. `migrationsExecuted`는 `expectedVersions.size() - LEGACY_CHECKPOINT`로, 버전 목록 단정은 `containsExactlyElementsOf(expectedVersions)`로 바꿨다. `.target("10")` legacy 체크포인트는 마이그레이션이 append-only이므로 `LEGACY_CHECKPOINT` 상수로 고정 유지했다.
- 동적 단정의 함정 차단: 파일 탐색이 실패해 빈 목록이 되면 단정들이 공허하게 통과한다. 이를 막기 위해 탐색 결과가 `LEGACY_CHECKPOINT`보다 많아야 한다는 단정을 먼저 두었고, 중복 버전 금지도 추가했다.
- 기존 보장 유지: 하드코딩 목록이 암묵적으로 보장했던 "1부터 빈틈없이 이어짐"을 명시적 단정으로 승격했다. 동적으로 바꾸면서 이 보장을 조용히 잃지 않도록 한 것이다. 의도적으로 번호를 건너뛸 일이 생기면 이 단정만 명시적으로 조정하면 된다.
- 검증:
  - 현재 상태 통과: `./scripts/run-db-tests.sh --tests com.meetingmind.demo.MigrationIntegrationTest` -> 1건 실행/0 skip/통과.
  - 취약성 제거 실증: 임시 `V25`를 추가한 상태로 재실행해 통과를 확인했다. 하드코딩이었다면 이 지점에서 실패한다.
  - 안전망 작동 실증: 같은 파일을 `V27`로 바꿔 25, 26에 구멍을 만든 뒤 재실행해 `migration versions must be contiguous starting at 1`으로 실패함을 확인했다. 즉 단정이 느슨해진 것이 아니다.
  - 임시 probe 파일은 삭제했고 마이그레이션 파일 수는 24로 원복했다. 전체 재실행 결과 Backend 206건/실패 0/skip 1을 유지한다.
- 판단: 이제 마이그레이션 추가 시 이 테스트를 손댈 필요가 없고, 누락·중복·순서 오류·번호 건너뜀은 여전히 잡힌다.

## T440 Soniox Realtime STT Provider Smoke

- 변경 파일: `backend/src/test/java/com/meetingmind/demo/domain/SonioxSttTranscriptSmokeIntegrationTest.java`, `scripts/run-db-tests.sh`, `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 배경: runbook이 지정한 유일한 provider 근거는 `ClovaSttTranscriptSmokeIntegrationTest`(`clova-nest`)였으나 실제 runtime 기본 provider는 `ConfiguredSttProvider`의 `soniox-realtime`이었다. 문서가 검증하려는 provider와 실제로 실행되는 provider가 달랐고 Clova 자격증명도 없었다. 결정에 따라 `soniox-realtime` 대상 opt-in smoke를 추가했다.
- 구현: `RUN_SONIOX_STT_SMOKE=true` 게이트, `SONIOX_STT_SMOKE_PCM_PATH` 입력, 선택적 `SONIOX_STT_SMOKE_EXPECTED_TEXT` 단정. 통과 기준은 transcript `COMPLETED`, provider 기록 일치, segment 1건 이상, 전사 텍스트 non-blank, `TRANSCRIPT_COMPLETED` embedding job 정확히 1건이다.
- 거짓 양성 차단: `ConfiguredSttProvider`는 primary 생성이 실패하면 `STT_FALLBACK_PROVIDER`(기본 `openai-realtime`)로 넘어간다. 그대로 두면 Soniox가 실패했는데 OpenAI가 전사해 통과할 수 있다. 테스트는 `STT_PROVIDER`와 `STT_FALLBACK_PROVIDER`를 모두 `soniox-realtime`으로 고정해 원래 예외가 다시 던져지게 하고, 주입된 `SttProvider.providerId()`가 `soniox-realtime`임을 먼저 단정한다.
- 입력 데이터 판단: `backend/output/debug-audio/*-16k-1ch.wav`에 기존 16 kHz mono 녹음이 있었지만 실제 회의 음성이므로 외부 provider로 전송하지 않았다. macOS `say`와 `afconvert`로 합성 한국어 음성을 만들어 WAV 헤더를 제거한 raw PCM(약 5초)을 사용했다. 합성 입력은 기대 문구를 알 수 있어 검증도 강해지고 저장소에 오디오를 커밋하지 않는다.
- 실행 증적: meeting `meeting-6e842ab8-ee8f-4b05-8534-68080350111a`, status `COMPLETED`, provider `soniox-realtime`, language `ko-KR`, segment 2건, `TRANSCRIPT_COMPLETED` embedding job 1건. 전사 결과는 `안녕하세요, 오늘 회의를 시작하겠습니다.` / `전사 스모크 테스트`로 합성 입력 문구와 일치했다.
- 부수 회귀 차단 (`run-db-tests.sh`): 첫 실행에서 provider를 호출하지 않고 통과한 것처럼 보이는 문제가 있었다. Gradle은 환경변수를 `test` 태스크 입력으로 추적하지 않으므로, gate 환경변수만 바꿔 재실행하면 태스크가 UP-TO-DATE로 판정되고 직전 실행의 결과 XML이 그대로 남는다. 그 결과가 `skipped`였기 때문에 실제로는 아무것도 실행되지 않았는데 `BUILD SUCCESSFUL`로 보였고, 결과 XML의 timestamp로만 구분됐다. 스크립트가 항상 `cleanTest test`를 실행하도록 고정했다.
- 판단: `SMK-002`의 STT provider tier는 실제 provider 호출로 근거를 확보했다. 다만 LiveKit 실서버 입장 증적은 여전히 없고 현재 `MeetingLiveKitTokenServiceTest`는 mock 기반이므로, `SMK-002` 본체와 `V119`는 계속 pending으로 둔다. `clova-nest`는 runtime 기본 provider가 아니므로 종료 조건에서 제외한다.
- 검증: `RUN_SONIOX_STT_SMOKE=true SONIOX_STT_SMOKE_PCM_PATH=... ./scripts/run-db-tests.sh --tests com.meetingmind.demo.domain.SonioxSttTranscriptSmokeIntegrationTest` -> 1건 실행/0 skip/통과. env 없이 실행하면 1건 skip으로 기본 비활성이 유지된다. 전체 실행은 Backend 207건/실패 0/skip 2(Clova, Soniox provider-gated)다.

## T442.1/T443/T444 BFF Redis Verification, Dev DB Migration State, LiveKit Real Server Smoke

- 변경 파일: `backend/src/test/java/com/meetingmind/demo/service/LiveKitRealServerSmokeIntegrationTest.java`, `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- `T442.1` (BFF skip 검증): BFF skip 6건은 전부 Redis-gated(`BFF_REDIS_INTEGRATION`)였다. `RedisSessionSharingIntegrationTest` 1건, `BffAuthRedisIntegrationTest` 3건, `RedisRefreshSingleFlightLockIntegrationTest` 1건, `RedisTokenVaultIntegrationTest` 1건이다. docker `meetingmind-redis-local`(6380)이 테스트 기본값과 일치해 바로 실행했고 88건/skip 0/실패 0으로 통과했다. Backend에서 9건 중 2건이 깨져 있던 것과 달리 BFF에는 숨은 결함이 없었다.
- `T443` (V24 적용 상태): dev DB의 `flyway_schema_history`를 확인한 결과 V24가 `2026-07-26 01:30:59`에 이미 적용돼 있었다. Flyway CLI로 확인하니 `Current version of schema "public": 24`, `No migration necessary`였다. 문제는 적용 경로다. 이 시각은 `T441` 이전에 dev DB(5434)를 대상으로 DB 테스트를 돌린 시점이며, Spring context가 flyway를 실행한 부수 효과로 적용된 것이다. 결과 상태는 의도와 같지만 테스트 실행이 dev 스키마를 바꿀 수 있다는 뜻이므로, 이후 DB 검증은 `scripts/run-db-tests.sh`(5435)만 사용한다. 이 사례 자체가 `T441`의 근거다.
- `T444` (LiveKit 실서버 smoke): 기존 `MeetingLiveKitTokenServiceTest`는 `LiveKitTokenService`를 mock으로 대체하므로 권한 분기와 응답 매핑만 검증하고, 자격증명 유효성이나 서버 도달성은 확인하지 않았다. `LiveKitRealServerSmokeIntegrationTest`를 추가해 실제 LiveKit Cloud에 room을 만들고 `listRooms`로 조회한 뒤 삭제하고, 삭제 후 조회로 잔존이 없음까지 단정한다. 발급 token은 payload를 디코드해 `iss`가 API key이고 `video.room`이 대상 room으로 스코프됨을 확인한다. `RUN_LIVEKIT_SMOKE` 게이트로 기본 비활성이며 DB를 쓰지 않는다.
- `T444` 구현 세부: `java-jwt`는 `livekit-server`의 전이 의존이라 test compile classpath에 없다. 서명 검증이 목적이 아니라 claim 스코프 확인이므로 Jackson으로 payload만 직접 디코드했다. Retrofit 응답 실패 시 provider 원문 body를 노출하지 않고 status code만 남긴다.
- `T444` 범위 한계: 매체 publish/subscribe는 검증하지 않는다. 실제 오디오/비디오 join은 브라우저 client가 필요하므로 product E2E 수동 절차로 남는다. 따라서 `SMK-002`는 STT 전사(`T440`)와 LiveKit 서버 도달성(`T444`)까지 자동 증적을 확보했고, 브라우저 기반 실제 입장만 수동으로 남는다.
- 검증:
  - `cd bff && BFF_REDIS_INTEGRATION=true ./gradlew cleanTest test` -> 88건/skip 0/실패 0.
  - `cd backend && RUN_LIVEKIT_SMOKE=true ./gradlew cleanTest test --tests com.meetingmind.demo.service.LiveKitRealServerSmokeIntegrationTest` -> 1건 실행/0 skip/통과.
  - `./scripts/run-db-tests.sh` -> Backend 208건/실패 0/skip 3(Clova, Soniox, LiveKit provider-gated).

## T445 SMK-003 Local Tier: Confirmed Report Index Linkage

- 변경 파일: `backend/src/test/java/com/meetingmind/demo/domain/ReportConfirmKnowledgeIndexIntegrationTest.java`, `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 조사 결과: `SMK-003`의 기준은 "확정된 회의록이 검색 가능한 knowledge source를 만든다"였다. 실제 구조를 확인하니 `confirmMeetingReport`는 `project_knowledge` row를 만들지 않는다. `createProjectKnowledge`는 `SpaceController`의 사용자 수동 생성 경로에서만 호출된다. 대신 색인은 `meeting_reports`의 `current_report_embedding_job_trigger`가 `status='CONFIRMED' and is_current=true`일 때 `enqueue_embedding_job(..., 'REPORT_CONFIRMED')`로 처리한다. 즉 확정 회의록은 별도 knowledge 문서가 아니라 meeting source로 색인된다. 기준 문장이 실제 설계와 달라 오해를 유발했다.
- 기존 커버리지 공백: `MigrationIntegrationTest`가 `5:REPORT_CONFIRMED`를 단정하지만, 이는 `meeting_reports`에 raw SQL로 직접 insert한 뒤 trigger 동작만 확인한 것이다. 애플리케이션 경로가 trigger 조건 두 가지(`CONFIRMED`, `is_current=true`)를 실제로 만족시키는지는 검증되지 않았다. `MeetingReport.confirmed()`가 `current=true`를 함께 설정하는 것에 의존하는데, 이 연결이 깨지면 회의록을 확정해도 색인이 걸리지 않고 조용히 누락된다.
- 구현: `ReportConfirmKnowledgeIndexIntegrationTest`를 추가해 `saveReportCandidate` -> `confirmMeetingReport` 애플리케이션 경로로 검증한다. CANDIDATE 상태에서는 색인 작업이 없어야 하고, 확정 후 `REPORT_CONFIRMED` 작업이 정확히 1건 생기며, 그 작업이 space 범위이고 `project_knowledge_id`가 null인 meeting source임을 단정한다. AI provider를 쓰지 않으므로 결정론적이다.
- 범위 한계: 회의록 본문 생성(AI provider)과 embedding worker가 작업을 소비해 `embedding_chunks`에 `source_type='report'`로 적재하는 단계는 이 테스트 범위 밖이다. 따라서 `SMK-003`의 "실제로 검색된다"까지는 provider 실행이 남는다. 이 테스트가 고정하는 것은 "확정이 색인 작업을 만든다"는 연결이다.
- 검증: `./scripts/run-db-tests.sh --tests com.meetingmind.demo.domain.ReportConfirmKnowledgeIndexIntegrationTest` -> 1건 실행/0 skip/통과. 전체는 Backend 209건/실패 0/skip 3(provider-gated)다.

## T439.2 Grafana Provisioning and Dashboards

- 변경 파일: `infra/grafana/**`(신규 5개), `specs/001-meetingmind-core/contracts/observability.md`, `specs/001-meetingmind-core/{tasks,implement}.md`.
- 범위 결정: **provisioning + dashboard JSON까지**만 만든다. STT/LiveKit custom metric 추가는 backend/stt 코드 변경이 따라오므로 별도로 둔다.
- 없는 지표로 패널을 만들지 않았다: `contracts/observability.md`가 STT/LiveKit 패널을 "최소 필요"로 적어 두었지만 해당 metric이 아직 구현되어 있지 않다. 없는 지표를 쿼리하면 Grafana는 오류를 내지 않고 **빈 패널**을 보여주므로, 만들어 두면 "대시보드가 있다"는 착각만 남는다. 그래서 제외하고 gap으로 남겼다.
- AI 지표 검증: dashboard JSON에서 쿼리를 파싱해 지표 이름을 뽑고, 실행 중인 AI 서버의 `GET /metrics` 실제 출력과 대조했다. 8종 전부 존재하며 histogram은 `_bucket` 접미사까지 확인했다. 쿼리가 0개인 경우를 실패로 처리해 공허한 통과를 막았다.
- BFF 지표: Micrometer 등록 코드에서 이름과 tag, meter 종류(Counter/Timer/Gauge)를 확인하고 Prometheus 변환 규칙을 적용했다(dot -> underscore, Counter `_total`, Timer `_seconds_count`/`_seconds_sum`). 실행 중인 BFF의 `/actuator/prometheus`는 인증이 걸려 있어(401) 직접 대조하지 못했다.
- **부수 발견**: 지표 이름을 테스트로 고정하려 했으나, `@SpringBootTest` 환경의 BFF `/actuator/prometheus`가 **Prometheus 노출 형식이 아닌 텍스트**를 반환한다(dot 이름 + `value=`). 기존 `exposesPrometheusMetrics`는 "비어 있지 않음"만 단정해 이를 잡지 못하고 있었다. 원인 규명은 이 작업 범위를 넘으므로 추가하려던 테스트는 되돌리고 gap으로 기록했다. **지표 이름이 어긋나면 dashboard는 오류 없이 빈 패널이 되므로 조용히 실패한다** — 고정 테스트가 필요한 이유다.
- 대시보드 구성: AI는 endpoint별 요청/실패율/지연 p50·p95, provider 소요 시간과 토큰 사용량, 요청당 근거 수, 검색 지연/결과 수, 색인 대기열. BFF는 guard 거부/circuit 상태, 브라우저 요청 결과와 지연, 토큰 재발급/세션 무효.
- 색인 대기열 패널에는 `T447`에서 실제로 5시간 47분 방치됐던 사실을 설명으로 남겼다. 이 패널이 있었으면 그때 바로 드러났다.

## T439.1 Space AI Usage and Quota

- 변경 파일: `backend/src/main/resources/application.yml`, `backend/src/main/java/com/meetingmind/demo/controller/AiUsageController.java`, `backend/src/test/java/com/meetingmind/demo/controller/AiUsageControllerTest.java`, `frontend/src/App.tsx`, `frontend/e2e/space-ai-usage.spec.ts`(신규), `specs/001-meetingmind-core/{tasks,implement}.md`.
- 착수 전 실측: API는 이미 있었다(`GET /api/v1/spaces/{spaceId}/ai/usage`, window/총계/기능별 집계). `limit`과 `usagePercent`만 `null` 하드코딩이었다. frontend에는 타입과 `fetchSpaceAiUsage`가 있었지만 **호출하는 화면이 없었다**. "Knowledge Indexed" 제거는 이미 끝나 있었다. 즉 남은 일은 quota 계산과 화면 연결이었다.
- 결정: quota 상한은 **환경변수 전역 기본값**(`MEETINGMIND_AI_TOKEN_QUOTA`)에서 온다. Space별 컬럼을 두면 마이그레이션과 설정 UI가 따라오는데 프로토타입 단계에 비해 과하다. 값이 0 이하이거나 미설정이면 `limit`/`usagePercent`를 **둘 다 null로** 둔다. 0을 내려보내면 클라이언트가 "한도 0"으로 오해한다.
- 결정: quota는 **표시 전용이며 초과해도 AI 호출을 차단하지 않는다**. 프로토타입에서 설정 실수로 팀 전체가 막히는 위험을 지지 않는다. `quotaOverrunIsReportedButNeverBlocks` 테스트가 이 결정을 고정한다 — 초과 이후에도 기록과 조회가 계속 성공함을 단정한다.
- UI: Space 개요에 사용량 카드를 추가했다. 총 토큰, 요청 수, 기능별 내역을 보여주고 quota가 설정된 경우에만 진행률 막대와 퍼센트를 렌더한다. 100%를 넘으면 색을 바꾸되 "AI 사용은 계속됩니다"를 함께 표시해 차단이 아님을 분명히 한다. 조회가 실패하면 카드만 숨기고 개요 전체는 정상 동작한다.
- **YAML 중복키를 스스로 만들었다가 잡았다**: `meetingmind.ai:` 블록이 이미 있는데 파일 앞쪽에 같은 키를 새로 열어 SnakeYAML `DuplicateKeyException`으로 backend가 기동하지 못했다. `V119.4`가 고쳤던 회귀와 정확히 같은 유형이며, `compileJava`는 통과했다. `T450`에서 만든 중복키 스캐너로 확인 후 기존 블록에 합쳤다. **YAML을 건드리면 컴파일이 아니라 기동으로 확인해야 한다**는 것이 다시 확인됐다.
- 검증: `AiUsageControllerTest` 3건 실행 / 0 skip / 0 실패. `npm run build` 통과, 단위 테스트 22건 통과, lint 0 errors / 4 warnings(기존과 동일, 신규 경고 없음). Playwright `space-ai-usage` 1건 통과.
- e2e를 따로 둔 이유: 카드가 조건부 렌더(`aiUsage ? ... : null`)라 조회가 실패하면 **조용히 사라진다**. 빌드도 단위 테스트도 그것을 잡지 못한다. 실제로 첫 실행에서 카드가 뜨지 않았고, 원인이 stale backend였음을 이 테스트가 드러냈다.

## T451.1 AH-009 — Token-Based Context Budget

- 변경 파일: `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/{test-matrix,tasks,implement}.md`.
- 배경: `T451`이 고친 것은 "상한을 넘길 때 무엇을 먼저 버리는가"였고 상한 자체는 여전히 **건수**였다. `PERF-TOKEN-01`은 프로토타입 목표("기능별 기본 상한을 두고 초과 시 낮은 점수 근거부터 제거")와 MVP 목표("요청 전 토큰 예상치를 계산하고 상한 초과 요청을 자동 축소")를 나눠 두는데, 전자는 `T451`로 충족됐고 후자가 이 작업이다.
- 토큰 측정 방식 결정: **문자 기반 추정**을 쓴다. `tiktoken`을 넣으면 정확하지만 `ai/requirements.txt` 의존성과 컨테이너 이미지가 커지고 모델별 인코딩 관리가 따라온다. 상한 판정에는 보수적 추정으로 충분하다. `estimate_tokens`는 한글/CJK를 문자당 1토큰, 그 외를 4문자당 1토큰으로 본다. CJK를 **과대평가**하는 방향이라 상한을 넘겨 잘리는 쪽으로 안전하게 틀린다.
- 기능별 예산 분리(`PERF-TOKEN-01`이 명시한 요건): `AI_CONTEXT_TOKEN_BUDGET`이 전역 기본값(6000)이고 `AI_CONTEXT_TOKEN_BUDGET_<FEATURE>`로 덮어쓴다. 적용 대상은 `explain_term`, `meeting_chat`, `project_chat`, `report`, `tasks` 다섯이다. 값이 정수가 아니거나 0 이하면 전역 기본값으로 되돌린다.
- **최소 1건은 남긴다**: 예산이 아무리 작아도 source를 전부 버리지 않는다. 전부 버리면 근거가 사라져 `NO_EVIDENCE`가 되는데, 그것은 **검색 실패** 신호이지 예산 초과 신호가 아니다. 둘을 섞으면 운영 중에 원인을 오판한다.
- 적용 순서: score 정렬 -> 건수 상한(있으면) -> 토큰 예산. 예산은 직렬화된 JSON 기준으로 재므로 실제 전달량과 어긋나지 않는다.
- 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests` -> 211건 / 실패 0 / skip 7. 신규 4건이 실제로 실행됨을 `-v`로 확인했다. 예산 축소 테스트는 **예산 없이 5건 전부 들어간다**는 양성 대조를 함께 단정해, 축소가 예산 때문임을 고정했다.
- 한계: 추정이므로 provider가 세는 실제 토큰과 다르다. 정확한 회계가 필요해지면 `tiktoken` 도입을 다시 판단한다. 출력 길이 제한(`PERF-TOKEN-05`)은 이 작업 범위 밖이다.

## T454 SMK-002 Media Axis — Two-Participant Publish/Subscribe

- 변경 파일: `frontend/e2e/live-media.spec.ts`(신규), `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 덮는 범위: `T444`는 LiveKit **서버 도달성**만 덮는다(room create/list/delete, token 스코프). 브라우저 client가 없어 매체 경로는 검증되지 않았고, 그래서 `SMK-002` 매체 축이 수동으로 남아 있었다. 이 스펙이 그 축을 브라우저 2개로 자동화한다.
- 구성: 호스트가 Space와 회의를 만들고 두 번째 사용자를 회의 참가자로 넣는다. 두 사용자가 각각 별도 browser context로 prejoin(`Join Now`)을 거쳐 live room에 들어간다. Chromium fake device(`--use-fake-device-for-media-stream`, `--use-fake-ui-for-media-stream`)를 쓰지 않으면 `getUserMedia`가 권한 프롬프트에서 멈춘다. `test.use({ launchOptions })`는 describe 안에 두면 worker를 새로 강제해 Playwright가 거부하므로 파일 top-level에 둔다.
- 단정: 원격 참가자 목록은 `isConnected && !isLocal`로 걸러진 것만 렌더하므로, 상대의 볼륨 컨트롤(`{name} volume`)이 보인다는 것은 **실제로 접속해 구독됐다**는 뜻이다. 양쪽에서 서로를 본다.
- 음성 기준선: 첫 참가자가 혼자 있을 때 `No other participants`가 보이는 것을 **먼저** 단정한다. 이것이 없으면 뒤의 양성 단정이 원래부터 떠 있던 것을 본 것인지 구분할 수 없다. "없음 -> 있음" 전이가 성립해야 공유 room이 실제로 동작한 것이다.
- 선행 단정: UI를 건드리기 전에 `POST /api/v1/meetings/{id}/livekit-token`이 성공하는지 확인한다. 자격증명이 죽어 있으면 UI 단정은 의미가 없고, 실패 원인도 화면 타임아웃이 아니라 토큰 응답으로 드러나야 한다.
- 발견: `participantType=member`는 `SPACE_ACCESS_DENIED`("member participant는 SpaceMember여야 합니다")로 거부된다. 회의 초대만 받은 사용자는 `guest`여야 한다. 경계가 의도대로 동작함을 확인한 셈이다.
- **opt-in인 이유**: `LiveKitTokenService`는 CWD의 `.env`를 읽고 Playwright backend webServer의 cwd가 `backend/`이므로 로컬에서는 `backend/.env`의 자격증명이 쓰인다. CI에는 그 파일이 없어 토큰 발급이 `LIVEKIT_NOT_CONFIGURED`가 된다. 게이트 없이 두면 CI가 항상 실패하고, 조건부 skip으로 두면 "통과처럼 보이는 skip"이 된다. 그래서 `RUN_LIVEKIT_MEDIA_E2E=true` 명시적 opt-in으로 뒀다. **게이트 없이 돌린 결과의 skip은 통과 근거가 아니다.**
- 검증: 신선한 `test` 프로파일 스택(`PLAYWRIGHT_BACKEND_PORT=8090` 등)에서 `RUN_LIVEKIT_MEDIA_E2E=true` -> 1건 실행/통과. 게이트 없이 전체 실행 시 8 passed / 1 skipped로 skip 경로도 정상이다. 이 스펙도 `T453`과 같은 `ensureUser` 문제로 처음에는 stale backend에서만 통과했었고 같은 방식으로 고쳤다.
- **fake media로 덮지 못하는 것**: 실제 마이크/카메라 권한 프롬프트, prejoin 장치 선택 UX, 실제 오디오 품질. 이 세 가지는 여전히 사람이 확인해야 하며 `SMK-002`의 진짜 수동 잔여다.

## T453 SMK-005 Browser Axis — Automated Instead of Manual

- 변경 파일: `frontend/e2e/guest-acl-ui.spec.ts`(신규), `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 판단 변경: `SMK-005` 브라우저 축을 "수동 필수"로 두고 있었으나 실제로는 자동화 자산이 이미 있었다. `playwright.config.ts`가 backend(`test` 프로파일) + BFF(legacy auth) + frontend dev server를 함께 띄우고, CI에 `Playwright` job이 `npm run test:e2e`(필터 없음)로 `e2e/` 전체를 실행한다. 새 스펙은 별도 배선 없이 CI에 포함된다.
- 스펙 구성: 호스트가 Space와 회의 2개를 만들고 guest를 **한쪽 회의에만** 참가자로 넣는다(`role=VIEWER`, `participantType=guest`, Space 멤버로는 넣지 않음). 그 상태에서 guest가 UI로 Space 범위에 도달할 수 있는지 본다.
  - Space 목록에 호스트 Space가 렌더되지 않는다.
  - `/spaces/{id}`, `/meetings`, `/members`, `/knowledge`, 초대되지 않은 회의 상세를 **URL 직접 입력**해도 Space 이름과 회의 제목이 렌더되지 않는다.
  - 같은 Space 범위 API가 guest 토큰에 대해 4xx로 거부된다. 화면에 안 보이는 것만으로는 클라이언트 필터로 가려둔 상태와 구분되지 않기 때문이다.
- **음성 단정 유효성 고정**: 이 스펙의 부정 단정은 전부 `toHaveCount(0)`이라, 라우트 오타나 로그인 실패로 페이지가 아무것도 렌더하지 않아도 통과한다. 그래서 `space owner does see the space screens the guest is denied`를 함께 두어 **같은 URL에서 소유자에게는 보인다**는 것을 고정했다. 이 테스트가 깨지면 나머지 음성 단정은 무의미해진다. 추가로 guest 토큰으로 초대된 회의를 실제로 읽을 수 있음을 셋업 유효성 근거로 먼저 단정한다.
- 계층 구분(중요): `playwright.config.ts`는 backend를 `SPRING_PROFILES_ACTIVE=test`로 띄우므로 **in-memory adapter**가 쓰인다. 이 스펙은 SQL 계층 결함을 잡지 못한다. SQL 축은 `T446`이 실 PostgreSQL로 담당한다. 두 축을 섞으면 "guest 테스트가 있으니 안전하다"는 잘못된 결론에 이르므로 스펙 상단 주석에 명시했다.
- 검증: `cd frontend && PLAYWRIGHT_BACKEND_PORT=8090 PLAYWRIGHT_BFF_PORT=8091 PLAYWRIGHT_FRONTEND_PORT=5199 BFF_REDIS_PORT=6380 npx playwright test` -> 8건 통과 / 1 skip(opt-in). local Redis는 6379가 아니라 6380이라 override가 필요하지만 CI service는 6379이므로 CI에서는 불필요하다.
- **정정(중요)**: 최초 실행은 이미 떠 있던 backend(포트 8080, 7시간 전 기동)에 붙어 통과했다. `reuseExistingServer: !CI`가 local에서 true라 Playwright가 기존 프로세스를 재사용했고, 그 프로세스는 `local` 프로파일(실 DB)이라 **CI가 쓰는 `test` 프로파일과 달랐다**. 별도 포트로 신선한 스택을 띄우자 4건이 실패했다. 즉 첫 통과는 CI 환경의 근거가 아니었다.
- 실패 원인과 수정: `signup`은 auth store에만 사용자를 만든다. workspace store에는 컨트롤러의 `currentUser()`가 호출하는 `ensureUser`로 등록된다. guest가 인증 API를 한 번도 호출하지 않은 상태에서 참가자로 추가하면 `addMeetingParticipant`의 `requireUser(userId)`가 `UNAUTHORIZED`로 거부한다. fixture에서 guest가 `GET /api/v1/spaces`를 먼저 호출하도록 고쳤다. 실제 사용자도 초대 전에 로그인하므로 현실과 어긋나지 않는다.
- 남은 수동 범위: 없음. `SMK-005`의 브라우저 축은 이 스펙으로 닫힌다.

## V119 Status — 자동 검증 범위 마감, 수동 2건 잔여

- `SMK-001~005` 중 **자동 검증이 가능한 범위는 전부 닫혔다**. `T445`(색인 작업 생성), `T446`(guest ACL), `T447`(worker 소비), `T448`(Project AI citation), `T449`(회의록 본문 생성)가 각각의 축을 덮는다.
- 남은 것은 브라우저 실조작이 필요한 2건이며 에이전트가 대신 수행할 수 없다.
  - `SMK-002` 매체 publish/subscribe를 포함한 실제 회의 입장
  - `SMK-005` UI가 서버 경계를 우회하는 경로(클라이언트에만 있는 필터 등)
- 이 둘은 서버 측 근거가 이미 확보돼 있으므로(`T440`/`T444`, `T446`) 남은 위험은 **클라이언트 계층에 국한**된다.
- 검증 과정에서 나온 기능 결함과 후속 과제는 별도로 남는다: `T451`(수정 완료), `T451.1` token budget, `T450.1` mtls 프로파일 기동, `T450.2` Terraform CI. 이 중 `V119` 마감을 막는 것은 없다.
- 판단: `V119`는 위 수동 2건이 채워지는 시점에 닫는다. 그 전까지 열어 둔다.

## T452 Embedding Job Failure Cause Retention

- 변경 파일: `backend/src/main/resources/db/migration/V25__add_embedding_job_failure_detail.sql`(신규), `ai/app/{embedding_worker,repository,observability}.py`, `ai/tests/test_embedding_worker.py`, `specs/001-meetingmind-core/{tasks,implement}.md`.
- 문제: `normalize_failure_code`가 미분류 예외를 전부 `INTERNAL_ERROR`로 접고 `embedding_jobs`에 원인을 남길 컬럼이 없었다. `T447`에서 dev DB의 `INTERNAL_ERROR` 3건을 진단할 때 실패 행만으로는 아무것도 알 수 없어 **재실행으로만** 환경성 실패임을 좁힐 수 있었다.
- 구현: `V25`가 `failure_detail varchar(200)`과 `failure_detail is null or failure_code is not null` check 제약을 추가한다. worker는 `failure_detail_for(error)`로 예외의 정규화된 타입 이름(`app.embedding_provider.EmbeddingProviderError`, `ZeroDivisionError` 등)을 기록한다.
- **예외 메시지를 저장하지 않는 이유**: provider 응답 본문이나 DSN(비밀번호 포함)이 예외 메시지에 실려 올 수 있어 `NFR-LOG-01` 원문 비노출 원칙과 충돌한다. 타입 이름만으로도 psycopg 오류와 provider 오류를 즉시 구분할 수 있어 진단 목적은 달성된다. 컬럼 comment에도 같은 제약을 남겼다.
- 재시도 경로 처리: 재시도로 되돌릴 때 `failure_code`를 지우므로 `failure_detail`도 함께 지운다. 그러지 않으면 새 check 제약을 위반한다.
- **로그 allowlist 함정**: `log_event`는 `_SAFE_FIELDS` allowlist 밖의 key를 조용히 버린다. `failureDetail`을 로그 호출에 추가했지만 allowlist에 없어 payload에서 사라졌고, DB 값만 단정한 첫 테스트는 이를 통과했다. allowlist에 추가하고 테스트가 로그 payload도 함께 단정하도록 고쳤다. 조용히 버려지는 필드는 단정 없이는 드러나지 않는다.
- 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests` -> 207건 / 실패 0 / skip 7. `./scripts/run-db-tests.sh --tests com.meetingmind.demo.MigrationIntegrationTest` -> 1건 실행 / 0 skip / 0 실패로 `V25`가 pristine DB에 적용되고 classpath 유도 기대 목록에 반영됨을 확인했다. `BUILD SUCCESSFUL`은 근거가 아니므로 결과 XML의 `tests`/`skipped`를 직접 읽었다.

## T451 AH-009 — Shrink Order Defect in Report/Task Context

- 변경 파일: `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/{test-matrix,tasks,implement}.md`.
- 발단: `AH-009`를 "자동 검증만 추가하면 되는 공백"으로 보고 접근했으나, 검증을 짜려고 보니 **정책 자체가 리포트 경로에서 성립하지 않았다**.
- 결함: `format_untrusted_sources(sources, limit=12)`가 `sources[:limit]`로 **위치 기반 절단**을 했다. 검색 경로는 `InMemoryRagRetriever.search`가 `(-score, chunkId)`로 정렬한 뒤 자르므로 문제가 없지만, 리포트 생성과 task 추출은 Backend가 전달한 순서를 그대로 받는다. Backend는 transcript를 발화 순서로 보내므로 score와 위치가 무관하고, 결과적으로 **높은 score 근거가 먼저 잘려 나갔다**.
- 실증: 15건 중 뒤쪽 3건만 score 0.9, 나머지 12건을 0.2로 두고 호출하니 high-score 3건이 전부 provider 문맥에서 빠지고 low-score 12건이 전부 남았다. 수정 후에는 high-score 3건이 전부 유지되고 low-score 3건이 밀려난다.
- 기존 테스트가 못 잡은 이유: `test_report_generation_limits_provider_context_to_first_twelve_sources`가 15건의 `relevanceScore`를 **전부 0.9로 동일**하게 두었다. 그래서 "앞 12건이 남는다"는 위치 단정만 하고 있었고 순서 정책은 아무것도 검증하지 않았다. score가 균일하면 위치 절단과 score 절단이 같은 결과를 내므로 결함이 보이지 않는다.
- 수정: 절단이 일어나는 지점(`limit`이 주어진 경우)에만 `-(relevanceScore or 0.0)`로 정렬한 뒤 자른다. `sorted`는 stable이므로 score가 같거나 전부 `None`이면 기존 순서가 그대로 유지되어 회귀가 없다. 정렬만 하고 source를 추가하지 않으므로 scope는 넓어지지 않는다. `limit`이 없는 호출부(meeting chat, project chat)는 절단 자체가 없어 영향을 받지 않는다.
- 적용 범위: `limit=12` 호출부는 리포트 생성과 task 추출 두 곳이며 둘 다 같은 결함이었다. 공통 함수에서 고쳐 양쪽이 함께 해결된다.
- 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests` -> 205건 실행 / 실패 0 / skip 7. 신규 `test_report_generation_drops_low_score_sources_first_when_over_limit`가 실제로 실행됨을 `-v`로 확인했다. `./.venv/bin/python -m compileall app` 통과.
- **남은 공백**: `AH-009`의 이름은 token budget이지만 현재 상한은 여전히 **건수**(retrieval `limit`, 문맥 12건)다. token 단위 회계는 구현되어 있지 않다. 긴 transcript segment가 12건만으로 상한을 넘길 수 있으므로 token 기반 budget은 별도 과제로 남긴다. 이번 변경이 고친 것은 "상한을 넘길 때 무엇을 먼저 버리는가"이지 "상한을 token으로 재는가"가 아니다.

## T450 Audit — `#56` 유입분 검증 커버리지 실측

- 배경: `#56`으로 들어온 `cert-loader`(Go), `ai/envoy`, 각 서비스 `application-mtls.yml`, `infra/aws` Terraform이 한 번도 검증되지 않았다고 보고 있었다. 실측해 보니 **절반은 이미 덮여 있었고, 덮이지 않은 곳은 따로 있었다**.
- CI 실적: dev(`15bb730`) CI run `30168886298`의 12개 job이 전부 success이며 skip이 없다. run 단위 success는 근거가 아니므로 job 단위로 확인했다.
- 덮여 있음:
  - `cert-loader`: `Certificate Loader` job이 `go mod verify && go test ./... && go vet ./...`를 실제로 실행한다. Go 툴체인이 로컬에 없어도 CI가 검증한다.
  - `ai/envoy`: `Container Images` job이 이미지를 빌드할 뿐 아니라 openssl로 self-signed 인증서를 만들어 `envoy.yaml`을 실제로 로드시킨다. 설정 파일이 파싱만 되는 수준이 아니라 기동까지 확인된다.
- 덮여 있지 않음(실제 공백):
  - `application-mtls.yml` 4종(`auth`, `backend`, `bff`, `stt`): CI workflow에 `mtls` 문자열이 없고, 어떤 테스트도 이 프로파일을 활성화하지 않는다. 즉 property binding과 Spring context 기동이 한 번도 실행된 적이 없다. `management:` 중복키 회귀와 같은 계열의 결함이 그대로 남을 수 있는 자리다.
  - `infra/aws` Terraform: CI에 terraform job 자체가 없다. `fmt`/`validate`조차 돌지 않는다.
  - 위 두 항목은 배포 인프라 영역이라 해당 담당자가 맡는다(`T450.1`, `T450.2`). 이 감사의 결과물은 **공백의 위치를 특정한 것**까지다.
- 자체 검증한 것: SnakeYAML `DuplicateKeyException` 계열 결함을 전수 검사했다. PyYAML 기본 로더는 중복키를 조용히 덮어쓰므로(마지막 값 승) 중복키를 예외로 올리는 로더를 따로 구성했다. build/산출물 디렉터리를 제외한 YAML 26개가 전부 통과했고, 대상 목록에 `application-mtls.yml` 4종과 `ai/envoy/envoy.yaml`이 실제로 포함됐음을 파일 목록 대조로 확인했다(탐색이 비어서 통과하는 경우를 배제).
- 한계: YAML이 파싱된다는 것과 Spring이 그 값을 바인딩해 context를 띄운다는 것은 다르다. mtls 프로파일의 실제 기동 검증과 Terraform 검증은 후속 작업으로 남긴다.

## T449 SMK-003 Remainder — Report Body Generation by Real Provider

- 변경 파일: `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 대상 경로: `POST /api/internal/meeting-ai/generate-report`. Project AI와 달리 이 endpoint는 **자체 검색을 하지 않는다**. `build_backend_report_sources`가 Backend가 전달한 `sources`만 사용하므로, Backend가 권한 검증과 단일 meeting 선필터를 끝낸 뒤에만 근거가 들어온다.
- 양성 1건: dev DB의 `meeting-1b4438c0…`에서 실제 transcript segment 37건을 source로 전달했다. `unsupported=false`, summary와 markdown(976자), decision 3건, actionItem 3건이 생성됐고 model은 `gpt-4.1-mini-2025-04-14`다. 인용된 source 6건이 **전부 전달 범위 안**이며 범위 밖 인용은 0건이다.
- 음성 2건(`validate_backend_report_sources` 가드): 다른 회의의 source를 섞으면 HTTP 403 `AI_CONTEXT_FORBIDDEN`("Report source meetingId must match request meetingId."). 허용되지 않은 source type(`report`)을 섞어도 HTTP 403 `AI_CONTEXT_FORBIDDEN`("Report source type is not allowed."). 즉 단일 meeting 경계와 source type allowlist가 실제로 강제된다.
- 응답 필드 주의: 응답 모델 `GenerateReportResponse`의 필드는 `supported`가 아니라 `unsupported`다. provider JSON 쪽은 `supported` boolean을 요구하고(`supported`가 false면 `unsupported_report(reason="MODEL_UNSUPPORTED")`로 전환) 응답에서는 반전된 이름으로 노출된다. 검증 스크립트에서 `supported`를 읽으면 항상 `None`이 나오므로 공허하게 통과할 수 있다.
- 이로써 SMK-003의 두 축이 모두 닫혔다. 본문 생성(`T449`) -> 확정 시 색인 작업 생성(`T445`) -> worker가 소비해 `report` chunk 적재(`T447`).
- 범위 한계: AI 서버 내부 endpoint 직접 호출이다. Backend가 생성 결과를 `CANDIDATE`로 저장하는 단계는 `T445`가 다루는 별개 경로다. opt-in 수동 smoke이며 실 provider 과금이 발생한다.

## T448 SMK-004 Project AI Answers from Confirmed Report with Citation

- 변경 파일: `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 대상 경로: `POST /api/internal/project-ai/chat`. `sources`가 비어 있으면 `build_backend_project_chat_sources`가 `search_postgres_sources`로 pgvector 검색을 수행하고, source type에 `report`가 포함된다. `T447`로 색인된 `report` chunk가 이 단계의 선행 조건이다.
- 양성 2건: Space A(`space-9a7d73d7…`)에서 "베타 시작 시점" 질문에 `supported`로 답하며 인용 source에 확정 회의록 `report-738390bc…`가 포함된다. Space B(`space-fac05824…`)에서도 자기 Space의 `report-741bdeb4…`와 decision 3건이 인용된다. 서로 다른 Space에서 각각 성립하므로 특정 데이터에 우연히 맞은 결과가 아니다.
- 음성 3건: 근거 없는 질문 -> `unsupported=true`, `LOW_RELEVANCE`. `allowedMeetingIds=[]` -> `NO_EVIDENCE`(회의 소유 source가 회의 scope 없이는 노출되지 않는다). 교차 Space(`projectId=B` + `allowedMeetingIds=A의 회의`) -> `unsupported=true`. 세 건의 reason이 서로 달라 일괄 거부가 아니다.
- 응답 단정만으로 부족한 이유와 보완: unsupported 응답은 `sources`를 비우고 반환한다. 따라서 교차 Space 요청이 거부된 것이 **검색이 아무것도 반환하지 않아서인지**, 아니면 **검색은 누출했는데 모델이 답하지 못한 것인지** 응답만으로는 구분할 수 없다. `PostgresRagRetriever`를 직접 호출해 모델을 배제하고 확인했다. 올바른 scope에서는 8건(그중 대상 report 포함)이 반환되고, 교차 Space 요청에서는 Space B 자신의 `projectKnowledge` 4건만 반환되어 A의 source는 0건이었다. 양성 대조가 8건으로 비어있지 않으므로 공허한 통과가 아니다.
- 범위 한계: AI 서버 내부 endpoint를 직접 호출했다. Backend의 권한 검증과 `allowedMeetingIds` 선필터를 거치는 public 경로(`POST /api/v1/spaces/{spaceId}/ai/chat`)의 end-to-end 검증은 별도다. 자동 회귀 테스트가 아니라 opt-in 수동 smoke이며 실 provider 과금이 발생한다.

## T447 SMK-003 Provider Tier — Embedding Worker Consumes REPORT_CONFIRMED

- 변경 파일: `scripts/run-embedding-worker.sh`(신규), `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 조사 결과 1 — 미구현이 아니라 실행 공백이다: `ai/app/embedding_worker.py`의 `EmbeddingWorker.run_once()`/`main()` poll 루프, `claim_job`/`complete_job`/`record_failure`/`retry_delay_for`가 모두 구현되어 있다. 문제는 local에서 이 프로세스를 띄우는 경로가 없다는 것이다. `scripts/run-ai.sh`는 `uvicorn app.main:app`만 실행하고, `compose.local.yml:146`의 `meetingmind-ai-worker`는 `profiles: ["ai"]` 뒤에 있어 `docker compose up -d`로는 뜨지 않는다. 결과적으로 `embedding_jobs`가 소비되지 않고 PENDING으로 누적된다. 실측으로 가장 오래된 PENDING이 `oldestPendingAgeSeconds=20624`(약 5.7시간)였다.
- 조사 결과 2 — 기존 실패 3건은 코드 결함이 아니었다: dev DB에 `INTERNAL_ERROR`로 4회 재시도 소진된 작업이 3건(그중 `REPORT_CONFIRMED` 2건) 있었다. 과금 전에 무료로 원인을 좁히기 위해 provider 호출 이전 단계인 `load_snapshot`만 3건 모두 재실행했고 전부 정상이었다(각 3/7/9 chunk). 이후 `REPORT_CONFIRMED` 2건을 재큐잉해 실행하니 둘 다 1회 시도로 성공했다. 즉 2026-07-24 00:31~01:20 구간의 환경성 실패이며, 해당 기간 `ai/app/embedding_{provider,worker}.py`와 `repository.py`에는 커밋도 없다.
- 관측성 공백(후속 필요): `normalize_failure_code`가 미분류 예외를 전부 `INTERNAL_ERROR`로 접고 `embedding_jobs`에는 메시지를 남기는 컬럼이 없다. 실패 작업만 보고는 원인을 알 수 없어, 위 진단도 재실행으로만 좁힐 수 있었다.
- 검증(실 provider, OpenAI 과금 발생): `text-embedding-3-small` / dimension 1536.
  - `REPORT_CONFIRMED` `embedding-job-f4cd7b44...`(meeting-0e6afc58) -> COMPLETED, chunkCount 7, activated=true.
  - `REPORT_CONFIRMED` `embedding-job-90559ff3...`(meeting-1b8ae733) -> COMPLETED, chunkCount 9, activated=true. 이 건은 신규 `scripts/run-embedding-worker.sh`로 소비시켜 스크립트 자체의 양성 대조로 삼았다(빈 큐 폴링만으로는 스크립트가 동작한다는 증거가 되지 않는다).
  - 최종 상태: `embedding_jobs`에 `REPORT_CONFIRMED` 3건 전부 COMPLETED, PENDING 0. `embedding_chunks`의 active `report` chunk 3건이 모두 1536차원 vector를 가진다. 이전 generation의 transcript chunk 4건은 `is_active=false`로 교체되어 generation swap도 함께 확인됐다.
- 남은 실패 2건은 별개다: `INVALID_SOURCE` 1건(전사 세그먼트가 없는 회의 — 정상 동작), `INTERNAL_ERROR` 1건(`TRANSCRIPT_COMPLETED`, 재큐잉하지 않음).
- 범위 한계: dev DB(5434)에 대해 실행했으므로 `scripts/run-db-tests.sh`가 쓰는 격리 테스트 DB와 무관하다. 실패 작업 재큐잉은 dev 데이터를 변경한다. 자동화된 회귀 테스트는 아니며, 이 단계는 실 provider 과금 때문에 opt-in으로 남는다.

## T446 SMK-005 Guest ACL Negative on Real Database

- 변경 파일: `backend/src/test/java/com/meetingmind/demo/domain/GuestSpaceAclNegativeIntegrationTest.java`, `specs/001-meetingmind-core/{operational-smoke-runbook,tasks,implement}.md`.
- 조사 결과: guest 관련 커버리지는 이미 여러 건 있었다(`guestCanReadOnlyWhenParticipantForThisMeeting`, `meetingGuestDoesNotCreateSpaceAccess`, `meetingGuestCannotUseProjectAiWithoutSpaceMembership`, `confirmRejectsMeetingGuestEvenWhenGuestIsEditor` 등). 그러나 전부 `InMemoryWorkspaceStore`와 정책 객체 단위였다. 즉 실제 `JdbcWorkspaceStore`의 SQL이 guest 음성 경로로 실행된 적이 없고, SQL에서 space 멤버십 조건이 빠져도 기존 테스트는 전부 통과한다. 이것이 `SMK-005`의 실질적 공백이었다.
- 구현: 실제 PostgreSQL에서 회의 전용 GUEST(Space 멤버가 아니고 회의 참가자로만 등록)를 만들어 다음을 검증한다. 초대되지 않은 같은 Space 회의 상세, Space 회의 목록, Space 상세, Space 멤버 목록, Space knowledge 목록이 모두 `AuthorizationException`으로 거부된다. 추가로 회의 참가자 등록이 Space 멤버십으로 승격되지 않았음을 `findSpaceMembers`로 확인한다.
- 양성 대조를 함께 둔 이유: GUEST가 자기 회의를 읽을 수 있어야 셋업이 유효하다. 셋업이 잘못돼 모든 접근이 거부되는 상태는 거부 단정만으로는 통과처럼 보이지만 아무것도 증명하지 못한다. 따라서 `meetingDetail(guest, invitedMeeting)`이 성공하는 것을 먼저 단정한다.
- 범위 한계: 브라우저 기반 수동 확인은 여전히 남는다. 이 테스트가 고정하는 것은 서버 권한 경계이며, UI가 그 경계를 우회하는 경로(예: 클라이언트에만 있는 필터)는 다루지 않는다.
- 검증: `./scripts/run-db-tests.sh --tests com.meetingmind.demo.domain.GuestSpaceAclNegativeIntegrationTest` -> 1건 실행/0 skip/통과. 전체는 Backend 210건/실패 0/skip 3(provider-gated)다.
