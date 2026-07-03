이 문서는 기능 구현 방법, 기술 결정, 병렬 작업 배정, 충돌 경계를 계획하기 위한 Markdown 템플릿이다.

# Implementation Plan: [FEATURE_NAME]

## Current State

- [현재 코드/데이터/화면 상태]

## Target Architecture

- Frontend: [변경]
- Backend: [변경]
- AI: [변경]
- Data: [변경]

## Technical Decisions

| Decision | Choice | Reason | Alternatives |
| --- | --- | --- | --- |
| [주제] | [선택] | [이유] | [대안] |

## API Contracts

- [엔드포인트/요청/응답/오류]

## Data Model

- [엔티티/관계/보존 정책]

## Security and Permissions

- [권한 적용 지점]
- [AI 컨텍스트 필터링]

## Parallel Work Plan

- Team Members: [사람 수]
- Agents: [사용 에이전트 수]

| Workstream | Owner | Agent | Scope | Expected Files | Dependencies |
| --- | --- | --- | --- | --- | --- |
| Frontend | [이름] | [에이전트] | [화면/상태/클라이언트 변경] | `[path]` | [선행 작업] |
| Backend | [이름] | [에이전트] | [API/권한/도메인 변경] | `[path]` | [선행 작업] |
| AI | [이름] | [에이전트] | [AI 컨텍스트/응답 변경] | `[path]` | [선행 작업] |
| Data | [이름] | [에이전트] | [스키마/마이그레이션 변경] | `[path]` | [선행 작업] |
| Docs/Contracts | [이름] | [에이전트] | [스펙/API 계약 변경] | `[path]` | [선행 작업] |

## Conflict Boundaries

- Single-owner files:
  - `[path]`: [owner와 이유]
- Shared contracts:
  - `[path]`: [변경 합의/검토 절차]
- Do Not Edit Concurrently:
  - `[path 또는 영역]`

## Integration Order

1. [API/데이터/권한 계약 확정]
2. [영역별 구현 병합 순서]
3. [통합 검증 순서]

## Test Plan

- [빌드/단위/통합/UI 검증]

## Rollout Plan

- [단계적 적용 방법]
