이 문서는 다음 세션에서 MeetingMind 문서/에이전트 작업 맥락을 빠르게 복원하기 위한 Markdown 문서이다.

# Session Handoff

## Current State

- 프로젝트는 `frontend`, `backend`, `ai` 3개 영역으로 구성된 MeetingMind 프로토타입이다.
- 현재까지 실제 제품 코드는 변경하지 않았고, 에이전틱 코딩을 위한 Markdown 문서 체계를 정리했다.
- specs 변경 동반 갱신 규칙, research/data-model/api contract 템플릿, 작업 완료 기준, clarification 우선순위, analysis 추적 상태를 보완했다.
- Git 작업 절차, staging, commit, pull, branch, push 규칙을 `AGENTS.md`에 구체화했다.
- 팀원/에이전트 병렬 작업을 위해 `AGENTS.md`, `plan-template.md`, `tasks-template.md`, `implement-template.md`, core `plan.md`, `tasks.md`, `implement.md`에 병렬 계획/충돌 방지 구조를 추가했다.
- task 작성 시 milestone과 에이전트 친화 작업 단위로 나누도록 `AGENTS.md`, `tasks-template.md`, core `tasks.md`, `implement.md`를 보완했다.
- API_SPEC 초안에서 공통 API 규칙, Meeting status, 오류 응답, transcript/speaker 계약 후보를 MeetingMind 기준으로 선별 반영했다.
- 기존 umbrella task T010-T018을 즉시 배정 가능한 상세 task T024-T069로 분해했다.
- Google Sheets 요구사항 정의서를 `requirements/*` Markdown 기준선으로 분할했다.
- 기능/비기능 요구사항은 요약 카탈로그와 전체 우선순위 상세 문서로 나눴다.
- `AGENTS.md`와 constitution에 `requirements/INDEX.md` 기반 요구사항 읽기 라우팅을 추가했다.
- 용어집, 권한 매트릭스, 상태값을 core spec, plan, data-model, contracts, clarify, tasks, analyze, implement에 반영했다.
- Q-002 회의 권한 등급, Q-003 STT 기본 보존기간, Q-004 Project Knowledge 주체는 결정 완료 상태다.
- `.claude` 폴더는 삭제했다. Claude 전용 commands/skills 대신 도구 중립 구조를 사용한다.
- `CLAUDE.md`는 Claude Code 호환용 포인터로만 유지한다.

## Always Read

다음 세션 시작 시 아래 3개 파일을 먼저 읽는다.

- `AGENTS.md`: 공통 에이전트 운영 규칙, 7개 개념 계층, Git 협업 지침
- `AGENT.md`: 사용자가 직접 작성한 코딩 에이전트 사고 규칙과 구현 판단 순서
- `.specify/memory/constitution.md`: MeetingMind 불변 제품 원칙

## Current Document Model

7개 개념 계층을 유지하되, 실제 파일 수는 압축한다.

1. Constitution: `.specify/memory/constitution.md`
2. Reasoning: `AGENT.md`, `AGENTS.md`
3. Decision: `specs/<feature>/research.md`, `plan.md`
4. Process: `AGENTS.md`
5. Skills: `.specify/skills/*`
6. Spec: `specs/<feature>/spec.md`
7. Tasks: `specs/<feature>/tasks.md`

요구사항 정의서는 별도 로컬 스냅샷 `requirements/*`로 관리한다. 기능 작업 시 `requirements/INDEX.md`를 먼저 읽고 관련 요구사항 문서만 추가로 읽는다.

## Current Feature Scope

현재 기능 폴더는 `specs/001-meetingmind-core`이다.

- `spec.md`: MeetingMind Core Prototype의 무엇/왜
- `plan.md`: React/Vite, Spring Boot, FastAPI 기반 구현 계획
- `research.md`: 제품 방향과 기술 선택 근거
- `data-model.md`: User, Space, Meeting, Transcript, Report, Knowledge, Embedding 모델 초안
- `contracts/api.md`: 현재 prototype API, 공통 API 규칙, 오류 응답, Meeting status, transcript/speaker target contract, async STT future draft
- `clarify.md`: 로그인, 권한 등급, STT 보존 정책, Project Knowledge 승인 주체 등 결정/미결정 질문
- `tasks.md`: milestone별 상세 구현 작업 목록. 실제 배정은 T024-T106 기준으로 진행한다.
- `analyze.md`: 현재 문서 간 일관성 검증
- `implement.md`: 문서 체계 정리 구현 기록
- `.specify/templates/research-template.md`: 결정 근거 문서 템플릿
- `.specify/templates/data-model-template.md`: 데이터 모델/권한 규칙 템플릿
- `.specify/templates/api-contract-template.md`: API 계약 템플릿
- `.specify/templates/plan-template.md`: 기술 결정과 병렬 작업 계획 템플릿
- `.specify/templates/tasks-template.md`: milestone, owner, agent, dependency, files, completion 기반 작업 목록 템플릿
- `.specify/templates/implement-template.md`: 작업 배정, 충돌 처리, 통합 결과 템플릿

## Important Rules

- 작업 시작 전 `git status --short`를 확인한다.
- 기존 사용자/팀원 변경은 되돌리지 않는다.
- 커밋은 요청받았을 때만 만든다.
- staging, commit, pull, merge, rebase, branch, push는 `AGENTS.md`의 Git 작업 절차를 따른다.
- 새 기능/코드/의존성 판단은 `AGENT.md`의 구현 판단 순서를 따른다.
- specs 변경 시 `AGENTS.md`의 동반 갱신 규칙에 따라 관련 문서 영향 여부를 확인한다.
- 새 기능/계약/데이터/AI 작업 전 `requirements/INDEX.md`에서 필요한 요구사항 문서를 확인한다.
- 도메인 용어는 `requirements/glossary.md`, 권한은 `requirements/permissions.md`, 상태값은 `requirements/status-values.md`를 기준으로 한다.
- 병렬 작업 전 `plan.md`에 팀원 수, 에이전트 수, workstream, 충돌 경계, 통합 순서를 기록한다.
- `tasks.md`의 각 작업에는 owner, agent, dependency, 예상 수정 파일을 명시한다.
- `tasks.md`는 milestone과 에이전트가 독립 수행/검증 가능한 task 단위로 작성한다.
- 같은 파일을 여러 팀원/에이전트가 동시에 수정하지 않는다.
- 작업을 완료로 표시할 때는 검증 결과 또는 미실행 사유를 함께 남긴다.
- Meeting AI는 현재 회의 범위만 답해야 한다.
- Project AI는 사용자가 접근 가능한 회의와 공식 Project Knowledge만 검색해야 한다.
- RAG/AI 컨텍스트 구성 전 권한 필터를 먼저 적용한다.
- 요구사항 기준선 반영 변경은 PR #8의 `agent/requirements-docs-baseline` 브랜치에 커밋되어 원격 push되어 있다.
- 현재 로컬에는 개인 IDE 설정인 `.idea/`만 Git 미추적 상태로 남아 있으며 PR에는 포함하지 않는다.

## Next Likely Work

`specs/001-meetingmind-core/tasks.md` 기준 다음 작업은 상세 task 기준으로 진행한다.

- M013/T102-T106: backend/frontend/ai/data가 요구사항 기준선과 충돌하는지 영향도 점검
- M003/T026-T028: Target API base URL, 오디오 업로드 방식 결정 준비와 analysis 갱신
- M004/T029-T034: Space/Meeting/Transcript/Report/AI API 계약 세분화
- M005/T035-T043: Backend 구조 조사, 도메인 모델, 권한 정책, 오류 응답, backend 검증
- M006/T044-T050: Frontend 구조 조사, API type, 선택 상태, mock fallback, frontend 검증
- M007/T051-T057: Meeting AI context 조사, backend-to-ai 계약, source metadata, AI 검증
- M008/T058-T064: migration 도구 조사, schema 초안, retention/RAG 필드, data 검증
- M009/T065-T069: backend/frontend/ai 통합 검증, 수동 흐름 확인, closeout 문서 갱신

## Requirements Definition Follow-up

요구사항 정의서는 Google Sheet를 원본 기준으로 두고, 프로젝트 폴더 내부의 `requirements/*` Markdown 스냅샷을 에이전트가 읽는 공식 로컬 기준으로 사용한다. 루트의 로컬 `요구사항정의서.md`는 제거되었다.

### Completion Criteria

- 완료: Google Sheet 요구사항 정의서에 용어집, 권한 매트릭스, 상태값이 추가되어 있다.
- 완료: Google Sheet 요구사항을 도메인별 Markdown으로 변환해 `requirements/`에 저장했다.
- 완료: 기능/비기능 P2를 포함한 전체 우선순위 상세 요구사항을 별도 Markdown으로 저장했다.
- 완료: 정책, 성능지표, 용어집, 상태값 문서는 Google Sheets의 전체 컬럼을 보존하는 상세 스냅샷으로 보강했다.
- 완료: 에이전트는 전체 요구사항을 항상 읽지 않고, 요구사항 INDEX를 통해 현재 작업과 관련된 문서만 읽는다.
- 완료: Constitution은 원칙만 담고, 실제 읽기 라우팅은 `AGENTS.md`와 `requirements/INDEX.md`가 담당한다.
- 남음: Backend/Frontend/AI/Data 구현과 세부 API 계약이 새 기준선과 충돌하는지 T102-T105에서 점검한다.

### Task List

| ID | Area | Task | Output |
| --- | --- | --- | --- |
| REQ-001 | requirements | Google Sheet에 유비쿼터스 랭귀지 탭/섹션을 추가한다. | Done |
| REQ-002 | requirements | Google Sheet에 권한 매트릭스 탭/섹션을 추가한다. | Done |
| REQ-003 | requirements | Google Sheet 요구사항을 도메인별 Markdown으로 변환할 대상 구조를 확정한다. | Done: `requirements/` |
| REQ-004 | requirements | 요구사항 INDEX 문서를 만든다. | Done: `requirements/INDEX.md` |
| REQ-005 | docs/process | `constitution.md`에 관련 요구사항 확인 원칙만 추가한다. | Done |
| REQ-006 | docs/process | `AGENTS.md`의 Read By Task 규칙을 요구사항 INDEX 기반으로 갱신한다. | Done |
| REQ-007 | templates | `spec-template.md`에 Requirement Sources 섹션을 추가한다. | Done |
| REQ-008 | specs/core | `specs/001-meetingmind-core/spec.md`에 Requirement Sources를 추가한다. | Done |
| REQ-009 | specs/core | `data-model.md`를 유비쿼터스 랭귀지와 권한 매트릭스 기준으로 재검토한다. | Done |
| REQ-010 | specs/core | `contracts/api.md` 분리 필요성을 평가한다. | Partial: 기본 충돌 정리, 분리 평가는 후속 |
| REQ-011 | specs/core | `feature-implementation-comparison.md`를 요구사항 정의서 기준 추적 문서로 갱신한다. | Done |
| REQ-012 | validation | 요구사항 문서 구조 변경 후 참조와 중복을 점검한다. | Done: P2 포함, 요약/상세 문서 라우팅, 정책/성능/용어집/상태값 상세 반영, role enum 표기 정합성 확인 |

## Verification Status

- PR #8 문서 변경은 `git diff --check`, stale enum/role/source pattern search, task dependency scan으로 검증했다.
- 앱 빌드/테스트는 이번 docs-only PR에서 새로 실행하지 않았다.
- 권장 검증 명령:
  - `cd frontend && npm run build`
  - `cd backend && ./gradlew test`
  - `cd ai && python3 -m compileall app`
