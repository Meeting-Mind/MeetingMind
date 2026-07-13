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
| Medium | 인증/인가 방식과 Auth API 계약이 확정됐다. | Backend Auth 구현 전 password hash, refresh token 폐기, Google token 검증을 구현 누락 없이 처리해야 한다. | T091 Backend Auth 구현에서 Google OAuth, 자체 signup/login, access/refresh token, refresh token hash/revoke 처리를 계약대로 구현한다. | `clarify.md`, `research.md`, `contracts/auth-api.md`, `contracts/common.md`, `data-model.md`, `plan.md`, `tasks.md` | Decided |
| Medium | 요구사항 정의서가 로컬 Markdown 기준선으로 반영됐다. | 구현자는 Google Sheets 전체를 매번 읽지 않고 `requirements/INDEX.md`에서 필요한 요구사항 문서만 읽으면 된다. | 새 기능/계약/데이터 변경 전 관련 `requirements/*` 문서를 확인한다. | `requirements/*`, `AGENTS.md`, `constitution.md`, `spec.md`, `plan.md`, `tasks.md` | Decided |
| Medium | 회의 권한 등급과 회의 게스트 범위가 확정됐다. | `MeetingParticipant.role`, UI 제어, LiveKit token, AI/RAG 권한 필터가 같은 기준을 따라야 한다. | `HOST`, `EDITOR`, `VIEWER`와 `participantType=guest` 기준으로 backend/frontend/ai 영향도를 점검한다. | `requirements/permissions.md`, `clarify.md`, `data-model.md`, `contracts/meeting-api.md`, `contracts/live-stt-api.md`, `contracts/ai-api.md`, `tasks.md` | Decided |
| Medium | Target API base URL과 실제 오디오 업로드 방식이 미정이다. | `/api/v1` route migration, 대용량 파일 처리, S3 연계 방식이 달라질 수 있다. | `clarify.md` Q-006, Q-007을 실제 구현 전에 결정한다. | `clarify.md`, `contracts/README.md`, `contracts/common.md`, `contracts/live-stt-api.md`, `plan.md` | Open |
| Medium | Project AI Backend 권한 선필터 1차 연동이 완료됐다. | active SpaceMember와 meeting ACL을 통과한 공식 지식/회의 요약만 AI context에 포함되며 Frontend 직접 AI 호출이 제거됐다. 실제 DB 검색으로 오해하지 않도록 in-memory 경계를 유지해야 한다. | M021 검증 결과를 기준선으로 유지하고 PostgreSQL/pgvector, embedding worker, persistent audit는 후속 Data/AI milestone으로 분리한다. | `spec.md`, `plan.md`, `contracts/ai-api.md`, `contracts/space-api.md`, `tasks.md`, `implement.md`, `ai/**`, `backend/**`, `frontend/**` | M021 Verified |
| Medium | AI 회의록 candidate Backend 경유 전환이 완료됐다. | 편집 권한과 단일 meeting source 검증 뒤에서 supported candidate만 임시 저장된다. confirm/update/export와 실제 DB repository는 아직 구현되지 않았다. | M022 기준선을 유지하고 report confirm/version 및 PostgreSQL repository를 후속 milestone으로 분리한다. | `plan.md`, `contracts/ai-api.md`, `contracts/meeting-api.md`, `data-model.md`, `erd.md`, `tasks.md`, `implement.md`, `ai/**`, `backend/**`, `frontend/**` | M022 Verified |
| Low | Async STT Processing API는 Future Draft다. | 현재 Core Prototype의 확정 구현 계약으로 오해하면 scope creep이 생길 수 있다. | 실제 STT 작업 전 별도 milestone 또는 feature spec으로 승격한다. | `contracts/live-stt-api.md`, future `specs/*` | Deferred |
| Low | `/api/workspace`는 현재 프로토타입 통합 API다. | 실제 확장 시 API 분리가 필요하다. | T029-T034에서 Space/Meeting/Report/AI API 계약을 세분화한다. | `contracts/README.md`, `contracts/space-api.md`, `contracts/meeting-api.md`, `contracts/ai-api.md`, `tasks.md` | Open |
| Low | Auth workstream의 backend/frontend/AI 회귀 검증과 Auth API smoke가 실행됐다. | 핵심 auth token 발급, Spring bean wiring, frontend build 회귀는 확인됐다. 브라우저 자동화 도구는 현재 환경에 없어 UI 클릭 흐름은 자동 검증하지 못했다. | Browser automation 도구가 준비되면 보호 route redirect와 자체 회원가입 UI 흐름을 추가 확인한다. | `tasks.md`, `implement.md` | Auth Verified |
| Medium | 요구사항 상세 반영 후 Meeting status, role 표기, 보고서 편집 권한, AI source 예시 충돌을 정리했다. | 구현자가 구형 enum이나 과도한 VIEWER 편집 권한을 따라 구현할 위험을 낮췄다. | T102-T105에서 실제 backend/frontend/ai/data 구현 영향도를 이어서 점검한다. | `requirements/*`, `contracts/*`, `plan.md`, `data-model.md`, `implement.md`, `tasks.md` | Reviewed |
| Medium | API 명세 템플릿이 MeetingMind 기준으로 보강되고 분리 API 문서에 적용됐다. | 신규 구현자가 endpoint별 권한, 데이터 범위, 검증, audit, 요구사항 trace를 같은 형식으로 확인할 수 있다. | T115에서 기능 owner별로 누락 endpoint와 ERD 관계를 최종 리뷰한다. | `.specify/templates/api-contract-template.md`, `contracts/README.md`, `contracts/*`, `tasks.md`, `implement.md` | Template Applied |
| Medium | API 요구사항 trace와 ERD/API 누락 항목을 1차 리뷰했다. | 잘못된 FR ID로 구현 우선순위가 오해되는 위험과 Project Knowledge/Domain Term API 누락 위험을 낮췄다. | T116에서 기능 owner가 보완된 trace, Knowledge/Term API, ERD constraints를 최종 확인한다. | `contracts/*`, `erd.md`, `data-model.md`, `tasks.md`, `implement.md` | Codex Reviewed |
| Low | Space invitation과 Meeting invitation 분리를 결정했다. | 회의 게스트 초대가 Space membership을 만들지 않도록 API/ERD 경계가 명확해졌다. | Backend migration 구현 시 `SPACE_INVITATION`, `MEETING_INVITATION`을 별도 테이블로 만든다. | `clarify.md`, `erd.md`, `space-api.md`, `meeting-api.md` | Decided |
| Low | 회의당 current confirmed report 1개 정책을 결정했다. | Project Knowledge 승격, 다운로드, Project AI source 연결 시 공식 회의록이 중복되는 위험이 줄었다. | Backend migration 구현 시 partial unique index 또는 애플리케이션 제약을 둔다. | `clarify.md`, `erd.md`, `data-model.md`, `meeting-api.md` | Decided |
| Low | ProjectKnowledge embedding 재생성은 비동기로 결정했다. | Knowledge 수정 API 응답 지연과 embedding provider 장애 영향을 줄이고 기존 chunk로 검색 안정성을 유지한다. | Backend/Data 구현 시 `embeddingStatus`, `embeddingJobId`, 비동기 worker 또는 job 경계를 설계한다. | `clarify.md`, `erd.md`, `data-model.md`, `knowledge-api.md` | Decided |
| Medium | AI/RAG prototype과 Backend-to-AI 경계를 분리 API/ERD 기준으로 재검토했다. | Meeting/Project scope, source 분리, 근거 없음 처리, candidate 원칙과 처리 시간/model/source count observability log는 코드와 테스트로 확인됐다. token budget 축소 정책과 persistent audit는 아직 없다. | report/task/term Backend 경유 전환에서도 같은 strict source 검증을 적용하고, 실제 RAG 저장소 도입 시 token budget과 persistent audit를 별도 task로 구현한다. | `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `contracts/ai-api.md`, `knowledge-api.md`, `erd.md`, `data-model.md`, `tasks.md`, `implement.md` | AI Reviewed |

## Recommendation

1. M022 AI 회의록 candidate Backend 경유 변경을 리뷰한 뒤 별도 커밋/PR로 `dev`에 통합한다.
2. report candidate confirm/update/version API 또는 task candidate Backend 경유 전환 중 다음 milestone을 선정한다.
3. report confirm 구현 시 current confirmed 단일 제약과 Project AI source 승격을 함께 검증한다.
4. STT 입력 계약이 안정되면 용어 설명을 Backend 권한 검증 뒤로 이동한다.
5. Data/Backend 영속화 이후 PostgreSQL/pgvector retriever, embedding worker, persistent `AI_REQUESTED` audit를 구현한다.
