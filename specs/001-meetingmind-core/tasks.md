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
| M010 | AI prototype 기능 착수 | 백엔드 구현 없이 AI 서버와 프론트 AI 화면에서 용어 설명, 요약/보고서 생성, 챗봇, 태스크 추출 작업 경계가 분리되어 있다. | T070-T077 |
| M011 | AI RAG prototype 기반 구축 | 실제 STT/DB/pgvector 구현 전에도 mock transcript를 RAG chunk로 변환하고, 회의별/프로젝트별 검색 scope와 source metadata가 분리되어 있다. | T078-T088 |

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

기존 T010-T018은 넓은 umbrella task였으므로 직접 배정하지 않는다. 실제 구현은 아래 T024-T069 세부 task 기준으로 진행한다.

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T024 | M003 | [ ] | docs/decision | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/research.md` | Q-001 인증 방식 선택지를 Google OAuth 단독, 자체 JWT, 병행안으로 정리하고 장단점/영향 범위를 기록한다. | Q-001에 선택지, 추천안, 영향 파일이 기록되어 팀 결정만 남은 상태다. |
| T025 | M003 | [ ] | docs/decision | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/data-model.md` | Q-002 회의 권한 등급(host/editor/participant/viewer)의 권한 매트릭스를 작성한다. | 각 role별 조회/수정/AI 질문/speaker 수정 권한이 표로 정리되어 있다. |
| T026 | M003 | [ ] | docs/decision | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/plan.md` | Q-006 Target API Base URL을 `/api/v1` 단일화 또는 prototype 경로 병행 중 하나로 결정할 수 있게 migration plan을 작성한다. | route migration 순서와 frontend 영향이 문서화되어 있다. |
| T027 | M003 | [ ] | docs/decision | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/plan.md` | Q-007 실제 오디오 업로드 방식을 multipart 직접 업로드와 presigned URL 후보로 비교한다. | 파일 크기, S3 연계, 보안 경계, prototype 적용 여부가 기록되어 있다. |
| T028 | M003 | [ ] | docs/analysis | TBD | TBD | T024, T025, T026, T027 | `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/tasks.md` | 결정 결과에 따라 analyze findings와 후속 task dependency를 갱신한다. | Open/Deferred 상태가 최신 결정과 일치하고 막힌 task가 없다. |
| T029 | M004 | [ ] | contracts | TBD | TBD | T026 | `specs/001-meetingmind-core/contracts/api.md` | Space API target contract를 정의한다: Space 목록, Space 상세, Space 멤버 요약, 접근 오류. | endpoint, request/query, response, error code, 권한 규칙이 문서화되어 있다. |
| T030 | M004 | [ ] | contracts | TBD | TBD | T025, T026 | `specs/001-meetingmind-core/contracts/api.md` | Meeting API target contract를 정의한다: 회의 목록, 상세/status, 참여자, 삭제 또는 보존 정책 후보. | Meeting status, participant 권한, 403/404/409 오류가 계약에 반영되어 있다. |
| T031 | M004 | [ ] | contracts | TBD | TBD | T030 | `specs/001-meetingmind-core/contracts/api.md` | Transcript와 speaker API contract를 구현 가능한 필드 수준으로 확정한다. | `speakers`, `segments`, `startMs/endMs`, speaker 수정 권한과 오류가 확정되어 있다. |
| T032 | M004 | [ ] | contracts | TBD | TBD | T030 | `specs/001-meetingmind-core/contracts/api.md` | Report, Action Item, Project Knowledge API target contract 초안을 작성한다. | report summary/decision/action item/source metadata 필드가 정의되어 있다. |
| T033 | M004 | [ ] | contracts/ai | TBD | TBD | T031, T032 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/plan.md` | Meeting AI request/response 계약을 출처 메타데이터 포함 구조로 확장한다. | AI 응답에 source/citation 후보 구조와 근거 없음 처리 규칙이 있다. |
| T034 | M004 | [ ] | docs/contracts | TBD | TBD | T029, T030, T031, T032, T033 | `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/tasks.md` | API 계약 변경 영향도를 점검하고 frontend/backend/ai task dependency를 최신화한다. | analyze의 Contracts vs Data Model과 Permission Rules가 최신 상태다. |
| T035 | M005 | [ ] | backend/discovery | TBD | TBD | T024, T025, T029, T030 | `backend/**` | Backend 현재 패키지 구조, controller/service/dto/test 패턴을 조사한다. | 구현 대상 패키지와 재사용할 기존 패턴이 작업 메모 또는 implement.md에 기록되어 있다. |
| T036 | M005 | [ ] | backend/domain | TBD | TBD | T035 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | User, Space, SpaceMember 도메인 모델/DTO를 기존 backend 패턴에 맞춰 추가한다. | 모델 필드가 data-model.md와 일치하고 compile/test 대상에 포함된다. |
| T037 | M005 | [ ] | backend/domain | TBD | TBD | T036 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | Meeting, MeetingParticipant, MeetingSpeaker 도메인 모델/DTO를 추가한다. | Meeting status enum, role enum, speaker label/displayName이 구현되어 있다. |
| T038 | M005 | [ ] | backend/domain | TBD | TBD | T037 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | TranscriptSegment, MeetingReport, ProjectKnowledge, EmbeddingChunk 모델/DTO를 추가한다. | `startMs/endMs`, source metadata, retention 관련 필드가 반영되어 있다. |
| T039 | M005 | [ ] | backend/security | TBD | TBD | T037 | `backend/**` | Space 접근 검증 service 또는 policy 계층을 추가한다. | SpaceMember 기준 접근 허용/거부 함수와 실패 오류 코드가 있다. |
| T040 | M005 | [ ] | backend/security | TBD | TBD | T039 | `backend/**` | Meeting 접근 검증 service 또는 policy 계층을 추가한다. | MeetingParticipant 기준 조회/수정/AI 컨텍스트 권한 함수가 있다. |
| T041 | M005 | [ ] | backend/api | TBD | TBD | T029, T039 | `backend/**`, `specs/001-meetingmind-core/contracts/api.md` | `/api/workspace` 통합 mock 응답을 Space/Meeting/Report read model로 분리할 backend plan 또는 adapter를 구현한다. | 기존 frontend mock fallback을 깨지 않고 target API 전환 지점이 생긴다. |
| T042 | M005 | [ ] | backend/errors | TBD | TBD | T039, T040 | `backend/**`, `specs/001-meetingmind-core/contracts/api.md` | 공통 오류 응답(`code`, `message`, `fieldErrors`, `traceId`) 처리 방식을 추가한다. | INVALID_REQUEST, ACCESS_DENIED, NOT_FOUND 계열 오류가 일관된 body로 반환된다. |
| T043 | M005 | [ ] | backend/verification | TBD | TBD | T036, T037, T038, T039, T040, T041, T042 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend 테스트 또는 최소 검증을 실행하고 결과를 기록한다. | `cd backend && mvn test` 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T044 | M006 | [ ] | frontend/discovery | TBD | TBD | T029, T030, T031, T032, T033 | `frontend/**` | Frontend route, API client, mock fallback 위치를 조사한다. | 수정할 파일 목록과 기존 패턴이 implement.md에 기록되어 있다. |
| T045 | M006 | [ ] | frontend/types | TBD | TBD | T044 | `frontend/**`, `specs/001-meetingmind-core/contracts/api.md` | API contract에 맞춘 frontend TypeScript type을 정리한다. | Space, Meeting, Transcript, Report, AI response type이 계약과 일치한다. |
| T046 | M006 | [ ] | frontend/state | TBD | TBD | T045 | `frontend/**` | Project/Meeting 선택 상태를 URL param 또는 명시 state 중 하나로 정리한다. | 새로고침/직접 URL 접근 시 선택 상태가 예측 가능하다. |
| T047 | M006 | [ ] | frontend/api | TBD | TBD | T045, T046 | `frontend/**` | Workspace 통합 mock 호출과 target API 호출의 전환 경계를 분리한다. | mock fallback과 실제 API client가 같은 화면에서 혼동되지 않는다. |
| T048 | M006 | [ ] | frontend/ui | TBD | TBD | T047 | `frontend/**` | 개발자용 mock/API 상태 표시를 업무형 UI 톤으로 추가한다. | 사용자는 노출되지 않거나 최소화되고 개발자는 현재 데이터 소스를 확인할 수 있다. |
| T049 | M006 | [ ] | frontend/smoke | TBD | TBD | T046, T047, T048 | `frontend/**` | 워크스페이스 홈, Space 개요, 회의 대기, Meeting AI, Report Agent 이동 흐름을 수동 점검한다. | 주요 route 이동 결과와 발견 이슈가 implement.md에 기록되어 있다. |
| T050 | M006 | [ ] | frontend/verification | TBD | TBD | T045, T046, T047, T048 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend 빌드를 실행하고 결과를 기록한다. | `cd frontend && npm run build` 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T051 | M007 | [ ] | ai/discovery | TBD | TBD | T033, T040 | `ai/**`, `backend/**` | 현재 Meeting AI ask endpoint와 backend 호출 흐름을 조사한다. | AI 서버가 어떤 context shape를 받는지와 변경 파일 목록이 기록되어 있다. |
| T052 | M007 | [ ] | ai/contracts | TBD | TBD | T051 | `specs/001-meetingmind-core/contracts/api.md`, `ai/**`, `backend/**` | Backend-to-AI request shape를 meetingId, transcript, decisions, actions, source metadata 기준으로 확정한다. | AI 서버 입력 계약이 권한 필터 이후 데이터만 받도록 정의되어 있다. |
| T053 | M007 | [ ] | backend/ai | TBD | TBD | T040, T052 | `backend/**` | Backend에서 Meeting AI 컨텍스트 조립 service를 추가한다. | MeetingParticipant 권한 확인 후 transcript/report/action context가 구성된다. |
| T054 | M007 | [ ] | ai/response | TBD | TBD | T052 | `ai/**` | AI 응답에 source/citation metadata 구조를 추가한다. | answer와 sources 또는 citations 후보가 응답되고 근거 없음 처리 규칙이 유지된다. |
| T055 | M007 | [ ] | ai/safety | TBD | TBD | T054 | `ai/**` | 제공 context 밖 질문에 대해 추정하지 않는 방어 테스트 또는 최소 자체 검사를 추가한다. | context 밖 질문이 확인 불가로 처리되는 검증 기록이 있다. |
| T056 | M007 | [ ] | backend/ai | TBD | TBD | T053, T054 | `backend/**`, `ai/**` | Backend와 AI 서버 간 응답 mapping을 연결한다. | frontend가 사용할 AI response shape가 contracts/api.md와 일치한다. |
| T057 | M007 | [ ] | ai/verification | TBD | TBD | T052, T053, T054, T055, T056 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI compile 또는 최소 검증을 실행하고 결과를 기록한다. | `cd ai && python -m compileall app` 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T058 | M008 | [ ] | data/discovery | TBD | TBD | T024, T025, T036, T037, T038 | `backend/**` | Backend의 migration 도구와 DB 설정 방식을 확인한다. | Flyway/Liquibase/기타 방식과 migration 위치가 기록되어 있다. |
| T059 | M008 | [ ] | data/schema | TBD | TBD | T058 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | User, Space, SpaceMember schema 초안을 migration으로 작성한다. | PK/FK/unique/index와 Space membership 관계가 반영되어 있다. |
| T060 | M008 | [ ] | data/schema | TBD | TBD | T059 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | Meeting, MeetingParticipant, MeetingSpeaker schema 초안을 migration으로 작성한다. | Meeting status, participant role, speaker label/displayName 관계가 반영되어 있다. |
| T061 | M008 | [ ] | data/schema | TBD | TBD | T060 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | TranscriptSegment와 MeetingReport schema 초안을 migration으로 작성한다. | `startMs/endMs`, report version, action/decision 저장 방식이 반영되어 있다. |
| T062 | M008 | [ ] | data/schema | TBD | TBD | T061 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | ProjectKnowledge와 EmbeddingChunk schema 초안을 migration으로 작성한다. | Space 기준 ProjectKnowledge와 권한 필터 가능한 meeting chunk 관계가 반영되어 있다. |
| T063 | M008 | [ ] | data/retention | TBD | TBD | T061 | `backend/**`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/clarify.md` | retentionPolicy, failureReason, STT 원문 보존 정책 필드를 schema/document에 맞춘다. | Q-003 미결정 상태에서도 nullable/default 전략이 문서화되어 있다. |
| T064 | M008 | [ ] | data/verification | TBD | TBD | T059, T060, T061, T062, T063 | `backend/**`, `specs/001-meetingmind-core/implement.md` | migration 적용 또는 schema 검증 명령을 실행하고 결과를 기록한다. | migration 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T065 | M009 | [ ] | integration/backend | TBD | TBD | T043, T064 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend 전체 검증을 실행한다. | `cd backend && mvn test` 결과가 implement.md에 기록되어 있다. |
| T066 | M009 | [ ] | integration/frontend | TBD | TBD | T050 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend 전체 빌드를 실행한다. | `cd frontend && npm run build` 결과가 implement.md에 기록되어 있다. |
| T067 | M009 | [ ] | integration/ai | TBD | TBD | T057 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI 전체 compile 검증을 실행한다. | `cd ai && python -m compileall app` 결과가 implement.md에 기록되어 있다. |
| T068 | M009 | [ ] | integration/manual | TBD | TBD | T065, T066, T067 | `frontend/**`, `backend/**`, `ai/**`, `specs/001-meetingmind-core/implement.md` | 워크스페이스 홈부터 Meeting AI/Report Agent까지 핵심 흐름을 수동 확인한다. | 통합 수동 검증 결과와 남은 이슈가 implement.md에 기록되어 있다. |
| T069 | M009 | [ ] | docs/closeout | TBD | TBD | T065, T066, T067, T068 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | 완료된 task 상태, 검증 결과, 남은 작업, analysis 상태를 정리한다. | tasks/implement/analyze가 실제 구현 상태와 일치한다. |
| T070 | M010 | [x] | ai/discovery | 사용자 | Codex | - | `ai/**`, `frontend/src/pages/LiveRoomPage.tsx`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/pages/ReportAgentPage.tsx`, `specs/001-meetingmind-core/implement.md` | 현재 AI 기능 코드 경계와 백엔드 비담당 범위를 확인하고 기록한다. | AI 서버, 프론트 AI 호출 지점, Report Agent 로컬 편집 경계, 백엔드 dependency가 implement.md에 기록되어 있다. |
| T071 | M010 | [x] | contracts/ai | 사용자 | Codex | T070 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/clarify.md` | 용어 설명, 요약/보고서 생성, 회의별/프로젝트별 챗봇, 태스크 추출 AI API prototype 계약을 정의한다. | 각 AI 기능의 request/response, source metadata, 권한 필터 전제, prototype 제한이 문서화되어 있다. |
| T072 | M010 | [x] | ai/term | 사용자 | Codex | T071 | `ai/**`, `frontend/src/pages/LiveRoomPage.tsx`, `frontend/src/types.ts` | 회의 중 transcript 용어 설명 prototype을 구현한다. | Domain Dictionary 우선 설명과 AI fallback 후보 흐름이 mock 또는 AI 서버 기반으로 동작한다. |
| T073 | M010 | [ ] | ai/report | 사용자 | Codex | T071 | `ai/**`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/types.ts` | 회의 transcript 기반 요약/보고서 생성 prototype을 구현한다. | summary, decisions, actionItems, source metadata 후보가 생성되어 Report Agent 화면에 연결된다. |
| T074 | M010 | [ ] | ai/chat | 사용자 | Codex | T071 | `ai/**`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/types.ts` | 회의별 챗봇과 프로젝트별 챗봇의 컨텍스트 범위를 프론트/AI 서버에서 분리한다. | Meeting AI와 Project AI 요청 타입이 분리되고, Project AI는 prototype context임이 명확하다. |
| T075 | M010 | [ ] | ai/tasks | 사용자 | Codex | T071 | `ai/**`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/types.ts` | 회의 종료 시 태스크 후보 추출 prototype을 구현한다. | assignee, task title, source, confirmation state 후보가 생성되고 저장은 backend TBD로 남는다. |
| T076 | M010 | [ ] | ai/safety | 사용자 | Codex | T072, T073, T074, T075 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI 컨텍스트 밖 질문과 민감 데이터 혼입 방지를 최소 검증한다. | 컨텍스트 밖 질문은 확인 불가로 처리되고 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T077 | M010 | [ ] | ai/verification | 사용자 | Codex | T076 | `ai/**`, `frontend/**`, `specs/001-meetingmind-core/implement.md` | 우리 AI workstream 검증을 실행한다. | `cd ai && python -m compileall app`와 필요한 frontend 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T078 | M011 | [x] | contracts/rag | 사용자 | Codex | T071 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | STT 기반 RAG chunk와 embeddingText 형식, source metadata, scope 규칙을 정의한다. | TranscriptSegment 원천 데이터와 EmbeddingChunk 임베딩 데이터의 차이, 회의별/프로젝트별 검색 범위, backend 권한 필터 전제가 문서화되어 있다. |
| T079 | M011 | [x] | ai/rag-types | 사용자 | Codex | T078 | `ai/app/rag.py`, `ai/app/main.py` | AI 서버 내부 RAG 타입과 retriever 경계를 추가한다. | `RagChunk`, `RagSource`, `RagSearchRequest`, `RagSearchResult`, `RagRetriever` 또는 동등한 경계가 생기고 기존 endpoint와 분리된다. |
| T080 | M011 | [~] | ai/rag-builder | 사용자 | Codex | T079 | `ai/app/rag.py` | mock transcript, decisions, actions, project knowledge를 RAG chunk로 변환하는 builder를 구현한다. | 짧은 STT 발화를 여러 segment window로 묶고 `sourceSegmentIds`, speaker, time, sourceType metadata가 유지된다. |
| T081 | M011 | [ ] | ai/rag-search | 사용자 | Codex | T080 | `ai/app/rag.py` | pgvector 전환 전 사용할 in-memory retriever를 구현한다. | meeting scope는 단일 meeting chunk만, project scope는 projectKnowledge와 허용된 meeting summary/chunk만 검색하도록 필터가 분리된다. |
| T082 | M011 | [ ] | ai/term-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py`, `frontend/src/pages/LiveRoomPage.tsx`, `frontend/src/types.ts` | 회의 중 용어 설명 endpoint를 RAG retriever 기반으로 전환한다. | glossary 우선 검색, 선택 발화 주변 transcript window, sources 표시가 retriever 결과 기준으로 동작한다. |
| T083 | M011 | [ ] | ai/meeting-chat-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py`, `frontend/src/pages/MeetingAiPage.tsx`, `frontend/src/types.ts` | 회의별 챗봇을 RAG scope `meeting`으로 구현한다. | 단일 meetingId의 transcript/decision/action/report chunk만 검색하고 Project 전체 context는 제외된다. |
| T084 | M011 | [ ] | ai/project-chat-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py`, `frontend/src/pages/ProjectOverviewPage.tsx`, `frontend/src/types.ts` | 프로젝트별 챗봇을 RAG scope `project`로 구현한다. | ProjectKnowledge와 prototype에서 허용된 meeting chunk만 검색하고, 응답에 공식 지식과 회의 기록 출처가 구분된다. |
| T085 | M011 | [ ] | ai/report-rag | 사용자 | Codex | T080 | `ai/app/main.py`, `ai/app/rag.py`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/types.ts` | 회의 요약/보고서 생성 prototype을 RAG chunk/source metadata 구조에 맞춰 구현한다. | summary, decisions, actionItems, markdown draft가 sourceIds와 함께 생성되어 Report Agent 화면에 연결된다. |
| T086 | M011 | [ ] | ai/task-candidates | 사용자 | Codex | T085 | `ai/app/main.py`, `frontend/src/pages/ReportAgentPage.tsx`, `frontend/src/types.ts` | 회의 종료 태스크 후보 추출 prototype을 구현한다. | assignee, title, dueDate, sourceIds, confirmationState=`candidate`가 반환되고 저장은 backend TBD로 남는다. |
| T087 | M011 | [ ] | ai/rag-safety | 사용자 | Codex | T082, T083, T084, T085, T086 | `ai/**`, `specs/001-meetingmind-core/implement.md` | RAG scope와 컨텍스트 밖 질문 방어를 검증한다. | meeting/project scope 혼입이 없고, 근거 없는 질문은 확인 불가로 처리되며 결과가 implement.md에 기록된다. |
| T088 | M011 | [ ] | ai/rag-verification | 사용자 | Codex | T087 | `ai/**`, `frontend/**`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/tasks.md` | RAG workstream 검증과 작업 상태를 정리한다. | `cd ai && python3 -m compileall app`, `cd frontend && npm run build`, 필요한 endpoint curl 결과 또는 미실행 사유가 기록된다. |

## Verification

- [ ] V001 `cd frontend && npm run build`
- [ ] V002 `cd backend && mvn test`
- [ ] V003 `cd ai && python -m compileall app`
- [ ] V004 주요 화면 라우팅 수동 확인

## Notes

- 이 작업 목록은 문서 기준선 생성 이후의 구현 순서를 제안한다.
- 실제 구현 전 Q-001, Q-002는 먼저 결정하는 편이 안전하다.
- 현재 문서 기준선 파일은 아직 Git 미추적 상태이므로 커밋 전 포함 범위를 확인해야 한다.
