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
| M001 | 문서/에이전트 기준선 확정 | Spec Kit, 헌법, 에이전트 지침, 템플릿, QA 체크리스트가 작성되어 있다. | T001-T006, T019-T022 |
| M002 | 현재 프로토타입 계약 기준선 문서화 | Frontend, Backend, AI의 현재 route/API/컨텍스트 제한이 문서화되어 있다. | T007-T009 |
| M003 | 구현 전 제품/권한 결정 확정 | 인증 방식, 회의 권한 등급 등 구현 차단 질문이 결정되어 있다. | T018 |
| M004 | Backend 권한 기반 도메인/API 기반 구축 | Space/Meeting/Report API 분리 계획, 도메인 모델, 회의 권한 검증 계층이 준비되어 있다. | T010-T012 |
| M005 | Frontend 상태와 mock fallback 정리 | Project/Meeting 선택 상태와 mock fallback 표시 정책이 정리되어 있다. | T013-T014 |
| M006 | Meeting AI 권한 필터 이후 컨텍스트 전환 | Meeting AI가 Backend 권한 필터 이후의 컨텍스트와 출처 메타데이터를 사용하도록 전환되어 있다. | T015-T016 |
| M007 | Data/RAG 기반 준비 | PostgreSQL/pgvector 스키마 초안이 migration으로 준비되어 있다. | T017 |

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

## Current Prototype Baseline

- [x] T007 [frontend] 기존 화면 라우트와 mock fallback 범위를 문서화한다.
- [x] T008 [backend] `/api/workspace`, `/api/livekit/token` 현재 계약을 문서화한다.
- [x] T009 [ai] `/api/meeting-ai/ask` 현재 계약과 컨텍스트 제한을 문서화한다.

## Next Implementation Tasks

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T018 | M003 | [ ] | docs | TBD | TBD | - | `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/plan.md` | `clarify.md`의 Open 질문을 결정사항으로 갱신한다. | Q-001, Q-002가 결정되고 후속 문서 영향이 표시되어 있다. |
| T010 | M004 | [ ] | backend/contracts | TBD | TBD | T018 | `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/plan.md` | Workspace 통합 mock API를 Space/Meeting/Report 단위 API로 분리할 계획을 세분화한다. | 분리 대상 endpoint, request/response, migration path가 문서화되어 있다. |
| T011 | M004 | [ ] | backend | TBD | TBD | T018 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | User, Space, SpaceMember, Meeting, MeetingParticipant 도메인 모델을 추가한다. | 도메인 모델과 data-model 문서가 일치한다. |
| T012 | M004 | [ ] | backend | TBD | TBD | T011 | `backend/**` | 회의 접근 권한 검증 계층을 추가한다. | 회의 접근 권한 검증 지점과 실패 동작이 구현/문서화되어 있다. |
| T013 | M005 | [ ] | frontend | TBD | TBD | T010 | `frontend/**` | Project/Meeting 선택 상태를 URL 또는 명시적 state로 정리한다. | 선택 상태가 새로고침/이동 후에도 예측 가능하게 유지된다. |
| T014 | M005 | [ ] | frontend | TBD | TBD | T010 | `frontend/**` | mock fallback 사용 여부를 개발자에게 표시할 내부 상태를 정리한다. | mock/API 상태가 개발 중 혼동되지 않게 구분된다. |
| T015 | M006 | [ ] | ai/backend | TBD | TBD | T012 | `ai/**`, `backend/**` | Meeting AI 호출을 Backend 권한 필터 이후의 컨텍스트 조립 흐름으로 전환한다. | AI 서버가 권한 필터 이후 컨텍스트만 받는 흐름이 문서화/구현되어 있다. |
| T016 | M006 | [ ] | ai/contracts | TBD | TBD | T015 | `ai/**`, `specs/001-meetingmind-core/contracts/api.md` | AI 응답에 출처 메타데이터 구조를 추가한다. | 응답 계약에 출처 메타데이터가 포함되고 근거 없음 처리가 유지된다. |
| T017 | M007 | [ ] | data/backend | TBD | TBD | T011 | `backend/**`, `specs/001-meetingmind-core/data-model.md` | PostgreSQL/pgvector 스키마 초안을 migration으로 추가한다. | 초기 migration 초안과 권한/RAG 관련 테이블 관계가 준비되어 있다. |

## Verification

- [ ] V001 `cd frontend && npm run build`
- [ ] V002 `cd backend && mvn test`
- [ ] V003 `cd ai && python -m compileall app`
- [ ] V004 주요 화면 라우팅 수동 확인

## Notes

- 이 작업 목록은 문서 기준선 생성 이후의 구현 순서를 제안한다.
- 실제 구현 전 Q-001, Q-002는 먼저 결정하는 편이 안전하다.
- 현재 문서 기준선 파일은 아직 Git 미추적 상태이므로 커밋 전 포함 범위를 확인해야 한다.
