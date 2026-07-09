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
| T043 | M005 | [ ] | backend/verification | TBD | TBD | T036, T037, T038, T039, T040, T041, T042 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend 테스트 또는 최소 검증을 실행하고 결과를 기록한다. | `cd backend && ./gradlew test` 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
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
| T063 | M008 | [ ] | data/retention | TBD | TBD | T061 | `backend/**`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/clarify.md` | retentionPolicy, failureReason, STT 원문 보존 정책 필드를 schema/document에 맞춘다. | STT 기본 보존기간 30일과 7/30일/영구 선택지를 기준으로 nullable/default 전략이 문서화되어 있다. |
| T064 | M008 | [ ] | data/verification | TBD | TBD | T059, T060, T061, T062, T063 | `backend/**`, `specs/001-meetingmind-core/implement.md` | migration 적용 또는 schema 검증 명령을 실행하고 결과를 기록한다. | migration 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T065 | M009 | [ ] | integration/backend | TBD | TBD | T043, T064 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend 전체 검증을 실행한다. | `cd backend && ./gradlew test` 결과가 implement.md에 기록되어 있다. |
| T066 | M009 | [ ] | integration/frontend | TBD | TBD | T050 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend 전체 빌드를 실행한다. | `cd frontend && npm run build` 결과가 implement.md에 기록되어 있다. |
| T067 | M009 | [ ] | integration/ai | TBD | TBD | T057 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI 전체 compile 검증을 실행한다. | `cd ai && python -m compileall app` 결과가 implement.md에 기록되어 있다. |
| T068 | M009 | [ ] | integration/manual | TBD | TBD | T065, T066, T067 | `frontend/**`, `backend/**`, `ai/**`, `specs/001-meetingmind-core/implement.md` | 워크스페이스 홈부터 Meeting AI/Report Agent까지 핵심 흐름을 수동 확인한다. | 통합 수동 검증 결과와 남은 이슈가 implement.md에 기록되어 있다. |
| T069 | M009 | [ ] | docs/closeout | TBD | TBD | T065, T066, T067, T068 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | 완료된 task 상태, 검증 결과, 남은 작업, analysis 상태를 정리한다. | tasks/implement/analyze가 실제 구현 상태와 일치한다. |
| T070 | M010 | [x] | ai/discovery | 사용자 | Codex | - | `ai/**`, `specs/001-meetingmind-core/implement.md` | 현재 AI 기능 코드 경계와 백엔드/프론트엔드 비담당 범위를 확인하고 기록한다. | AI 서버 진입점과 프론트 AI 호출 지점은 읽기 전용으로 확인하고, backend/frontend dependency가 implement.md에 기록되어 있다. |
| T071 | M010 | [x] | contracts/ai | 사용자 | Codex | T070 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/clarify.md` | 용어 설명, 요약/보고서 생성, 회의별/프로젝트별 챗봇, 태스크 추출 AI API prototype 계약을 정의한다. | 각 AI 기능의 request/response, source metadata, 권한 필터 전제, prototype 제한이 문서화되어 있다. |
| T072 | M010 | [x] | ai/term | 사용자 | Codex | T071 | `ai/**` | 회의 중 transcript 용어 설명 prototype API를 구현한다. | Domain Dictionary 우선 설명과 AI fallback 후보 흐름이 AI 서버 기반으로 동작하고, Frontend 연결은 TBD로 남는다. |
| T073 | M010 | [ ] | ai/report | 사용자 | Codex | T071 | `ai/**` | 회의 transcript 기반 요약/보고서 생성 prototype API를 구현한다. | summary, decisions, actionItems, source metadata 후보가 생성되고 화면 연결은 Frontend 담당 TBD로 남는다. |
| T074 | M010 | [ ] | ai/chat | 사용자 | Codex | T071 | `ai/**` | 회의별 챗봇과 프로젝트별 챗봇의 컨텍스트 범위를 AI 서버에서 분리한다. | Meeting AI와 Project AI 요청 타입이 분리되고, Project AI는 prototype context임이 명확하다. |
| T075 | M010 | [ ] | ai/tasks | 사용자 | Codex | T071 | `ai/**` | 회의 종료 시 태스크 후보 추출 prototype API를 구현한다. | assignee, task title, source, confirmation state 후보가 생성되고 저장과 화면 연결은 backend/frontend TBD로 남는다. |
| T076 | M010 | [ ] | ai/safety | 사용자 | Codex | T072, T073, T074, T075 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI 컨텍스트 밖 질문과 민감 데이터 혼입 방지를 최소 검증한다. | 컨텍스트 밖 질문은 확인 불가로 처리되고 검증 결과 또는 미실행 사유가 implement.md에 기록되어 있다. |
| T077 | M010 | [ ] | ai/verification | 사용자 | Codex | T076 | `ai/**`, `specs/001-meetingmind-core/implement.md` | 우리 AI workstream 검증을 실행한다. | `cd ai && python -m compileall app` 또는 `python3 -m compileall app` 결과가 implement.md에 기록되어 있다. |
| T078 | M011 | [x] | contracts/rag | 사용자 | Codex | T071 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | STT 기반 RAG chunk와 embeddingText 형식, source metadata, scope 규칙을 정의한다. | TranscriptSegment 원천 데이터와 EmbeddingChunk 임베딩 데이터의 차이, 회의별/프로젝트별 검색 범위, backend 권한 필터 전제가 문서화되어 있다. |
| T079 | M011 | [x] | ai/rag-types | 사용자 | Codex | T078 | `ai/app/rag.py`, `ai/app/main.py` | AI 서버 내부 RAG 타입과 retriever 경계를 추가한다. | `RagChunk`, `RagSource`, `RagSearchRequest`, `RagSearchResult`, `RagRetriever` 또는 동등한 경계가 생기고 기존 endpoint와 분리된다. |
| T080 | M011 | [x] | ai/rag-builder | 사용자 | Codex | T079 | `ai/app/rag.py` | mock transcript, decisions, actions, project knowledge를 RAG chunk로 변환하는 builder를 구현한다. | 짧은 STT 발화를 여러 segment window로 묶고 `sourceSegmentIds`, speaker, time, sourceType metadata가 유지된다. |
| T081 | M011 | [x] | ai/rag-search | 사용자 | Codex | T080 | `ai/app/rag.py` | pgvector 전환 전 사용할 in-memory retriever를 구현한다. | meeting scope는 단일 meeting chunk만, project scope는 projectKnowledge와 허용된 meeting summary/chunk만 검색하도록 필터가 분리된다. |
| T082 | M011 | [x] | ai/term-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py` | 회의 중 용어 설명 endpoint를 RAG retriever 기반으로 전환한다. | glossary 우선 검색, 선택 발화 주변 transcript window, sources가 retriever 결과 기준으로 응답된다. |
| T083 | M011 | [x] | ai/meeting-chat-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py` | 회의별 챗봇을 RAG scope `meeting`으로 구현한다. | 단일 meetingId의 transcript/decision/action/report chunk만 검색하고 Project 전체 context는 제외된다. |
| T084 | M011 | [x] | ai/project-chat-rag | 사용자 | Codex | T081 | `ai/app/main.py`, `ai/app/rag.py` | 프로젝트별 챗봇을 RAG scope `project`로 구현한다. | ProjectKnowledge와 prototype에서 허용된 meeting chunk만 검색하고, 응답에 공식 지식과 회의 기록 출처가 구분된다. |
| T085 | M011 | [x] | ai/report-rag | 사용자 | Codex | T080 | `ai/app/main.py`, `ai/app/rag.py` | 회의 요약/보고서 생성 prototype API를 RAG chunk/source metadata 구조에 맞춰 구현한다. | summary, decisions, actionItems, markdown draft가 sourceIds와 함께 생성되고 화면 연결은 Frontend 담당 TBD로 남는다. |
| T086 | M011 | [x] | ai/task-candidates | 사용자 | Codex | T085 | `ai/app/main.py` | 회의 종료 태스크 후보 추출 prototype API를 구현한다. | assignee, title, dueDate, sourceIds, confirmationState=`candidate`가 반환되고 저장과 화면 연결은 backend/frontend TBD로 남는다. |
| T087 | M011 | [ ] | ai/rag-safety | 사용자 | Codex | T082, T083, T084, T085, T086 | `ai/**`, `specs/001-meetingmind-core/implement.md` | RAG scope와 컨텍스트 밖 질문 방어를 검증한다. | meeting/project scope 혼입이 없고, 근거 없는 질문은 확인 불가로 처리되며 결과가 implement.md에 기록된다. |
| T088 | M011 | [ ] | ai/rag-verification | 사용자 | Codex | T087 | `ai/**`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/tasks.md` | RAG workstream 검증과 작업 상태를 정리한다. | `cd ai && python3 -m compileall app`, 필요한 endpoint curl 결과 또는 미실행 사유가 기록된다. |
| T089 | M012 | [x] | auth/discovery | 사용자(Auth 담당) | Codex | T024 | `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, `backend/src/main/java/com/meetingmind/demo/**`, `backend/build.gradle`, `specs/001-meetingmind-core/implement.md` | 현재 Frontend Google 로그인 모달, App route 상태, Backend controller/config/dependency 경계를 조사한다. | 현재 인증은 Frontend 모달 표시용이고 Backend auth/security 계층은 없다는 점이 implement.md에 기록되어 있다. |
| T090 | M012 | [x] | auth/contracts | 사용자(Auth 담당) | Codex | T024 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/implement.md` | Auth API target contract를 정의한다: Google credential 교환, 자체 회원가입/로그인, refresh, 현재 사용자 조회, 로그아웃, 인증 오류. | endpoint, request/response, access/refresh token 전달, User/AuthIdentity/AuthSession 필드, 401/409 오류가 문서화되어 있다. |
| T091 | M012 | [x] | auth/backend | 사용자(Auth 담당) | Codex | T090 | `backend/src/main/java/com/meetingmind/demo/auth/**`, `backend/src/main/java/com/meetingmind/demo/config/**`, `backend/src/main/resources/application.yml`, `backend/build.gradle` | Backend에서 Google ID token 검증, 자체 회원가입/로그인, access/refresh token 발급 service/controller/dto를 추가한다. | Google credential 또는 자체 계정을 Backend가 검증하고 access token, refresh token, user profile을 반환한다. 현재 prototype 저장소는 in-memory이며 영속 DB 전환은 후속 Data/Backend 작업이다. |
| T092 | M012 | [x] | auth/frontend | 사용자(Auth 담당) | Codex | T090, T091 | `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, future `frontend/src/auth/**` | Frontend 로그인/회원가입 성공 처리를 Backend auth exchange로 전환하고 access/refresh token 저장/전달 경계를 만든다. | Google credential decode는 표시용으로만 남고, 앱 로그인 상태는 Backend 응답 기준으로 관리되며 token pair는 `sessionStorage`에 저장된다. |
| T093 | M012 | [x] | auth/frontend-guard | 사용자(Auth 담당) | Codex | T092 | `frontend/src/App.tsx`, future `frontend/src/auth/**`, route 대상 page files only if needed | `/spaces`, 회의 입장, 팀원 관리 등 보호 route 또는 action guard를 최소 범위로 적용한다. | 비로그인 사용자는 로그인 모달로 유도되고, 기존 mock fallback route는 깨지지 않는다. |
| T094 | M012 | [ ] | auth/livekit | 사용자(Auth 담당) | Codex | T091, T040 | `backend/src/main/java/com/meetingmind/demo/controller/LiveKitController.java`, `backend/src/main/java/com/meetingmind/demo/service/LiveKitTokenService.java`, auth package | LiveKit token 발급을 인증된 사용자와 회의 접근 권한 확인 뒤 허용하도록 전환한다. | 인증되지 않았거나 회의 권한이 없는 사용자는 LiveKit token을 받을 수 없다. |
| T095 | M012 | [x] | auth/verification | 사용자(Auth 담당) | Codex | T091, T092, T093 | `frontend/**`, `backend/**`, `specs/001-meetingmind-core/implement.md` | Auth workstream 검증을 실행한다. | `cd frontend && npm run build`, `cd backend && ./gradlew test`, AI regression checks, Auth API smoke 결과와 브라우저 자동화 미실행 사유가 implement.md에 기록되어 있다. |
| T096 | M012 | [x] | auth/closeout | 사용자(Auth 담당) | Codex | T095 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md`, `specs/001-meetingmind-core/analyze.md` | Auth 작업 상태, 충돌 여부, 검증 결과, 후속 권한 작업을 정리한다. | T094는 T040 회의 접근 검증 계층 구현 전까지 남은 작업으로 유지하고, Auth 관련 tasks/implement/analyze가 실제 구현 상태와 일치한다. |

## Requirements Baseline Adoption

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T097 | M013 | [x] | requirements/docs | 사용자 | Codex | - | `requirements/INDEX.md`, `requirements/*.md` | Google Sheets 요구사항 정의서를 로컬 Markdown 기준선으로 분할한다. | overview, glossary, permissions, status-values, policies, performance, functional/non-functional 요약과 전체 우선순위 상세 문서가 생성되어 있다. |
| T098 | M013 | [x] | agent/process | 사용자 | Codex | T097 | `AGENTS.md`, `.specify/memory/constitution.md`, `.specify/templates/spec-template.md` | 요구사항 정의서 읽기 전략을 에이전트 지침, constitution, 새 spec 템플릿에 반영한다. | `requirements/INDEX.md` 기반 라우팅과 관련 문서만 읽는 규칙이 명시되어 있다. |
| T099 | M013 | [x] | docs/decision | 사용자 | Codex | T097 | `requirements/glossary.md`, `requirements/permissions.md`, `requirements/status-values.md`, `specs/001-meetingmind-core/clarify.md` | 용어집, 권한 매트릭스, 상태값을 core spec 결정사항에 반영한다. | Q-002, Q-003, Q-004가 Decided 상태로 갱신되어 있다. |
| T100 | M013 | [x] | specs/model | 사용자 | Codex | T099 | `specs/001-meetingmind-core/spec.md`, `specs/001-meetingmind-core/plan.md`, `specs/001-meetingmind-core/data-model.md` | core spec, plan, data-model을 요구사항 기준선과 충돌하지 않도록 갱신한다. | MeetingRole, Meeting status, 회의 게스트, STT 기본 보존기간이 요구사항 기준과 일치한다. |
| T101 | M013 | [x] | contracts | 사용자 | Codex | T100 | `specs/001-meetingmind-core/contracts/api.md` | API contract의 role/status/permission 표현을 요구사항 기준선과 맞춘다. | `participant` role, 구형 Meeting status, AuthSession 명칭이 계약에서 정리되어 있다. |
| T102 | M013 | [ ] | backend/impact | TBD | TBD | T101 | `backend/**`, `specs/001-meetingmind-core/implement.md` | Backend auth/LiveKit/meeting 권한 구현이 요구사항 권한 매트릭스와 충돌하는지 점검한다. | 충돌 목록과 수정 필요 파일이 implement.md에 기록되어 있다. |
| T103 | M013 | [ ] | frontend/impact | TBD | TBD | T101 | `frontend/**`, `specs/001-meetingmind-core/implement.md` | Frontend route guard, 회의 입장, Project AI/Meeting AI UI가 요구사항 권한 범위와 충돌하는지 점검한다. | 화면별 권한 노출/차단 gap이 기록되어 있다. |
| T104 | M013 | [ ] | ai/impact | TBD | TBD | T101 | `ai/**`, `specs/001-meetingmind-core/implement.md` | AI/RAG prototype의 Meeting AI, Project AI, token 전략이 요구사항 기준과 충돌하는지 점검한다. | scope, source, token budget, unsupported 처리 gap이 기록되어 있다. |
| T105 | M013 | [ ] | data/impact | TBD | TBD | T100 | `specs/001-meetingmind-core/data-model.md`, future migration files | DB enum, retention, MeetingGuest/MeetingParticipant 모델을 migration 기준으로 구체화한다. | migration 전 필요한 enum/field 변경 목록이 확정되어 있다. |
| T106 | M013 | [ ] | docs/closeout | TBD | TBD | T101, T102, T103, T104, T105 | `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/analyze.md`, `specs/001-meetingmind-core/implement.md` | 요구사항 반영 후속 영향도를 닫고 다음 구현 milestone으로 넘긴다. | contracts/analyze/implement/tasks가 요구사항 기준선과 실제 구현 상태를 함께 반영한다. |

## Verification

- [x] V001 이전 구현 검증: `cd frontend && npm run build`
- [x] V002 이전 구현 검증: `cd backend && ./gradlew test`
- [x] V003 이전 구현 검증: `cd ai && python3 -m compileall app tests`
- [x] V004 PR #8 문서 검증: `git diff --check`, stale enum/role/source pattern search, task dependency scan
- [ ] V005 주요 화면 라우팅 수동 확인

## Notes

- 이 작업 목록은 문서 기준선 생성 이후의 구현 순서를 제안한다.
- Q-001은 Google OAuth와 자체 회원가입/로그인, access/refresh token, `/api/v1/auth/*`, `sessionStorage`, 랜딩 외 보호 route로 결정되었다.
- 실제 권한 구현 전 Q-002는 먼저 결정하는 편이 안전하다.
- 요구사항 기준선 파일은 PR #8의 `agent/requirements-docs-baseline` 브랜치에 포함되어 있다.
