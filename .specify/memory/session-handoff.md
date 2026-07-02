이 문서는 다음 세션에서 MeetingMind 문서/에이전트 작업 맥락을 빠르게 복원하기 위한 Markdown 문서이다.

# Session Handoff

## Current State

- 프로젝트는 `frontend`, `backend`, `ai` 3개 영역으로 구성된 MeetingMind 프로토타입이다.
- 현재까지 실제 제품 코드는 변경하지 않았고, 에이전틱 코딩을 위한 Markdown 문서 체계를 정리했다.
- specs 변경 동반 갱신 규칙, research/data-model/api contract 템플릿, 작업 완료 기준, clarification 우선순위, analysis 추적 상태를 보완했다.
- Git 작업 절차, staging, commit, pull, branch, push 규칙을 `AGENTS.md`에 구체화했다.
- 팀원/에이전트 병렬 작업을 위해 `AGENTS.md`, `plan-template.md`, `tasks-template.md`, `implement-template.md`, core `plan.md`, `tasks.md`, `implement.md`에 병렬 계획/충돌 방지 구조를 추가했다.
- task 작성 시 milestone과 에이전트 친화 작업 단위로 나누도록 `AGENTS.md`, `tasks-template.md`, core `tasks.md`, `implement.md`를 보완했다.
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

## Current Feature Scope

현재 기능 폴더는 `specs/001-meetingmind-core`이다.

- `spec.md`: MeetingMind Core Prototype의 무엇/왜
- `plan.md`: React/Vite, Spring Boot, FastAPI 기반 구현 계획
- `research.md`: 제품 방향과 기술 선택 근거
- `data-model.md`: User, Space, Meeting, Transcript, Report, Knowledge, Embedding 모델 초안
- `contracts/api.md`: `/api/workspace`, `/api/livekit/token`, `/api/meeting-ai/ask` 계약
- `clarify.md`: 로그인, 권한 등급, STT 보존 정책, Project Knowledge 승인 주체 등 미결정 질문
- `tasks.md`: 다음 구현 작업 목록
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
- 병렬 작업 전 `plan.md`에 팀원 수, 에이전트 수, workstream, 충돌 경계, 통합 순서를 기록한다.
- `tasks.md`의 각 작업에는 owner, agent, dependency, 예상 수정 파일을 명시한다.
- `tasks.md`는 milestone과 에이전트가 독립 수행/검증 가능한 task 단위로 작성한다.
- 같은 파일을 여러 팀원/에이전트가 동시에 수정하지 않는다.
- 작업을 완료로 표시할 때는 검증 결과 또는 미실행 사유를 함께 남긴다.
- Meeting AI는 현재 회의 범위만 답해야 한다.
- Project AI는 사용자가 접근 가능한 회의와 공식 Project Knowledge만 검색해야 한다.
- RAG/AI 컨텍스트 구성 전 권한 필터를 먼저 적용한다.
- 현재 문서 기준선 파일은 Git 미추적 상태이므로 커밋 전 포함 범위를 확인한다.

## Next Likely Work

`specs/001-meetingmind-core/tasks.md` 기준 다음 작업은 아래 항목들이다.

- T010: Workspace 통합 mock API를 Space/Meeting/Report 단위 API로 분리할 계획 세분화
- T011: User, Space, SpaceMember, Meeting, MeetingParticipant 도메인 모델 추가
- T012: 회의 접근 권한 검증 계층 추가
- T013: Project/Meeting 선택 상태를 URL 또는 명시적 state로 정리
- T014: mock fallback 사용 여부를 개발자에게 표시할 내부 상태 정리
- T015: Meeting AI 호출을 Backend 권한 필터 이후의 컨텍스트 조립 흐름으로 전환
- T016: AI 응답에 출처 메타데이터 구조 추가
- T017: PostgreSQL/pgvector 스키마 초안을 migration으로 추가
- T018: `clarify.md`의 Open 질문을 결정사항으로 갱신

## Verification Status

- 문서 작업 위주라 앱 빌드는 아직 실행하지 않았다.
- 권장 검증 명령:
  - `cd frontend && npm run build`
  - `cd backend && mvn test`
  - `cd ai && python -m compileall app`
