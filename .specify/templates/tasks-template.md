이 문서는 기능 구현을 milestone과 에이전트 친화적인 task 단위로 나누고 owner, agent, dependency, 파일 경계를 드러내기 위한 Markdown 템플릿이다.

# Tasks: [FEATURE_NAME]

## Status Legend

- `[ ]`: Not started
- `[~]`: In progress
- `[x]`: Done

## Milestones

| ID | Goal | Exit Criteria | Related Tasks |
| --- | --- | --- | --- |
| M001 | [마일스톤 목표] | [검증 가능한 완료 기준] | T001-T003 |

## Task Granularity Rules

- 하나의 task는 한 명 또는 한 에이전트가 독립적으로 수행하고 검증할 수 있어야 한다.
- 하나의 task는 가능한 한 하나의 area와 제한된 파일 범위만 수정한다.
- 하나의 task는 명확한 완료 기준과 검증 방법을 가져야 한다.
- 여러 에이전트가 동시에 작업할 수 있도록 dependency를 명시한다.
- shared contract, migration, 공통 타입, 권한 규칙 변경은 별도 task로 분리한다.
- task가 너무 크면 planning, contract, implementation, verification task로 나눈다.

## Tasks

| ID | Milestone | Status | Area | Owner | Agent | Depends On | Files | Task | Completion |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T001 | M001 | [ ] | [area] | [owner] | [agent] | - | `[path]` | [구체적인 작업] | [완료 기준] |
| T002 | M001 | [ ] | [area] | [owner] | [agent] | T001 | `[path]` | [구체적인 작업] | [완료 기준] |

## Parallel Safety Rules

- 같은 `Files` 항목을 가진 작업은 동시에 진행하지 않는다.
- shared contract 파일은 owner를 하나로 지정하고, 관련 workstream은 해당 변경 이후 진행한다.
- dependency가 남아 있는 작업은 `[~]` 또는 `[x]`로 변경하지 않는다.

## Completion Rules

- 작업을 `[x]`로 변경하려면 관련 구현 또는 문서 변경이 완료되어야 한다.
- 작업 완료 시 관련 검증 항목을 실행하거나 미실행 사유를 `Verification` 또는 `implement.md`에 남긴다.
- 스펙/계획/API/데이터 모델 변경이 동반되면 관련 문서를 함께 갱신한다.

## Verification

- [ ] V001 [검증 명령 또는 수동 검증]

## Notes

- [결정사항/리스크/후속 작업]
