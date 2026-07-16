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
| Medium | Project AI Backend 권한 선필터가 PostgreSQL runtime에 연결됐다. | active SpaceMember를 먼저 확인하고 OWNER/ADMIN은 전체 meeting, MEMBER는 active participant meeting만 SQL 후보에 포함한다. meeting guest는 Project AI를 호출할 수 없다. | M032 SQL prefilter와 internal source contract를 유지하고 T230은 이 `allowedMeetingIds` 경계를 좁히지 않도록 구현한다. | `spec.md`, `plan.md`, `contracts/ai-api.md`, `data-model.md`, `tasks.md`, `implement.md`, `backend/**` | M032 Verified |
| Medium | AI 회의록 candidate Backend 경유와 PostgreSQL 저장이 완료됐다. | 편집 권한과 단일 meeting source 검증 뒤에서 supported candidate만 저장되고 report version/current 전환은 meeting row lock transaction으로 처리된다. | `Q-008` TTL과 update/history/export를 별도 milestone으로 구현한다. | `plan.md`, `contracts/ai-api.md`, `contracts/meeting-api.md`, `data-model.md`, `tasks.md`, `implement.md`, `backend/**` | M032 Verified |
| Medium | Report candidate confirm과 current version 전환이 완료됐다. | 편집 권한자가 candidate/draft를 확정하면 기존 current는 해제되고 새 report만 current가 된다. candidate TTL과 version history 조회는 아직 없다. | M024 기준선을 유지하고 `Q-008` TTL, update/history/export, persistent audit를 후속 milestone으로 분리한다. | `clarify.md`, `contracts/meeting-api.md`, `data-model.md`, `erd.md`, `tasks.md`, `implement.md`, `backend/**`, `frontend/**` | M024 Verified |
| Medium | AI 태스크 후보와 TaskCard 확정이 PostgreSQL transaction에 연결됐다. | candidate row lock과 sourceCandidate unique 제약으로 후보당 카드 하나를 보장하고 sourceIds를 JSONB로 보존한다. | `Q-009`, 후보 제외 API와 일반 Kanban CRUD를 후속 milestone으로 분리한다. | `requirements/permissions.md`, `requirements/status-values.md`, `contracts/kanban-api.md`, `data-model.md`, `tasks.md`, `implement.md`, `backend/**` | M032 Verified |
| Low | Async STT Processing API는 Future Draft다. | 현재 Core Prototype의 확정 구현 계약으로 오해하면 scope creep이 생길 수 있다. | 실제 STT 작업 전 별도 milestone 또는 feature spec으로 승격한다. | `contracts/live-stt-api.md`, future `specs/*` | Deferred |
| Low | `/api/workspace`는 현재 프로토타입 통합 API다. | 실제 확장 시 API 분리가 필요하다. | T029-T034에서 Space/Meeting/Report/AI API 계약을 세분화한다. | `contracts/README.md`, `contracts/space-api.md`, `contracts/meeting-api.md`, `contracts/ai-api.md`, `tasks.md` | Open |
| Low | Auth workstream의 backend/frontend/AI 회귀 검증과 Auth API smoke가 실행됐다. | 핵심 auth token 발급, Spring bean wiring, frontend build 회귀는 확인됐다. 브라우저 자동화 도구는 현재 환경에 없어 UI 클릭 흐름은 자동 검증하지 못했다. | Browser automation 도구가 준비되면 보호 route redirect와 자체 회원가입 UI 흐름을 추가 확인한다. | `tasks.md`, `implement.md` | Auth Verified |
| Medium | 요구사항 상세 반영 후 Meeting status, role 표기, 보고서 편집 권한, AI source 예시 충돌을 정리했다. | 구현자가 구형 enum이나 과도한 VIEWER 편집 권한을 따라 구현할 위험을 낮췄다. | T102-T105에서 실제 backend/frontend/ai/data 구현 영향도를 이어서 점검한다. | `requirements/*`, `contracts/*`, `plan.md`, `data-model.md`, `implement.md`, `tasks.md` | Reviewed |
| Medium | API 명세 템플릿이 MeetingMind 기준으로 보강되고 분리 API 문서에 적용됐다. | 신규 구현자가 endpoint별 권한, 데이터 범위, 검증, audit, 요구사항 trace를 같은 형식으로 확인할 수 있다. | T115에서 기능 owner별로 누락 endpoint와 ERD 관계를 최종 리뷰한다. | `.specify/templates/api-contract-template.md`, `contracts/README.md`, `contracts/*`, `tasks.md`, `implement.md` | Template Applied |
| Medium | API 요구사항 trace와 ERD/API 누락 항목을 1차 리뷰했다. | 잘못된 FR ID로 구현 우선순위가 오해되는 위험과 Project Knowledge/Domain Term API 누락 위험을 낮췄다. | T116에서 기능 owner가 보완된 trace, Knowledge/Term API, ERD constraints를 최종 확인한다. | `contracts/*`, `erd.md`, `data-model.md`, `tasks.md`, `implement.md` | Codex Reviewed |
| Low | Space invitation과 회의 참가 신청을 분리했다. | Space 가입은 `SPACE_INVITATION`, 회의 단독 접근은 URL/코드 기반 `MEETING_JOIN_REQUEST`와 HOST 승인으로 경계가 명확해졌다. | Backend migration 구현 시 `SPACE_INVITATION`, `MEETING_JOIN_REQUEST`를 별도 테이블로 만든다. 기존 `MEETING_INVITATION` target 계약은 superseded 상태다. | `clarify.md`, `erd.md`, `space-api.md`, `meeting-api.md` | Decided |
| Low | 회의당 current confirmed report 1개 정책을 결정했다. | Project Knowledge 승격, 다운로드, Project AI source 연결 시 공식 회의록이 중복되는 위험이 줄었다. | Backend migration 구현 시 partial unique index 또는 애플리케이션 제약을 둔다. | `clarify.md`, `erd.md`, `data-model.md`, `meeting-api.md` | Decided |
| Low | ProjectKnowledge embedding 재생성은 비동기로 결정했다. | Knowledge 수정 API 응답 지연과 embedding provider 장애 영향을 줄이고 기존 chunk로 검색 안정성을 유지한다. | Backend/Data 구현 시 `embeddingStatus`, `embeddingJobId`, 비동기 worker 또는 job 경계를 설계한다. | `clarify.md`, `erd.md`, `data-model.md`, `knowledge-api.md` | Decided |
| Medium | AI/RAG prototype과 Backend-to-AI 경계를 분리 API/ERD 기준으로 재검토했다. | Meeting/Project scope, source 분리, 근거 없음 처리, candidate 원칙과 처리 시간/model/source count observability log는 코드와 테스트로 확인됐다. token budget 축소 정책과 persistent audit는 아직 없다. | report/task/term Backend 경유 전환에서도 같은 strict source 검증을 적용하고, 실제 RAG 저장소 도입 시 token budget과 persistent audit를 별도 task로 구현한다. | `ai/app/main.py`, `ai/tests/test_meeting_ai.py`, `contracts/ai-api.md`, `knowledge-api.md`, `erd.md`, `data-model.md`, `tasks.md`, `implement.md` | AI Reviewed |
| Medium | AI provider 오류, timeout, 공통 오류 body가 M026에서 정규화됐다. | public/internal 경로에서 provider raw detail이 노출되지 않고 챗봇 계열 30초, 보고서 60초 timeout과 `{code, message, fieldErrors, traceId}`가 적용된다. 구현 비교 문서도 현재 기준선으로 갱신됐으며 자동 재시도는 중복 과금 위험 때문에 제외됐다. | internal service auth, token budget 자동 축소, persistent audit는 각각 선행 의존성을 갖는 후속 milestone으로 분리한다. | `requirements/performance.md`, `contracts/ai-api.md`, `feature-implementation-comparison.md`, `plan.md`, `tasks.md`, `implement.md`, `ai/app/main.py`, `ai/tests/test_meeting_ai.py` | M026 Verified |
| Medium | M027 권한 mutation과 audit가 M032 PostgreSQL runtime에 연결됐다. | SpaceMember 제거, participant revoke, owner transfer, join approval과 audit가 같은 transaction 경계에서 저장된다. | 권한 mutation 추가 시 같은 Space/Meeting row lock 순서와 persistent audit를 유지한다. | `backend/src/main/java/com/meetingmind/demo/authz/**`, `backend/src/main/java/com/meetingmind/demo/domain/**`, `contracts/space-api.md`, `contracts/meeting-api.md`, `tasks.md`, `plan.md`, `implement.md` | M032 Verified |
| Medium | URL/코드 기반 회의 참가 신청과 HOST 승인 runtime이 PostgreSQL에 연결됐다. | join code 원문은 생성 응답에서만 반환되고 DB에는 SHA-256 hash만 저장한다. pending request와 승인 participant/audit는 transaction과 partial unique 제약을 사용한다. | 실제 계정 applicant→HOST approval→prejoin→LiveKit E2E를 후속 통합 검증한다. | `requirements/*`, `backend/src/main/java/com/meetingmind/demo/**`, `contracts/meeting-api.md`, `data-model.md`, `tasks.md`, `implement.md` | M032 Verified |
| Medium | M029에서 Frontend URL/code 신청, 권한 확인, HOST 승인, prejoin/LiveKit default-deny gate를 구현했다. | 사용자는 SpaceRole과 meeting ACL을 분리해 확인하고, 승인 전에는 회의 media/room 진입을 시도할 수 없다. local 승인과 SpaceMember 제거 semantics도 Backend 정책과 일치한다. | Backend와 유효한 실제 계정/meeting을 함께 실행해 applicant→HOST approval→prejoin→LiveKit E2E와 desktop/mobile visual smoke를 추가 검증한다. | `frontend/src/pages/MeetingAccessPage.tsx`, `LiveMeetingPage.tsx`, `LiveRoomPage.tsx`, `TeamMembersPage.tsx`, `App.tsx`, `tasks.md`, `implement.md` | M029 Build Verified |
| Medium | M033 Meeting CRUD가 PostgreSQL과 Frontend target 화면에 연결됐다. | 회의 목록·상세·수정·soft delete가 ACL과 canonical 상태 전이를 따르고, 삭제된 회의는 일반 조회와 Meeting/Project AI 후보에서 제외된다. | hard purge·복구·유예 기간은 보존 정책을 먼저 결정한 뒤 별도 milestone로 구현하고, M033의 active 조회 조건을 유지한다. | `requirements/functional-requirements-detail.md`, `clarify.md`, `contracts/meeting-api.md`, `data-model.md`, `erd.md`, `backend/**`, `frontend/**`, `tasks.md`, `implement.md` | M033 Verified |
| Medium | M037에서 target 회의 상세·participant ACL·캘린더 생성 화면을 Backend 응답 기준으로 완성했다. | Space member userId와 meetingId 기반 participant state를 사용하고, role/access mutation 뒤 상세를 재조회하며 생성 코드/URL은 Frontend 메모리에만 유지한다. | `GET /calendar/events` runtime, Meeting description/endAt 계약과 Space member 초대/search API는 별도 Backend milestone로 구현한다. | `requirements/functional-requirements-detail.md`, `permissions.md`, `contracts/meeting-api.md`, `contracts/space-api.md`, `frontend/**`, `tasks.md`, `implement.md` | M037 Verified |

## Recommendation

1. Meeting hard purge·복구·삭제 유예 기간을 보존 정책과 함께 결정한다.
2. 통합된 M027~M029 권한·참가 신청 흐름을 실제 applicant/HOST 계정으로 E2E 검증한다.
3. internal API 서비스 인증 header를 Backend와 shared contract로 확정한다.
4. `Q-008`, `Q-009` candidate TTL을 결정한 뒤 만료 검증과 정리 작업을 추가한다.
5. 별도 AI/RAG 담당자는 M032/M033의 `allowedMeetingIds`와 active meeting 조건을 유지하면서 T230 pgvector retriever와 embedding worker를 구현한다.
6. 캘린더 전용 조회 endpoint와 Meeting description/endAt 계약을 확정해 현재 ACL-filtered Space meeting read model을 교체한다.
