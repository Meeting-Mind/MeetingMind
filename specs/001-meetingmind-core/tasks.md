이 문서는 MeetingMind Core Prototype 구현을 milestone과 에이전트 친화적인 task 단위로 나누고 owner, agent, dependency, 파일 경계를 드러내기 위한 Markdown 문서이다.

# Tasks: MeetingMind Core Prototype

## Status Legend

- `[ ]`: Not started
- `[~]`: In progress
- `[x]`: Done

## Completion Rules

- 작업을 `[x]`로 변경하려면 관련 구현 또는 문서 변경이 완료되어야 한다.
- 작업 완료 시 관련 검증 항목을 실행하거나 미실행 사유를 `Verification` 또는 `implement.md`에 남긴다.
- 스펙, 계획, API 계약, 데이터 모델 변경이 동반되면 관련 문서를 함께 갱신한다.

## Task Granularity Rules

- 하나의 task는 한 명 또는 한 에이전트가 독립적으로 수행하고 검증할 수 있어야 한다.
- 하나의 task는 가능한 한 하나의 area와 제한된 파일 범위만 수정한다.
- 하나의 task는 명확한 완료 기준과 검증 방법을 가져야 한다.
- 여러 에이전트가 동시에 작업할 수 있도록 dependency를 명시한다.
- shared contract, migration, 공통 타입, 권한 규칙 변경은 별도 task로 분리한다.
- task가 너무 크면 planning, contract, implementation, verification task로 나눈다.

## Parallel Safety Rules

- 같은 `Files` 항목을 가진 작업은 동시에 진행하지 않는다.
- shared contract 파일은 owner를 하나로 지정하고, 관련 workstream은 해당 변경 이후 진행한다.
- dependency가 남아 있는 작업은 `[~]` 또는 `[x]`로 변경하지 않는다.
- 팀원과 에이전트 배정이 확정되면 `Owner`, `Agent`를 TBD에서 실제 이름으로 바꾼다.

## Milestones

| ID | Goal | Exit Criteria | Related Tasks |
| --- | --- | --- | --- |
| M001 | 문서/에이전트 기준선 확정 | Spec Kit, 헌법, 에이전트 지침, 템플릿, QA 체크리스트가 작성되어 있다. | T001-T006, T019-T023 |
| M002 | 현재 프로토타입 계약 기준선 문서화 | Frontend, Backend, AI의 현재 route/API/컨텍스트 제한이 문서화되어 있다. | T007-T009 |
| M003 | 구현 전 제품/권한 결정 확정 | 인증 방식, 회의 권한 등급, Target API route, 오디오 업로드 방식의 결정 기록이 있다. | T024-T028 |
| M004 | API 계약 세분화 | Space/Meeting/Report/AI/오류 응답 계약이 frontend/backend가 구현 가능한 수준으로 분리되어 있다. | T029-T034 |
| M005 | Backend 권한 기반 도메인/API 기반 구축 | 도메인 모델, 권한 검증 계층, API 분리, 오류 응답 처리, backend 검증이 준비되어 있다. | T035-T043 |
| M006 | Frontend 상태와 mock fallback 정리 | Project/Meeting 선택 상태, API client type, mock fallback 표시, frontend 검증이 준비되어 있다. | T044-T050 |
| M007 | Meeting AI 권한 필터 이후 컨텍스트 전환 | Meeting AI가 Backend 권한 필터 이후의 컨텍스트와 출처 메타데이터를 사용하도록 전환되어 있다. | T051-T057 |
| M008 | Data/RAG 기반 준비 | PostgreSQL/pgvector 스키마 초안과 retention/RAG 관계가 migration 단위로 준비되어 있다. | T058-T064 |
| M009 | 통합 검증과 작업 기록 | 세 영역 검증, 수동 흐름 확인, 구현 로그와 후속 작업 기록이 완료되어 있다. | T065-T069 |
| M010 | AI prototype 기능 착수 | 백엔드/프론트엔드 구현 없이 AI 서버에서 용어 설명, 요약/보고서 생성, 챗봇, 태스크 추출 API 작업 경계가 분리되어 있다. | T070-T077 |
| M011 | AI RAG prototype 기반 구축 | 실제 STT/DB/pgvector 구현 전에도 mock transcript를 RAG chunk로 변환하고, 회의별/프로젝트별 검색 scope와 source metadata가 분리되어 있다. | T078-T088 |
| M012 | 로그인/인증 기반 구축 | Google OAuth와 자체 회원가입/로그인, MeetingMind access/refresh token, Frontend auth 상태, 보호 route 경계가 준비되어 있다. | T089-T096 |
| M013 | 요구사항 정의서 Markdown 기준선 반영 | Google Sheets 요구사항 정의서가 로컬 Markdown으로 분할되고, 에이전트/스펙/후속 작업이 해당 기준을 참조한다. | T097-T106 |
| M014 | API 명세와 ERD 기준선 분리 | 기능군별 API 명세와 backend 전체 도메인 ERD 초안이 생기고, 후속 구현자가 이를 기준으로 작업할 수 있다. | T107-T110 |
| M017 | 메인 대시보드와 캘린더 프론트엔드 구현 | FR-DASH-01~07, FR-CAL-01~05 기준으로 프로젝트 대시보드, 프로젝트 관리 local flow, 캘린더 월/주/일 뷰, 일정→회의 이동이 mock/API 경계를 유지한 채 동작한다. | T121-T130 |
| M018 | 프로젝트 워크스페이스 프론트엔드 구현 | FR-MREG, FR-ACL, FR-KAN, FR-PBOT, FR-PERM, FR-OWN 기준으로 회의 관리/ACL, 칸반, Project AI, 멤버/오너 관리가 권한 경계를 드러낸 상태로 동작한다. | T131-T144 |
| M019 | 회의 워크스페이스 프론트엔드 구현 | FR-RPT, FR-MBOT, FR-TASK 기준으로 Meeting AI 단일 회의 scope, report candidate/편집/확정, task candidate 검토/칸반 등록이 source metadata를 유지한 채 동작한다. | T145-T157 |

## Foundation

- [x] T001 [docs] Spec Kit 계층 구조를 생성한다.
- [x] T002 [docs] 프로젝트 헌법을 `.specify/memory/constitution.md`에 작성한다.
- [x] T003 [docs] 공통 에이전트 지침을 `AGENTS.md`, `AGENT.md`, `CLAUDE.md`에 정리한다.
- [x] T004 [docs] spec/plan/tasks/clarify/analyze/implement 템플릿을 작성한다.
- [x] T005 [docs] 7개 개념 계층을 압축된 실제 파일 구조로 정리한다.
- [x] T006 [docs] 도구 중립 QA 체크리스트를 `.specify/skills`에 작성한다.
- [x] T019 [docs] specs 변경 동반 갱신 규칙과 누락 템플릿을 보완한다.
- [x] T020 [docs] Git 작업 절차, staging, commit, pull, branch, push 규칙을 보완한다.
- [x] T021 [docs] 팀/에이전트 병렬 작업 계획과 충돌 방지 규칙을 보완한다.
- [x] T022 [docs] milestone 기반 task 작성 규칙과 에이전트 친화 작업 단위 기준을 보완한다.
- [x] T023 [docs/contracts] API_SPEC 초안에서 공통 API 규칙, 상태, 오류, transcript/speaker 계약 후보를 반영한다.

## Current Prototype Baseline

- [x] T007 [frontend] 기존 화면 라우트와 mock fallback 범위를 문서화한다.
- [x] T008 [backend] `/api/workspace`, `/api/livekit/token` 현재 계약을 문서화한다.
- [x] T009 [ai] `/api/meeting-ai/ask` 현재 계약과 컨텍스트 제한을 문서화한다.

## Next Implementation Tasks

기존 T010-T018은 넓은 umbrella task였으므로 직접 배정하지 않는다. 실제 구현은 아래 T024-T106 세부 task 기준으로 진행한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T024 | M003 | [x] | docs/decision | 사용자(Auth 담당) | Codex | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/research.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Q-001 인증 방식 선택지를 Google OAuth 단독, 자체 JWT, 병행안으로 정리하고 장단점/영향 범위를 기록한다. | Q-001은 Google OAuth와 자체 회원가입/로그인, access/refresh token, `/api/v1/auth/*`, `sessionStorage`, 랜딩 외 보호 route로 결정되었다. |
| T025 | M003 | [x] | docs/decision | 사용자 | Codex | - | `requirements/permissions.md`, `requirements/glossary.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/data-model.md` | Q-002 회의 권한 등급과 회의 게스트 권한 매트릭스를 요구사항 기준선에 맞춰 정리한다. | MeetingRole은 `HOST`, `EDITOR`, `VIEWER`로 결정되었고, 회의 게스트는 특정 회의의 MeetingParticipant로만 접근한다. |
| T026 | M003 | [ ] | docs/decision | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/README.md`, `specs/001-meetingmind-core/contracts/common.md`, `specs/001-meetingmind-core/plan.md` | Q-006 Target API Base URL을 `/api/v1` 단일화 또는 prototype 경로 병행 중 하나로 결정할 수 있게 migration plan을 작성한다. | route migration 순서와 frontend 영향이 문서화되어 있다. |
| T027 | M003 | [ ] | docs/decision | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/live-stt-api.md`, `specs/001-meetingmind-core/plan.md` | Q-007 실제 오디오 업로드 방식을 multipart 직접 업로드와 presigned URL 후보로 비교한다. | 파일 크기, S3 연계, 보안 경계, prototype 적용 여부가 기록되어 있다. |
| T028 | M003 | [ ] | docs/analysis | TBD | TBD | T024, T025, T026, T027 | `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/tasks.md` | 결정 결과에 따라 analyze findings와 후속 task dependency를 갱신한다. | Open/Deferred 상태가 최신 결정과 일치하고 막힌 task가 없다. |
| T029 | M004 | [ ] | contracts | TBD | TBD | T026 | `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/common.md` | Space API target contract를 정의한다: Space 목록, Space 상세, Space 멤버 요약, 접근 오류. | endpoint, request/query, response, error code, 권한 규칙이 문서화되어 있다. |
| T030 | M004 | [ ] | contracts | TBD | TBD | T025, T026 | `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/common.md` | Meeting API target contract를 정의한다: 회의 목록, 상세/status, 참여자, 삭제 또는 보존 정책 후보. | Meeting status, participant 권한, 403/404/409 오류가 계약에 반영되어 있다. |
| T031 | M004 | [ ] | contracts | TBD | TBD | T030 | `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/live-stt-api.md` | Transcript와 speaker API contract를 구현 가능한 필드 수준으로 확정한다. | `speakers`, `segments`, `startMs/endMs`, speaker 수정 권한과 오류가 확정되어 있다. |
| T032 | M004 | [ ] | contracts | TBD | TBD | T030 | `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/contracts/knowledge-api.md` | Report, Action Item, Project Knowledge API target contract 초안을 작성한다. | report summary/decision/action item/source metadata와 Project Knowledge/Domain Term 관리 계약이 정의되어 있다. |
| T033 | M004 | [ ] | contracts/ai | TBD | TBD | T031, T032 | `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/common.md`, `specs/001-meetingmind-core/plan.md` | Meeting AI request/response 계약을 출처 메타데이터 포함 구조로 확장한다. | AI 응답에 source/citation 후보 구조와 근거 없음 처리 규칙이 있다. |
| T034 | M004 | [ ] | docs/contracts | TBD | TBD | T029, T030, T031, T032, T033 | `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/tasks.md` | API 계약 변경 영향도를 점검하고 frontend/backend/ai task dependency를 최신화한다. | analyze의 Contracts vs Data Model과 Permission Rules가 최신 상태다. |
| T035 | M005 | [x] | backend/discovery | 사용자(Auth 담당) | Codex | T024, T025, T029, T030 | `backend/**` | Backend 현재 패키지 구조, controller/service/dto/test 패턴을 조사한다. | Auth와 기존 prototype 패턴을 기준으로 in-memory repository/service/unit test 경계를 재사용하기로 implement.md에 기록되어 있다. |
| T036 | M005 | [x] | backend/domain | 사용자(Auth 담당) | Codex | T035 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | User, Space, SpaceMember 도메인 모델/DTO를 기존 backend 패턴에 맞춰 추가한다. | `User`, `Space`, `SpaceMember` domain record와 in-memory workspace store가 추가되고 compile/test 대상에 포함된다. |
| T037 | M005 | [x] | backend/domain | 사용자(Auth 담당) | Codex | T036 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | Meeting, MeetingParticipant, MeetingSpeaker 도메인 모델/DTO를 추가한다. | `Meeting`, `MeetingParticipant`, `MeetingSpeaker` domain record와 회의 생성 시 `HOST` participant 등록 검증이 추가되어 있다. |
| T038 | M005 | [x] | backend/domain | 사용자(Auth 담당) | Codex | T037 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | TranscriptSegment, MeetingReport, ProjectKnowledge, EmbeddingChunk 모델/DTO를 추가한다. | `TranscriptSegment`, `MeetingReport`, `ProjectKnowledge`, `EmbeddingChunk` domain record와 in-memory store 경계가 추가되어 `startMs/endMs`, source metadata, RAG scope, embedding status가 테스트로 검증된다. |
| T039 | M005 | [x] | backend/security | 사용자(Auth 담당) | Codex | T037 | `backend/**` | Space 접근 검증 service 또는 policy 계층을 추가한다. | `SpaceAccessPolicy`와 domain service context adapter가 SpaceMember 기준 접근 허용/거부와 실패 오류 코드를 검증한다. |
| T040 | M005 | [x] | backend/security | 사용자(Auth 담당) | Codex | T039 | `backend/**` | Meeting 접근 검증 service 또는 policy 계층을 추가한다. | `MeetingAccessPolicy`와 domain service context adapter가 MeetingParticipant 조회/수정/AI 컨텍스트 권한과 마지막 HOST 보호를 검증한다. |
| T041 | M005 | [x] | backend/api | 사용자(Auth 담당) | Codex | T029, T039 | `backend/**`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/meeting-api.md` | `/api/workspace` 통합 mock 응답을 Space/Meeting/Report read model로 분리할 backend plan 또는 adapter를 구현한다. | legacy `/api/workspace` 응답은 유지하고 target `/api/v1/spaces`, `/api/v1/spaces/{spaceId}/meetings`, `/api/v1/meetings/{meetingId}/livekit-token` 전환 지점과 in-memory read/write model이 생겼다. |
| T042 | M005 | [x] | backend/errors | 사용자(Auth 담당) | Codex | T039, T040 | `backend/**`, `specs/001-meetingmind-core/contracts/common.md` | 공통 오류 응답(`code`, `message`, `fieldErrors`, `traceId`) 처리 방식을 추가한다. | Auth/Authz/validation/LiveKit 설정 오류가 공통 `code`, `message`, `fieldErrors`, `traceId` body로 반환되도록 전역 handler가 적용되어 있다. |
| T043 | M005 | [x] | backend/verification | 사용자(Auth 담당) | Codex | T036, T037, T038, T039, T040, T041, T042 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend 테스트 또는 최소 검증을 실행하고 결과를 기록한다. | `cd backend && ./gradlew test` 통과 결과가 implement.md에 기록되어 있다. |
| T044 | M006 | [x] | frontend/discovery | 사용자(Frontend 담당) | Codex | T029, T030, T031, T032, T033 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend route, API client, mock fallback 위치를 조사한다. | `App.tsx`, `types.ts`, `mockData.ts`, `WorkspaceHomePage.tsx`, `ProjectOverviewPage.tsx`, `WorkspaceSidebar.tsx`, `TeamMembersPage.tsx`, `MeetingAiPage.tsx`, `ReportAgentPage.tsx` 수정 경계와 `/api/workspace` fallback 패턴이 implement.md에 기록되어 있다. |
| T045 | M006 | [x] | frontend/types | 사용자(Frontend 담당) | Codex | T044 | `frontend/src/types.ts`, `specs/001-meetingmind-core/contracts/*`, `specs/001-meetingmind-core/implement.md` | API contract에 맞춘 frontend TypeScript type을 정리한다. | Legacy `WorkspaceData`와 분리된 target API용 Space, Meeting, Transcript, Report, Task, Project AI response type이 추가되어 계약과 일치한다. |
| T046 | M006 | [x] | frontend/state | 사용자(Frontend 담당) | Codex | T045 | `frontend/src/App.tsx`, `frontend/src/types.ts`, `frontend/src/data/mockData.ts`, `frontend/src/components/WorkspaceSidebar.tsx`, `frontend/src/pages/WorkspaceHomePage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/pages/LiveMeetingPage.tsx`, `specs/001-meetingmind-core/implement.md` | Project/Meeting 선택 상태를 URL param 또는 명시 state 중 하나로 정리한다. | Project 선택 route가 `spaceId` query를 우선 사용하고 기존 project name fallback을 유지해 새로고침/직접 URL 접근 시 선택 Space가 예측 가능하다. |
| T047 | M006 | [x] | frontend/api | 사용자(Frontend 담당) | Codex | T045, T046 | `frontend/src/api/workspace.ts`, `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md` | Workspace 통합 mock 호출과 target API 호출의 전환 경계를 분리한다. | legacy `/api/workspace` snapshot client와 target `/api/v1/spaces`, `/api/v1/spaces/{spaceId}/meetings` client 함수가 분리되고, 기존 화면은 legacy mock fallback 경계를 통해 동작한다. |
| T048 | M006 | [ ] | frontend/ui | TBD | TBD | T047 | `frontend/**` | 개발자용 mock/API 상태 표시를 업무형 UI 톤으로 추가한다. | 사용자는 노출되지 않거나 최소화되고 개발자는 현재 데이터 소스를 확인할 수 있다. |
| T049 | M006 | [ ] | frontend/smoke | TBD | TBD | T046, T047, T048 | `frontend/**` | 워크스페이스 홈, Space 개요, 회의 대기, Meeting AI, Report Agent 이동 흐름을 수동 점검한다. | 주요 route 이동 결과와 발견 이슈가 implement.md에 기록되어 있다. |
| T050 | M006 | [ ] | frontend/verification | TBD | TBD | T045, T046, T047, T048 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend 빌드를 실행하고 결과를 기록한다. | `cd frontend && npm run build` 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T051 | M007 | [ ] | ai/discovery | TBD | TBD | T033, T040 | `ai/**`, `backend/**` | 현재 Meeting AI ask endpoint와 backend 호출 흐름을 조사한다. | AI 서버가 어떤 context shape를 받는지와 변경 파일 목록이 기록되어 있다. |
| T052 | M007 | [ ] | ai/contracts | TBD | TBD | T051 | `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/common.md`, `ai/**`, `backend/**` | Backend-to-AI request shape를 meetingId, transcript, decisions, actions, source metadata 기준으로 확정한다. | AI 서버 입력 계약이 권한 필터 이후 데이터만 받도록 정의되어 있다. |
| T053 | M007 | [ ] | backend/ai | TBD | TBD | T040, T052 | `backend/**` | Backend에서 Meeting AI 컨텍스트 조립 service를 추가한다. | MeetingParticipant 권한 확인 후 transcript/report/action context가 구성된다. |
| T054 | M007 | [ ] | ai/response | TBD | TBD | T052 | `ai/**` | AI 응답에 source/citation metadata 구조를 추가한다. | answer와 sources 또는 citations 후보가 응답되고 근거 없음 처리 규칙이 유지된다. |
| T055 | M007 | [ ] | ai/safety | TBD | TBD | T054 | `ai/**` | 제공 context 밖 질문에 대해 추정하지 않는 방어 테스트 또는 최소 자체 검사를 추가한다. | context 밖 질문이 확인 불가로 처리되는 검증 기록이 있다. |
| T056 | M007 | [ ] | backend/ai | TBD | TBD | T053, T054 | `backend/**`, `ai/**`, `specs/001-meetingmind-core/contracts/ai-api.md` | Backend와 AI 서버 간 응답 mapping을 연결한다. | frontend가 사용할 AI response shape가 `contracts/ai-api.md`와 일치한다. |
| T057 | M007 | [ ] | ai/verification | TBD | TBD | T052, T053, T054, T055, T056 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI compile 또는 최소 검증을 실행하고 결과를 기록한다. | `cd ai && python -m compileall app` 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T058 | M008 | [x] | data/discovery | 사용자(Data 담당) | Codex | T024, T025, T036, T037, T038 | `backend/**`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/implement.md` | Backend의 migration 도구와 DB 설정 방식을 확인한다. | 현재 backend에 DB/migration 의존성 및 datasource 설정이 없음을 확인했고, Flyway SQL migration과 `backend/src/main/resources/db/migration` 위치를 기록했다. |
| T059 | M008 | [x] | data/schema | 사용자(Data 담당) | Codex | T058 | `backend/build.gradle`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/V1__create_users_spaces.sql`, `specs/001-meetingmind-core/implement.md` | User, Space, SpaceMember schema 초안을 migration으로 작성한다. | `users`, `spaces`, `space_members` PK/FK/check 제약, active member unique index, active OWNER unique index, 조회용 index가 Flyway V1 migration에 반영되어 있다. |
| T060 | M008 | [x] | data/schema | 사용자(Data 담당) | Codex | T059 | `backend/src/main/resources/db/migration/V2__create_meetings_acl.sql`, `specs/001-meetingmind-core/implement.md` | Meeting, MeetingParticipant, MeetingSpeaker schema 초안을 migration으로 작성한다. | `meetings`, `meeting_participants`, `meeting_speakers` PK/FK/check 제약, meeting schedule/status index, active participant unique index, active role lookup index, speaker label unique index가 반영되어 있다. |
| T061 | M008 | [x] | data/schema | 사용자(Data 담당) | Codex | T060 | `backend/src/main/resources/db/migration/V3__create_transcripts_reports.sql`, `specs/001-meetingmind-core/implement.md` | TranscriptSegment와 MeetingReport schema 초안을 migration으로 작성한다. | `transcript_segments`의 `start_ms/end_ms` 시간 제약, `meeting_id/sequence` unique, `meeting_id/start_ms` index, `meeting_reports` version/current confirmed 제약, `report_decisions`/`report_action_items` 하위 테이블과 `source_ids jsonb` 저장 방식이 반영되어 있다. |
| T062 | M008 | [x] | data/schema | 사용자(Data 담당) | Codex | T061 | `backend/src/main/resources/db/migration/V4__create_knowledge_embeddings.sql`, `specs/001-meetingmind-core/implement.md` | ProjectKnowledge와 EmbeddingChunk schema 초안을 migration으로 작성한다. | `project_knowledge`의 Space FK/type/status/embeddingStatus 제약과 `(space_id,type,updated_at)` index, `embedding_chunks`의 `space_id/scope/source_type/source_id` index, meeting scope `meeting_id` required 제약, ProjectKnowledge FK, `chunk_source_segments` unique 관계가 반영되어 있다. |
| T063 | M008 | [ ] | data/retention | 팀원(STT/Audio 담당) | TBD | T061 | `backend/**`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/clarify.md` | retentionPolicy, failureReason, STT 원문 보존 정책 필드를 schema/document에 맞춘다. | STT 기본 보존기간 30일과 7/30일/영구 선택지를 기준으로 nullable/default 전략이 문서화되어 있다. |
| T064 | M008 | [ ] | data/verification | TBD | TBD | T059, T060, T061, T062, T063 | `backend/**`, `specs/001-meetingmind-core/implement.md` | migration 적용 또는 schema 검증 명령을 실행하고 결과를 기록한다. | migration 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T065 | M009 | [ ] | integration/backend | TBD | TBD | T043, T064 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend 전체 검증을 실행한다. | `cd backend && ./gradlew test` 결과가 implement.md에 기록되어 있다. |
| T066 | M009 | [ ] | integration/frontend | TBD | TBD | T050 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend 전체 빌드를 실행한다. | `cd frontend && npm run build` 결과가 implement.md에 기록되어 있다. |
| T067 | M009 | [ ] | integration/ai | TBD | TBD | T057 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI 전체 compile 검증을 실행한다. | `cd ai && python -m compileall app` 결과가 implement.md에 기록되어 있다. |
| T068 | M009 | [ ] | integration/manual | TBD | TBD | T065, T066, T067 | `frontend/**`, `backend/**`, `ai/**`, `specs/001-meetingmind-core/implement.md` | 워크스페이스 홈부터 Meeting AI/Report Agent까지 핵심 흐름을 수동 확인한다. | 통합 수동 검증 결과와 남은 이슈가 implement.md에 기록되어 있다. |
| T069 | M009 | [ ] | docs/closeout | TBD | TBD | T065, T066, T067, T068 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | 완료된 task 상태, 검증 결과, 남은 작업, analysis 상태를 정리한다. | tasks/implement/analyze가 실제 구현 상태와 일치한다. |
| T070 | M010 | [x] | ai/discovery | 사용자 | Codex | - | `ai/**`, `specs/001-meetingmind-core/implement.md` | 현재 AI 기능 코드 경계와 백엔드/프론트엔드 비담당 범위를 확인하고 기록한다. | AI 서버 진입점과 프론트 AI 호출 지점은 읽기 전용으로 확인하고, backend/frontend dependency가 implement.md에 기록되어 있다. |
| T071 | M010 | [x] | contracts/ai | 사용자 | Codex | T070 | `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/clarify.md` | 용어 설명, 요약/보고서 생성, 회의별/프로젝트별 챗봇, 태스크 추출 AI API prototype 계약을 정의한다. | 각 AI 기능의 request/response, source metadata, 권한 필터 전제, prototype 제한이 문서화되어 있다. |
| T072 | M010 | [x] | ai/term | 사용자 | Codex | T071 | `ai/**` | 회의 중 transcript 용어 설명 prototype API를 구현한다. | Domain Dictionary 우선 설명과 AI fallback 후보 흐름이 AI 서버 기반으로 동작하고, Frontend 연결은 TBD로 남는다. |
| T073 | M010 | [x] | ai/report | 사용자 | Codex | T071 | `ai/**` | 회의 transcript 기반 요약/보고서 생성 prototype API를 구현한다. | summary, decisions, actionItems, source metadata 후보가 생성되고 화면 연결은 Frontend 담당 TBD로 남는다. |
| T074 | M010 | [x] | ai/chat | 사용자 | Codex | T071 | `ai/**` | 회의별 챗봇과 프로젝트별 챗봇의 컨텍스트 범위를 AI 서버에서 분리한다. | Meeting AI와 Project AI 요청 타입이 분리되고, Project AI는 prototype context임이 명확하다. |
| T075 | M010 | [x] | ai/tasks | 사용자 | Codex | T071 | `ai/**` | 회의 종료 시 태스크 후보 추출 prototype API를 구현한다. | assignee, task title, source, confirmation state 후보가 생성되고 저장과 화면 연결은 backend/frontend TBD로 남는다. |
| T076 | M010 | [x] | ai/safety | 사용자 | Codex | T072, T073, T074, T075 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI 컨텍스트 밖 질문과 민감 데이터 혼입 방지를 최소 검증한다. | 컨텍스트 밖 질문은 확인 불가로 처리되고 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T077 | M010 | [x] | ai/verification | 사용자 | Codex | T076 | `ai/**`, `specs/001-meetingmind-core/implement.md` | 우리 AI workstream 검증을 실행한다. | `cd ai && python -m compileall app` 또는 `python3 -m compileall app` 결과가 implement.md에 기록되어 있다. |
| T078 | M011 | [x] | contracts/rag | 사용자 | Codex | T071 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | STT 기반 RAG chunk와 embeddingText 형식, source metadata, scope 규칙을 정의한다. | TranscriptSegment 원천 데이터와 EmbeddingChunk 임베딩 데이터의 차이, 회의별/프로젝트별 검색 범위, backend 권한 필터 전제가 문서화되어 있다. |
| T079 | M011 | [x] | ai/rag-types | 사용자 | Codex | T078 | `ai/app/rag.py`, `ai/app/main.py` | AI 서버 내부 RAG 타입과 retriever 경계를 추가한다. | `RagChunk`, `RagSource`, `RagSearchRequest`, `RagSearchResult`, `RagRetriever` 또는 동등한 경계가 생기고 기존 endpoint와 분리된다. |
| T080 | M011 | [x] | ai/rag-builder | 사용자 | Codex | T079 | `ai/app/rag.py` | mock transcript, decisions, actions, project knowledge를 RAG chunk로 변환하는 builder를 구현한다. | 짧은 STT 발화를 여러 segment window로 묶고 `sourceSegmentIds`, speaker, time, sourceType metadata가 유지된다. |
| T081 | M011 | [x] | ai/rag-search | 사용자 | Codex | T080 | `ai/app/rag.py` | pgvector 전환 전 사용할 in-memory retriever를 구현한다. | meeting scope는 단일 meeting chunk만, project scope는 projectKnowledge와 허용된 meeting summary/chunk만 검색하도록 필터가 분리된다. |
| T082 | M011 | [x] | ai/term-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py` | 회의 중 용어 설명 endpoint를 RAG retriever 기반으로 전환한다. | glossary 우선 검색, 선택 발화 주변 transcript window, sources가 retriever 결과 기준으로 응답된다. |
| T083 | M011 | [x] | ai/meeting-chat-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py` | 회의별 챗봇을 RAG scope `meeting`으로 구현한다. | 단일 meetingId의 transcript/decision/action/report chunk만 검색하고 Project 전체 context는 제외된다. |
| T084 | M011 | [x] | ai/project-chat-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py` | 프로젝트별 챗봇을 RAG scope `project`로 구현한다. | ProjectKnowledge와 prototype에서 허용된 meeting chunk만 검색하고, 응답에 공식 지식과 회의 기록 출처가 구분된다. |
| T085 | M011 | [x] | ai/report-rag | 사용자 | Codex | T080 | `ai/app/main.py`, `ai/app/rag.py` | 회의 요약/보고서 생성 prototype API를 RAG chunk/source metadata 구조에 맞춰 구현한다. | summary, decisions, actionItems, markdown draft가 sourceIds와 함께 생성되고 화면 연결은 Frontend 담당 TBD로 남는다. |
| T086 | M011 | [x] | ai/task-candidates | 사용자 | Codex | T085 | `ai/app/main.py` | 회의 종료 태스크 후보 추출 prototype API를 구현한다. | assignee, title, dueDate, sourceIds, confirmationState=`candidate`가 반환되고 저장과 화면 연결은 backend/frontend TBD로 남는다. |
| T087 | M011 | [x] | ai/rag-safety | 사용자 | Codex | T082, T083, T084, T085, T086 | `ai/**`, `specs/001-meetingmind-core/implement.md` | RAG scope와 컨텍스트 밖 질문 방어를 검증한다. | meeting/project scope 혼입 방지, 근거 없는 질문 LLM 미호출, sourceId 필터링, candidate 정규화를 unittest로 검증했다. |
| T088 | M011 | [x] | ai/rag-verification | 사용자 | Codex | T087 | `ai/**`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/tasks.md` | RAG workstream 검증과 작업 상태를 정리한다. | `cd ai && python3 -m compileall app tests`, `cd ai && ./.venv/bin/python -m unittest discover -s tests` 결과를 implement.md에 기록했다. |
| T089 | M012 | [x] | auth/discovery | 사용자(Auth 담당) | Codex | T024 | `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, `backend/src/main/java/com/meetingmind/demo/**`, `backend/build.gradle`, `specs/001-meetingmind-core/implement.md` | 현재 Frontend Google 로그인 모달, App route 상태, Backend controller/config/dependency 경계를 조사한다. | 현재 인증은 Frontend 모달 표시용이고 Backend auth/security 계층은 없다는 점이 implement.md에 기록되어 있다. |
| T090 | M012 | [x] | auth/contracts | 사용자(Auth 담당) | Codex | T024 | `specs/001-meetingmind-core/contracts/auth-api.md`, `specs/001-meetingmind-core/contracts/common.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/implement.md` | Auth API target contract를 정의한다: Google credential 교환, 자체 회원가입/로그인, refresh, 현재 사용자 조회, 로그아웃, 인증 오류. | endpoint, request/response, access/refresh token 전달, User/AuthIdentity/AuthSession 필드, 401/409 오류가 문서화되어 있다. |
| T091 | M012 | [x] | auth/backend | 사용자(Auth 담당) | Codex | T090 | `backend/src/main/java/com/meetingmind/demo/auth/**`, `backend/src/main/java/com/meetingmind/demo/config/**`, `backend/src/main/resources/application.yml`, `backend/build.gradle` | Backend에서 Google ID token 검증, 자체 회원가입/로그인, access/refresh token 발급 service/controller/dto를 추가한다. | Google credential 또는 자체 계정을 Backend가 검증하고 access token, refresh token, user profile을 반환한다. 현재 prototype 저장소는 in-memory이며 영속 DB 전환은 후속 Data/Backend 작업이다. |
| T092 | M012 | [x] | auth/frontend | 사용자(Auth 담당) | Codex | T090, T091 | `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, future `frontend/src/auth/**` | Frontend 로그인/회원가입 성공 처리를 Backend auth exchange로 전환하고 access/refresh token 저장/전달 경계를 만든다. | Google credential decode는 표시용으로만 남고, 앱 로그인 상태는 Backend 응답 기준으로 관리되며 token pair는 `sessionStorage`에 저장된다. |
| T093 | M012 | [x] | auth/frontend-guard | 사용자(Auth 담당) | Codex | T092 | `frontend/src/App.tsx`, future `frontend/src/auth/**`, route 대상 page files only if needed | `/spaces`, 회의 입장, 팀원 관리 등 보호 route 또는 action guard를 최소 범위로 적용한다. | 비로그인 사용자는 로그인 모달로 유도되고, 기존 mock fallback route는 깨지지 않는다. |
| T094 | M012 | [x] | auth/livekit | 사용자(Auth 담당) | Codex | T091, T040 | `backend/src/main/java/com/meetingmind/demo/controller/MeetingLiveKitController.java`, `backend/src/main/java/com/meetingmind/demo/service/MeetingLiveKitTokenService.java`, `backend/src/main/java/com/meetingmind/demo/service/LiveKitTokenService.java`, auth package | LiveKit token 발급을 인증된 사용자와 회의 접근 권한 확인 뒤 허용하도록 전환한다. | Target `/api/v1/meetings/{meetingId}/livekit-token`는 인증 사용자 id와 Meeting access policy를 확인한 뒤 token을 발급하며 T094 단위 테스트를 통과했다. |
| T095 | M012 | [x] | auth/verification | 사용자(Auth 담당) | Codex | T091, T092, T093 | `frontend/**`, `backend/**`, `specs/001-meetingmind-core/implement.md` | Auth workstream 검증을 실행한다. | `cd frontend && npm run build`, `cd backend && ./gradlew test`, AI regression checks, Auth API smoke 결과와 브라우저 자동화 미실행 사유가 implement.md에 기록되어 있다. |
| T096 | M012 | [x] | auth/closeout | 사용자(Auth 담당) | Codex | T095 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | Auth 작업 상태, 충돌 여부, 검증 결과, 후속 권한 작업을 정리한다. | T094는 T040 회의 접근 검증 계층 구현 전까지 남은 작업으로 유지하고, Auth 관련 tasks/implement/analyze가 실제 구현 상태와 일치한다. |

## Requirements Baseline Adoption

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T097 | M013 | [x] | requirements/docs | 사용자 | Codex | - | `requirements/INDEX.md`, `requirements/*.md` | Google Sheets 요구사항 정의서를 로컬 Markdown 기준선으로 분할한다. | overview, glossary, permissions, status-values, policies, performance, functional/non-functional 요약과 전체 우선순위 상세 문서가 생성되어 있다. |
| T098 | M013 | [x] | agent/process | 사용자 | Codex | T097 | `AGENTS.md`, `.specify/memory/constitution.md`, `.specify/templates/spec-template.md` | 요구사항 정의서 읽기 전략을 에이전트 지침, constitution, 새 spec 템플릿에 반영한다. | `requirements/INDEX.md` 기반 라우팅과 관련 문서만 읽는 규칙이 명시되어 있다. |
| T099 | M013 | [x] | docs/decision | 사용자 | Codex | T097 | `requirements/glossary.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/clarify.md` | 용어집, 권한 매트릭스, 상태값을 core spec 결정사항에 반영한다. | Q-002, Q-003, Q-004가 Decided 상태로 갱신되어 있다. |
| T100 | M013 | [x] | specs/model | 사용자 | Codex | T099 | `specs/001-meetingmind-core/spec.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/data-model.md` | core spec, plan, data-model을 요구사항 기준선과 충돌하지 않도록 갱신한다. | MeetingRole, Meeting status, 회의 게스트, STT 기본 보존기간이 요구사항 기준과 일치한다. |
| T101 | M013 | [x] | contracts | 사용자 | Codex | T100 | `specs/001-meetingmind-core/contracts/*` | API contract의 role/status/permission 표현을 요구사항 기준선과 맞춘다. | `participant` role, 구형 Meeting status, AuthSession 명칭이 계약에서 정리되어 있다. |
| T102 | M013 | [x] | backend/impact | 사용자(Auth 담당) | Codex | T101 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend auth/LiveKit/meeting 권한 구현이 요구사항 권한 매트릭스와 충돌하는지 점검한다. | LiveKit legacy endpoint가 아직 request identity를 신뢰하고 회의 권한을 보지 않는 gap, DB/domain 미구현 gap, T039/T040 policy 선행 구현 결과가 implement.md에 기록되어 있다. |
| T103 | M013 | [ ] | frontend/impact | TBD | TBD | T101 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend route guard, 회의 입장, Project AI/Meeting AI UI가 요구사항 권한 범위와 충돌하는지 점검한다. | 화면별 권한 노출/차단 gap이 기록되어 있다. |
| T104 | M013 | [x] | ai/impact | 사용자 | Codex | T101 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI/RAG prototype의 Meeting AI, Project AI, token 전략이 요구사항 기준과 충돌하는지 점검한다. | scope/source/unsupported는 구현과 테스트 기준으로 정합하고, token budget/observability는 후속 gap으로 기록했다. |
| T105 | M013 | [ ] | data/impact | TBD | TBD | T100 | `specs/001-meetingmind-core/data-model.md`, future migration files | DB enum, retention, MeetingGuest/MeetingParticipant 모델을 migration 기준으로 구체화한다. | migration 전 필요한 enum/field 변경 목록이 확정되어 있다. |
| T106 | M013 | [ ] | docs/closeout | TBD | TBD | T101, T102, T103, T104, T105 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/implement.md` | 요구사항 반영 후속 영향도를 닫고 다음 구현 milestone으로 넘긴다. | contracts/analyze/implement/tasks가 요구사항 기준선과 실제 구현 상태를 함께 반영한다. |

## API and ERD Baseline

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T107 | M014 | [x] | contracts | 사용자 | Codex | T097, T101 | `specs/001-meetingmind-core/contracts/*` | 단일 API 초안을 기능군별 명세로 분리한다. | common/auth/space/meeting/kanban/ai/live-stt API 초안과 index가 작성되어 있다. |
| T108 | M014 | [x] | data/erd | 사용자 | Codex | T100 | `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/data-model.md` | backend 전체 도메인 ERD 초안을 작성하고 기존 data model과 연결한다. | Auth, Space, Meeting, STT, Report, Kanban, AI/RAG 관계가 Mermaid ERD로 정리되어 있다. |
| T109 | M014 | [x] | process | 사용자 | Codex | T107, T108 | `AGENTS.md`, `specs/001-meetingmind-core/implement.md` | 후속 구현자가 API/ERD 변경 시 문서와 로그를 갱신하도록 지침을 보강한다. | contracts/erd/data-model 변경 시 implement.md 로그를 남기는 규칙이 명시되어 있다. |
| T110 | M014 | [ ] | docs/verification | TBD | TBD | T107, T108, T109 | `specs/001-meetingmind-core/contracts/*`, `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/analyze.md` | 분리된 API 명세와 ERD의 누락/충돌을 리뷰한다. | 각 기능 owner가 endpoint, 권한, 데이터 관계를 검토하고 발견 사항을 analyze.md에 남긴다. |

### M015: API Contract Template Standardization

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T111 | M015 | [x] | docs/template | 사용자 | Codex | T107 | `.specify/templates/api-contract-template.md` | MeetingMind API 명세 템플릿을 Status, Data Scope, Validation, Audit, Requirement Trace 포함 구조로 보강한다. | endpoint별 표준 섹션이 템플릿에 포함되어 있다. |
| T112 | M015 | [x] | docs/contracts | 사용자 | Codex | T111 | `specs/001-meetingmind-core/contracts/README.md`, `specs/001-meetingmind-core/contracts/common.md` | contracts README에 endpoint 작성 규칙과 리뷰 체크리스트를 추가한다. | 후속 구현자가 분리 API 문서에 동일 템플릿을 적용할 기준이 있다. |
| T113 | M015 | [x] | docs/contracts | 사용자 | Codex | T112 | `specs/001-meetingmind-core/contracts/auth-api.md`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/contracts/knowledge-api.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/live-stt-api.md` | 분리 API 문서에 표준 섹션을 적용한다. | 각 endpoint에 Status/Auth/Data Scope/Validation/Errors/Audit/Trace/Notes가 있다. |
| T114 | M015 | [x] | docs/legacy | 사용자 | Codex | T113 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/tasks.md` | 기존 통합 API 문서를 legacy snapshot으로 명확히 하고 plan/tasks의 단일 문서 참조를 분리 문서 기준으로 갱신한다. | 신규 구현 기준이 `contracts/README.md`와 분리 문서로 고정되어 있다. |
| T115 | M015 | [x] | docs/review | 사용자 | Codex | T113, T114 | `specs/001-meetingmind-core/contracts/*`, `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/analyze.md` | 템플릿 적용 이후 요구사항 trace, ERD 관계, 누락 endpoint를 리뷰하고 보완한다. | 요구사항 trace 오기, Project Knowledge/용어사전 관리 API 누락, ERD 제약 부족을 보완했다. |
| T116 | M015 | [x] | docs/decision | 사용자 | Codex | T115 | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/*`, `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/analyze.md` | Invitation 분리, current confirmed report, ProjectKnowledge embedding 재생성 방식을 결정하고 문서에 반영한다. | `SPACE_INVITATION`/`MEETING_INVITATION` 분리, current confirmed report 1개, 비동기 embedding 재생성 기준이 문서화되어 있다. |
| T117 | M015 | [ ] | docs/owner-review | TBD | TBD | T116 | `specs/001-meetingmind-core/contracts/*`, `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/analyze.md` | 기능 owner가 결정 반영된 API/ERD를 최종 확인한다. | Backend/Frontend/AI/Data owner가 endpoint, 권한, 데이터 관계를 확인하고 남은 구현 결정을 analyze.md에 닫는다. |

### M016: Test and CI Baseline

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T118 | M016 | [x] | backend/test | 사용자(Auth 담당) | Codex | D-014 | `backend/src/test/java/com/meetingmind/demo/auth/**` | 비밀번호 정책 단위 테스트와 signup 거부 테스트를 추가한다. | `POL-PW-01` 통과/거부 케이스와 signup validation이 backend test로 검증된다. |
| T119 | M016 | [x] | ci | 사용자 | Codex | T118 | `.github/workflows/ci.yml`, `.gitignore` | Backend, Frontend, AI 검증을 GitHub Actions CI로 작성한다. | PR/push 시 `./gradlew test`, `npm run build`, `python -m compileall app tests`, `python -m unittest discover -s tests`가 실행된다. |
| T120 | M016 | [x] | docs/test | 사용자(Auth 담당) | Codex | D-015 | `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | T039/T040/T094 구현 전에 권한/LiveKit 단위 테스트 성공/실패 matrix를 문서화한다. | Space access, Meeting access, HOST 보호, SpaceMember 제거, LiveKit token 발급의 성공/실패 케이스가 요구사항 ID와 연결되어 있다. |

### M017: Main Dashboard and Calendar Frontend

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T121 | M017 | [x] | frontend/design | 사용자(Frontend 담당) | Codex | T047 | `requirements/functional-requirements-detail.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | FR-DASH-01~07, FR-CAL-01~05 상세 요구와 현재 frontend/backend gap을 기준으로 dashboard/calendar 구현 계획을 작성한다. | `plan.md`에 Dashboard and Calendar Frontend Plan이 있고, M017 task가 FR-DASH/FR-CAL 구현 단위로 분해되어 있다. |
| T122 | M017 | [x] | frontend/types-api | 사용자(Frontend 담당) | Codex | T121 | `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/implement.md` | dashboard summary, calendar event, Space update/delete 후보 type과 API client 경계를 target contract에 맞춰 추가한다. | 기존 `WorkspaceData` mock fallback은 유지하면서 `DashboardSummaryResponse`, `CalendarEvent`, `UpdateSpaceRequest`, `DeleteSpaceResponse` 등 target type/client가 분리되어 있다. |
| T123 | M017 | [x] | frontend/state | 사용자(Frontend 담당) | Codex | T122 | `frontend/src/App.tsx`, `frontend/src/data/mockData.ts`, `frontend/src/types.ts`, `specs/001-meetingmind-core/implement.md` | 프로젝트 수정/삭제와 일정 생성에 필요한 local state update 함수를 추가한다. | 프로젝트 생성/수정/삭제와 회의 일정 생성이 mock fallback 상태에서 목록, 개요, 캘린더 데이터에 일관되게 반영된다. |
| T124 | M017 | [x] | frontend/dashboard | 사용자(Frontend 담당) | Codex | T123, T048 | `frontend/src/pages/WorkspaceHomePage.tsx`, `frontend/src/components/WorkspaceSidebar.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | `/spaces`를 프로젝트 대시보드 홈으로 정리한다. | 참여 프로젝트 목록, 검색/필터, 오늘 회의, 최근 활동, Action Item 요약, 낮은 비중의 mock/API 데이터 소스 표시가 업무형 UI 톤으로 표시된다. |
| T125 | M017 | [x] | frontend/project-management | 사용자(Frontend 담당) | Codex | T123 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/components/WorkspaceSidebar.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 프로젝트 수정/삭제 UI와 확인 절차를 추가한다. | owner/admin 권한 전제를 드러내는 수정/삭제 affordance가 있고, 삭제 확인 후 프로젝트가 목록/검색/개요 대상에서 제외된다. |
| T126 | M017 | [x] | frontend/calendar | 사용자(Frontend 담당) | Codex | T123 | `frontend/src/pages/WorkspaceHomePage.tsx`, future `frontend/src/pages/CalendarPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 캘린더 월/주/일 뷰와 회의 일정 렌더링을 추가한다. | 접근 가능한 mock 회의 일정이 월/주/일 전환에 맞게 표시되고 일정이 없을 때 빈 상태가 보인다. |
| T127 | M017 | [x] | frontend/calendar-routing | 사용자(Frontend 담당) | Codex | T126 | `frontend/src/pages/WorkspaceHomePage.tsx`, future `frontend/src/pages/CalendarPage.tsx`, `frontend/src/pages/LiveMeetingPage.tsx`, `frontend/src/pages/ReportAgentPage.tsx`, `specs/001-meetingmind-core/implement.md` | 캘린더 일정 클릭 시 회의 화면으로 이동하는 route를 연결한다. | 예정 회의는 회의 대기 화면, 완료/보고서 생성 회의는 보고서 화면으로 이동하고 `spaceId`, `project`, `meeting`, `round` query가 보존된다. |
| T128 | M017 | [x] | frontend/calendar-create | 사용자(Frontend 담당) | Codex | T126 | `frontend/src/pages/WorkspaceHomePage.tsx`, future `frontend/src/pages/CalendarPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 캘린더에서 회의 일정을 생성하는 local flow를 추가한다. | 제목, 프로젝트, 시작 일시를 입력하면 캘린더와 프로젝트 회의 목록에 같은 회의가 나타나며 잘못된 입력은 client에서 막는다. |
| T129 | M017 | [x] | frontend/smoke | 사용자(Frontend 담당) | Codex | T124, T125, T127, T128 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | `/spaces`, `/project-overview`, 캘린더, 회의 대기, Report Agent 이동 흐름을 수동 점검한다. | 주요 route 이동 결과와 발견한 gap 또는 미실행 사유가 `implement.md`에 기록되어 있다. |
| T130 | M017 | [x] | frontend/verification | 사용자(Frontend 담당) | Codex | T129 | `frontend/**`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Frontend build와 diff 검증을 실행하고 M017 완료 상태를 정리한다. | `cd frontend && npm run build`, `git diff --check` 결과가 기록되고 완료된 task만 `[x]`로 표시되어 있다. |

### M018: Project Workspace Frontend

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T131 | M018 | [x] | frontend/design | 사용자(Frontend 담당) | Codex | T047, T121 | `requirements/functional-requirements-detail.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | FR-MREG, FR-ACL, FR-KAN, FR-PBOT, FR-PERM, FR-OWN 상세 요구와 계약 gap을 기준으로 project workspace 구현 계획을 작성한다. | `plan.md`에 Project Workspace Frontend Plan이 있고, M018 task가 회의 관리/ACL/칸반/Project AI/멤버/오너 관리 단위로 분해되어 있다. |
| T132 | M018 | [x] | frontend/types-api | 사용자(Frontend 담당) | Codex | T131 | `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/implement.md` | Meeting detail/participant/invitation/update/delete, TaskCard CRUD, Space member/invitation/owner transfer, Project AI source type과 API client 경계를 target contract에 맞춰 추가한다. | legacy `WorkspaceData`와 분리된 target type/client가 있고 아직 backend 미구현 endpoint는 호출 경계만 분리되어 있다. |
| T133 | M018 | [ ] | frontend/state | 사용자(Frontend 담당) | Codex | T132, T123 | `frontend/src/App.tsx`, `frontend/src/data/mockData.ts`, `frontend/src/types.ts`, `specs/001-meetingmind-core/implement.md` | SpaceMember, MeetingParticipant, TaskCard, Project AI source local state를 분리한다. | Space 멤버십과 회의 ACL이 별도 상태로 표현되고, mock 생성/수정/삭제가 프로젝트 개요, 멤버 화면, 칸반, Project AI source에 일관되게 반영된다. |
| T134 | M018 | [ ] | frontend/meetings | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 프로젝트 회의 목록을 생성/삭제/상태 표시/상세 진입이 가능한 관리형 목록으로 확장한다. | 회의 생성은 제목/일시/참여자 후보를 받고, 삭제/취소는 권한 제한 copy와 확인 절차를 거치며, 목록은 접근 가능한 회의만 표시한다. |
| T135 | M018 | [ ] | frontend/acl | 사용자(Frontend 담당) | Codex | T133, T134 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 회의별 참여자/ACL 관리 패널을 추가한다. | `VIEWER`/`EDITOR`/`HOST`, `ACTIVE`/`REVOKED`, owner/admin override, default-deny, 마지막 active HOST 보호, 삭제 권한 제한이 UI 상태와 copy에 반영된다. |
| T136 | M018 | [ ] | frontend/kanban | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/ProjectOverviewPage.tsx`, future `frontend/src/components/KanbanBoard.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 프로젝트 칸반 보드를 추가한다. | `TODO`, `IN_PROGRESS`, `DONE` 컬럼, 카드 생성/편집/담당자/마감일/상태 이동/삭제/검색 또는 필터가 mock/local state로 동작한다. |
| T137 | M018 | [ ] | frontend/project-ai | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/api/workspace.ts`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | Project AI 패널의 source 표시와 unsupported 상태를 요구사항 기준으로 정리한다. | 공식 Project Knowledge와 회의 기록 출처가 구분되고, 접근 가능한 meeting source만 prompt/context 후보로 사용하며 근거 없음 응답이 추정처럼 보이지 않는다. |
| T138 | M018 | [ ] | frontend/members | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | Space 멤버 목록, 초대, 역할 변경, 제거 흐름을 정리한다. | Space invitation과 Meeting invitation copy가 분리되고, owner/admin/member 역할 변경과 제거가 local state와 화면 액션에 반영된다. |
| T139 | M018 | [ ] | frontend/owner-transfer | 사용자(Frontend 담당) | Codex | T138 | `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 오너 권한 이양 확인 절차와 기존 오너 강등 local flow를 추가한다. | 활성 SpaceMember만 대상이 되고, 확인 절차 없이 이양되지 않으며, 이양 후 새 owner/기존 owner 역할이 화면에 일관되게 표시된다. |
| T140 | M018 | [ ] | frontend/negative-permission | 사용자(Frontend 담당) | Codex | T135, T138, T139 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | default-deny, 회수 즉시 접근 차단, owner/admin override, 마지막 HOST 보호, owner transfer 확인 누락 등 negative case를 수동 점검한다. | 권한 제한/disabled/빈 상태/오류 안내가 요구사항과 맞는지 결과가 `implement.md`에 기록되어 있다. |
| T141 | M018 | [ ] | backend-gap | 사용자(Frontend 담당) | Codex | T132, T140 | `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | Project workspace frontend 구현 중 확인한 backend/API gap을 정리한다. | meeting participant API, invitation, kanban, owner transfer, audit log, Project AI backend 권한 필터의 미구현 범위가 후속 task 후보로 기록되어 있다. |
| T142 | M018 | [ ] | frontend/smoke | 사용자(Frontend 담당) | Codex | T134, T135, T136, T137, T138, T139 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | 프로젝트 개요, 회의 관리, ACL, 칸반, Project AI, 팀 멤버/오너 이양 route 흐름을 수동 점검한다. | 주요 route 이동 결과와 발견 이슈 또는 미실행 사유가 `implement.md`에 기록되어 있다. |
| T143 | M018 | [ ] | frontend/verification | 사용자(Frontend 담당) | Codex | T142 | `frontend/**`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Frontend build와 diff 검증을 실행한다. | `cd frontend && npm run build`, `git diff --check` 결과가 기록되고 완료된 task만 `[x]`로 표시되어 있다. |
| T144 | M018 | [ ] | integration/handoff | 사용자(Frontend 담당) | Codex | T141, T143 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | M018 완료/미완료 범위와 다음 backend/frontend 연계 작업을 정리한다. | M018 남은 gap과 다음 owner가 잡아야 할 backend/API 작업이 명확히 남아 있다. |

### M019: Meeting Workspace Frontend

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T145 | M019 | [x] | frontend/design | 사용자(Frontend 담당) | Codex | T047, T131 | `requirements/functional-requirements-detail.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | FR-RPT, FR-MBOT, FR-TASK 상세 요구와 현재 Report Agent/Meeting AI gap을 기준으로 meeting workspace 구현 계획을 작성한다. | `plan.md`에 Meeting Workspace Frontend Plan이 있고, M019 task가 Meeting AI/report/task candidate 단위로 분해되어 있다. |
| T146 | M019 | [x] | frontend/types-api | 사용자(Frontend 담당) | Codex | T145 | `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/implement.md` | Meeting AI chat, report candidate/list/confirm/update/download, task candidate extract/confirm type과 API client 경계를 추가한다. | legacy `WorkspaceData`와 분리된 source-aware AI/report/task candidate type과 client 함수가 추가되어 있다. |
| T147 | M019 | [ ] | frontend/state | 사용자(Frontend 담당) | Codex | T146, T133 | `frontend/src/App.tsx`, `frontend/src/data/mockData.ts`, `frontend/src/types.ts`, `specs/001-meetingmind-core/implement.md` | meeting-scoped report candidate, draft, current confirmed report, task candidate local state를 분리한다. | candidate는 공식 report/task와 구분되고 meetingId/sourceIds가 유지된다. |
| T148 | M019 | [x] | frontend/meeting-ai | 사용자(Frontend 담당) | Codex | T146, T147 | `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/api/workspace.ts`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | Meeting AI 화면을 단일 회의 source-aware chat shape로 정리한다. | `/api/meeting-ai/chat` shape를 사용하고 source 시간/발화자/결정 id와 unsupported 상태가 UI에 표시된다. |
| T149 | M019 | [x] | frontend/report-candidate | 사용자(Frontend 담당) | Codex | T147 | `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/api/workspace.ts`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | AI 회의록 candidate 생성과 공식 report 구분 UI를 추가한다. | 생성 결과는 `CANDIDATE`로 표시되고 확정 전에는 current confirmed report로 취급되지 않는다. |
| T150 | M019 | [x] | frontend/report-edit | 사용자(Frontend 담당) | Codex | T149 | `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | AI 대화 편집과 수동 편집을 draft state에 반영한다. | pending change apply/revert와 직접 편집 저장이 같은 draft에 반영되고 범위 밖 요청은 확인 필요/unsupported로 표시된다. |
| T151 | M019 | [ ] | frontend/report-confirm-version | 사용자(Frontend 담당) | Codex | T150 | `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 회의록 확정, current confirmed 표시, version 표시를 추가한다. | `CANDIDATE`/`DRAFT`만 확정 가능하고 확정 시 이전 current report는 current가 아닌 버전처럼 표시된다. |
| T152 | M019 | [ ] | frontend/report-export | 사용자(Frontend 담당) | Codex | T151 | `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 회의록 Markdown 내보내기 또는 다운로드 후보를 추가한다. | 현재 draft/current report 기준 Markdown export가 가능하고 PDF/DOCX는 backend gap으로 표시된다. |
| T153 | M019 | [ ] | frontend/task-candidates | 사용자(Frontend 담당) | Codex | T147, T136 | `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 태스크 후보 추출, 검토, 등록 전 편집, 칸반 등록 local flow를 추가한다. | `TaskCandidate`는 확정 전 칸반 카드가 아니며, 확정 시 `TaskCard.sourceCandidateId`가 유지된다. |
| T154 | M019 | [ ] | frontend/scope-safety | 사용자(Frontend 담당) | Codex | T148, T149, T153 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Meeting AI/report/task payload가 단일 meeting source만 사용하는지 negative case를 점검한다. | 다른 meeting/project source가 payload에 섞이지 않고 근거 없음은 unsupported/확인 불가로 표시된 결과가 기록되어 있다. |
| T155 | M019 | [ ] | backend-ai-gap | 사용자(Frontend 담당) | Codex | T146, T154 | `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | Meeting workspace 구현 중 확인한 backend/AI gap을 정리한다. | Backend 권한 필터 이후 context 조립, report controller, task candidate 저장/confirm, export 구현 gap이 후속 작업으로 기록되어 있다. |
| T156 | M019 | [ ] | frontend/smoke | 사용자(Frontend 담당) | Codex | T148, T151, T153 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Meeting AI, Report Agent, task candidate, report export route 흐름을 수동 점검한다. | 주요 route 이동 결과와 발견 이슈 또는 미실행 사유가 `implement.md`에 기록되어 있다. |
| T157 | M019 | [ ] | frontend/verification | 사용자(Frontend 담당) | Codex | T156 | `frontend/**`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Frontend build와 diff 검증을 실행하고 M019 완료 상태를 정리한다. | `cd frontend && npm run build`, `git diff --check` 결과가 기록되고 완료된 task만 `[x]`로 표시되어 있다. |

## Verification

- [x] V001 이전 구현 검증: `cd frontend && npm run build`
- [x] V002 이전 구현 검증: `cd backend && ./gradlew test`
- [x] V003 이전 구현 검증: `cd ai && python3 -m compileall app tests`
- [x] V004 PR #8 문서 검증: `git diff --check`, stale enum/role/source pattern search, task dependency scan
- [x] V006 AI RAG safety 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests`
- [ ] V005 주요 화면 라우팅 수동 확인
- [x] V006 Auth policy/CI 기준선 검증: `cd backend && ./gradlew test`, `cd frontend && npm run build`, `cd ai && python3 -m compileall app tests`, `cd ai && python3 -m unittest discover -s tests`, `git diff --check`
- [x] V007 Authz test matrix 문서 검증: `git diff --check`
- [x] V008 AI observability 검증: `cd ai && python3 -m compileall app tests`, `cd ai && ./.venv/bin/python -m unittest discover -s tests`

## Notes

- 이 작업 목록은 문서 기준선 생성 이후의 구현 순서를 제안한다.
- Q-001은 Google OAuth와 자체 회원가입/로그인, access/refresh token, `/api/v1/auth/*`, `sessionStorage`, 랜딩 외 보호 route로 결정되었다.
- 실제 권한 구현 전 Q-002는 먼저 결정하는 편이 안전하다.
- 요구사항 기준선 파일은 PR #8의 `agent/requirements-docs-baseline` 브랜치에 포함되어 있다.
