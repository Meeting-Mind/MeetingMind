이 문서는 MeetingMind Core Prototype의 스펙, 계획, 작업 목록 일관성을 검증하기 위한 Markdown 문서이다.

# Consistency Analysis: MeetingMind Core Prototype

이 문서는 read-only 검증 기록이다. 수정이 필요하면 `spec.md`, `plan.md`, `tasks.md`에 별도 반영한다.

## Checks

- Spec vs Constitution: Pass
- Spec vs Plan: Pass
- Plan vs Tasks: Pass
- Contracts vs Data Model: Pass with notes
- Permission Rules: Pass with open questions
- Verification Coverage: Partial

## Findings

| Severity | Finding | Impact | Recommended Action | Source Update | Status |
| --- | --- | --- | --- | --- | --- |
| Medium | 인증/인가 방식이 미정이다. | 실제 권한 기반 AI 구현 전에 Backend 보안 구조와 Frontend 인증 흐름이 흔들릴 수 있다. | `clarify.md` Q-001을 먼저 결정한다. | `clarify.md`, `plan.md`, `tasks.md` | Open |
| Medium | 회의 권한 등급이 미정이다. | `MeetingParticipant.role`과 UI 제어가 달라질 수 있다. | `clarify.md` Q-002를 먼저 결정한다. | `clarify.md`, `data-model.md`, `tasks.md` | Open |
| Medium | Target API base URL과 실제 오디오 업로드 방식이 미정이다. | `/api/v1` route migration, 대용량 파일 처리, S3 연계 방식이 달라질 수 있다. | `clarify.md` Q-006, Q-007을 실제 구현 전에 결정한다. | `clarify.md`, `contracts/api.md`, `plan.md` | Open |
| Medium | Project AI 실제 RAG는 범위 밖이다. | 현재 문서는 원칙과 목표 모델만 정의한다. | Meeting AI 안정화 이후 별도 feature spec으로 분리한다. | future `specs/*` | Deferred |
| Low | Async STT Processing API는 Future Draft다. | 현재 Core Prototype의 확정 구현 계약으로 오해하면 scope creep이 생길 수 있다. | 실제 STT 작업 전 별도 milestone 또는 feature spec으로 승격한다. | `contracts/api.md`, future `specs/*` | Deferred |
| Low | `/api/workspace`는 현재 프로토타입 통합 API다. | 실제 확장 시 API 분리가 필요하다. | T010에서 Space/Meeting/Report API 분리 계획을 세분화한다. | `contracts/api.md`, `tasks.md` | Open |
| Low | 검증 명령은 문서화되어 있으나 아직 실행 결과는 없다. | 현재 문서 기준선이 실제 앱 빌드 상태와 연결되지 않았다. | 코드 변경 작업 전후에 권장 검증을 실행하거나 미실행 사유를 남긴다. | `tasks.md`, `implement.md` | Open |

## Recommendation

1. 로그인/권한 등급을 먼저 확정한다.
2. Backend 도메인 모델과 권한 필터를 만든다.
3. Meeting AI 컨텍스트 조립을 Backend 권한 검증 뒤로 이동한다.
4. 그 다음 Project AI RAG를 도입한다.
