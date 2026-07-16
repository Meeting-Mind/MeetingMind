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
| M020 | AI 계약 prototype/target 경계 정리 | Current Prototype과 Target Backend-to-AI 차이가 문서화되고 Backend Meeting AI chat 1차 연동 경로가 분리되어 있다. | T158-T165 |
| M021 | Project AI Backend 권한 선필터 연동 | Project AI가 SpaceMember 인증과 회의 ACL 선필터 이후의 공식 지식/회의 요약만 사용하고 Frontend가 Backend API를 호출한다. | T166-T173 |
| M022 | AI 회의록 candidate Backend 경유 전환 | AI 회의록 생성이 회의 편집 권한과 단일 meeting source 검증 뒤에서 실행되고 candidate 저장/화면 연결 경계가 생긴다. | T174-T181 |
| M023 | Session handoff 공용/개인 상태 분리 | 팀 공용 기준선과 개인 작업 상태가 별도 파일로 관리되고 개인 파일은 Git에서 제외된다. | T182-T184 |
| M024 | Report candidate 확정과 current version 전환 | 편집 권한자가 candidate를 공식 report로 확정하고 회의당 current confirmed report를 하나만 유지한다. | T185-T190 |
| M025 | AI 태스크 후보 Backend 경유와 TaskCard 확정 | 편집 권한자가 단일 회의 근거로 태스크 후보를 생성하고 검토한 후보를 중복 없이 프로젝트 TaskCard로 확정한다. | T191-T199 |
| M026 | AI provider 오류·timeout 안전성 | provider 원문을 노출하지 않고 기능별 timeout과 공통 오류 응답 shape가 테스트로 검증되어 있다. | T200-T203 |
| M027 | Backend 권한 매트릭스 runtime | SpaceMember, MeetingParticipant, owner transfer와 Project AI 후보 권한이 실제 mutation/API에서 강제된다. | T204-T211 |
| M028 | 회의 참가 신청과 HOST 승인 | URL/코드 신청은 승인 전 접근권을 만들지 않고 HOST 승인 후 회의 단독 participant만 생성한다. | T212-T215 |
| M029 | Frontend 회의 접근·권한 화면 | 사용자가 참가 신청, 승인 상태, Space role과 meeting ACL, default-deny prejoin을 화면에서 확인한다. | T216-T221 |
| M030 | 로컬 PostgreSQL/pgvector 영속화 기준선 | 문서와 migration이 일치하고 격리된 로컬 DB에 V1 이후 schema가 재현 가능하게 적용된다. | T222-T231 |
| M031 | CI 품질·공급망 검증 강화 | `dev`/PR 변경이 애플리케이션 빌드, V1~V10 migration, 핵심 테스트, 컨테이너·secret 검사를 통과하고 `main`은 필수 check 없는 직접 변경이 차단된다. | T232-T245 |
| M032 | Backend PostgreSQL runtime 영속화 | Auth/Workspace/회의 산출물이 PostgreSQL에 유지되고 권한·확정 mutation 및 AI context 선필터가 DB transaction으로 검증된다. | T246-T253 |
| M033 | 회의 CRUD PostgreSQL end-to-end | 회의 CRUD와 soft delete가 실제 PostgreSQL API 및 Frontend target 화면에 ACL과 canonical 상태 전이 기준으로 연결된다. | T254-T263 |
| M034 | Grounded PostgreSQL RAG 통합 | 권한 scope가 강제된 pgvector retrieval, grounding, internal auth, embedding generation과 관측성을 구현한다. | T264-T276 |
| M035 | 회의 채팅 텍스트 첨부파일 RAG | 텍스트 추출 가능한 회의 첨부파일을 권한·보존·출처 기준으로 RAG에 연결하고 이미지 처리는 후속으로 둔다. | T277-T284 |
| M036 | Frontend Workspace 영속 데이터 복원 | 로그인 후 Space/Meeting/SpaceMember를 API에서 복원하고 API 실패가 mock 성공으로 보이지 않게 한다. | T285-T287 |
| M037 | 회의 CRUD 프론트엔드 target 완성 | target Space의 회의 상세·초기 참여자·ACL·캘린더 mutation이 Backend 응답과 재조회 결과로 동작하고 mock/local state와 섞이지 않는다. | T288-T296 |

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
| T063 | M008 | [x] | data/retention | 사용자 | Codex | T061 | `backend/**`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/clarify.md` | retentionPolicy, failureReason, STT 원문 보존 정책 필드를 schema/document에 맞춘다. | `DAYS_7`, `DAYS_30`, `PERMANENT`, 기본 30일, `retentionUntil`, `legalHold`, `purgedAt` 전략이 문서와 V8에 반영되어 있다. |
| T064 | M008 | [x] | data/verification | 사용자 | Codex | T059, T060, T061, T062, T063 | `backend/**`, `specs/001-meetingmind-core/implement.md` | migration 적용 또는 schema 검증 명령을 실행하고 결과를 기록한다. | 격리된 PostgreSQL 16 + pgvector DB에 V1~V9 적용과 재실행 validation이 통과했다. |
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
| T133 | M018 | [x] | frontend/state | 사용자(Frontend 담당) | Codex | T132, T123 | `frontend/src/App.tsx`, `frontend/src/data/mockData.ts`, `frontend/src/types.ts`, `specs/001-meetingmind-core/implement.md` | SpaceMember, MeetingParticipant, TaskCard, Project AI source local state를 분리한다. | Space 멤버십과 회의 ACL이 별도 상태로 표현되고, mock 생성/수정/삭제가 프로젝트 개요, 멤버 화면, 칸반, Project AI source에 일관되게 반영된다. |
| T134 | M018 | [x] | frontend/meetings | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 프로젝트 회의 목록을 생성/삭제/상태 표시/상세 진입이 가능한 관리형 목록으로 확장한다. | 회의 생성은 제목/일시/참여자 후보를 받고, 삭제/취소는 권한 제한 copy와 확인 절차를 거치며, 목록은 접근 가능한 회의만 표시한다. |
| T135 | M018 | [x] | frontend/acl | 사용자(Frontend 담당) | Codex | T133, T134 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 회의별 참여자/ACL 관리 패널을 추가한다. | `VIEWER`/`EDITOR`/`HOST`, `ACTIVE`/`REVOKED`, owner/admin override, default-deny, 마지막 active HOST 보호, 삭제 권한 제한이 UI 상태와 copy에 반영된다. |
| T136 | M018 | [x] | frontend/kanban | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/ProjectOverviewPage.tsx`, future `frontend/src/components/KanbanBoard.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 프로젝트 칸반 보드를 추가한다. | `TODO`, `IN_PROGRESS`, `DONE` 컬럼, 카드 생성/편집/담당자/마감일/상태 이동/삭제/검색 또는 필터가 mock/local state로 동작한다. |
| T137 | M018 | [x] | frontend/project-ai | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/api/workspace.ts`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | Project AI 패널의 source 표시와 unsupported 상태를 요구사항 기준으로 정리한다. | 공식 Project Knowledge와 회의 기록 출처가 구분되고, 접근 가능한 meeting source만 prompt/context 후보로 사용하며 근거 없음 응답이 추정처럼 보이지 않는다. |
| T138 | M018 | [x] | frontend/members | 사용자(Frontend 담당) | Codex | T133 | `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | Space 멤버 목록, 초대, 역할 변경, 제거 흐름을 정리한다. | Space invitation과 Meeting invitation copy가 분리되고, owner/admin/member 역할 변경과 제거가 local state와 화면 액션에 반영된다. |
| T139 | M018 | [x] | frontend/owner-transfer | 사용자(Frontend 담당) | Codex | T138 | `frontend/src/pages/TeamMembersPage.tsx`, `frontend/src/App.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | 오너 권한 이양 확인 절차와 기존 오너 강등 local flow를 추가한다. | 활성 SpaceMember만 대상이 되고, 확인 절차 없이 이양되지 않으며, 이양 후 새 owner/기존 owner 역할이 화면에 일관되게 표시된다. |
| T140 | M018 | [x] | frontend/negative-permission | 사용자(Frontend 담당) | Codex | T135, T138, T139 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | default-deny, 회수 즉시 접근 차단, owner/admin override, 마지막 HOST 보호, owner transfer 확인 누락 등 negative case를 수동 점검한다. | 권한 제한/disabled/빈 상태/오류 안내가 요구사항과 맞는지 결과가 `implement.md`에 기록되어 있다. |
| T141 | M018 | [x] | backend-gap | 사용자(Frontend 담당) | Codex | T132, T140 | `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | Project workspace frontend 구현 중 확인한 backend/API gap을 정리한다. | meeting participant API, invitation, kanban, owner transfer, audit log, Project AI backend 권한 필터의 미구현 범위가 후속 task 후보로 기록되어 있다. |
| T142 | M018 | [x] | frontend/smoke | 사용자(Frontend 담당) | Codex | T134, T135, T136, T137, T138, T139 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | 프로젝트 개요, 회의 관리, ACL, 칸반, Project AI, 팀 멤버/오너 이양 route 흐름을 수동 점검한다. | 주요 route 이동 결과와 발견 이슈 또는 미실행 사유가 `implement.md`에 기록되어 있다. |
| T143 | M018 | [x] | frontend/verification | 사용자(Frontend 담당) | Codex | T142 | `frontend/**`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Frontend build와 diff 검증을 실행한다. | `cd frontend && npm run build`, `git diff --check` 결과가 기록되고 완료된 task만 `[x]`로 표시되어 있다. |
| T144 | M018 | [x] | integration/handoff | 사용자(Frontend 담당) | Codex | T141, T143 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | M018 완료/미완료 범위와 다음 backend/frontend 연계 작업을 정리한다. | M018 남은 gap과 다음 owner가 잡아야 할 backend/API 작업이 명확히 남아 있다. |

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

### M020: AI Contract Prototype and Target Split

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T158 | M020 | [x] | contracts/ai | 사용자 | Codex | T088, T104 | `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | AI API 문서에서 Current Prototype과 Target Backend-to-AI validation/error/audit 경계를 분리한다. | optional fallback, provider error, audit/observability, report context gap이 문서화되어 있다. |
| T159 | M020 | [x] | ai/errors | 사용자 | Codex | T158 | `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/contracts/ai-api.md` | AI provider error response를 target common code와 맞출지 또는 Backend adapter에서 변환할지 구현한다. | Target internal endpoint가 provider `500/502/503`을 `503 AI_PROVIDER_UNAVAILABLE`로 변환하고 기존 prototype endpoint의 `500/502` 호환은 유지한다. |
| T160 | M020 | [x] | ai/schema | 사용자 | Codex | T158 | `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `specs/001-meetingmind-core/contracts/ai-api.md` | Backend-to-AI strict request schema와 source metadata validator를 prototype endpoint와 분리한다. | `/api/internal/meeting-ai/chat` target schema가 `projectId`, `meetingId`, `question`, `sources[].sourceId/type/meetingId/text`를 받고 source meeting 불일치 시 `403 AI_CONTEXT_FORBIDDEN`을 반환하며 기존 `/api/meeting-ai/chat`은 유지된다. |
| T161 | M020 | [x] | ai/rag | 사용자 | Codex | T160 | `ai/app/main.py`, `ai/app/rag.py`, `ai/tests/test_meeting_ai.py` | Meeting chat의 report chunk 포함 여부와 source type 처리를 target 계약과 맞춘다. | Target internal Meeting chat 검색 대상에 `report` source type이 포함되고 report source 단위 테스트가 통과했다. |
| T162 | M020 | [x] | backend/ai | 사용자 | Codex | T052, T160 | `backend/**`, `ai/**`, `specs/001-meetingmind-core/contracts/ai-api.md` | Backend 권한 필터 이후 AI context 조립/호출 경로를 target schema에 연결한다. | Backend Meeting AI chat이 권한 확인 후 transcript/current confirmed report/decision/action source metadata를 조립해 `/api/internal/meeting-ai/chat`으로 호출한다. |
| T163 | M020 | [x] | backend/ai-chat | 사용자 | Codex | T158, T040 | `backend/**`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/implement.md` | `POST /api/v1/meetings/{meetingId}/ai/chat` 1차 연동을 추가한다. | Backend가 인증 사용자와 meeting read 권한을 확인한 뒤 AI 서버 `/api/internal/meeting-ai/chat`으로 already-filtered context를 전달하고 `cd backend && ./gradlew test`가 통과했다. |
| T164 | M020 | [x] | frontend/meeting-ai | 사용자 | Codex | T163 | `frontend/src/api/workspace.ts`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/App.tsx`, `specs/001-meetingmind-core/implement.md` | Meeting AI 화면의 직접 AI 서버 호출을 Backend endpoint로 전환한다. | `POST /api/v1/meetings/{meetingId}/ai/chat`에 인증 header와 `{question}`만 전송하고 기존 prototype context 직접 전달은 제거되었으며 `cd frontend && npm run build`가 통과했다. |
| T165 | M020 | [x] | frontend/routing | 사용자 | Codex | T164 | `frontend/src/pages/WorkspaceHomePage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/styles/app.css`, `specs/001-meetingmind-core/implement.md` | Meeting AI Backend 경유 호출에 필요한 target `meetingId` route query를 보존한다. | `meeting.id`가 있는 회의 이동 경로는 `meetingId` query를 포함하고, `ReportAgentPage`는 해당 query를 Meeting AI 링크로 전달하며, `MeetingAiPage`는 `meetingId` 없는 직접 진입에서 Backend 호출을 막고 `cd frontend && npm run build`가 통과했다. |

### M021: Project AI Backend Permission Prefilter Integration

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T166 | M021 | [x] | contracts/ai | 사용자 | Codex | T084, T104, T162 | `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/implement.md` | Project AI public Backend API와 strict Backend-to-AI internal 계약을 확정한다. | public request는 질문만 받고 internal request는 `projectId`, `allowedMeetingIds`, `sources[]`를 받으며 공식 지식/회의 기록 source type과 오류가 문서화되어 있다. |
| T167 | M021 | [x] | ai/schema | 사용자 | Codex | T166 | `ai/app/main.py`, `ai/tests/test_meeting_ai.py` | `/api/internal/project-ai/chat` strict request와 source validator를 구현한다. | source `projectId` 불일치, 허용되지 않은 meeting source, 잘못된 source type이 `403 AI_CONTEXT_FORBIDDEN`으로 차단된다. |
| T168 | M021 | [x] | ai/rag | 사용자 | Codex | T167 | `ai/app/main.py`, `ai/app/rag.py`, `ai/tests/test_meeting_ai.py` | Project AI internal endpoint가 공식 ProjectKnowledge와 허용된 meetingSummary만 project scope로 검색하도록 연결한다. | 공식 지식/회의 기록 출처가 구분되고 근거가 없으면 LLM 미호출 `unsupported=true`가 유지된다. |
| T169 | M021 | [x] | backend/context | 사용자 | Codex | T166, T040 | `backend/src/main/java/com/meetingmind/demo/authz/**`, `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/service/**`, `backend/src/test/**` | Backend가 Space 접근을 확인하고 Project AI용 ProjectKnowledge와 읽기 가능한 meeting report summary를 선필터한다. | 일반 MEMBER는 active MeetingParticipant 회의만 포함하고 OWNER/ADMIN override와 REVOKED 제외가 단위 테스트로 검증된다. |
| T170 | M021 | [x] | backend/api | 사용자 | Codex | T167, T169 | `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/main/java/com/meetingmind/demo/dto/ai/**`, `backend/src/main/java/com/meetingmind/demo/service/**`, `backend/src/test/**` | `POST /api/v1/spaces/{spaceId}/ai/chat`과 AI gateway를 구현한다. | 인증/Space 권한 검사 후 `/api/internal/project-ai/chat`을 호출하고 provider 실패를 `503 AI_PROVIDER_UNAVAILABLE`로 매핑한다. |
| T171 | M021 | [x] | frontend/project-ai | 사용자 | Codex | T170 | `frontend/src/App.tsx`, `frontend/src/api/workspace.ts`, `frontend/src/pages/ProjectOverviewPage.tsx` | Project AI 화면의 AI 서버 직접 호출과 mock context 전송을 제거하고 Backend API로 전환한다. | 인증 header와 `{question}`만 전송하고 응답 source type을 공식 지식/회의 기록으로 구분하며 target Space 목록에 없는 mock/legacy Space 호출은 차단한다. |
| T172 | M021 | [x] | verification | 사용자 | Codex | T168, T170, T171 | `ai/**`, `backend/**`, `frontend/**`, `specs/001-meetingmind-core/implement.md` | AI/Backend/Frontend 검증과 권한 negative case를 실행한다. | AI unittest/compile, Backend test, Frontend build, diff check와 real API smoke 결과가 기록되어 있다. |
| T173 | M021 | [x] | docs/closeout | 사용자 | Codex | T172 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `.specify/memory/session-handoff.md` | M021 구현 범위, 데이터 모델 영향 없음, 실제 DB/pgvector 후속 경계를 정리한다. | 완료 task와 검증 결과가 실제 구현과 일치하고 실제 DB/embedding/audit 후속 작업이 남아 있다. |

### M022: AI Report Candidate Backend Route

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T174 | M022 | [x] | contracts/report-ai | 사용자 | Codex | T150, T162 | `requirements/functional-requirements-detail.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/erd.md` | report candidate public route, strict internal request, 생성 권한, 저장 shape와 source metadata를 확정한다. | public/internal endpoint, edit 권한, 단일 meeting source, supported candidate 임시 저장, `markdown`/`createdBy`/`sourceIds`, 오류와 ERD/data-model 영향이 결정되어 있다. |
| T175 | M022 | [x] | ai/report-schema | 사용자 | Codex | T174 | `ai/app/main.py`, `ai/tests/test_meeting_ai.py` | strict `/api/internal/meeting-ai/generate-report` request와 단일 meeting source validator를 구현한다. | 다른 meeting source와 report source type이 `403 AI_CONTEXT_FORBIDDEN`으로 차단되고 provider 오류가 `503`으로 정규화된다. |
| T176 | M022 | [x] | backend/report-context | 사용자 | Codex | T174, T175 | `backend/src/main/java/com/meetingmind/demo/authz/**`, `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/service/**`, `backend/src/test/**` | Backend가 report 생성 권한을 확인하고 해당 meeting transcript/decision/action source만 조립해 AI gateway를 호출한다. | 권한 없는 사용자는 AI 호출 전에 차단되고 AI request와 public source는 Backend가 선필터한 단일 meeting source로 제한된다. |
| T177 | M022 | [x] | backend/report-candidate | 사용자 | Codex | T176 | `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/main/java/com/meetingmind/demo/dto/**`, `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/resources/db/migration/V5__add_report_candidate_metadata.sql`, `backend/src/test/**` | AI 응답을 `MeetingReport.CANDIDATE`로 저장하고 public API response로 반환한다. | supported candidate는 `markdown`, `createdBy`, `sourceIds`, 증가한 version, `current=false`를 보존하고 unsupported 결과는 저장하지 않는다. |
| T178 | M022 | [x] | frontend/report-candidate | 사용자 | Codex | T177 | `frontend/src/App.tsx`, `frontend/src/api/workspace.ts`, `frontend/src/pages/ReportAgentPage.tsx` | Report Agent의 로컬 candidate 생성과 AI 서버 직접 호출 후보를 Backend API로 전환한다. | 인증 header와 meetingId만 기준으로 생성하고 Backend candidate/source/unsupported 상태를 화면에 반영하며 미구현 confirm은 성공처럼 표시하지 않는다. |
| T179 | M022 | [x] | verification/unit | 사용자 | Codex | T175, T177, T178 | `ai/**`, `backend/**`, `frontend/**`, `specs/001-meetingmind-core/implement.md` | AI/Backend/Frontend 단위 검증과 report 권한 negative case를 실행한다. | AI 24 tests/compile, Backend test, Frontend build, diff check가 통과했다. |
| T180 | M022 | [x] | verification/integration | 사용자 | Codex | T179 | `ai/**`, `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend-to-AI report candidate 실제 API smoke를 수행한다. | supported candidate 저장은 Backend service 통합 테스트, 실제 public API는 빈 근거 `200 unsupported`와 비권한 `403`으로 검증됐다. |
| T181 | M022 | [x] | docs/closeout | 사용자 | Codex | T180 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `.specify/memory/session-handoff.md` | M022 상태와 report confirm/version/export 후속 경계를 정리한다. | confirm/update/download, 실제 PostgreSQL 적용, persistent audit가 후속 범위로 명시되어 있다. |

### M023: Shared and Local Session Handoff

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T182 | M023 | [x] | docs/process | 사용자 | Codex | T020 | `AGENTS.md`, `.gitignore` | 공용 handoff와 개인 local handoff의 작성 및 Git 관리 규칙을 정의한다. | 공용 파일의 허용 범위와 local 파일의 커밋 금지 규칙이 명시되어 있다. |
| T183 | M023 | [x] | docs/handoff | 사용자 | Codex | T182 | `.specify/memory/session-handoff.md`, `.specify/memory/session-handoff.example.md`, `.specify/memory/session-handoff.local.md` | 누적 세션 로그를 팀 공통 기준선으로 정리하고 개인 작업용 템플릿과 local 파일을 만든다. | 공용 파일에 개인 브랜치 상태가 없고 local 파일은 owner와 작업 상태를 기록할 수 있으며 Git에서 제외된다. |
| T184 | M023 | [x] | verification | 사용자 | Codex | T183 | `.gitignore`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | ignore 적용, tracked/untracked 범위, 문서 중복과 diff 형식을 검증한다. | `git check-ignore`, stale 개인 상태 검색, `git diff --check`가 통과했다. |

### M024: Report Confirm and Current Version

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T185 | M024 | [x] | contracts/report | 사용자 | Codex | T181 | `requirements/functional-requirements-detail.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/plan.md` | confirm 권한, 상태 전이, current 단일 제약, `confirmedAt`, candidate 만료 정책 경계를 확정한다. | confirm 계약과 모델 영향이 문서화되고 TTL은 `Q-008`로 분리되어 있다. |
| T186 | M024 | [x] | backend/domain | 사용자 | Codex | T185 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/test/**` | candidate/draft 확정과 기존 current report 해제를 하나의 domain transition으로 구현한다. | 최신 version 대상만 `CONFIRMED/current=true`가 되고 기존 current는 false이며 중복/다른 meeting/stale 확정이 거부된다. |
| T187 | M024 | [x] | backend/api | 사용자 | Codex | T186 | `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/main/java/com/meetingmind/demo/dto/**`, `backend/src/main/java/com/meetingmind/demo/service/**`, `backend/src/test/**` | 인증과 report 편집 권한을 적용한 confirm endpoint를 구현한다. | edit 권한만 성공하고 VIEWER는 domain 변경 전에 차단되며 report 불일치는 `REPORT_NOT_FOUND`로 응답한다. |
| T188 | M024 | [x] | frontend/report | 사용자 | Codex | T187 | `frontend/src/api/workspace.ts`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/types.ts` | 비활성 confirm 버튼을 Backend API와 연결하고 status/version/current 상태를 표시한다. | 성공 시 confirmed/current가 표시되고 loading/error와 중복 제출 방지가 동작한다. |
| T189 | M024 | [x] | verification | 사용자 | Codex | T187, T188 | `backend/**`, `frontend/**`, `specs/001-meetingmind-core/implement.md` | Backend/Frontend 테스트, API smoke, diff 검증을 실행한다. | current 교체, 권한 거부, 중복 확정, Frontend build, public API 오류 매핑이 검증됐다. |
| T190 | M024 | [x] | docs/closeout | 사용자 | Codex | T189 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md`, `.specify/memory/session-handoff.md` | M024 완료 범위와 TTL/update/history/export 후속 경계를 정리한다. | TTL은 `Q-008`, update/history/export와 persistent audit는 후속 범위로 명시되어 있다. |

### M025: Task Candidate Backend Route and TaskCard Confirmation

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T191 | M025 | [x] | contracts/task | 사용자 | Codex | T190 | `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/contracts/kanban-api.md`, `specs/001-meetingmind-core/plan.md` | 태스크 추출/조회/확정 권한, 상태 전이, source 범위, candidate 만료 정책 경계를 확정한다. | public/internal 계약과 권한이 문서화되고 TTL은 `Q-009`로 분리되어 있다. |
| T192 | M025 | [x] | data/task | 사용자 | Codex | T191 | `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/erd.md`, `backend/src/main/resources/db/migration/**`, `backend/src/main/java/com/meetingmind/demo/domain/**` | TaskCandidate와 TaskCard 모델, unique 제약, target migration을 구현한다. | candidate 상태와 source가 보존되고 sourceCandidateId당 카드가 최대 하나다. |
| T193 | M025 | [x] | ai/task | 사용자 | Codex | T191 | `ai/app/main.py`, `ai/tests/**` | Backend가 선필터한 단일 회의 source만 받는 strict 태스크 추출 endpoint를 구현한다. | source type/meeting allowlist 위반은 403, 근거 없음은 LLM 미호출 unsupported, provider 오류는 503이다. |
| T194 | M025 | [x] | backend/task-generate | 사용자 | Codex | T192, T193 | `backend/src/main/java/com/meetingmind/demo/**`, `backend/src/test/**` | 편집 권한 선검증, canonical context 조립, AI 호출, TaskCandidate 저장을 구현한다. | 권한 검증이 데이터/AI 호출보다 먼저 수행되고 지원되는 후보만 저장된다. |
| T195 | M025 | [x] | backend/task-query | 사용자 | Codex | T192 | `backend/src/main/java/com/meetingmind/demo/**`, `backend/src/test/**` | 회의 접근 권한을 적용한 TaskCandidate 조회 API를 구현한다. | 해당 meeting 후보만 반환하고 권한이 회수된 사용자는 조회할 수 없다. |
| T196 | M025 | [x] | backend/task-confirm | 사용자 | Codex | T192, T195 | `backend/src/main/java/com/meetingmind/demo/**`, `backend/src/test/**` | 후보 확정, 입력 검증, TaskCard 생성, 중복 방지를 하나의 domain transition으로 구현한다. | 활성 SpaceMember이면서 편집 권한인 사용자만 확정하고 후보당 카드 하나만 생성된다. |
| T197 | M025 | [x] | frontend/task | 사용자 | Codex | T194, T196 | `frontend/src/api/workspace.ts`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/types.ts` | 로컬 태스크 추출/등록 flow를 Backend 생성/조회/확정 API로 전환한다. | 재진입 후보 복원, loading/error, 제목·설명·담당자·마감일 편집, 확정 결과가 Backend 응답과 일치한다. |
| T198 | M025 | [x] | verification | 사용자 | Codex | T193-T197 | `ai/**`, `backend/**`, `frontend/**`, `specs/001-meetingmind-core/implement.md` | AI/Backend/Frontend 테스트, API smoke, diff와 계약 정합성 리뷰를 실행한다. | scope/권한/중복 negative case와 정상 생성/확정 흐름이 검증된다. |
| T199 | M025 | [x] | docs/closeout | 사용자 | Codex | T198 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | 구현 범위, 검증 결과, TTL과 일반 Kanban API 후속 경계를 정리한다. | 완료 task와 미실행 사유, 남은 위험이 원본 문서에 기록되어 있다. |

### M026: AI Provider Safety

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T200 | M026 | [x] | contracts/ai | 사용자 | Codex | T199 | `requirements/performance.md`, `requirements/non-functional-requirements-detail.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/feature-implementation-comparison.md` | OpenAI timeout과 외부 오류 응답 기준을 확정하고 구현 비교 기준선을 최신화한다. | 챗봇·용어·태스크 30초, 보고서 60초 timeout, 공통 오류 body와 현재 구현 경계가 문서화되고 자동 재시도 제외 근거가 기록되어 있다. |
| T201 | M026 | [x] | ai/provider | 사용자 | Codex | T200 | `ai/app/main.py` | OpenAI 공통 호출의 timeout을 기능별로 적용하고 provider 원문 오류 노출을 제거한다. | 설정 누락, HTTP 오류, 연결 오류, 빈 provider 응답이 raw detail 없이 고정 `503 AI_PROVIDER_UNAVAILABLE`와 공통 오류 body로 반환된다. |
| T202 | M026 | [x] | ai/test | 사용자 | Codex | T201 | `ai/tests/test_meeting_ai.py` | provider 오류 비노출, 기능별 timeout, 공통 오류 body를 단위 테스트한다. | raw provider body/연결 사유가 응답에 없고 30초/60초 timeout과 `{code, message, fieldErrors, traceId}`가 검증된다. |
| T203 | M026 | [x] | docs/closeout | 사용자 | Codex | T202 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/feature-implementation-comparison.md` | 구현 범위, 현재 구현 비교, 검증 결과, 후속 의존 작업을 정리한다. | AI 검증 결과와 internal service auth, pgvector, embedding worker, 실제 STT 선행 의존성이 기록되어 있다. |

### M027: Backend Permission Matrix Runtime

M027은 M018에서 frontend local flow로 표현한 권한 규칙을 backend domain/store/API 경계에 연결하는 보안 작업이다. Project AI와 Meeting AI context 후보가 backend 권한 필터를 먼저 통과하도록 만든다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T204 | M027 | [x] | backend/design | 사용자(Backend 담당) | Codex | T040, T120, T144 | `requirements/permissions.md`, `requirements/functional-requirements-detail.md`, `specs/001-meetingmind-core/contracts/space-api.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/contracts/ai-api.md`, `specs/001-meetingmind-core/test-matrix.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | SpaceMember, MeetingParticipant, Project AI 권한 요구와 target contract를 재확인하고 backend 구현 순서를 고정한다. | M027 plan/task가 상세 요구조건, 계약, negative smoke 항목과 연결되어 있고 baseline `cd backend && ./gradlew test` 결과가 기록되어 있다. |
| T205 | M027 | [x] | backend/space-members | 사용자(Backend 담당) | Codex | T204 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java`, `backend/src/test/java/com/meetingmind/demo/**` | SpaceMember role 변경/제거 domain/store/API를 구현한다. | role 변경은 OWNER 전용이고 대상 role은 `ADMIN`/`MEMBER`만 허용한다. owner 제거는 금지되고, 제거된 member는 project 접근과 Project AI 접근이 즉시 차단되며 기존 회의 접근은 MeetingParticipant ACL에 따라 유지된다. |
| T206 | M027 | [x] | backend/meeting-participants | 사용자(Backend 담당) | Codex | T204, T205 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/test/java/com/meetingmind/demo/**` | MeetingParticipant add/update/revoke API와 mutation 검증을 구현한다. | `OWNER`/`ADMIN` override 또는 active `HOST`만 참여자 변경이 가능하고, role/access 변경과 revoke/remove는 마지막 active `HOST`를 깨면 `LAST_ACTIVE_HOST_REQUIRED`로 거부된다. |
| T207 | M027 | [x] | backend/member-removal-cascade | 사용자(Backend 담당) | Codex | T205, T206 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/test/java/com/meetingmind/demo/**` | SpaceMember 제거 시 같은 Space의 member MeetingParticipant를 회의 단독 `GUEST`로 전환한다. | SpaceMember 제거는 프로젝트 전체 접근권만 제거하고, 기존 active MeetingParticipant는 회의 범위 read/edit/delete/LiveKit/Meeting AI 권한을 유지한다. 회의 접근을 끊으려면 participant revoke API를 별도로 사용한다. |
| T208 | M027 | [x] | backend/owner-transfer | 사용자(Backend 담당) | Codex | T205 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java`, `backend/src/test/java/com/meetingmind/demo/**` | owner transfer transaction local domain flow를 구현한다. | 대상은 active SpaceMember만 허용하고 확인 문자열 누락/불일치 시 API 실행을 거부한다. 성공 시 새 owner는 `OWNER`, 기존 owner는 요청한 `ADMIN` 또는 `MEMBER`로 강등되며 owner 공백/중복이 생기지 않는다. |
| T209 | M027 | [x] | backend/project-ai-auth-filter | 사용자(Backend 담당) | Codex | T205, T206 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/test/java/com/meetingmind/demo/**` | Project AI context 후보 조회 시 backend 권한 선필터를 구현한다. | Project AI는 active SpaceMember만 사용할 수 있고, 후보 payload는 `projectKnowledge[]`와 `meetings[]`로 분리되며 revoked/default-deny/guest-only meeting source는 후보에서 제외된다. |
| T210 | M027 | [x] | backend/audit | 사용자(Backend 담당) | Codex | T205, T206, T208 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/test/java/com/meetingmind/demo/**`, `specs/001-meetingmind-core/implement.md` | 권한 변경 audit를 최소 in-memory event로 남기거나 runtime gap을 명시한다. | role 변경, participant grant/update/revoke, member removal cascade, owner transfer가 actor/target/before/after/timestamp 기준으로 in-memory event에 기록된다. |
| T211 | M027 | [x] | backend/verification | 사용자(Backend 담당) | Codex | T205, T206, T207, T208, T209, T210 | `backend/**`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | negative permission smoke와 backend 검증을 실행하고 문서를 정리한다. | default-deny, participant revoke 즉시 차단, owner/admin override, 마지막 HOST 보호, owner transfer 확인 누락, SpaceMember 제거 후 Project AI 차단/회의 접근 유지, Project AI 후보 필터 테스트가 통과하고 `cd backend && ./gradlew test`, `git diff --check` 결과가 기록되어 있다. |

### M028: Meeting Join Request Approval

M028은 사용자-facing 회의 참여를 URL/코드 참가 신청과 HOST 승인 흐름으로 전환한다. Space invitation 및 운영상 participant ACL 직접 조정과 분리하며, Backend in-memory prototype 경계에서 상태 전이와 권한을 검증한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T212 | M028 | [x] | contracts/requirements | 사용자(Backend 담당) | Codex | T211 | `requirements/functional-requirements*.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/clarify.md`, `plan.md`, `contracts/*`, `data-model.md`, `erd.md` | 회의 URL/코드 참가 신청과 HOST 승인 계약을 기존 Invitation/직접 ACL 흐름에서 분리한다. | code-only 신청 endpoint, PENDING/APPROVED/REJECTED 상태, 승인 권한, SpaceMember 비생성, 수동 ACL 경계가 문서에서 일치한다. |
| T213 | M028 | [x] | backend/domain-api | 사용자(Backend 담당) | Codex | T212 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/main/java/com/meetingmind/demo/dto/**` | 안전한 joinCode 생성, URL/code lookup, 신청 생성/목록/승인/거절 API를 구현한다. | code 또는 URL만으로 신청 가능하고 승인 전 접근은 없으며, 승인 시 VIEWER participant만 생성된다. |
| T214 | M028 | [x] | backend/test | 사용자(Backend 담당) | Codex | T213 | `backend/src/test/java/com/meetingmind/demo/**` | 참가 신청 상태와 negative permission 테스트를 추가한다. | URL/code, invalid code, duplicate pending, existing participant, unauthorized review, approve/reject replay, guest/member 분기가 검증된다. |
| T215 | M028 | [x] | backend/verification | 사용자(Backend 담당) | Codex | T214 | `backend/**`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | Backend 전체 검증과 문서 closeout을 수행한다. | `cd backend && ./gradlew test`, `git diff --check` 결과와 persistence/frontend gap이 기록된다. |

### M029: Frontend Meeting Access and Permission Surfaces

M029는 이미 존재하는 Space role/회의 ACL 관리 화면을 M028 Backend 참가 신청 계약과 연결하고, 회의 진입 전에 Backend 권한을 확인하는 default-deny 화면을 추가한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T216 | M029 | [x] | frontend/discovery | 사용자(Frontend 담당) | Codex | T215 | `frontend/src/App.tsx`, `frontend/src/pages/**`, `frontend/src/api/workspace.ts`, `specs/001-meetingmind-core/plan.md`, `tasks.md`, `implement.md` | 기존 권한 확인/회의 접근 화면과 M028 연결 gap을 조사한다. | TeamMembers Space role, ProjectOverview meeting ACL은 존재하고 LiveMeeting backend ACL gate, join code form, JoinRequest API 연결은 없음을 기록한다. |
| T217 | M029 | [x] | frontend/types-api | 사용자(Frontend 담당) | Codex | T216 | `frontend/src/types.ts`, `frontend/src/api/workspace.ts` | MeetingJoinRequest와 access probe type/API client를 추가한다. | create/list/approve/reject response shape가 M028 contract와 일치하고 superseded MeetingInvitation client를 사용하지 않는다. |
| T218 | M029 | [x] | frontend/access-page | 사용자(Frontend 담당) | Codex | T217 | `frontend/src/pages/MeetingAccessPage.tsx`, `frontend/src/App.tsx`, `frontend/src/components/WorkspaceSidebar.tsx`, `frontend/src/styles/app.css` | URL/코드 참가 신청과 pending/access 확인 화면을 구현한다. | invalid/submitting/pending/allowed/denied 상태와 접근 확인 action이 화면에 분리되어 있다. |
| T219 | M029 | [x] | frontend/prejoin-gate | 사용자(Frontend 담당) | Codex | T217 | `frontend/src/pages/LiveMeetingPage.tsx`, `frontend/src/pages/LiveRoomPage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/WorkspaceHomePage.tsx` | LiveMeeting 진입 전에 Backend meeting ACL을 확인한다. | meetingId 누락/403/API 실패는 default-deny이고 access probe 성공 시에만 media prejoin과 target LiveKit token 요청이 가능하다. |
| T220 | M029 | [x] | frontend/approval-semantics | 사용자(Frontend 담당) | Codex | T216 | `frontend/src/App.tsx`, `frontend/src/pages/TeamMembersPage.tsx` | 회의 참가 신청 승인 local flow가 MeetingParticipant만 생성하도록 수정한다. | 승인 후 SpaceMember 수/role은 변하지 않고 대상 회의에 VIEWER guest participant만 추가된다. SpaceMember 제거 시 기존 meeting ACL은 guest로 유지된다. |
| T221 | M029 | [x] | frontend/verification | 사용자(Frontend 담당) | Codex | T218, T219, T220 | `frontend/**`, `specs/001-meetingmind-core/tasks.md`, `implement.md`, `analyze.md` | build, route/visual smoke, diff 검증과 문서 closeout을 수행한다. | `npm run build`, `/meeting-access` HTTP 200, `git diff --check`가 통과했다. in-app browser가 현재 세션에 없어 desktop/mobile visual smoke는 미실행 사유를 기록했다. |

### M030: Local PostgreSQL and pgvector Foundation

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T222 | M030 | [x] | contracts/data | 사용자 | Codex | T221 | `requirements/*`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/erd.md`, `specs/001-meetingmind-core/plan.md` | 최신 ERD와 V1~V6 migration 차이를 분석하고 참가 신청, 전사 생명주기, 보존, 출처, embedding generation 결정을 확정한다. | 문서가 MeetingJoinRequest, forward-only migration, MeetingTranscript, SourceReference 논리 모델, EmbeddingJob/generation, Q-010 경계를 일관되게 설명한다. |
| T223 | M030 | [x] | data/local | 사용자 | Codex | T222 | `compose.local.yml`, `README.md` | 다른 프로젝트 DB와 격리된 PostgreSQL 16 + pgvector 로컬 실행 구성을 추가한다. | host 5434에서 health check가 통과하고 실행/중지/Flyway 환경변수 사용법이 문서화되어 있다. |
| T224 | M030 | [x] | data/schema | 사용자 | Codex | T222 | `backend/src/main/resources/db/migration/V7__*.sql` | AuthIdentity/AuthSession, SpaceInvitation, 기존 MeetingInvitation, MeetingRoom 누락 schema를 forward migration으로 추가한다. | 이미 공유된 V7 checksum을 유지하면서 provider/token/status unique/check/index를 추가한다. |
| T225 | M030 | [x] | data/schema | 사용자 | Codex | T224 | `backend/src/main/resources/db/migration/V8__*.sql` | MeetingTranscript, retention, DomainTerm, AuditLog schema를 추가한다. | 전사 상태/보존/법적 보류와 용어/감사 index가 요구사항 및 ERD와 일치한다. |
| T226 | M030 | [x] | data/schema | 사용자 | Codex | T225 | `backend/src/main/resources/db/migration/V9__*.sql` | EmbeddingJob과 EmbeddingChunk generation/active 교체 metadata를 추가한다. | 비동기 재색인 동안 기존 active chunk를 유지할 수 있고 Q-010 전까지 vector 차원/index를 강제하지 않는다. |
| T227 | M030 | [x] | data/schema | 사용자 | Codex | T226 | `backend/src/main/resources/db/migration/V10__*.sql` | 최신 MeetingJoinRequest와 joinCodeHash schema를 공유 migration을 수정하지 않는 forward migration으로 추가한다. | pending unique, review 상태 제약, joinCodeHash unique index가 ERD와 일치하고 기존 V7 checksum이 유지된다. |
| T228 | M030 | [x] | data/verification | 사용자 | Codex | T223-T227 | `backend/**`, `specs/001-meetingmind-core/implement.md` | 빈 로컬 DB와 기존 V9 DB에 Flyway V1~V10을 적용하고 table/constraint/index와 backend test를 검증한다. | Flyway 최초 적용/재검증/기존 V9 upgrade, 25개 도메인 테이블, vector extension, join request/default/check/partial index, `./gradlew test`, `git diff --check` 결과가 기록되어 있다. |
| T229 | M030 | [x] | backend/persistence | 사용자(Backend 담당) | Codex | T228 | `backend/src/main/java/com/meetingmind/demo/**`, `backend/build.gradle`, `backend/src/test/**` | in-memory Auth/Workspace 저장소와 저장된 Transcript 산출물을 transaction 가능한 PostgreSQL repository로 단계 전환한다. M032 T246~T253으로 실행한다. | 권한 선검증과 report/task current/confirm 원자성이 DB transaction 및 제약으로 검증된다. legacy STT streaming session/file prototype은 실제 STT pipeline 범위로 분리한다. |
| T230 | M030 | [x] | ai/pgvector | 사용자(AI 담당) | Codex | T253, T254 | `ai/**`, embedding/vector 관련 forward migration, `specs/001-meetingmind-core/contracts/ai-api.md` | M033 세부 task를 통합해 embedding worker와 권한 필터된 pgvector retriever를 연결한다. | 완료된 active generation만 검색하고 Meeting/Project scope 및 source citation negative test가 통과한다. |
| T231 | M030 | [x] | data/local-profile | 사용자 | Codex | T223, T228 | `backend/build.gradle`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-local.yml`, `compose.local.yml`, `README.md` | 팀 공용 Docker DB를 Backend 기본 `local` profile과 연결하고 환경변수 기반 `db`, DB 비의존 `test` profile과 분리한다. | 팀원이 Compose 실행 후 `./gradlew bootRun`으로 동일 DataSource/Flyway 환경을 재현하고 Backend test는 Docker 실행 여부와 독립적으로 통과한다. |

### M031: CI Quality and Supply Chain Gates

M031은 기존 compile/build 기준선을 실제 배포 산출물, PostgreSQL migration, Frontend 자동 테스트, 컨테이너·secret 검사까지 확장한다. CI 코드와 GitHub 원격 branch protection 설정을 분리하며, required check 이름이 원격에서 생성된 뒤 `main` 보호 규칙을 적용한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T232 | M031 | [x] | ci/discovery | 사용자 | Codex | T231 | `.github/workflows/ci.yml`, `backend/build.gradle`, `backend/src/main/resources/**`, `frontend/package.json`, `ai/requirements.txt`, `specs/001-meetingmind-core/{plan,tasks,implement}.md` | 현재 CI와 빌드·migration·테스트·Docker·GitHub 설정 gap을 조사하고 구현 순서를 확정한다. | 기존 CI가 `main` push/PR의 Backend test, Frontend build, AI unit만 수행함을 확인하고 `dev` trigger, `bootJar`, V1~V10 migration, Dockerfile/image, Frontend lint/unit/E2E, 보안 검사, Summary, branch protection을 후속 task로 분리했다. |
| T233 | M031 | [x] | ci/workflow | 사용자 | Codex | T232 | `.github/workflows/ci.yml` | `dev`/`main` 대상 PR/push trigger, concurrency, 최소 권한과 stable Backend/Frontend/AI job 이름을 구성한다. | `contents: read`, 중복 run 취소, `Backend`/`Frontend`/`AI`/`CI Gate` 이름을 정적 검토했고 `git diff --check`가 통과했다. |
| T234 | M031 | [x] | backend/package | 사용자 | Codex | T233 | `.github/workflows/ci.yml`, `backend/**` | Backend test와 `bootJar` 산출물 생성을 CI에서 검증한다. | Java 21에서 `./gradlew test bootJar`가 통과하고 실행 가능한 jar가 생성됐다. |
| T235 | M031 | [x] | data/migration | 사용자 | Codex | T233 | `.github/workflows/ci.yml`, `backend/src/main/resources/db/migration/**`, `backend/src/test/**` | pgvector 지원 PostgreSQL 16 service container에 Flyway V1~V10을 순서대로 적용한다. | 격리된 빈 DB에서 migration 10개, schema history와 V4 `vector` extension을 CI와 같은 `MigrationIntegrationTest` 경로로 검증했다. |
| T236 | M031 | [x] | container/build | 사용자 | Codex | T234 | `backend/Dockerfile`, `backend/.dockerignore`, `ai/Dockerfile`, `ai/.dockerignore`, `.github/workflows/ci.yml` | Backend와 AI production image를 재현 가능한 multi-stage/minimal runtime 기준으로 빌드한다. | 두 image build, `meetingmind` non-root runtime, Backend jar/AI uvicorn entrypoint와 content digest를 확인했다. |
| T237 | M031 | [x] | frontend/test | 사용자 | Codex | T233 | `frontend/package.json`, `frontend/package-lock.json`, `frontend/eslint.config.*`, `frontend/src/**/*.test.*`, `.github/workflows/ci.yml` | Frontend lint와 unit test 기반을 추가하고 build와 함께 실행한다. | `npm run lint` 오류 0건(기존 경고 8건), unit 6건, `npm run build`가 통과했다. |
| T238 | M031 | [x] | frontend/e2e | 사용자 | Codex | T234, T237 | `frontend/playwright.config.*`, `frontend/e2e/**`, `.github/workflows/ci.yml` | 실제 Frontend/Backend를 기동해 핵심 로그인과 회의 접근 gate를 Playwright로 검증한다. | Chromium에서 로그인 성공, active HOST prejoin 허용, unknown meeting default-deny 2건이 통과했다. |
| T239 | M031 | [x] | security/image-scan | 사용자 | Codex | T236 | `.github/workflows/ci.yml`, `backend/Dockerfile`, `ai/Dockerfile` | checksum 검증된 Trivy로 Backend/AI image의 HIGH/CRITICAL 취약점을 검사한다. | 공식 64-bit checksum 교정 후 실제 Backend/AI image에서 HIGH/CRITICAL 취약점 0건을 확인했다. |
| T240 | M031 | [x] | security/secret-discovery | 사용자 | Codex | T233 | `.github/workflows/ci.yml`, `specs/001-meetingmind-core/{plan,implement}.md` | checksum 검증된 Gitleaks로 전체 Git 이력을 검사하고 finding을 값 노출 없이 목록화한다. | 44개 커밋에서 `backend/.env` 4건, `ai/.env.example` 1건을 확인했고 secret 값 없이 규칙/파일/건수만 기록했다. |
| T241 | M031 | [x] | security/credential-response | 저장소 관리자/키 소유자 | 사용자 | T240 | credential provider, `specs/001-meetingmind-core/implement.md` | 5개 finding의 실제 credential을 공급자에서 폐기·재발급한다. | OpenAI/LiveKit 기존 credential의 폐기·재발급 완료를 확인했고 secret 값 없이 완료 사실만 기록했다. |
| T242 | M031 | [x] | git/history-remediation | 저장소 관리자 | 사용자+Codex | T241 | `.gitleaksignore`, `specs/001-meetingmind-core/{plan,implement}.md` | 폐기된 5건만 exact fingerprint로 예외 처리해 공유 브랜치 history rewrite를 피하고 신규 secret 차단을 유지한다. | commit/path/rule/line 단위 5건만 등록했고 `gitleaks git . --redact --no-banner`가 0건으로 통과한다. |
| T243 | M031 | [x] | ci/summary-gate | 사용자 | Codex | T234-T239, T242 | `.github/workflows/ci.yml` | 테스트, migration, 보안 검사, image digest를 Summary에 집계하고 원격 최종 gate를 검증한다. | PR #29에서 전체 선행 job, Secret Scan, Summary와 최종 `CI Gate`가 성공했다. |
| T244 | M031 | [ ] | github/protection | 저장소 관리자 | 사용자 | T243 | GitHub branch ruleset 또는 branch protection 설정 | `main` 직접 push를 금지하고 PR 및 M031 최종 required check 통과를 강제한다. | `CI Gate` context는 생성됐으나 private repository의 현재 GitHub 요금제가 protection API를 403으로 차단한다. Pro 업그레이드 또는 공개 전환 후 적용한다. |
| T245 | M031 | [ ] | verification/docs | 사용자 | Codex | T243, T244 | `.github/**`, `backend/**`, `ai/**`, `frontend/**`, `specs/001-meetingmind-core/{tasks,implement,analyze}.md` | 로컬/원격 CI 실행 결과와 branch protection 상태를 검증하고 closeout한다. | required workflow, Summary/digest/protection을 확인하고 tasks/implement에 결과 또는 미실행 사유를 남긴다. |

### M032: Backend PostgreSQL Runtime Persistence

M032는 T229의 실제 구현 milestone이다. Backend는 관계형 원천 데이터와 권한 선필터를 담당하고, 별도 AI/RAG 담당자는 embedding/vector runtime과 semantic retriever를 담당한다. 두 작업은 기존 AI source contract만 공유하며 같은 파일을 동시에 수정하지 않는다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T246 | M032 | [x] | docs/design | 사용자(Backend 담당) | Codex | T229 | `spec.md`, `clarify.md`, `plan.md`, `tasks.md` | Backend persistence와 AI/RAG 별도 담당의 범위, transaction, profile, 충돌 경계를 확정한다. | T229 실행 단위와 vector 제외 파일 경계가 문서에 일치한다. |
| T247 | M032 | [x] | backend/auth-persistence | 사용자(Backend 담당) | Codex | T246 | `backend/src/main/java/com/meetingmind/demo/auth/**`, `backend/src/test/**` | AuthStore port와 PostgreSQL JDBC adapter를 구현하고 signup/login/refresh/logout를 transaction으로 묶는다. | user/identity/session이 재시작 후 유지되고 refresh rotation과 unique 충돌이 검증된다. |
| T248 | M032 | [x] | backend/workspace-persistence | 사용자(Backend 담당) | Codex | T247 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/test/**` | Space/Meeting/member/participant/join request JDBC adapter와 joinCode hash lookup을 구현한다. | 생성·ACL·승인 데이터가 DB에 유지되고 원문 joinCode가 저장되지 않는다. |
| T249 | M032 | [x] | backend/artifact-persistence | 사용자(Backend 담당) | Codex | T248 | `backend/src/main/java/com/meetingmind/demo/domain/**`, `backend/src/test/**` | Transcript/Speaker/Report/Task/ProjectKnowledge/Audit JDBC adapter를 구현한다. | JSONB source와 산출물 상태가 기존 domain 계약으로 round-trip된다. |
| T250 | M032 | [x] | backend/transactions | 사용자(Backend 담당) | Codex | T249 | `backend/src/main/java/com/meetingmind/demo/{auth,domain}/**`, `backend/src/test/**` | owner transfer, join approval, report confirm, task confirm과 audit를 transaction 경계에 연결한다. | 오류 시 부분 mutation이 없고 current/unique 제약이 유지된다. |
| T251 | M032 | [x] | backend/profile-ai-context | 사용자(Backend 담당) | Codex | T250 | `backend/src/main/java/**`, `backend/src/main/resources/**`, `backend/src/test/**` | test in-memory/local·db JDBC bean을 분리하고 DB 기반 Meeting/Project AI context 선필터를 검증한다. | local/db는 JDBC, test는 in-memory이며 권한 밖 source가 AI request에서 제외된다. |
| T252 | M032 | [x] | backend/integration-test | 사용자(Backend 담당) | Codex | T251 | `backend/src/test/**`, `compose.local.yml` | PostgreSQL repository, 재시작 유지, transaction, migration 회귀 통합 테스트를 실행한다. | JDBC 통합 test, 전체 Backend test, Flyway V1~V10 검증이 통과한다. |
| T253 | M032 | [x] | docs/closeout | 사용자(Backend 담당) | Codex | T252 | `specs/001-meetingmind-core/{tasks,implement,analyze}.md`, `.specify/memory/session-handoff.md` | 실제 변경, 검증, 남은 AI/RAG 경계와 미실행 사유를 기록한다. | T229/M032 상태와 구현 기록이 실제 결과에 맞고 vector 담당 후속 작업이 보존된다. |

### M034: Grounded PostgreSQL RAG Integration

M034는 T230을 shared contract, grounding, data, internal auth, backend scope, worker, retriever, monitoring으로 분해한다. 완료된 M032 Backend persistence 위에서 `ai/grounding`을 먼저 진행할 수 있고, vector migration과 AI 구현은 별도 owner가 파일 경계를 유지한다. `contracts/ai-api.md`, `data-model.md`, `erd.md`, migration은 shared contract/Data owner가 순차 통합한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T264 | M034 | [x] | contracts/rag | 사용자(AI 담당) | Codex | T253 | `specs/001-meetingmind-core/clarify.md`, `plan.md`, `contracts/ai-api.md`, `data-model.md`, `erd.md`, `tasks.md`, `implement.md`, `.specify/memory/session-handoff.md` | 검색 모델, 권한 scope, grounding, generation 갱신, 운영 지표 기준을 shared contract로 확정한다. | Q-010이 닫히고 query mode와 chunk scope, unsupported reason, 재색인 trigger, owner/dependency/검증 순서가 문서에서 일치한다. |
| T265 | M034 | [x] | ai/grounding | 사용자(AI 담당) | Codex | T264 | `ai/app/grounding.py`, `ai/app/main.py`, `ai/app/rag.py`, `ai/tests/**`, `specs/001-meetingmind-core/contracts/ai-api.md` | 공통 evidence gate, structured provider result, citation allowlist와 unsupported reason을 구현한다. | 근거 0건/관련도 미달 LLM 미호출, citation 없음/위조 ID 답변 차단, report/task의 근거 없는 항목 제거가 단위 테스트로 검증된다. |
| T266 | M034 | [x] | ai/structured-output | 사용자(AI 담당) | Codex | T265 | `ai/app/grounding.py`, `ai/app/main.py`, `ai/tests/**`, `specs/001-meetingmind-core/contracts/ai-api.md`, `implement.md` | Responses API strict JSON Schema로 grounded answer/report/task provider 출력을 강제하고 source context를 실행 불가 데이터로 명시한다. | 용어 설명·Meeting AI·Project AI·report·task 5개 provider 경로가 `text.format=json_schema`, `strict=true`, closed schema를 사용하고 legacy ask는 plain text를 유지하며 prompt-injection 경계와 회귀 테스트가 통과한다. |
| T267 | M034 | [x] | data/vector-job | Data owner | Codex | T264 | `backend/src/main/resources/db/migration/V12__*.sql`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/erd.md` | `pg_trgm`, `vector(1536)`, source별 generation unique/XOR, job trigger/hash/retry/lease 필드를 forward migration으로 추가한다. | 빈 DB와 V10 DB upgrade가 모두 통과하고 기존 migration checksum을 바꾸지 않으며 exact cosine과 trigram query가 실행된다. |
| T268 | M034 | [x] | data/job-trigger | Data owner, Backend owner | Codex | T267 | `backend/src/main/resources/db/migration/V12__*.sql`, `backend/src/test/java/com/meetingmind/demo/MigrationIntegrationTest.java`, `specs/001-meetingmind-core/tasks.md`, `implement.md` | 색인 원천 변경과 같은 DB transaction에서 source별 다음 generation EmbeddingJob을 생성하는 trigger를 추가한다. | ProjectKnowledge 생성/수정/복원, MeetingTranscript 완료, 발화자명·회의명 변경, current confirmed report 전환은 source당 한 job을 만들고 segment insert와 candidate/draft 편집은 job을 만들지 않으며 전사 purge는 chunk/link를 즉시 제외한다. |
| T269 | M034 | [x] | shared/internal-auth | Backend owner, AI owner | Codex | T264 | `specs/001-meetingmind-core/contracts/ai-api.md`, `backend/src/main/java/com/meetingmind/demo/service/Http*AiGatewayClient.java`, `backend/src/main/resources/application*.yml`, `ai/app/main.py`, `ai/tests/**`, `backend/src/test/**` | Backend service credential로 `/api/internal/*` 호출을 인증하고 public prototype endpoint와 신뢰 경계를 분리한다. | 인증 없는 직접 호출과 잘못된 credential은 거부되고 정상 Backend gateway만 통과하며 secret과 credential 값은 로그/응답에 노출되지 않는다. |
| T270 | M034 | [x] | backend/search-scope | Backend owner | Codex | T264 | `backend/src/main/java/com/meetingmind/demo/authz/**`, `backend/src/main/java/com/meetingmind/demo/service/**`, `backend/src/main/java/com/meetingmind/demo/dto/ai/**`, `backend/src/test/**` | `AiSearchScopeResolver`가 Meeting scope와 Project allowed meeting scope를 요청마다 확정하도록 구현한다. | guest/revoked/default-deny/owner-admin override와 빈 allowed list가 검증되고 권한 거부 시 AI gateway를 호출하지 않는다. |
| T271 | M034 | [x] | ai/embedding-worker | 사용자(AI 담당) | Codex | T268 | `ai/app/embedding_worker.py`, `ai/app/embedding_provider.py`, `ai/app/repository.py`, `ai/tests/**`, `compose.local.yml` | PostgreSQL job 선점, chunk/embedding 생성, retry/lease, 최신 generation 원자적 교체 worker를 구현한다. | STT segment별 중복 job 없이 trigger가 동작하고 실패 시 기존 active generation 유지, stale job 비활성, 최대 3회 retry가 검증된다. |
| T272 | M034 | [x] | ai/pgvector-retriever | 사용자(AI 담당) | Codex | T267-T271 | `ai/app/rag.py`, `ai/app/repository.py`, `ai/app/main.py`, `ai/tests/**` | exact cosine 후보와 `pg_trgm` 후보를 RRF로 결합하고 Backend scope를 SQL에 강제한다. | Meeting 단일 회의, Project knowledge+allowed meeting union, active/completed generation, cross-space/meeting 차단과 topK가 PostgreSQL 통합 테스트로 검증된다. |
| T273 | M034 | [x] | observability/ai | 사용자(AI 담당) | Codex | T265, T266, T271, T272 | `ai/app/observability.py`, `backend/src/main/java/com/meetingmind/demo/**`, `ai/tests/**`, `backend/src/test/**` | 원문 비노출 구조화 로그에 검색 지연, 근거 수, unsupported reason, citation 실패, job queue/실패 지표를 추가한다. | traceId와 필수 지표가 기록되고 질문/STT/답변/API key는 로그에 없으며 초기 알림 기준을 운영 문서에 남긴다. |
| T274 | M034 | [ ] | frontend/unsupported | Frontend owner | TBD | T265 | `frontend/src/types.ts`, `frontend/src/api/workspace.ts`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx` | nullable `unsupportedReason`을 받아 근거 없음과 일시 오류를 구분해 표시한다. | 기존 응답과 하위 호환되고 unsupported reason별 메시지와 retry 가능 오류가 혼동되지 않는다. |
| T275 | M034 | [ ] | verification/rag | Integration owner | TBD | T265-T274 | `ai/**`, `backend/**`, `frontend/**`, `specs/001-meetingmind-core/test-matrix.md`, `implement.md` | 한국어 근거 있음/없음 평가 질의와 Backend-to-AI-to-PostgreSQL 통합 검증을 수행한다. | false-supported 5% 이하 목표, 검색 p95 1초 목표, scope/citation/generation/internal-auth negative case와 전체 권장 검증 결과가 기록된다. |
| T276 | M034 | [ ] | docs/closeout | Integration owner | TBD | T275 | `specs/001-meetingmind-core/tasks.md`, `implement.md`, `analyze.md`, `feature-implementation-comparison.md`, `.specify/memory/session-handoff.md` | T230/M034 완료 상태, 실제 성능값, 남은 위험과 운영 기준을 원본 문서에 반영한다. | 완료 task만 체크되고 실행 결과 또는 미실행 사유와 다음 shared milestone이 기록된다. |

### M035: Meeting Chat Text Attachment RAG

M035는 아직 구현되지 않은 실시간 회의 채팅과 첨부파일 저장을 먼저 영속 도메인으로 만들고, 텍스트 추출 가능한 파일만 기존 RAG에 연결한다. 이미지와 image-only PDF는 이 milestone의 완료 범위가 아니다. M034 grounding은 M035와 독립적으로 먼저 진행한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T277 | M035 | [x] | decision/attachment-rag | 사용자(AI 담당) | Codex | T264 | `specs/001-meetingmind-core/clarify.md`, `plan.md`, `contracts/ai-api.md`, `tasks.md`, `implement.md`, `.specify/memory/session-handoff.md` | 첨부파일 RAG를 텍스트 임베딩 MVP와 이미지 확장 범위로 분리한다. | TXT/Markdown/텍스트 PDF만 1536차원 텍스트 embedding을 사용하고 visual embedding과 Vision 처리가 현재 범위에서 제외되어 있다. |
| T278 | M035 | [ ] | contracts/meeting-attachment | Integration owner | TBD | T277 | `requirements/*`, `specs/001-meetingmind-core/contracts/*`, `data-model.md`, `erd.md`, `plan.md`, `tasks.md` | MeetingMessage/Attachment, 업로드·다운로드, 허용 MIME/용량, 보존, 권한, citation/page anchor와 embedding source 계약을 확정한다. | requirements, API, ERD, data model이 같은 상태·권한·삭제 규칙을 사용하고 이미지 확장 경계가 명시된다. |
| T279 | M035 | [ ] | backend/meeting-attachment | Backend owner | TBD | T263, T278 | `backend/src/main/java/com/meetingmind/demo/**`, `backend/src/main/resources/db/migration/**`, `backend/src/test/**` | 영속 MeetingMessage/Attachment와 권한 기반 업로드 완료·조회·삭제 API를 구현하고 READY 텍스트 파일의 embedding job을 생성한다. | 회의 접근권, MIME/크기, checksum, 삭제/보존 만료와 job 원자성이 Backend 테스트로 검증된다. |
| T280 | M035 | [ ] | frontend/meeting-chat | Frontend owner | TBD | T279 | `frontend/src/**` | 실시간 회의 채팅에 텍스트 메시지와 지원 파일 업로드·다운로드·처리 상태 UI를 연결한다. | 재접속 후 메시지가 유지되고 권한 오류, 업로드 실패, extraction pending/unsupported 상태가 구분된다. |
| T281 | M035 | [ ] | ai/attachment-extraction | 사용자(AI 담당) | Codex | T271, T278, T279 | `ai/app/**`, `ai/tests/**` | TXT/Markdown/PDF 텍스트 extractor를 공통 결과로 정규화하고 attachment generation chunk를 생성한다. | 이미지·image-only PDF는 임베딩하지 않고 텍스트 파일의 파일명·페이지 anchor·content hash·실패 코드가 검증된다. |
| T282 | M035 | [ ] | ai/attachment-retrieval | 사용자(AI 담당) | Codex | T270, T272, T281 | `ai/app/**`, `ai/tests/**`, `specs/001-meetingmind-core/contracts/ai-api.md` | `meetingAttachment` chunk를 Meeting/Project AI 권한 범위와 grounding allowlist에 연결한다. | 단일 meeting, allowed meeting, 삭제·보존 만료·권한 회수, 다른 회의 파일 차단과 citation이 통합 테스트로 검증된다. |
| T283 | M035 | [ ] | verification/attachment | Integration owner | TBD | T280, T282 | `frontend/**`, `backend/**`, `ai/**`, `specs/001-meetingmind-core/test-matrix.md`, `implement.md` | 업로드부터 검색·출처 표시까지 실제 파일 통합 검증을 수행한다. | TXT/Markdown/텍스트 PDF 성공과 이미지·image-only PDF 미지원, cross-meeting, 삭제, prompt injection negative case가 기록된다. |
| T284 | M035 | [ ] | docs/closeout | Integration owner | TBD | T283 | `specs/001-meetingmind-core/tasks.md`, `implement.md`, `analyze.md`, `.specify/memory/session-handoff.md` | M035 실제 구현 범위와 이미지 확장 backlog를 정리한다. | 완료 task만 체크되고 검증 결과, 미지원 형식, 다음 visual milestone 조건이 기록된다. |

### M036: Frontend Workspace Persistence Hydration

M036는 PostgreSQL에 저장된 Space/Meeting/SpaceMember가 새로고침 후 프론트에 복원되지 않고, 생성 API 실패가 local mock 성공으로 보이는 문제를 수정한다. 기존 Space/Meeting 계약을 사용하며 ERD와 데이터 모델은 변경하지 않는다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T285 | M036 | [x] | backend/meeting-list | Backend owner | Codex | T248 | `backend/src/main/java/com/meetingmind/demo/**`, `backend/src/test/**`, `specs/001-meetingmind-core/contracts/meeting-api.md` | 접근 가능한 회의 목록 API를 기존 권한 정책과 영속 store에 연결한다. | OWNER/ADMIN override와 active participant 범위만 반환하고 실제 active participant가 없으면 `myRole=null`이며 controller test가 통과한다. |
| T286 | M036 | [x] | frontend/workspace-hydration | Frontend owner | Codex | T285 | `frontend/src/App.tsx`, `frontend/src/components/WorkspaceSidebar.tsx`, `frontend/src/pages/{WorkspaceHomePage,ProjectOverviewPage,TeamMembersPage}.tsx`, `frontend/src/styles/app.css`, `frontend/e2e/auth-and-meeting-access.spec.ts` | 로그인 후 Space/Meeting/SpaceMember를 API에서 복원하고 생성 실패·중복 이름·비동기 제출 상태를 화면에 반영한다. | 새로고침 후 DB 데이터가 유지되고 API 실패 시 local phantom 항목을 만들지 않으며 성공할 때만 입력을 초기화한다. |
| T287 | M036 | [x] | verification/docs | Integration owner | Codex | T285, T286 | `backend/**`, `frontend/**`, `specs/001-meetingmind-core/{plan,tasks,implement}.md` | Backend/Frontend 회귀 검증과 문서 closeout을 수행한다. | Backend test, Frontend lint/test/build, `git diff --check` 결과 또는 미실행 사유가 기록된다. |
### M033: Meeting CRUD PostgreSQL End-to-End

M033은 FR-MREG-01/04/05/06/07과 FR-ACL-07을 기준으로 회의 CRUD를 Backend PostgreSQL API와 실제 Frontend 화면까지 연결한다. 한 명의 통합 owner가 shared contract부터 순차 처리하며 AI/RAG 파일은 수정하지 않는다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T254 | M033 | [x] | contracts/decision | 사용자(Backend 담당) | Codex | T253 | `requirements/{functional-requirements-detail,permissions,status-values,policies}.md`, `specs/001-meetingmind-core/{clarify,plan,data-model,erd}.md`, `specs/001-meetingmind-core/contracts/meeting-api.md`, `specs/001-meetingmind-core/test-matrix.md` | 회의 CRUD 권한, 상태 전이, 삭제 의미와 active Meeting 조회 조건을 확정한다. | OWNER/HOST 삭제, ADMIN 기본 거부, SCHEDULED cancel+soft delete, IN_PROGRESS 409, ENDED soft delete, AI/목록 제외 기준이 문서에서 일치한다. |
| T255 | M033 | [x] | data/migration | 사용자(Backend 담당) | Codex | T254 | `specs/001-meetingmind-core/{data-model,erd}.md`, `backend/src/main/resources/db/migration/V11__*.sql`, `backend/src/test/**` | Meeting soft-delete metadata와 active 조회 index를 forward migration으로 추가한다. | V1~V10 checksum을 유지하고 `deleted_at`, `deleted_by`, FK/index가 빈 DB와 V10 upgrade DB에서 검증된다. |
| T256 | M033 | [x] | backend/domain-store | 사용자(Backend 담당) | Codex | T255 | `backend/src/main/java/com/meetingmind/demo/{authz,domain}/**`, `backend/src/test/java/com/meetingmind/demo/**` | ACL-filtered 목록/상세, canonical update, row-locked soft delete와 audit transaction을 store/domain에 구현한다. | 권한 밖·삭제된 회의가 조회/AI 후보에서 제외되고 잘못된 상태 전이와 진행 중 삭제가 mutation 전에 거부되며 실패 시 부분 저장이 없다. |
| T257 | M033 | [x] | backend/read-api | 사용자(Backend 담당) | Codex | T256 | `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/main/java/com/meetingmind/demo/dto/**`, `backend/src/test/java/com/meetingmind/demo/controller/**` | 회의 목록과 상세 target API를 구현한다. | `GET /spaces/{spaceId}/meetings`의 status/from/to와 `GET /meetings/{meetingId}`의 myRole/ACL/400/403/404 응답이 계약과 일치한다. |
| T258 | M033 | [x] | backend/mutation-api | 사용자(Backend 담당) | Codex | T256 | `backend/src/main/java/com/meetingmind/demo/controller/**`, `backend/src/main/java/com/meetingmind/demo/dto/**`, `backend/src/test/java/com/meetingmind/demo/controller/**` | 회의 PATCH와 DELETE target API를 구현한다. | title/schedule/status validation, OWNER/ADMIN/HOST 수정, OWNER/HOST 삭제, ADMIN/EDITOR/VIEWER 삭제 거부, delete audit와 400/403/404/409가 검증된다. |
| T259 | M033 | [x] | backend/verification | 사용자(Backend 담당) | Codex | T257, T258 | `backend/src/test/**`, `specs/001-meetingmind-core/test-matrix.md` | 도메인, controller, JDBC 회귀 테스트를 완성한다. | create/list/detail/update/delete round-trip, ACL negative case, transaction rollback, soft-deleted AI source 제외와 전체 Backend test가 통과한다. |
| T260 | M033 | [x] | frontend/integration | 사용자(Frontend 담당) | Codex | T257, T258 | `frontend/src/{api/workspace.ts,types.ts,App.tsx}`, `frontend/src/pages/{ProjectOverviewPage,WorkspaceHomePage}.tsx`, `frontend/src/styles/app.css` | target Space 회의 화면을 실제 Backend CRUD API에 연결한다. | target 데이터는 mock과 섞이지 않고 생성/수정/삭제 후 Backend 재조회 결과가 표시되며 local-only 성공 경로가 없다. |
| T261 | M033 | [x] | frontend/verification | 사용자(Frontend 담당) | Codex | T260 | `frontend/src/**/*.test.*`, `frontend/e2e/**`, `frontend/package.json` | CRUD loading/error/권한/target-mock 경계와 사용자 흐름을 검증한다. | unit test, lint, build와 생성->수정->삭제 UI E2E가 통과하고 API 실패가 성공으로 표시되지 않는다. |
| T262 | M033 | [x] | verification/postgresql-e2e | 사용자(Backend 담당) | Codex | T259, T261 | `backend/**`, `frontend/**`, `compose.local.yml`, `specs/001-meetingmind-core/implement.md` | local PostgreSQL에서 인증부터 회의 CRUD까지 real API smoke와 Flyway 회귀를 수행한다. | signup -> Space -> create -> list/detail -> patch -> delete -> 목록/상세/AI 제외가 통과하고 V1~V11 migration 및 재조회 영속성이 확인된다. |
| T263 | M033 | [x] | docs/closeout | 사용자(Backend 담당) | Codex | T262 | `specs/001-meetingmind-core/{tasks,implement,analyze,feature-implementation-comparison}.md`, `.specify/memory/session-handoff.md` | 실제 변경, 검증 결과, hard purge/복구/유예 기간 후속 경계를 정리한다. | 완료 상태와 검증 명령이 실제 결과와 일치하고 남은 삭제 보존 작업 또는 미실행 사유가 기록된다. |

### M037: Meeting CRUD Frontend Target Completion

M037은 M033의 Backend CRUD를 선택 회의 상세, 초기 참여자, participant ACL, 캘린더 사용자 흐름까지 연결한다. Frontend 단일 owner가 target API와 demo fallback 경계를 순차 처리하며 Backend·AI 파일은 수정하지 않는다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T288 | M037 | [x] | docs/design | 사용자(Frontend 담당) | Codex | T263 | `requirements/{functional-requirements-detail,permissions,status-values}.md`, `specs/001-meetingmind-core/{plan,tasks}.md`, `specs/001-meetingmind-core/contracts/{meeting-api,space-api}.md` | M033 이후 상세·ACL·캘린더 gap과 target/mock 경계, 계약 밖 필드를 확정한다. | plan에 상세/participant/canonical status/calendar 데이터 소스와 description/endAt 후속 경계가 기록된다. |
| T289 | M037 | [x] | frontend/member-detail-state | 사용자(Frontend 담당) | Codex | T288 | `frontend/src/{App.tsx,types.ts,api/workspace.ts}`, `frontend/src/pages/ProjectOverviewPage.tsx` | target Space member의 userId와 선택 meeting 상세/participant를 Backend에서 조회한다. | target participant state가 meetingId 기준으로 저장되고 상세 선택 시 title/status/schedule/myRole/participant가 재조회된다. |
| T290 | M037 | [x] | frontend/create-update | 사용자(Frontend 담당) | Codex | T289 | `frontend/src/App.tsx`, `frontend/src/pages/{ProjectOverviewPage,WorkspaceHomePage}.tsx` | 초기 참여자 지정 생성, canonical 상태 전이, 참가 코드/URL 표시, 성공 후 입력 초기화와 재조회를 구현한다. | participantUserIds가 전달되고 허용되지 않은 전이를 UI가 제안하지 않으며 실패한 mutation이 성공처럼 보이지 않는다. |
| T291 | M037 | [x] | frontend/participant-acl | 사용자(Frontend 담당) | Codex | T289 | `frontend/src/App.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/styles/app.css` | participant 조회·추가·role 변경·REVOKED 회수를 target API에 연결한다. | mutation 뒤 상세가 재조회되고 403/409와 마지막 HOST 보호가 Backend 결과 기준으로 표시된다. |
| T292 | M037 | [x] | frontend/detail-permission | 사용자(Frontend 담당) | Codex | T289-T291 | `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/styles/app.css` | 상세 loading/error와 myRole/SpaceRole 기반 control, 삭제 확인 상태를 정리한다. | 400/403/404/409가 구분되고 OWNER/HOST 삭제, OWNER/ADMIN/HOST 수정, default-deny가 화면/API에서 일치한다. |
| T293 | M037 | [x] | frontend/calendar | 사용자(Frontend 담당) | Codex | T290 | `frontend/src/App.tsx`, `frontend/src/pages/WorkspaceHomePage.tsx`, `frontend/src/styles/app.css` | ACL-filtered meeting 목록 기반 캘린더 생성·갱신·오류·라우팅을 보강한다. | 생성 성공 후 목록/캘린더가 함께 갱신되고 실패 시 입력과 오류가 유지되며 target meetingId route가 보존된다. |
| T294 | M037 | [x] | frontend/unit | 사용자(Frontend 담당) | Codex | T291-T293 | `frontend/src/**/*.test.*` | detail/member/participant API와 request/error 회귀 단위 테스트를 추가한다. | target route, bearer auth, participantUserIds, participant PATCH와 오류 전파가 검증된다. |
| T295 | M037 | [x] | frontend/e2e-verification | 사용자(Frontend 담당) | Codex | T294 | `frontend/e2e/**`, `frontend/package.json` | 실제 Backend로 상세·초기 참여자·ACL·캘린더와 기존 CRUD 회귀를 검증한다. | unit, lint, build, Playwright가 통과하고 prejoin default-deny, CRUD/ACL mutation과 기존 409 client 오류 전파가 검증된다. |
| T296 | M037 | [x] | docs/closeout | 사용자(Frontend 담당) | Codex | T295 | `specs/001-meetingmind-core/{tasks,implement,analyze,feature-implementation-comparison}.md`, `.specify/memory/session-handoff.md` | 구현·검증 결과와 calendar endpoint/description/endAt 후속 경계를 정리한다. | 완료 task만 체크되고 실제 검증 명령, 남은 Backend 계약 의존성이 문서에 일치한다. |

## Verification

- [x] V001 이전 구현 검증: `cd frontend && npm run build`
- [x] V002 이전 구현 검증: `cd backend && ./gradlew test`
- [x] V003 이전 구현 검증: `cd ai && python3 -m compileall app tests`
- [x] V004 PR #8 문서 검증: `git diff --check`, stale enum/role/source pattern search, task dependency scan
- [ ] V005 주요 화면 라우팅 수동 확인
- [x] V006 Auth policy/CI 기준선 검증: `cd backend && ./gradlew test`, `cd frontend && npm run build`, `cd ai && python3 -m compileall app tests`, `cd ai && python3 -m unittest discover -s tests`, `git diff --check`
- [x] V007 Authz test matrix 문서 검증: `git diff --check`
- [x] V008 AI observability 검증: `cd ai && python3 -m compileall app tests`, `cd ai && ./.venv/bin/python -m unittest discover -s tests`
- [x] V009 AI contract prototype/target split 문서 검증: `git diff --check`
- [x] V010 Backend Meeting AI chat 1차 연동 검증: `cd backend && ./gradlew test`, `git diff --check`
- [x] V011 Frontend Meeting AI Backend 경유 전환 검증: `cd frontend && npm run build`, `git diff --check`
- [x] V012 Meeting AI route `meetingId` 연결 검증: `cd frontend && npm run build`, `git diff --check`
- [x] V013 Backend-to-AI target schema/API 검증: `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai`, `cd ai && ./.venv/bin/python -m compileall app`, `cd backend && ./gradlew test`, `cd frontend && npm run build`, Backend `18080` + AI `18000` real API smoke, `git diff --check`
- [x] V014 Project AI Backend 권한 선필터 검증: AI 19 tests/compile, Backend test, Frontend build, public `200 context-only`, 비멤버 `403 SPACE_ACCESS_DENIED`, internal allowlist 위반 `403 AI_CONTEXT_FORBIDDEN`, `git diff --check`
- [x] V015 Session handoff 분리 검증: `git check-ignore -v .specify/memory/session-handoff.local.md`, 공용 파일 개인 상태 검색, `git diff --check`
- [x] V016 AI report candidate Backend route 검증: AI 24 tests/compile, Backend test, Frontend build, supported candidate 저장 service test, public `200 unsupported`, 비권한 `403 MEETING_ACCESS_DENIED`, `git diff --check`
- [x] V017 Report confirm/current version 검증: Backend current 교체·중복 확정·다른 meeting·VIEWER 테스트, Frontend build, public `404 REPORT_NOT_FOUND`와 비권한 `403 MEETING_ACCESS_DENIED`, AI regression 24 tests/compile, `git diff --check`
- [x] V018 Task candidate Backend route 검증: AI 29 tests/compile, Backend 전체 test, Frontend build, source scope·권한·게스트·중복 확정 test, public `200 context-only`/조회 `200`/없는 후보 `404`/무인증 `401`, `git diff --check`
- [x] V019 AI provider safety 검증: AI 35 tests/compile, provider raw detail 비노출, 설정 누락 고정 `503`, 공통 오류 body, 기본 30초와 보고서 60초 timeout 전달, `git diff --check`
- [x] V020 Backend permission matrix runtime 검증: `cd backend && ./gradlew test`, `git diff --check`
- [x] V021 Meeting join request approval 검증: `cd backend && ./gradlew test` 64건, `git diff --check`
- [x] V022 Frontend meeting access 검증: `cd frontend && npm run build`, `/meeting-access` HTTP 200, `git diff --check`; in-app browser unavailable로 visual smoke 미실행
- [x] V023 로컬 DB 기준선 검증: PostgreSQL 16.14 + pgvector 0.8.5 healthy, Flyway V1~V10 최초 적용/재검증과 기존 V9 upgrade, 25개 도메인 테이블, join request/retention/default/check/partial index, Backend 전체 test, `git diff --check`
- [x] V024 Backend local profile 검증: Compose DB healthy, Hikari `localhost:5434` 연결, Flyway v10 up-to-date, Backend `18080` 기동, `/api/workspace` 200, Backend 전체 test
- [x] V025 Backend 기본 실행 검증: profile 미지정 `./gradlew bootRun`, default `local` 자동 적용, 기존 8080 프로세스를 보존하기 위한 `18080` port override, `/api/workspace` 200
- [x] V026 CI hardening discovery 검증: workflow/build/test/migration/container gap 대조, M031 dependency와 PostgreSQL V1~V10 기준 검토, `git diff --check`
- [x] V027 CI local baseline 검증: Backend `test bootJar`, Frontend lint 오류 0건/unit 6건/build, AI compile/unit 35건, `git diff --check`
- [x] V028 Gitleaks discovery 검증: 44개 커밋, 2개 파일, secret 후보 5건을 값 노출 없이 확인
- [x] V029 OpenAI/LiveKit credential 폐기·재발급, exact fingerprint 5건 적용 후 Gitleaks 0건 검증
- [x] V030 Docker 기반 pgvector migration, Backend/AI image build·digest·Trivy 0건, Playwright 2건 검증
- [x] V031 PR #29 원격 GitHub Actions 전체 job과 `CI Gate`/Summary 성공 검증
- [ ] V032 `main` required `CI Gate`, PR-only, force-push/삭제 금지 검증; private repository 현재 요금제 API 403으로 차단
- [x] V033 Backend PostgreSQL runtime 검증: Auth/Workspace JDBC integration, joinCode hash, ACL 선필터, Transcript/Report/Task/Knowledge/Audit JSONB round-trip, 재시작 후 로그인, 전체 Backend test/bootJar, 빈 pgvector DB Flyway V1~V10
- [x] V034 Meeting CRUD PostgreSQL E2E 검증: Flyway V1~V11 빈 DB/upgrade, Backend 전체/JDBC test, Frontend unit 9건·lint 오류 0건·build, Playwright 3건, local PostgreSQL 재시작 영속성 및 create/list/detail/update/delete/목록·상세·Meeting AI 제외 smoke
- [x] V035 Meeting CRUD Frontend target 완성 검증: Frontend unit 11건, lint 오류 0건(기존 경고 8건), build, 격리 Backend `18083` Playwright 4건; 생성/참가 코드, 상세 participant, role 변경·REVOKED 회수, 캘린더 생성, CRUD와 prejoin default-deny 통과
- [x] V036 AI grounding 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests -v` 43건, `cd ai && ./.venv/bin/python -m compileall app tests`, `git diff --check`
- [x] V037 기존 AI RAG safety 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests`
- [x] V038 AI Structured Outputs 검증: `cd ai && ./.venv/bin/python -m unittest discover -s tests -v` 48건, `cd ai && ./.venv/bin/python -m compileall app tests`, `git diff --check`
- [x] V039 PostgreSQL RAG 통합 검증: PostgreSQL 16 임시 DB V1~V11→V12 migration/trigger/vector/trigram, AI worker generation swap와 Meeting/Project scope DB 통합, AI 60건, Backend 전체 test, Compose 기본/AI profile, `git diff --check`
- [x] V040 Workspace 영속 데이터 복원 검증: Backend 전체 test, PostgreSQL `JdbcWorkspaceStoreIntegrationTest`, Frontend lint 오류 0건/unit 6건/build, Playwright Space/Meeting reload 및 생성 실패 2건, `git diff --check`
- [x] V041 AI RAG 관측성 검증: AI compile 및 PostgreSQL 포함 62 tests, Backend 전체 test와 RequestTrace/gateway header, PostgreSQL migration/workspace integration, Compose 기본/AI profile, Frontend lint 오류 0건/unit 6건/build, Playwright 4건, `git diff --check`

## Notes

- 이 작업 목록은 문서 기준선 생성 이후의 구현 순서를 제안한다.
- Q-001은 Google OAuth와 자체 회원가입/로그인, access/refresh token, `/api/v1/auth/*`, `sessionStorage`, 랜딩 외 보호 route로 결정되었다.
- 실제 권한 구현 전 Q-002는 먼저 결정하는 편이 안전하다.
- 요구사항 기준선 파일은 PR #8의 `agent/requirements-docs-baseline` 브랜치에 포함되어 있다.
