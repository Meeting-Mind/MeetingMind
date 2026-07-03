# AGENTS.md

MeetingMind에서 Codex, Claude Code, 기타 코딩 에이전트가 공통으로 따르는 표준이다.

## 지침 우선순위

1. 시스템/개발자 지침
2. `AGENTS.md`
3. `AGENT.md`
4. `.specify/memory/constitution.md`
5. 현재 작업 중인 `specs/*`
6. 인접 코드의 기존 패턴

## 프로젝트 요약

MeetingMind는 회의 기록을 프로젝트 지식 자산으로 전환하는 AI 협업 플랫폼이다. 핵심은 개별 회의의 STT/보고서 생성이 아니라, Space 단위의 지식 축적과 권한 기반 AI 검색이다.

현재 저장소는 세 영역으로 구성된다.

- `frontend`: React + Vite + TypeScript UI
- `backend`: Spring Boot 3 + Java 21 API
- `ai`: FastAPI 기반 Meeting AI 응답 서비스

## 운영 계층

MeetingMind는 7개 개념 계층을 사용하되, 물리 파일 수는 작게 유지한다.

| Concept Layer | Meaning | Physical Location | Read Timing |
| --- | --- | --- | --- |
| Layer 1 Constitution | 무엇을 믿는가 | `.specify/memory/constitution.md` | 항상 |
| Layer 2 Reasoning | 어떻게 생각하는가 | `AGENT.md`, `AGENTS.md` | 항상 |
| Layer 3 Decision | 무엇을 선택하는가 | `specs/<feature>/research.md`, `plan.md` | 기능 작업 시 |
| Layer 4 Process | 어떻게 실행하는가 | `AGENTS.md` | 항상 |
| Layer 5 Skills | 도메인별 절차 | `.specify/skills/*` | 필요할 때만 |
| Layer 6 Spec | 무엇을 만들 것인가 | `specs/<feature>/spec.md` | 기능 작업 시 |
| Layer 7 Tasks | 지금 무엇을 할 것인가 | `specs/<feature>/tasks.md` | 구현 작업 시 |

### Always Read

- `AGENTS.md`: 프로젝트 공통 실행, Process, Git 협업 지침
- `AGENT.md`: 사용자가 직접 작성한 코딩 에이전트 행동 규칙
- `.specify/memory/constitution.md`: 프로젝트 불변 원칙

### Read By Task

- `.specify/templates/*`: 새 스펙/계획/작업 문서를 만들 때
- `.specify/skills/*`: 특정 도메인 절차가 필요할 때
- `.specify/memory/session-handoff.md`: 이전 세션 작업 맥락을 이어받을 때
- `specs/<feature>/spec.md`: 기능의 무엇/왜를 확인할 때
- `specs/<feature>/research.md`, `plan.md`: 결정 근거와 구현 방식을 확인할 때
- `specs/<feature>/data-model.md`, `contracts/*`: 데이터/API를 바꿀 때
- `specs/<feature>/tasks.md`: 실제 구현 작업을 진행할 때
- `specs/<feature>/analyze.md`, `implement.md`: 검증/이전 구현 기록을 확인할 때

`CLAUDE.md`는 Claude Code 호환용 포인터로만 유지한다.

## 불변 원칙

- Meeting AI는 현재 회의 범위만 답한다.
- Project AI는 사용자가 접근 가능한 회의와 공식 Project Knowledge만 검색한다.
- 권한 필터는 RAG 검색 전 단계에서 적용한다.
- AI 응답은 근거 출처를 제공하고, 근거가 없으면 모른다고 답한다.
- 음성 원본은 기본 장기 보관하지 않는다.
- mock 데이터는 데모와 UI 검증용으로만 사용한다.

## 코드 작성 기준

- 작업 시작 시 `AGENTS.md`, `AGENT.md`, `.specify/memory/constitution.md`를 기본 규칙으로 읽는다.
- 필요한 경우에만 feature 문서와 skill 문서를 추가로 읽는다.
- 새 기능, 새 코드, 새 의존성 판단은 `AGENT.md`의 구현 판단 순서를 따른다.
- 변경 전 관련 코드를 먼저 읽고 실제 흐름을 확인한다.
- 기존 패턴과 파일 경계를 우선한다.
- 새 의존성은 명확한 필요와 문서화된 이유가 있을 때만 추가한다.
- 외부 입력은 신뢰하지 않고 검증한다.
- 보안/권한/데이터 보존 정책은 UI 편의보다 우선한다.
- 프론트엔드 문구는 제품 도메인에 맞게 업무형 협업 도구 톤을 유지한다.
- 기능 변경은 `spec.md`의 사용자 가치와 `plan.md`의 기술 결정에 연결되어야 한다.

## Spec Kit 작업 방식

- 애매한 요구는 `specs/*/clarify.md`에 질문과 결정사항을 남긴 뒤 구현한다.
- 구현 전 `tasks.md`에서 작업 단위를 확인한다.
- 구현 후에는 `tasks.md` 체크리스트와 검증 결과를 갱신한다.
- `analyze.md`는 읽기 전용 검증 기록으로 사용한다.
- 검증 중 발견한 수정은 `spec.md`, `plan.md`, `tasks.md`에 별도 반영한 뒤 구현한다.

### Specs 변경 동반 갱신 규칙

- `spec.md`의 사용자 가치, 범위, 요구사항이 바뀌면 `plan.md`, `tasks.md`, `analyze.md` 영향 여부를 확인한다.
- `plan.md`의 기술 결정, API, 데이터 모델, 보안 방식이 바뀌면 `research.md`, `data-model.md`, `contracts/*`, `tasks.md` 영향 여부를 확인한다.
- `contracts/*` 또는 `data-model.md`가 바뀌면 권한 규칙, 입력 검증, AI 컨텍스트 범위가 `spec.md`, `plan.md`, `tasks.md`와 일치하는지 확인한다.
- Open 질문이 구현 판단을 막으면 `clarify.md`에 질문을 추가하거나 기존 질문을 결정사항으로 갱신한 뒤 구현한다.
- 작업을 완료로 표시하려면 관련 검증 결과 또는 미실행 사유를 `tasks.md`와 `implement.md`에 남긴다.
- `analyze.md`에서 발견한 문제는 `analyze.md`만 고치지 않고 원본 문서에 반영한 뒤 추적 상태를 남긴다.

## 병렬 작업 지침

- 팀원과 에이전트가 동시에 작업할 때는 `plan.md`에 팀원 수, 에이전트 수, workstream, 충돌 경계, 통합 순서를 먼저 기록한다.
- 병렬 작업 단위는 가능한 한 `frontend`, `backend`, `ai`, `data`, `docs`, `contracts`처럼 파일 경계가 분리되는 영역으로 나눈다.
- 각 작업은 `tasks.md`에 owner, agent, dependency, 예상 수정 파일을 명시한 뒤 시작한다.
- 같은 파일을 두 명 이상 또는 두 에이전트 이상이 동시에 수정하지 않는다. 불가피하면 한 명을 파일 owner로 지정하고 나머지는 owner를 통해 변경한다.
- API 계약, 데이터 모델, 공통 타입, 권한 규칙, 마이그레이션처럼 여러 영역에 영향을 주는 파일은 shared contract로 취급하고 먼저 합의한다.
- shared contract가 바뀌면 관련 workstream은 구현을 멈추고 `plan.md`, `tasks.md`, `contracts/*`, `data-model.md` 영향 여부를 갱신한다.
- 에이전트 여러 개를 사용할 때는 에이전트 하나가 하나의 task group만 맡는다. 한 에이전트가 다른 에이전트의 작업 파일을 임의로 수정하지 않는다.
- 충돌 가능성이 높은 통합 작업은 병렬 작업이 아니라 integration owner가 순차 처리한다.
- 구현 완료 후 `implement.md`에 실제 작업 배정, 충돌 여부, 통합 순서, 검증 결과를 남긴다.

### Task 작성 규칙

- `tasks.md`는 먼저 milestone으로 사용자 가치나 통합 목표를 나누고, 그 아래에 실행 가능한 task를 둔다.
- milestone은 여러 task가 모여 도달하는 검증 가능한 중간 목표여야 한다.
- task는 한 명 또는 한 에이전트가 독립적으로 수행하고 검증할 수 있는 단위로 쪼갠다.
- task는 가능한 한 하나의 area와 제한된 파일 범위를 가진다.
- shared contract, migration, 공통 타입, 권한 규칙 변경은 별도 task로 분리한다.
- task가 너무 크면 planning, contract, implementation, verification task로 나눈다.
- task마다 milestone, owner, agent, dependency, 예상 수정 파일, 완료 기준을 드러낸다.
- 여러 에이전트가 병렬 처리하기 쉽도록 dependency가 없는 task와 순차 처리 task를 구분한다.

## Git 협업 지침

- 작업 시작 전 `git status --short`로 현재 변경 상태를 확인한다.
- 사용자나 팀원이 만든 기존 변경을 임의로 되돌리지 않는다.
- `git reset --hard`, `git checkout -- <file>`, 강제 push, rebase, branch 삭제 같은 파괴적 작업은 명시 요청 없이는 하지 않는다.
- 커밋은 요청받았을 때만 만든다. 커밋 전에는 변경 범위를 확인하고 요청과 무관한 파일을 포함하지 않는다.
- 커밋 메시지는 작업 의도가 드러나게 작성한다. 예: `docs: add agent git collaboration rules`
- 같은 파일에 사용자 변경과 에이전트 변경이 섞여 있으면, 먼저 내용을 읽고 보존하면서 필요한 부분만 수정한다.
- pull, merge, rebase 중 충돌이 나면 자동으로 임의 해결하지 말고 충돌 파일과 선택지를 보고한다.
- 브랜치 전환은 현재 변경사항이 안전하게 보존되는지 확인한 뒤 수행한다.
- secret, `.env`, 빌드 산출물, 개인 IDE 설정은 커밋하지 않는다.
- 팀 협업에서는 작은 단위로 커밋하고, 스펙/작업 문서와 코드 변경의 관계가 드러나게 한다.

### Git 작업 절차

1. 시작 전 `git status --short`로 tracked/untracked 변경을 확인한다.
2. 관련 파일을 읽어 기존 사용자 변경과 작업 요청의 경계를 파악한다.
3. 변경 후 `git status --short`와 필요한 경우 `git diff`로 실제 수정 범위를 확인한다.
4. 검증 명령을 실행했으면 결과를 기록하고, 실행하지 못했으면 이유를 남긴다.
5. 커밋 요청이 있을 때만 staging, commit, push를 진행한다.

### Staging 규칙

- `git add`는 요청 범위에 해당하는 파일만 대상으로 한다.
- `git add .` 또는 전체 디렉터리 staging은 변경 범위가 모두 요청과 일치할 때만 사용한다.
- 미추적 파일은 생성 의도와 포함 범위를 확인한 뒤 staging한다.
- secret, `.env`, 빌드 산출물, 개인 IDE 설정, 임시 파일은 staging하지 않는다.
- 요청과 무관한 변경이 섞여 있으면 해당 파일은 제외하거나, 같은 파일이면 필요한 hunk만 선별한다.

### Commit 규칙

- 커밋은 사용자가 명시적으로 요청했을 때만 만든다.
- 커밋 전 `git status --short`로 staged/unstaged/untracked 상태를 다시 확인한다.
- 커밋 전 요청 범위, 관련 specs 문서, 검증 결과 또는 미실행 사유가 맞는지 확인한다.
- 문서/spec 변경과 코드 변경은 같은 사용자 가치에 속하면 함께 커밋할 수 있고, 독립적인 작업이면 나눈다.
- 커밋 메시지는 `<type>: <intent>` 형식을 권장한다.
- 예: `docs: clarify git collaboration workflow`, `feat: add meeting permission model`, `fix: validate livekit token request`

### Pull, Merge, Rebase 규칙

- pull, merge, rebase는 사용자가 요청했거나 현재 작업에 명확히 필요할 때만 수행한다.
- 실행 전 현재 변경사항이 보존 가능한 상태인지 확인한다.
- 충돌이 발생하면 자동으로 임의 해결하지 않고 충돌 파일, 원인, 선택지를 보고한다.
- rebase, force push, branch 삭제처럼 히스토리나 원격 상태를 바꾸는 작업은 명시 요청 없이는 하지 않는다.

### Branch, Push, PR 규칙

- 브랜치 전환 전 현재 변경사항이 안전하게 보존되는지 확인한다.
- 새 브랜치 생성은 사용자가 요청했거나 작업 분리가 명확히 필요한 경우에만 한다.
- push는 사용자가 요청했을 때만 수행한다.
- PR 생성이 필요하면 포함 파일, 검증 결과, 남은 위험을 요약한다.
- 현재 문서 기준선처럼 대량 미추적 파일이 있을 때는 커밋/push 전 포함 범위를 사용자에게 확인한다.

## 금지/주의

- 민감 정보, API 키, LiveKit/OpenAI secret을 커밋하지 않는다.
- 사용자 권한이 필요한 회의 데이터는 더 넓은 범위의 AI 컨텍스트로 섞지 않는다.
- 기존 사용자 변경을 임의로 되돌리지 않는다.
- Git 히스토리를 바꾸는 작업은 명시 요청 없이는 하지 않는다.
- 데모 mock 데이터와 실제 영속 데이터의 경계를 문서와 코드에서 명확히 유지한다.

## 작업 전 체크

- `AGENT.md`의 정확성/안전/최소 코드 원칙을 확인했는가?
- `git status --short`로 기존 변경사항을 확인했는가?
- 관련 `specs/*` 문서를 읽었는가?
- 스펙이 없으면 새 기능 폴더를 만들었는가?
- 불확실한 요구를 `clarify.md`에 기록했는가?
- 변경 범위가 `tasks.md` 작업 단위와 연결되는가?

## 작업 후 체크

- 변경한 파일과 이유를 요약한다.
- 커밋을 요청받은 경우, 커밋에 포함된 파일이 요청 범위와 일치하는지 확인한다.
- 실행한 검증 명령과 결과를 기록한다.
- 실행하지 못한 검증은 이유를 남긴다.
- 다음 작업이 있으면 `tasks.md`에 남긴다.

## 권장 검증

- Frontend: `cd frontend && npm run build`
- Backend: `cd backend && mvn test`
- AI: `cd ai && python -m compileall app`
