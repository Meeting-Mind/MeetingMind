이 문서는 MeetingMind Core Prototype을 어떻게 구현할지 기술 계획, 병렬 작업 배정, 충돌 경계를 정리하기 위한 Markdown 문서이다.

# Implementation Plan: MeetingMind Core Prototype

## Current State

- Frontend는 React/Vite/TypeScript로 워크스페이스, 회의 대기, 라이브룸, Meeting AI, 프로젝트 개요, 팀원, Report Agent 화면을 제공한다.
- Backend는 Spring Boot 3/Java 21로 `/api/workspace` mock 응답과 `/api/livekit/token` 토큰 발급을 제공한다.
- AI 서버는 FastAPI로 `/api/meeting-ai/ask`를 제공하고 OpenAI Responses API를 직접 호출한다.
- Backend Auth/Workspace와 AI source로 사용하는 Transcript/Report/Task/ProjectKnowledge/Audit runtime은 `local`/`db` profile에서 Spring JDBC PostgreSQL repository를 사용한다. `test` profile은 격리된 in-memory adapter를 사용하며 legacy STT streaming session/file prototype과 pgvector semantic 검색은 아직 별도 경계다.
- 제품 요구사항 기준선은 `requirements/INDEX.md`에서 라우팅되는 Markdown 문서다. 기능 구현 전 관련 요구사항 문서를 먼저 확인한다.

## Target Architecture

- Frontend: mock fallback은 유지하되 API 계약과 실제 데이터 전환 지점을 분리한다.
- Backend: Space, Meeting, Membership, Report, Action Item, Knowledge API를 단계적으로 추가한다.
- AI: Meeting AI 컨텍스트 제한을 유지하고, 이후 retrieval 계층을 별도 모듈로 분리한다.
- Data: PostgreSQL + pgvector를 기본 영속 저장소로 설계하고, 파일성 원문/보고서는 S3 연계를 고려한다.
- Requirements: 용어는 `requirements/glossary.md`, 권한은 `requirements/permissions.md`, 상태값은 `requirements/status-values.md`, 성능/토큰 목표는 `requirements/performance.md`를 따른다.

## Technical Decisions

| Decision | Choice | Reason | Alternatives |
| --- | --- | --- | --- |
| Frontend stack | React + Vite + TypeScript | 현재 코드와 일치하고 빠른 프로토타입 검증에 적합하다. | Next.js |
| Backend stack | Spring Boot 3 + Java 21 | 인증/권한/API 서버 확장에 적합하고 현재 코드와 일치한다. | Node/NestJS |
| AI service | FastAPI | AI API 연동과 Python RAG 생태계 확장에 적합하다. | Spring 내장 AI 호출 |
| Realtime meeting | LiveKit | 현재 토큰 발급 코드와 `livekit-client` 의존성이 존재한다. | WebRTC 직접 구현 |
| Vector search | PostgreSQL + pgvector | 기획서와 일치하고 관계형 권한 모델과 같이 운용하기 좋다. | Pinecone, OpenSearch |
| File storage | S3 | STT 원문/보고서/첨부 파일 분리에 적합하다. | DB BLOB |
| DB migration | Flyway SQL migration | Spring Boot 통합이 단순하고 PostgreSQL/pgvector extension, index, partial unique 제약을 SQL로 명확히 리뷰할 수 있다. | Liquibase, 수동 SQL 적용 |

### Data Migration Discovery

- 2026-07-14 기준 backend에는 Flyway core, PostgreSQL Flyway module, PostgreSQL driver, Spring Boot JDBC starter가 있다. `local` profile이 기본 profile이며 Compose 기본값으로 DataSource와 Flyway를 활성화하고 `db` profile은 환경변수 기반 DataSource를 사용한다. M032에서 Auth/Workspace JDBC repository 계층을 연결했으며 Docker PostgreSQL이 기본 실행 전제다.
- migration 도구는 Flyway를 사용한다. migration 파일 위치는 Spring Boot 기본 경로인 `backend/src/main/resources/db/migration`으로 둔다.
- 원격에 공유된 migration은 수정하지 않는다. `V1`~`V9` 이후 최신 MeetingJoinRequest 보강은 `V10` forward migration으로 추가한다.
- 로컬 DB는 PostgreSQL 16 + pgvector를 다른 프로젝트 DB와 격리된 컨테이너로 실행하고 host `5434`를 기본값으로 사용한다.
- Backend 기본 `local` profile은 `localhost:5434/meetingmind` 기본값으로 DataSource와 Flyway를 실행한다. `db` profile은 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 필수로 사용한다.

### Local Database Foundation

1. `clarify.md`, `data-model.md`, `erd.md`에서 전사 생명주기, 보존, 출처 저장, embedding generation 결정을 먼저 고정한다.
2. 로컬 PostgreSQL+pgvector compose를 추가하고 다른 프로젝트의 `5432`, `5433` DB를 재사용하지 않는다.
3. `V7`~`V9`로 인증 세션, Space 초대, 전사 상태·보존, 용어사전·감사 로그, embedding job/generation을 보강하고, 최신 회의 참가 신청은 공유 migration을 수정하지 않고 `V10`으로 추가한다.
4. 빈 로컬 DB에 Flyway를 처음부터 적용하고 schema/constraint/index를 검증한다.
5. Backend in-memory store를 repository로 전환한 뒤 AI retriever를 pgvector로 교체한다.
6. embedding model/차원과 vector index는 `Q-010` 결정 후 별도 migration으로 고정한다.

## API Contracts

- 기능별 target API 초안은 `contracts/README.md`에서 라우팅한다.
- 기존 통합 `contracts/api.md`는 prototype 기록과 과거 통합 초안으로 유지한다.
- Project Knowledge와 Domain Term 관리는 `contracts/knowledge-api.md`를 기준으로 한다.
- `GET /api/workspace`
  - 현재: 전체 데모 화면 데이터를 한 번에 반환
  - 목표: Space/Meeting/Report API로 분리
- `POST /api/livekit/token`
  - request: `roomName`, `identity`, `name`
  - response: `serverUrl`, `token`, `roomName`, `identity`, `name`
- `POST /api/meeting-ai/ask`
  - request: `question`, `transcript`, `decisions`, `actions`
  - response: `answer`, `model`
- Common API rules
  - 공통 오류 응답은 `code`, `message`, `fieldErrors`, `traceId`를 포함한다.
  - API 날짜시간은 ISO-8601, transcript 위치는 `startMs`, `endMs` 밀리초를 사용한다.
  - 빈 배열은 `null` 대신 `[]`를 반환한다.
- Target transcript/speaker contracts
  - `GET /api/v1/meetings/{meetingId}/transcript`
  - `PATCH /api/v1/meetings/{meetingId}/speakers/{speakerId}`
  - 실제 STT 업로드/요약 재생성 API는 Future Draft로만 관리한다.

## Data Model

- 전체 관계 초안은 `erd.md`에 Mermaid ERD로 기록한다.
- User: 사용자 식별, 이름, 이메일, 인증 공급자
- Space: 프로젝트 단위 컨테이너
- SpaceMember: Space별 멤버십과 역할
- Meeting: Space 하위 회의 회차
- MeetingParticipant: 회의별 접근 권한
- MeetingSpeaker: 자동 발화자 구분 label과 사용자 지정 displayName
- MeetingTranscript: 회의별 전사 처리 상태, 보존 만료, 정리 상태
- TranscriptSegment: `startMs`, `endMs`, 발화자, 텍스트, 회의 참조
- MeetingReport: 회의 요약, 결정사항, Action Item
- ProjectKnowledge: 공식 프로젝트 지식
- EmbeddingChunk: RAG 검색용 chunk와 vector
- EmbeddingJob: 비동기 embedding 생성 상태와 generation 교체

## Security and Permissions

- Backend API에서 Space/Meeting 접근 권한을 먼저 검증한다.
- AI 서버로 전달하는 컨텍스트는 Backend가 권한 필터링 후 구성하는 것을 목표로 한다.
- Project AI 구현 시 회의 데이터 retrieval 전에 MeetingParticipant 권한을 적용한다.
- Transcript, summary, speaker 수정 API는 MeetingParticipant 권한 확인 후 처리한다.
- Speaker 이름 수정은 `HOST` 또는 `EDITOR` 권한으로 제한한다.
- LiveKit 토큰은 짧은 만료 시간을 유지한다.
- 회의 게스트는 특정 회의의 MeetingParticipant로만 접근하며 Space 전체 권한, Project Knowledge, Project AI 권한을 기본으로 갖지 않는다.
- Meeting status는 `SCHEDULED`, `IN_PROGRESS`, `ENDED`, `CANCELED`를 기준으로 하고, Transcript/Report 후처리 상태는 별도 status로 관리한다.

## Parallel Work Plan

- Team Members: 3명 예정. Auth/Login 담당 1명 확정, AI 담당 1명 기존 배정, 나머지 TBD
- Agents: 각 팀원은 코딩 에이전트 1개를 둔다. Auth/Login workstream은 Codex 사용

| Workstream | Owner | Agent | Scope | Expected Files | Dependencies |
| --- | --- | --- | --- | --- | --- |
| Docs/Contracts | TBD | TBD | Open 질문 결정, API 계약, 데이터 모델, 작업 계획 갱신 | `specs/001-meetingmind-core/*` | - |
| Auth/Login | 사용자(Auth 담당) | Codex | Google OAuth와 자체 회원가입/로그인, Backend 검증 기반 access/refresh token 계약/구현, Frontend 로그인 상태 연결, 보호 route 경계 정의 | `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, future `frontend/src/auth/**`, future `backend/src/main/java/com/meetingmind/demo/auth/**`, `backend/src/main/java/com/meetingmind/demo/config/**`, `backend/src/main/resources/application.yml`, `specs/001-meetingmind-core/contracts/auth-api.md`, `specs/001-meetingmind-core/contracts/common.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/research.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Q-001 decided. Auth API는 `/api/v1/auth/*`로 시작하며 Q-006 전체 API 결정과 분리 |
| Backend | TBD | TBD | Space/Meeting/Knowledge API 분리, 도메인 모델, 권한 검증 | `backend/**`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/contracts/knowledge-api.md`, `specs/001-meetingmind-core/contracts/common.md`, `specs/001-meetingmind-core/data-model.md` | Auth/Login contract, Q-002, Docs/Contracts |
| Frontend | TBD | TBD | Project/Meeting 선택 상태, mock fallback 표시, 화면 연동 | `frontend/**` | API 계약 확정, Auth/Login guard 경계 |
| Dashboard/Calendar Frontend | 사용자(Frontend 담당) | Codex | FR-DASH-01~07, FR-CAL-01~05 기준의 프로젝트 홈, 프로젝트 관리, 캘린더 월/주/일 뷰, 일정에서 회의 이동 UX | `frontend/src/App.tsx`, `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `frontend/src/data/mockData.ts`, `frontend/src/pages/WorkspaceHomePage.tsx`, future calendar component/page, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | M006 mock/API 경계, `contracts/space-api.md`, `contracts/meeting-api.md`, Auth/Login guard |
| Project Workspace Frontend | 사용자(Frontend 담당) | Codex | FR-MREG, FR-ACL, FR-KAN, FR-PBOT, FR-PERM, FR-OWN 기준의 프로젝트 상세, 회의 관리/ACL, 칸반, Project AI, 멤버/오너 관리 UX | `frontend/src/App.tsx`, `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/pages/MeetingAiPage.tsx`, future kanban component/page, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | M006/M017 state 경계, `contracts/meeting-api.md`, `contracts/kanban-api.md`, `contracts/ai-api.md`, `contracts/space-api.md`, permissions/status-values |
| Meeting Workspace Frontend | 사용자(Frontend 담당) | Codex | FR-RPT, FR-MBOT, FR-TASK 기준의 Meeting AI, report candidate/편집/확정, 태스크 후보 검토/칸반 등록 UX | `frontend/src/App.tsx`, `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/pages/LiveRoomPage.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | M018 회의 ACL, `contracts/meeting-api.md`, `contracts/ai-api.md`, `contracts/kanban-api.md`, AI prototype endpoints |
| AI | 사용자 | Codex | 백엔드/프론트엔드 구현 없이 AI 서버에서 RAG chunk 형식, mock/in-memory retriever, 용어 설명, 회의 요약/보고서 생성, 회의별/프로젝트별 챗봇, 태스크 추출 prototype API를 준비한다. Backend 권한 필터 이후 컨텍스트 조립은 target architecture로 유지한다. | `ai/**`, `specs/001-meetingmind-core/*` | Backend 권한 필터, 실제 STT 저장 API, pgvector migration, Frontend 화면 연결은 후속 담당자 작업. 그 전까지 mock 또는 권한 필터링된 prototype context만 사용 |
| Data | TBD | TBD | PostgreSQL/pgvector 스키마 초안과 migration | `backend/**`, `specs/001-meetingmind-core/data-model.md` | Q-001, Q-002 |

## Conflict Boundaries

- Single-owner files:
  - Auth/Login owner: `frontend/src/components/GoogleLoginModal.tsx`, future `frontend/src/auth/**`, future `backend/src/main/java/com/meetingmind/demo/auth/**`
  - `specs/001-meetingmind-core/contracts/*`: Docs/Contracts owner가 형식과 shared contract를 관리하고, 기능별 owner가 담당 API 파일을 변경한다.
  - `specs/001-meetingmind-core/contracts/api.md`: legacy snapshot이다. 신규 구현 기준으로 수정하지 않는다.
  - `specs/001-meetingmind-core/data-model.md`: Docs/Contracts 또는 Data owner가 변경하고 Backend가 따른다.
  - migration 파일: Data owner가 순차 생성한다.
- Shared contracts:
  - Auth API, token claim, refresh token session, API 계약, 권한 등급, Meeting AI response shape는 구현 전 먼저 합의한다.
- Do Not Edit Concurrently:
  - Auth/Login owner와 합의 없이 `GoogleLoginModal.tsx`, auth token 저장/전달 코드, backend auth package를 동시에 수정하지 않는다.
  - 같은 API endpoint 구현 파일
  - 같은 migration 파일
  - 같은 화면 route/component 파일
  - 같은 `specs/001-meetingmind-core/contracts/*.md` 파일
  - `specs/001-meetingmind-core/data-model.md`

## Integration Order

1. `requirements/INDEX.md`에서 작업별 요구사항 문서를 확인한다.
2. Q-001 로그인 방식과 Auth API 경계는 확정된 기준을 따른다.
3. 회의 권한 등급은 `HOST`, `EDITOR`, `VIEWER`와 회의 게스트 기준을 따른다.
4. API 계약과 데이터 모델을 요구사항 용어/상태/권한 기준으로 맞춘다.
5. 공통 오류 응답, Meeting status, transcript/speaker 계약을 확정한다.
6. Backend 도메인 모델과 권한 필터를 먼저 구현한다.
7. AI 담당은 실제 STT/DB를 기다리지 않고 `TranscriptSegment` 유사 mock 데이터에서 RAG chunk를 만드는 adapter와 in-memory retriever를 먼저 구현한다.
8. Frontend와 AI는 확정된 계약에 맞춰 병렬 구현한다.
9. Data migration은 Backend 모델과 맞춘 뒤 순차 통합한다.
10. Frontend, Backend, AI 권장 검증을 실행하고 통합 흐름을 수동 확인한다.

## Dashboard and Calendar Frontend Plan

FR-DASH/FR-CAL 구현은 현재 prototype 경계를 숨기지 않는다. Backend target API는 Space 생성/목록/회의 생성 일부만 구현되어 있고, Space 수정/삭제, dashboard summary, calendar events는 `contracts/space-api.md`의 target contract 단계다. 따라서 1차 구현은 frontend mock fallback과 local state를 기준으로 화면 흐름을 완성하고, API client/type 이름은 target contract와 맞춰 둔다.

### Scope

- FR-DASH-01/02/06/07: `/spaces`를 프로젝트 대시보드 홈으로 정리한다. 참여 프로젝트 목록, 검색/필터, 오늘 회의, 최근 활동, 미완료 Action Item 요약을 같은 화면에서 제공한다.
- FR-DASH-03: `/project-overview`는 회의 목록, 최근 문서, Action Item, Project AI 진입점을 유지하되 선택 Space 기준 데이터와 빈 상태가 깨지지 않게 한다.
- FR-DASH-04/05: 프로젝트 수정/삭제는 owner/admin 권한 UI affordance와 확인 절차를 먼저 구현한다. 현재 prototype은 local state/mock fallback으로 반영하고, target API는 `PATCH /api/v1/spaces/{spaceId}`, `DELETE /api/v1/spaces/{spaceId}`에 연결할 수 있게 client 경계를 둔다.
- FR-CAL-01/02/03: 월/주/일 전환 가능한 캘린더 뷰를 추가하고 접근 가능한 회의 일정만 표시한다. 일정 클릭은 기존 회의 대기 또는 보고서 화면 라우팅 규칙을 재사용한다.
- FR-CAL-04: 캘린더에서 회의 일정 생성은 `POST /api/v1/spaces/{spaceId}/meetings`와 같은 request shape를 사용한다. 현재 local state 생성은 제목/일시를 보존하도록 확장한다.
- FR-CAL-05: 회의 알림은 아직 발송 backend가 없으므로 다가오는 회의 표시와 알림 준비 상태까지만 frontend에서 표현하고, 실제 발송은 후속 backend/notification task로 둔다.

### Implementation Slices

1. 현재 화면/계약 gap을 `implement.md`에 기록하고 M017 task를 확정한다.
2. Frontend type/API client에 dashboard summary, calendar event, Space update/delete 후보를 추가한다.
3. `/spaces`를 프로젝트 대시보드 홈으로 정리하고 mock/API 데이터 소스 표시를 낮은 비중으로 추가한다.
4. 프로젝트 수정/삭제 local flow를 추가하되 owner/admin 권한 문구와 확인 절차를 분리한다.
5. 캘린더 월/주/일 뷰와 일정 클릭 라우팅을 추가한다.
6. 캘린더 일정 생성 flow를 기존 회의 생성 local state와 연결한다.
7. `npm run build`, 주요 route smoke 결과, 미구현 backend gap을 `implement.md`에 기록한다.

## Project Workspace Frontend Plan

FR-MREG/FR-ACL/FR-KAN/FR-PBOT/FR-PERM/FR-OWN 구현은 프로젝트 상세 화면을 단순 정보 카드가 아니라 운영 화면으로 확장하는 작업이다. 핵심 원칙은 Space 멤버십과 MeetingParticipant ACL을 화면 상태에서도 분리하는 것이다. 일반 멤버는 Space에 속해도 명시 MeetingParticipant 또는 owner/admin override가 없으면 회의 상세, Meeting AI, transcript/report 진입점이 제한되어야 한다.

### Scope

- FR-MREG-01/05/06: `/project-overview`의 회의 목록을 생성, 상태 표시, 상태 변경 후보, 회의 상세 진입이 가능한 관리 목록으로 정리한다.
- FR-MREG-02/03, FR-ACL-01/02/03/04/05/06/07: 회의별 참여자/role UI를 추가한다. `VIEWER`, `EDITOR`, `HOST`, `ACTIVE`, `REVOKED`, owner/admin override, 마지막 active HOST 보호, 삭제 권한 제한을 화면 copy와 disabled state에 반영한다. 감사 로그는 backend target gap이므로 이번 frontend slice에서는 "기록 대상 이벤트"를 UI/문서에 남긴다.
- FR-KAN-01~08: 프로젝트 안의 TaskCard 칸반을 추가한다. 상태는 `TODO`, `IN_PROGRESS`, `DONE`만 사용하고, drag-and-drop은 새 dependency 없이 HTML drag event 또는 명시 이동 버튼 중 기존 코드에 더 작은 방식을 선택한다.
- FR-PBOT-01~05: Project AI 패널은 공식 Project Knowledge와 접근 가능한 meeting source를 구분해 출처를 표시한다. backend 권한 선필터가 들어오기 전까지 frontend는 prototype context임을 유지하고, 권한 밖 데이터 혼입을 피하도록 mock source를 선택 Space/허용 회의로 제한한다.
- FR-PERM-01~05: `TeamMembersPage`의 멤버 목록/초대/요청 승인 흐름을 SpaceMember role 기준으로 정리한다. Space invitation과 Meeting invitation은 같은 UI copy로 섞지 않는다.
- FR-OWN-01~03: owner transfer는 별도 확인 절차가 필요한 위험 작업으로 분리한다. 실제 backend transaction 전까지는 frontend local flow와 확인 모달, 후속 backend gap 기록까지만 구현한다.

### Implementation Slices

1. Project workspace 요구/계약 gap을 기록하고 M018 task를 확정한다.
2. Frontend type/API client에 Meeting detail/participant/invitation/update/delete, TaskCard CRUD, Space member/invitation/owner transfer, Project AI source 후보를 보강한다.
3. `App.tsx` local state를 SpaceMember, MeetingParticipant, TaskCard, Project AI source가 분리되도록 정리한다.
4. `/project-overview` 회의 목록을 관리형 목록으로 확장하고 회의 생성/삭제/상태 표시/권한 제한 affordance를 추가한다.
5. 회의별 ACL 패널을 추가해 참여자 초대, role 변경, access 회수, 마지막 HOST 보호 상태를 표현한다.
6. 칸반 보드와 카드 생성/편집/상태 이동/삭제/필터를 추가한다.
7. Project AI 패널에서 공식 지식과 회의 기록 출처를 구분하고 근거 없음/unsupported 상태를 UI에 반영한다.
8. `TeamMembersPage`에서 Space 멤버 초대, 역할 변경, 제거, owner transfer 확인 flow를 정리한다.
9. `npm run build`, 주요 route smoke, 권한 관련 negative case를 `implement.md`에 기록한다.

### Backend and Contract Gaps

- Backend는 target Space/Meeting 생성 일부를 구현했지만 Meeting participant 관리, meeting invitation, meeting delete/update, Kanban, Space invitation/member role/owner transfer, Project AI backend 권한 필터는 아직 target contract 단계다.
- AuditLog 저장은 문서와 ERD에 있지만 runtime 구현 전이다. 권한 부여/회수, task 변경, owner transfer는 후속 backend task에서 audit event를 검증해야 한다.
- Project AI prototype은 AI 서버에 구현되어 있지만 target architecture의 Backend 권한 필터 이후 context 조립은 후속 backend/AI integration 작업이다.

## Meeting Workspace Frontend Plan

FR-RPT/FR-MBOT/FR-TASK 구현은 단일 회의 scope를 제품 경험에서 보장하는 작업이다. Report Agent와 Meeting AI는 같은 회의 transcript/decision/action/report source를 공유하되, Project 전체 데이터나 다른 meeting source를 섞지 않는다. AI가 만든 저장성 산출물은 바로 공식 데이터로 취급하지 않고 `CANDIDATE` 상태로 보여준 뒤 사용자가 확정해야 한다.

### Scope

- FR-RPT-01/02: 완료된 transcript/dialogue에서 AI 회의록 후보를 생성한다. 후보에는 생성 시각, 생성자, 원천 meeting id, source ids를 표시하고 확정 전 공식 report 목록과 구분한다.
- FR-RPT-03/06: 후보 또는 초안을 확정하면 `MeetingReport.CONFIRMED`가 되고, 회의당 current confirmed report는 하나만 유지한다. 현재 frontend slice는 confirm UX와 version 표시를 먼저 만들고, backend 저장은 target API gap으로 기록한다.
- FR-RPT-04/05: Report Agent의 AI 대화 편집과 수동 편집을 같은 draft state에 반영한다. 범위 밖 내용 추가 요청은 확인 필요 또는 unsupported 상태로 표현한다.
- FR-RPT-07: Markdown export는 frontend에서 현재 draft를 기준으로 우선 제공할 수 있다. PDF/DOCX는 후속 backend/export task로 분리한다.
- FR-MBOT-01~04: Meeting AI는 `POST /api/meeting-ai/chat` target shape로 전환하고, 단일 `meetingId` source만 전달한다. 응답은 source 시간/발화자/결정 id를 표시하며 근거 없음은 추정 답변처럼 보이지 않게 한다.
- FR-TASK-01~04: report/transcript에서 TaskCandidate를 추출하고, 등록 전 검토/편집 후 `TaskCard`로 확정하는 흐름을 만든다. 확정 전 candidate는 칸반 카드로 표시하지 않는다.

### Implementation Slices

1. Meeting workspace 요구/계약 gap을 기록하고 M019 task를 확정한다.
2. Frontend type/API client에 Meeting AI chat, generate report, report list/confirm/update/download, task candidate extract/confirm 후보를 추가한다.
3. `App.tsx` 또는 meeting-scoped local state에 report candidate/draft/current confirmed report/task candidate를 분리한다.
4. `MeetingAiPage`를 legacy `/api/meeting-ai/ask`에서 source-aware `/api/meeting-ai/chat` shape로 전환하고 출처/unsupported UI를 추가한다.
5. `ReportAgentPage`에 report candidate 생성, draft 편집, confirm, version/current 표시, Markdown export를 연결한다.
6. AI 대화 편집과 수동 편집 충돌을 막기 위한 pending change/apply/revert 상태를 정리한다.
7. Task candidate 추출, 검토, 등록 전 편집, 칸반 등록 local flow를 추가하고 M018 칸반 state와 연결한다.
8. scope negative case를 점검한다. 다른 meeting/project source가 Meeting AI, report generation, task extraction payload에 들어가지 않아야 한다.
9. `npm run build`, AI endpoint smoke 가능 여부, 수동 route smoke 결과와 backend gap을 `implement.md`에 기록한다.

### Backend and AI Gaps

- AI 서버에는 `/api/meeting-ai/chat`, `/api/meeting-ai/generate-report`, `/api/meeting-ai/extract-tasks` prototype이 있지만, target architecture의 Backend 권한 필터 이후 context 조립은 아직 없다.
- Backend에는 `MeetingReport`와 artifact domain 일부가 있으나 report candidate 생성/저장/confirm/update/download controller, TaskCandidate 저장/confirm controller는 아직 target contract 단계다.
- Report export는 Markdown 우선 결정이 있으나 PDF/DOCX 생성 방식은 후속 작업이다.
- TaskCandidate confirm은 Kanban API 계약에 있지만 실제 칸반 저장소와 frontend state 연결은 M018/M019 통합 지점이다.

## Project AI Backend Permission Prefilter Plan

### Scope

- Public route: `POST /api/v1/spaces/{spaceId}/ai/chat`
- Internal route: `POST /api/internal/project-ai/chat`
- Backend는 활성 SpaceMember를 확인한 뒤 ProjectKnowledge와 사용자가 읽을 수 있는 회의의 current/confirmed report summary만 AI context로 조립한다.
- AI 서버는 `projectKnowledge`, `meetingSummary` source만 project scope에서 검색하고 source project/meeting allowlist를 다시 검증한다.
- Frontend는 질문만 Backend에 전달하며 mock transcript/decision/action context를 직접 보내지 않는다.
- 실제 PostgreSQL/pgvector 검색, embedding worker, 대화 이력, persistent audit log는 이번 slice에서 제외한다.

### Parallel Work Plan

- Team members: 1
- Agents: 1 Codex
- Workstreams: contracts -> AI -> Backend -> Frontend -> verification 순차 처리
- Shared contracts: `contracts/ai-api.md`, `contracts/space-api.md`
- Conflict boundaries: AI는 `ai/**`, Backend는 Project AI controller/service/dto와 기존 policy/domain의 최소 확장, Frontend는 `App.tsx`, `api/workspace.ts`, `ProjectOverviewPage.tsx`만 수정한다.
- Integration order: 계약 확정 후 AI strict schema, Backend context/gateway, Frontend 호출 전환, 전체 검증 순서로 통합한다.

### Security and Data Rules

- Project AI는 SpaceMember만 사용할 수 있고 meeting guest는 사용할 수 없다.
- OWNER/ADMIN은 기존 `MeetingAccessPolicy` manager override를 따르고 MEMBER는 active MeetingParticipant인 회의만 포함한다.
- `REVOKED` participant의 회의는 `allowedMeetingIds`와 `sources[]` 모두에서 제외한다.
- ProjectKnowledge는 `PUBLISHED`, `embeddingStatus=COMPLETED`이고 삭제되지 않은 항목만 포함한다.
- 회의 기록은 1차 연동에서 current confirmed report summary만 포함하며 원문 transcript 전체를 프로젝트 컨텍스트로 확장하지 않는다.

## AI Report Candidate Backend Route Plan

### Scope

- Public route: `POST /api/v1/meetings/{meetingId}/reports/generate`
- Internal route: `POST /api/internal/meeting-ai/generate-report`
- Backend는 `OWNER`/`ADMIN` 또는 `HOST`/`EDITOR` 권한을 확인한 뒤 해당 회의 transcript와 current/confirmed report의 decision/action source만 조립한다.
- AI 서버는 source type과 meeting scope를 다시 검증하고, 근거가 없으면 LLM 호출 없이 `unsupported=true`를 반환한다.
- 지원되는 결과만 `MeetingReport.CANDIDATE`로 임시 저장하고 공식 report와 Project AI source에서는 제외한다.
- Frontend Report Agent는 인증 header와 `meetingId`만 사용해 Backend endpoint를 호출한다.

### Integration Order

1. 계약, 권한, candidate 저장 shape와 ERD/data-model 영향을 확정한다.
2. AI strict schema와 단일 meeting source validator를 구현한다.
3. Backend 권한 검증, context 조립, AI gateway, candidate 저장을 연결한다.
4. Frontend 로컬 candidate 생성을 Backend 응답으로 전환한다.
5. 단위 테스트, 권한 negative case, 실제 API smoke를 실행한다.

### Persistence Boundary

- M022 당시 runtime 저장소는 in-memory였으며 M032에서 같은 `markdown`, `createdBy`, `sourceIds` 계약을 JSONB 포함 PostgreSQL repository에 연결했다.
- candidate version은 동일 meeting의 기존 report 최대 version 다음 값으로 생성한다.
- AI `unsupported=true` 또는 provider 실패 결과는 저장하지 않는다.
- confirm, manual edit, version history 조회, export는 M022 이후 범위다.

## Report Confirm and Current Version Plan

### Scope

- Public route: `POST /api/v1/meetings/{meetingId}/reports/{reportId}/confirm`
- `OWNER`/`ADMIN` 또는 해당 회의 `HOST`/`EDITOR`만 확정할 수 있다.
- `CANDIDATE` 또는 `DRAFT`만 `CONFIRMED`로 전환하고 중복 확정은 거부한다.
- 동일 meeting의 기존 current confirmed report를 `isCurrent=false`로 전환한 뒤 대상 report만 `isCurrent=true`와 `confirmedAt`을 기록한다.
- Frontend는 Backend 확정 성공 후 candidate status, version, current 표시를 갱신한다.

### Boundaries

- candidate TTL은 `Q-008` 결정 전이므로 이번 slice에서 임의 정책을 넣지 않는다.
- 수동 update, version history 조회/복원, export, persistent audit log는 후속 범위다.
- M032에서 runtime 저장소를 PostgreSQL로 전환했고 V3 partial unique index와 meeting row lock으로 current confirmed report 단일성을 유지한다.

### Integration Order

1. 계약과 `confirmedAt` 모델을 확정한다.
2. in-memory domain transition과 current 단일 제약을 구현한다.
3. 인증/편집 권한을 적용한 confirm controller/service를 연결한다.
4. Frontend 확정 버튼과 상태 표시를 Backend response에 연결한다.
5. 중복 확정, 다른 meeting report, VIEWER 거부, current 교체를 검증한다.

## Task Candidate Backend Route and TaskCard Confirmation Plan

### Scope

- Public routes: `POST /api/v1/meetings/{meetingId}/task-candidates/generate`, `GET /api/v1/meetings/{meetingId}/task-candidates`, `POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/confirm`
- Internal route: `POST /api/internal/meeting-ai/extract-tasks`
- Backend는 회의 편집 권한을 먼저 확인한 뒤 해당 회의 transcript와 current confirmed report source만 AI context로 조립한다.
- 지원되는 AI 결과는 `TaskCandidate.CANDIDATE`로 저장하고, 사용자가 확정할 때 `TaskCard`를 하나만 생성한다.
- Frontend Report Agent의 로컬 추출/등록 상태를 Backend 응답으로 전환한다.

### Permissions and Data Rules

- 생성은 `OWNER`/`ADMIN` 또는 해당 회의의 active `HOST`/`EDITOR`, 조회는 active 회의 접근 권한이 필요하다.
- 확정은 active SpaceMember와 회의 편집 권한을 모두 요구하므로 meeting guest는 프로젝트 칸반 카드를 만들 수 없다.
- AI 내부 endpoint는 `transcript`, `report`, `decision`, `actionItem`만 허용하고 request와 다른 project/meeting source를 거부한다.
- 담당자 이름은 active participant와 active SpaceMember가 정확히 일치할 때만 `suggestedAssigneeId`로 연결한다.
- `unsupported=true`는 저장하지 않고 `TaskCard.sourceCandidateId` unique 제약으로 중복 확정을 방지한다.

### Boundaries

- candidate TTL은 `Q-009` 결정 전이므로 이번 slice에서 임의 정책을 넣지 않는다.
- 후보 제외 API와 일반 Kanban 카드 CRUD/목록 화면의 Backend 전환은 후속 범위다.
- M032에서 runtime 저장소를 PostgreSQL로 전환했고 task candidate/card unique 제약과 row lock을 실제 확정 transaction에 연결했다.

### Integration Order

1. 계약, 권한, 상태, 모델과 migration을 확정한다.
2. AI strict schema와 단일 meeting source validator를 구현한다.
3. Backend 생성/저장, 조회, 확정 domain transition을 연결한다.
4. Frontend 추출/검토/확정 flow를 Backend API로 전환한다.
5. scope, 권한, 중복 negative case와 정상 API flow를 검증한다.

## AI Provider Safety Plan

- OpenAI Responses API 호출 timeout은 `requirements/performance.md`의 prototype 목표를 따라 챗봇, 용어 설명, 태스크 추출은 30초, 보고서 생성은 60초로 적용한다.
- provider 설정 누락, HTTP 오류, 연결 오류, 응답 본문 오류는 외부 원문을 노출하지 않고 `503 AI_PROVIDER_UNAVAILABLE`로 정규화하며 `{code, message, fieldErrors, traceId}` 공통 body를 사용한다.
- 생성 요청 자동 재시도는 응답 유실 시 같은 요청의 중복 과금 가능성이 있고 provider idempotency key를 사용하지 않으므로 이번 milestone에서 제외한다. 사용자가 Backend를 통해 명시적으로 다시 요청하는 흐름을 유지한다.
- 이번 변경은 AI provider adapter의 오류·timeout 경계만 바꾸며 API request/response 데이터 모델, ERD, RAG scope에는 영향이 없다.
- 구현 비교 문서는 현재 권한 기반 AI 통합 prototype 경계를 요약하고 상세 상태는 `tasks.md`, `implement.md`, `contracts/*`로 연결한다.
- internal API 서비스 인증은 Backend가 동일 credential/header를 전송해야 하므로 별도 shared contract milestone으로 분리한다.

## M027 Backend Permission Matrix Runtime Plan

M027은 권한 매트릭스를 backend runtime 경계에 연결하는 작업이다. 현재 backend에는 `SpaceAccessPolicy`와 `MeetingAccessPolicy` 단위 검증이 있으나, SpaceMember role/remove, MeetingParticipant mutation, owner transfer, Project AI context 후보 조회가 실제 controller/domain mutation에 모두 연결되어 있지는 않다. 이번 milestone은 in-memory store/service 패턴을 유지하면서 보안 규칙을 API 실행 경계에 적용한다.

### Scope

- SpaceMember role 변경/제거: `PATCH /api/v1/spaces/{spaceId}/members/{memberId}`, `DELETE /api/v1/spaces/{spaceId}/members/{memberId}` target contract에 맞춘다. role 변경은 `OWNER` 전용이고 변경 대상 role은 `ADMIN` 또는 `MEMBER`만 허용한다.
- SpaceMember 제거 cascade: 제거된 member의 같은 Space 내 `participantType=member` MeetingParticipant는 `participantType=guest`로 전환해 회의 단독 권한으로 남긴다. 프로젝트 접근권 제거와 특정 회의 접근권 revoke는 분리한다.
- MeetingParticipant 관리: add/update/revoke API는 관리자/host의 수동 ACL 조정용으로 둔다. 사용자-facing 참가 흐름은 join request 생성과 host 승인이다. `VIEWER`/`EDITOR`/`HOST` role hierarchy와 `ACTIVE`/`REVOKED` access status를 적용한다. `guest` participant는 SpaceMember 또는 프로젝트 접근권을 만들지 않는다.
- 마지막 active HOST 보호: participant role 변경, access revoke, meeting participant 삭제성 mutation에 적용한다. SpaceMember 제거는 meeting participant를 제거하지 않으므로 마지막 HOST를 깨지 않는다.
- Owner transfer: 현재 `OWNER`만 실행할 수 있고 대상은 active SpaceMember만 허용한다. 확인 문자열은 `TRANSFER OWNER`로 고정하며, 성공 시 새 owner는 `OWNER`, 기존 owner는 요청한 `ADMIN` 또는 `MEMBER`로 강등한다.
- Project AI context 후보: backend가 RAG/AI 서버 호출 전에 `projectKnowledge[]`와 accessible `meetings[]`를 분리해 후보를 만들고, revoked/default-deny/guest-only source는 제외한다. 회의 게스트는 SpaceMember가 아니므로 Project AI 권한을 기본으로 갖지 않는다.
- AuditLog: 이번 in-memory runtime에서 작은 event list로 구현할 수 있으면 actor, target, before/after, timestamp를 남긴다. 저장소 범위가 커지면 gap을 명시하고 DB/Audit milestone로 분리한다.

### Non-Scope

- DB/JPA 영속화 전환은 M027 범위가 아니다. 현재 backend의 in-memory domain/store 경계를 유지한다.
- Kanban persistence, MeetingReport confirm/download, TaskCandidate 저장/confirm은 후속 backend API milestone로 분리한다.
- 실제 pgvector 검색과 AI 서버 호출은 M027에서 구현하지 않는다. M027은 AI context 후보가 backend 권한 필터를 통과하는 경계까지만 닫는다.
- ADMIN의 SpaceMember 제거 권한은 기본 허용하지 않는다. 요구사항과 삭제 정책의 보수적 기준에 맞춰 owner-only로 시작하고, ADMIN 제거 허용은 명시 정책이 생기면 별도 변경한다.

### Permission Rules

- Default-deny: SpaceMember 또는 MeetingParticipant ACL이 없으면 접근을 거부한다. 예외는 active `OWNER`/`ADMIN` override가 명시된 API뿐이다.
- Owner/admin override: active SpaceMember `OWNER`/`ADMIN`은 meeting read/edit/participant management를 override할 수 있다. meeting delete는 기본 `OWNER` 또는 active `HOST`만 허용하며 `ADMIN` delete는 허용하지 않는다.
- Revocation immediate effect: `REVOKED` participant는 meeting read/edit/delete, LiveKit, Meeting AI에서 즉시 제외된다. removed SpaceMember는 프로젝트, Project Knowledge, Project AI context 후보에서 즉시 제외되지만, active MeetingParticipant가 남아 있으면 해당 회의 범위 접근은 유지된다.
- Last active HOST: 각 meeting에는 active `HOST`가 최소 1명 남아야 한다. role downgrade, revoke, delete성 mutation이 이 제약을 깨면 `LAST_ACTIVE_HOST_REQUIRED`로 거부한다.
- Owner transfer safety: 확인 문자열이 없거나 불일치하면 domain mutation을 시작하지 않는다. 성공 결과는 owner 공백과 중복 owner가 없는 단일 transaction local flow로 처리한다.

### Implementation Slices

1. `InMemoryWorkspaceStore`에 SpaceMember update/remove, MeetingParticipant create/update/revoke, optional AuditEvent 저장 helper를 추가한다.
2. `WorkspaceDomainService`에 SpaceMember role 변경/제거, owner transfer, participant add/update/revoke, Project AI 후보 조회 use case를 추가한다.
3. `SpaceController`와 meeting 관련 controller에 target endpoint를 연결하고 공통 error code를 contract와 맞춘다.
4. SpaceMember 제거 시 프로젝트 권한만 제거하고 기존 member participant는 guest participant로 전환한다.
5. 회의 참가 신청은 `joinCode` 또는 회의 URL을 입력받아 `MeetingJoinRequest`를 만들고, host가 승인/거절한다.
6. Project AI 후보 조회는 active SpaceMember 확인 뒤 Project Knowledge와 `MeetingAccessPolicy.requireReadAccess`를 통과한 meeting source만 반환한다.
7. 권한 negative case를 controller/domain test로 고정한다.
8. `cd backend && ./gradlew test`, `git diff --check` 결과와 runtime gap을 `implement.md`와 `tasks.md`에 기록한다.

### Verification Targets

- SpaceMember role change: owner succeeds, admin/member/nonmember denied, owner self downgrade is owner-transfer only.
- SpaceMember removal: owner removal denied, removed member blocked from project/Project AI, existing meeting participant remains active as guest unless separately revoked.
- Meeting join request: joinCode/url로 신청 가능, host 승인 전까지 participant 미생성, 승인 시 viewer + member/guest 분기.
- MeetingParticipant mutation: host/owner/admin allowed, editor/viewer/default-deny denied, revoked target blocked immediately.
- Owner transfer: active target only, missing confirmation denied, previous owner becomes selected lower role, no duplicate owner.
- Project AI context: SpaceMember only, Project Knowledge separated from Meeting record, inaccessible/revoked meeting source excluded, unsupported/no-source path remains explicit.

## M028 Meeting Join Request Approval

M028은 회의 생성 시 사용자를 직접 지정하는 방식을 사용자-facing 기본 흐름에서 제외하고, URL/코드 기반 참가 신청과 HOST 승인으로 전환한다. 기존 participant add API는 OWNER/ADMIN/active HOST의 운영상 ACL 조정 경계로 유지한다.

### Implementation Slices

1. 회의 생성 시 UUID 기반의 추측하기 어려운 unique `joinCode`를 만든다.
2. `POST /api/v1/meetings/join-requests`가 URL 또는 코드만 받아 meeting을 조회하고 `PENDING` 신청을 만든다.
3. 신청 승인/거절은 active HOST 또는 Space OWNER/ADMIN override만 허용한다.
4. 승인 시 기본 `VIEWER` participant를 만들고 SpaceMember 여부에 따라 `member`/`guest`만 분기한다.
5. 잘못된 코드, 중복 pending, 기존 participant, 권한 없는 검토, 완료 신청 재처리를 거부한다.
6. JoinRequest에는 참가 코드 원문을 복제 저장하지 않고 meeting/user/status/review 정보만 보존한다.
7. 영속화 시에는 Meeting의 코드 원문 대신 `joinCodeHash` 저장을 사용한다. 현재 in-memory prototype은 raw code 보관 gap을 명시한다.

### Verification Targets

- raw code와 generated URL이 같은 meeting의 pending request를 만든다.
- meeting ID에서 joinCode를 추측할 수 없다.
- invalid code는 meeting 존재 여부를 구분해 노출하지 않는다.
- 승인 전에는 participant와 회의 접근권이 없다.
- viewer/editor/nonparticipant는 승인/거절할 수 없다.
- 승인 후 guest는 해당 회의만 접근하고 Project AI/Project Knowledge 권한을 얻지 않는다.
- 승인 또는 거절된 신청은 다시 처리할 수 없다.

## M029 Frontend Meeting Access and Permission Surfaces

M029는 M028 Backend 참가 신청/승인 계약을 사용자가 확인하고 실행할 수 있는 Frontend 화면에 연결한다. 기존 `TeamMembersPage`의 Space role 관리와 `ProjectOverviewPage`의 회의 ACL 관리 UI는 유지하되, 회의 참가 승인이 SpaceMember를 생성하지 않도록 의미를 바로잡는다.

### Implementation Slices

1. Frontend target type/API client에 JoinRequest 생성/목록/승인/거절 계약을 추가하고 superseded MeetingInvitation client는 제거한다.
2. `/meeting-access`에서 URL 또는 코드를 입력해 `PENDING` 신청을 만들고 승인 대기 상태를 표시한다.
3. 승인 후 사용자가 직접 접근 상태를 다시 확인할 수 있도록 meeting participant 조회를 access probe로 사용한다.
4. `/live-meeting`은 `meetingId` 기준 Backend access probe가 성공한 경우에만 prejoin/media 진입을 허용한다. ID 누락, 403, API 실패는 default-deny로 처리한다.
5. `TeamMembersPage`의 회의 참가 신청 승인은 프로젝트 멤버가 아니라 해당 회의의 `VIEWER` guest participant만 local state에 추가한다.
6. ProjectOverview/WorkspaceHome meeting link에 stable `meetingId`를 전달하고, 직접 participant 추가 UI는 운영 ACL 조정임을 표시한다.

### Verification Targets

- Space role과 회의 ACL을 서로 다른 화면/표현으로 확인할 수 있다.
- URL/코드 신청 성공 후 승인 전에는 회의 입장 CTA가 노출되지 않는다.
- Backend access probe 실패 시 카메라/마이크 요청과 회의 시작을 수행하지 않는다.
- active participant 또는 OWNER/ADMIN override access probe 성공 시 role/override 상태를 표시하고 prejoin을 허용한다.
- HOST 승인 local flow가 SpaceMember 수를 늘리거나 Project AI 권한을 만들지 않는다.
- desktop/mobile에서 권한 상태, 신청 form, denied/pending/allowed 상태가 겹치거나 잘리지 않는다.

## M031 CI Quality and Supply Chain Gates

M031은 M030의 PostgreSQL/pgvector 기준선을 포함해 현재 CI의 compile/build 기준선을 merge gate로 강화한다. 제품 API나 데이터 모델은 바꾸지 않으며, target PostgreSQL schema와 배포 image가 실제로 생성 가능한지 검증한다.

### Decisions

- workflow는 `pull_request`의 `dev`/`main` 대상과 `push`의 `dev`/`main`에서 실행한다. required check 안정성을 위해 path filter는 두지 않는다.
- `main`은 PR과 최종 CI gate를 필수로 하고 직접 push, force push, branch 삭제를 금지한다. `dev`는 통합 피드백을 위해 push CI를 우선 적용하며 보호 강도는 팀 운영 정책 확정 후 별도로 올린다.
- Backend는 Java 21에서 test와 `bootJar`를 모두 실행한다.
- migration은 M030과 같은 PostgreSQL 16 계열의 pgvector service container에서 Backend가 사용하는 Flyway library로 V1~V10 전체를 적용하고 schema history를 확인한다. M032 이후에는 migration 유효성과 JDBC runtime round-trip을 각각 독립 테스트로 검증한다.
- Backend/AI Dockerfile은 build context를 각 디렉터리로 제한하고 non-root minimal runtime image를 사용한다. registry push는 이번 범위에 포함하지 않으며 CI가 계산한 content digest를 기록한다.
- Frontend는 lint/unit/build를 먼저 required gate에 넣고, Playwright는 실제 Backend와 Vite를 기동해 로그인 및 회의 access gate의 허용/거부 흐름을 검증한다.
- Playwright Backend는 기본 `local` profile의 외부 DB 의존성을 피하도록 `test` profile을 명시한다. PostgreSQL/Flyway 검증 책임은 migration job에만 둔다.
- secret scan은 repository history를 대상으로 하고 image scan은 Backend/AI image의 합의된 severity를 차단한다. 외부 action은 commit SHA 또는 immutable version으로 고정하고 최소 `contents: read` 권한을 사용한다.
- 최종 summary job은 모든 선행 job을 `always()`로 집계하되 실패를 성공으로 덮지 않는다. branch protection은 이 안정적인 최종 check를 required context로 사용한다.

### Implementation Order

1. trigger, concurrency, permissions와 stable check 이름을 확정한다.
2. Backend test/bootJar와 PostgreSQL/pgvector Flyway migration을 독립 job으로 추가한다.
3. Backend/AI Dockerfile을 추가하고 image build/digest 출력을 만든다.
4. Frontend ESLint/unit test 기반과 CI script를 추가한다.
5. 로그인, 무권한 회의 거부, active participant prejoin 허용 Playwright smoke를 추가한다.
6. secret/image scan을 차단 gate로 추가하고 초기 결과를 triage한다.
7. GitHub Summary와 최종 gate를 연결한다.
8. 원격 workflow가 한 번 실행된 뒤 `main` branch protection을 관리자 권한으로 적용한다.

### Current Gaps

- trigger/concurrency/최소 권한과 Backend/Frontend/AI job, Dockerfile, PostgreSQL V1~V10 migration job, Playwright, scanner, Summary/`CI Gate` 코드는 작성되어 있다.
- Backend `test bootJar`, Frontend lint/unit/build, AI compile/unit과 Docker 기반 PostgreSQL migration, Playwright, Backend/AI image build/digest, Trivy HIGH/CRITICAL scan은 로컬에서 통과했다.
- Gitleaks full-history scan은 과거 `backend/.env` 4건과 `ai/.env.example` 1건을 탐지했다. OpenAI/LiveKit 기존 credential은 공급자에서 폐기·재발급됐고, 저장소 전체 이력 재작성 대신 해당 5건의 exact fingerprint만 `.gitleaksignore`에 기록한다. 새로운 commit/path/rule/line 조합은 계속 차단한다.
- Trivy 0.72.0 Linux 64-bit archive checksum은 공식 release checksum과 대조했고, 잘못 사용된 32-bit checksum을 64-bit 값으로 교정했다. Gitleaks 8.30.1 Linux x64 checksum도 공식 release와 일치한다.
- `actions/checkout`, `setup-java`, `setup-node`, `setup-python`, `upload-artifact`는 공식 major ref의 현재 commit SHA로 고정했다.
- 원격 PR #29에서 모든 job과 `CI Gate` context 생성·성공을 확인했다. 다만 현재 private repository 요금제는 branch protection API를 `403 Upgrade to GitHub Pro or make this repository public`로 거부하므로 T244 적용은 요금제/공개 범위 결정까지 차단된다.
- M032에서 Auth/Workspace와 저장된 Transcript/Report/Task/Knowledge/Audit runtime repository를 PostgreSQL에 연결했다. legacy STT streaming session/file prototype과 T230 embedding/pgvector semantic retriever는 별도 후속 범위다.

### Remaining Execution Plan

1. **Credential response (T241)**: OpenAI/LiveKit 기존 credential의 공급자 폐기·재발급을 완료했고 값 없이 완료 여부만 기록한다.
2. **History remediation (T242)**: 여러 공유 브랜치의 강제 재작성 영향을 피하기 위해 폐기된 5건의 exact fingerprint만 `.gitleaksignore`로 예외 처리한다. 예외는 commit/path/rule/line 단위이며 신규 secret은 계속 차단한다.
3. **Local integration (T235, T236, T238, T239)**: 격리된 pgvector PostgreSQL의 V1~V10 migration, Backend/AI image build와 digest, Trivy HIGH/CRITICAL scan, Playwright 로그인·회의 access smoke를 완료 상태로 유지하고 workflow 변경 시 재검증한다.
4. **Remote gate (T243)**: PR #29에서 Backend, Frontend, AI, PostgreSQL Migration, Playwright, Container Images, Secret Scan과 최종 `CI Gate` 성공을 확인했다.
5. **Protection and closeout (T244, T245)**: `CI Gate` context는 생성됐지만 private repository branch protection이 현재 GitHub 요금제에서 차단된다. GitHub Pro 업그레이드 또는 공개 저장소 전환 결정 후 required check와 직접 push/force push/삭제 금지를 적용하고 closeout한다.

### Safety and Conflict Boundaries

- `.github/workflows/ci.yml`과 M031 task 상태는 통합 작업자 한 명만 수정한다.
- secret 값, 회전된 값, credential provider 응답은 저장소 문서와 CI 로그에 남기지 않는다.
- 실제 credential 폐기·회전 완료 전에는 history rewrite나 allowlist를 수행하지 않는다. 이번 5건은 폐기·재발급 확인 후 exact fingerprint를 적용했다.
- history rewrite, force push, branch protection 변경은 각각 사용자 명시 승인과 저장소 관리자 권한이 필요한 외부 상태 변경이다.
- 원격 workflow 성공 전에는 T243-T245를 완료로 표시하지 않는다.

### M031 Verification Plan

- Baseline: `cd backend && ./gradlew test bootJar`; `cd frontend && npm run lint && npm run test && npm run build`; `cd ai && python3 -m compileall app tests && python3 -m unittest discover -s tests`
- Secrets: `gitleaks git . --redact --no-banner`이 0건으로 종료한다.
- Migration: 빈 pgvector PostgreSQL 16 DB에서 `MigrationIntegrationTest`가 V1~V10과 `vector` extension을 검증한다.
- Containers: `meetingmind-backend:ci`, `meetingmind-ai:ci` build와 content digest 생성 후 Trivy HIGH/CRITICAL 차단 검사를 통과한다.
- E2E: 실제 Backend `test` profile과 Frontend를 기동해 Playwright 로그인, default-deny, active participant 허용 시나리오를 통과한다.
- Remote: GitHub Actions의 Backend, Frontend, AI, PostgreSQL Migration, Playwright, Container Images, Secret Scan과 최종 `CI Gate`가 모두 성공하고 Summary에 결과/digest가 표시된다.
- Protection: `main`에서 PR과 `CI Gate`가 필수이며 direct push, force push, branch deletion이 금지된다.

## M032 Backend PostgreSQL Runtime Persistence

M032는 T229를 실행 가능한 단위로 분리해 Backend runtime의 in-memory Auth/Workspace 저장소를 PostgreSQL로 전환한다. 사용자 가치와 API shape는 유지하고 저장소 경계, transaction, 재시작 후 데이터 유지와 권한 선필터를 실제 DB에 연결한다.

### Scope and Ownership

- Team members: Backend persistence 담당 1명, AI/RAG 담당 1명.
- Agents: Backend persistence는 Codex 1개가 T246~T253을 순차 처리한다. AI/RAG 담당 에이전트는 별도 작업으로 T230을 처리한다.
- Backend owner: `backend/src/main/java/com/meetingmind/demo/auth/**`, `backend/src/main/java/com/meetingmind/demo/domain/**`, persistence integration test, profile wiring, M032 문서.
- AI/RAG owner: `ai/app/rag.py`, embedding provider/model, vector 차원/index migration, `embedding_jobs`/`embedding_chunks` runtime, pgvector similarity query.
- Shared contract: Backend는 `spaceId`, 단일 `meetingId`, `allowedMeetingIds`, source type/id/title/text metadata를 권한 필터 후 제공한다. 이 shape가 바뀌면 양 workstream이 먼저 계약을 갱신한다.
- Conflict boundary: M032는 기존 V1~V10과 embedding/vector schema를 수정하지 않는다. 관계형 persistence에 schema gap이 발견되면 vector와 무관한 forward-only migration만 별도 검토한다.

### Design

1. `AuthStore`, `WorkspaceStore` port를 도입해 service가 concrete in-memory class에 직접 의존하지 않게 한다.
2. `test` profile은 기존 in-memory adapter를 사용하고 `local`/`db` profile은 Spring JDBC PostgreSQL adapter를 사용한다.
3. Auth signup/login/refresh/logout는 user, identity, session mutation을 transaction으로 묶고 DB unique 제약 충돌을 공개 Auth 오류로 정규화한다.
4. Workspace mutation은 Space 생성+OWNER, Meeting 생성+HOST/participants, owner transfer, join request 승인, report confirm, task confirm, audit 기록을 transaction으로 묶는다.
5. 회의 join code 원문은 생성 응답에서만 반환하고 DB에는 deterministic hash만 저장한다. 참가 신청 조회는 입력 code를 같은 방식으로 hash해 수행한다.
6. Report decision/action/sourceIds와 Task sourceIds는 기존 JSONB 계약을 유지한다.
7. Meeting/Project AI service는 기존 source contract를 유지하되 PostgreSQL에서 읽은 원천 데이터와 active ACL로 context를 조립한다. semantic/vector 검색은 이번 milestone에서 구현하지 않는다.

### Implementation Order

1. 문서/ownership 및 repository port를 확정한다.
2. Auth JDBC adapter와 transaction을 구현한다.
3. Workspace/ACL JDBC adapter를 구현한다.
4. Transcript/Report/Task/Knowledge/Audit JDBC adapter를 구현한다.
5. profile bean wiring과 service transaction 경계를 연결한다.
6. PostgreSQL 통합 테스트로 재시작 유지, hash lookup, 권한 선필터, report/task 원자성을 검증한다.
7. 전체 Backend test와 Flyway migration test를 실행하고 `implement.md`를 갱신한다.

### Verification Plan

- Unit: `cd backend && ./gradlew test`
- PostgreSQL integration: 실행 중인 `compose.local.yml` DB 또는 격리 DB에서 JDBC repository integration test를 실행한다.
- Migration: V1~V10 checksum과 pgvector extension이 그대로 유지되는지 `MigrationIntegrationTest`로 확인한다.
- Runtime: 기본 `local` profile Backend를 재기동한 뒤 생성 데이터가 유지되고 Hikari/Flyway가 정상 연결되는지 확인한다.
- Security: revoked participant, Space 비멤버, 회의 게스트가 권한 밖 AI source에 포함되지 않는 negative test를 통과한다.
- Integrity: owner transfer, join approval, current report, task candidate confirm이 실패 시 부분 저장되지 않고 DB unique/transaction 경계를 지킨다.

## M033 Meeting CRUD PostgreSQL End-to-End

M033은 이미 PostgreSQL에 영속화되는 회의 생성 경로를 회의 목록·상세·수정·삭제 API와 실제 Frontend 화면까지 확장한다. CRUD 성공 여부는 화면의 local state가 아니라 Backend 재조회 결과와 PostgreSQL 상태를 기준으로 판단한다.

### Scope and Ownership

- Team members: Backend/Frontend 통합 담당 1명.
- Agents: Codex 1개가 계약, migration, Backend, Frontend, 검증을 순차 처리한다. 같은 파일을 병렬 수정하지 않는다.
- Shared contract owner: `contracts/meeting-api.md`, `data-model.md`, `erd.md`, `clarify.md`를 Backend 구현보다 먼저 확정한다.
- Backend owner: `WorkspaceStore`, `WorkspaceDomainService`, Meeting controller/DTO, `MeetingAccessPolicy`, JDBC/in-memory adapter, Backend tests, forward-only migration.
- Frontend owner: `frontend/src/api/workspace.ts`, `frontend/src/types.ts`, `App.tsx`, `ProjectOverviewPage.tsx`, `WorkspaceHomePage.tsx`의 target Meeting API 연결.
- AI/RAG 코드와 embedding/vector migration은 수정하지 않는다. 삭제된 회의를 Backend 관계형 조회와 권한 필터 단계에서 제외한다.

### Recommended Delete Baseline

1. `SCHEDULED` 회의 삭제는 `status=CANCELED`와 soft-delete metadata를 같은 transaction에 기록한다.
2. `IN_PROGRESS` 회의 삭제는 `409 MEETING_ALREADY_PROCESSING`으로 거부한다.
3. `ENDED` 회의 삭제는 상태를 유지한 채 soft delete한다. 전사, 보고서, 태스크, 감사 기록은 즉시 물리 삭제하지 않는다.
4. soft-deleted 회의는 일반 목록·상세·캘린더·Meeting AI·Project AI source에서 즉시 제외한다.
5. 삭제 권한은 `OWNER` 또는 해당 회의의 active `HOST`만 허용한다. `ADMIN`, `EDITOR`, `VIEWER`는 기본 거부한다.
6. hard purge, 복구 API, 유예 기간은 이번 milestone 범위 밖이며 보존 정책 작업으로 분리한다.

이 기준은 데이터 손실을 최소화하는 권장안이다. 구현 전 `clarify.md`, API 계약, 데이터 모델에 결정사항으로 확정한다.

### API and Domain Design

1. `GET /api/v1/spaces/{spaceId}/meetings`는 `status`, `from`, `to`를 검증하고 active SpaceMember의 역할과 active MeetingParticipant ACL을 적용한다. `OWNER`/`ADMIN`은 목록 조회 override를 가지며 일반 `MEMBER`는 참여 회의만 본다.
2. `GET /api/v1/meetings/{meetingId}`는 `OWNER`/`ADMIN` 또는 active `MeetingParticipant`에게만 상세와 `myRole`을 반환한다. 삭제된 회의는 일반 조회에서 찾을 수 없는 것으로 처리한다.
3. `PATCH /api/v1/meetings/{meetingId}`는 `OWNER`/`ADMIN` 또는 active `HOST`만 실행한다. 제목은 blank를 거부하고, 일정 수정은 `SCHEDULED` 상태에서만 허용하며, 상태는 canonical 전이만 허용한다.
4. canonical 상태 전이는 `SCHEDULED -> IN_PROGRESS`, `SCHEDULED -> CANCELED`, `IN_PROGRESS -> ENDED`다. 동일 값은 idempotent하게 허용하고 역전이는 `400 INVALID_REQUEST`로 거부한다.
5. `DELETE /api/v1/meetings/{meetingId}`는 row lock과 transaction 안에서 상태·권한을 다시 확인하고 soft delete와 `MEETING_DELETED` audit를 원자적으로 저장한다.
6. Meeting에 `deletedAt`, `deletedBy`를 추가하고 기존 V1~V10은 수정하지 않는다. 새 index와 제약은 V11 forward migration으로 추가한다.
7. 목록 필터와 AI context 후보 조회는 `deleted_at is null`을 공통 active Meeting 조건으로 사용한다.

### Frontend Integration

1. target Space가 선택되면 `fetchMeetings`로 Backend 목록을 로드하고 legacy/mock 회의와 섞지 않는다.
2. 생성·수정·삭제 UI는 `createMeeting`, `updateMeeting`, `deleteMeeting`을 호출한 뒤 Backend 목록을 재조회한다. 성공처럼 보이는 local-only mutation을 제거한다.
3. 권한에 따라 생성, 수정, 삭제 control을 표시하되 Backend의 403 판단을 최종 기준으로 유지한다.
4. loading, empty, 400/403/404/409 오류, 중복 제출 방지와 삭제 확인을 제공한다.
5. demo/legacy Space는 기존 mock fallback을 유지하되 target DB 데이터와 명확히 구분한다.

### Implementation Order

1. 삭제 의미, 상태 전이, 권한, active Meeting 조건을 계약/결정 문서에 확정한다.
2. `deletedAt`/`deletedBy` 모델과 V11 migration을 추가하고 ERD 영향을 맞춘다.
3. store/domain에 ACL 목록, 상세, 수정, soft delete와 audit transaction을 구현한다.
4. Meeting list/detail/PATCH/DELETE controller와 DTO, 오류 매핑을 구현한다.
5. Backend unit/controller/JDBC integration test를 추가한다.
6. Frontend target Meeting API를 실제 프로젝트 회의 화면에 연결하고 local-only success 경로를 제거한다.
7. Frontend unit/build와 PostgreSQL real API CRUD smoke를 통과시킨다.
8. `tasks.md`, `implement.md`, `analyze.md`에 결과와 미실행 사유를 반영한다.

### Verification Plan

- Backend unit/controller: 생성자 HOST 등록, ACL 목록, 상세 403, 수정 권한, 상태 전이, delete 권한, 진행 중 delete 409, audit를 검증한다.
- PostgreSQL integration: create -> reload/list -> detail -> patch -> reload -> delete -> reload exclusion과 transaction rollback을 검증한다.
- AI safety regression: soft-deleted meeting이 Meeting AI와 Project AI context 후보에 포함되지 않는지 검증한다.
- Frontend: API client unit test, target/mock 경계 test, `npm run lint`, `npm run test`, `npm run build`를 실행한다.
- Real API smoke: signup -> Space 생성 -> Meeting 생성 -> 목록/상세 -> 수정 -> 삭제 -> 목록/상세 제외를 local PostgreSQL에서 확인한다.
- Repository: 전체 Backend test, Flyway V1~V11 migration test, `git diff --check`를 통과한다.

## M034 Meeting CRUD Frontend Target Completion

M034는 M033에서 연결한 회의 목록·생성·수정·삭제에 상세 조회, 초기 참여자 지정, MeetingParticipant ACL mutation, 캘린더 오류 상태를 보강한다. target Space의 성공 상태는 Backend 응답과 재조회 결과만 사용하고 demo/legacy Space의 local state는 별도 경계로 유지한다.

### Scope and Decisions

- Frontend 통합 담당 1명과 Codex 1개가 문서, Frontend, 검증을 순차 처리한다. Backend와 AI/RAG 파일은 수정하지 않는다.
- `GET /api/v1/meetings/{meetingId}`를 선택된 target 회의의 상세·`myRole`·참여자 기준으로 사용한다.
- 초기 참여자 지정은 `GET /api/v1/spaces/{spaceId}/members`의 stable `userId`를 `participantUserIds`로 변환한다. 사용자-facing 신규 참여는 기존 URL/코드 참가 신청 흐름을 유지한다.
- 생성 응답의 `joinCode`/`joinUrl`은 생성 권한자에게 현재 Frontend 메모리 범위에서만 표시하고 영속 저장하지 않는다.
- 참여자 role/access 변경은 `POST/PATCH /api/v1/meetings/{meetingId}/participants`를 사용한다. `REVOKED`를 권한 회수로 사용하고 마지막 active HOST 보호와 403/409는 Backend 판단을 최종 기준으로 표시한다.
- canonical 상태 전이만 UI 선택지로 제공한다. 제목·예정 일시 수정은 `SCHEDULED`에서만 허용한다.
- `GET /api/v1/calendar/events`는 아직 Backend runtime이 없으므로 이번 Frontend 범위에서는 이미 ACL-filtered된 Space별 meeting 목록을 캘린더 read model로 재사용한다. 별도 endpoint 연결은 Backend 구현 후 교체한다.
- FR-MREG-01의 description과 FR-CAL-04의 종료 일시는 현재 Meeting API/data model 계약에 없으므로 M034에서 임의 추가하지 않고 계약 변경 milestone로 남긴다.

### Implementation Order

1. target Space 멤버를 Backend에서 조회해 `userId`가 있는 Frontend 상태로 분리한다.
2. 선택된 target meeting 상세를 조회하고 participant state를 `meetingId` 기준으로 저장한다.
3. 생성 시 선택한 초기 참여자를 `participantUserIds`로 전달하고 성공한 경우에만 입력을 초기화하며 참가 코드/URL을 표시한다.
4. participant 추가·role 변경·회수를 Backend API에 연결하고 mutation 뒤 상세를 재조회한다.
5. 상태별 허용 전이, 권한별 control, 상세/ACL loading·400/403/404/409 오류를 화면에 반영한다.
6. 캘린더 생성에 참여자 선택, mutation loading/error, 성공 후 목록·캘린더 동시 갱신을 연결한다.
7. API unit test와 실제 Backend를 사용한 Playwright 상세/ACL/캘린더 흐름을 추가한다.
8. `tasks.md`, `implement.md`, `analyze.md`, 기능 비교 문서를 실제 결과에 맞게 갱신한다.

### Verification Plan

- Frontend unit: meeting detail/participant client의 route, auth header, request body와 오류 전파를 검증한다.
- Frontend E2E: target Space에서 초기 참여자 지정 생성, 상세/ACL 재조회, role 변경·회수, canonical 상태 전이, 삭제, 캘린더 생성·오류 표시를 검증한다.
- Regression: 기존 login, prejoin default-deny, mock/target 경계와 M033 CRUD E2E를 유지한다.
- Commands: `cd frontend && npm run test`, `npm run lint`, `npm run build`, 격리 Backend를 사용한 `npm run test:e2e`, `git diff --check`를 실행한다.

## Test Plan

- Frontend: `cd frontend && npm run build`
- Backend: `cd backend && ./gradlew test`
- AI: `cd ai && python -m compileall app`
- Manual: 워크스페이스 홈, 회의 대기, 라이브룸, Meeting AI, Report Agent 화면 이동 확인

## Rollout Plan

1. 문서/스펙 기준선 확정
2. API 계약 분리와 mock fallback 정리
3. 인증/멤버십 모델 추가
4. 회의/보고서 영속화 추가
5. Meeting AI 서버 컨텍스트 조립을 backend 권한 필터 뒤로 이동
6. Project Knowledge와 권한 기반 RAG 추가
