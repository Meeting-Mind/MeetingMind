이 문서는 MeetingMind Core Prototype의 스펙, 계획, 작업 목록 일관성을 검증하기 위한 Markdown 문서이다.

# Consistency Analysis: MeetingMind Core Prototype

이 문서는 read-only 검증 기록이다. 수정이 필요하면 `spec.md`, `plan.md`, `tasks.md`에 별도 반영한다.

## Checks

- Spec vs Constitution: Pass
- Spec vs Plan: Pass
- Plan vs Tasks: Pass
- Contracts vs Data Model: Pass with notes
- Permission Rules: Pass
- Verification Coverage: Partial

## Findings

| Severity | Finding | Impact | Recommended Action | Source Update | Status |
| --- | --- | --- | --- | --- | --- |
| Medium | 인증/인가 방식과 Auth API 계약이 확정됐다. | Backend Auth 구현 전 password hash, refresh token 폐기, Google token 검증을 구현 누락 없이 처리해야 한다. | T091 Backend Auth 구현에서 Google OAuth, 자체 signup/login, access/refresh token, refresh token hash/revoke 처리를 계약대로 구현한다. | `clarify.md`, `research.md`, `contracts/api.md`, `data-model.md`, `plan.md`, `tasks.md` | Decided |
| Medium | 요구사항 정의서가 로컬 Markdown 기준선으로 반영됐다. | 구현자는 Google Sheets 전체를 매번 읽지 않고 `requirements/INDEX.md`에서 필요한 요구사항 문서만 읽으면 된다. | 새 기능/계약/데이터 변경 전 관련 `requirements/*` 문서를 확인한다. | `requirements/*`, `AGENTS.md`, `constitution.md`, `spec.md`, `plan.md`, `tasks.md` | Decided |
| Medium | 회의 권한 등급과 회의 게스트 범위가 확정됐다. | `MeetingParticipant.role`, UI 제어, LiveKit token, AI/RAG 권한 필터가 같은 기준을 따라야 한다. | `HOST`, `EDITOR`, `VIEWER`와 `participantType=guest` 기준으로 backend/frontend/ai 영향도를 점검한다. | `requirements/permissions.md`, `clarify.md`, `data-model.md`, `contracts/api.md`, `tasks.md` | Decided |
| Medium | Target API base URL과 실제 오디오 업로드 방식이 미정이다. | `/api/v1` route migration, 대용량 파일 처리, S3 연계 방식이 달라질 수 있다. | `clarify.md` Q-006, Q-007을 실제 구현 전에 결정한다. | `clarify.md`, `contracts/api.md`, `plan.md` | Open |
| Medium | Project AI 실제 RAG는 범위 밖이다. | 현재 문서는 원칙과 목표 모델만 정의한다. | Meeting AI 안정화 이후 별도 feature spec으로 분리한다. | future `specs/*` | Deferred |
| Low | Async STT Processing API는 Future Draft다. | 현재 Core Prototype의 확정 구현 계약으로 오해하면 scope creep이 생길 수 있다. | 실제 STT 작업 전 별도 milestone 또는 feature spec으로 승격한다. | `contracts/api.md`, future `specs/*` | Deferred |
| Low | `/api/workspace`는 현재 프로토타입 통합 API다. | 실제 확장 시 API 분리가 필요하다. | T029-T034에서 Space/Meeting/Report/AI API 계약을 세분화한다. | `contracts/api.md`, `tasks.md` | Open |
| Low | Auth workstream의 backend/frontend/AI 회귀 검증과 Auth API smoke가 실행됐다. | 핵심 auth token 발급, Spring bean wiring, frontend build 회귀는 확인됐다. 브라우저 자동화 도구는 현재 환경에 없어 UI 클릭 흐름은 자동 검증하지 못했다. | Browser automation 도구가 준비되면 보호 route redirect와 자체 회원가입 UI 흐름을 추가 확인한다. | `tasks.md`, `implement.md` | Auth Verified |
| Medium | 요구사항 상세 반영 후 Meeting status, role 표기, 보고서 편집 권한, AI source 예시 충돌을 정리했다. | 구현자가 구형 enum이나 과도한 VIEWER 편집 권한을 따라 구현할 위험을 낮췄다. | T102-T105에서 실제 backend/frontend/ai/data 구현 영향도를 이어서 점검한다. | `requirements/*`, `contracts/api.md`, `plan.md`, `data-model.md`, `implement.md`, `tasks.md` | Reviewed |

## Recommendation

1. T102-T105에서 backend/frontend/ai/data 영향도를 요구사항 기준선과 비교한다.
2. T040 Backend 도메인 모델과 회의 권한 필터를 만든다.
3. T094 LiveKit token 발급을 인증 사용자와 회의 접근 권한 확인 뒤로 이동한다.
4. Meeting AI 컨텍스트 조립을 Backend 권한 검증 뒤로 이동한다.
5. 그 다음 Project AI RAG를 도입한다.
