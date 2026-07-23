# Frontend Make ↔ Backend/BFF Gap Analysis

작성일: 2026-07-22

## 0. 목적

이 문서는 현재 `frontend/src/App.tsx`의 Figma Make UI를 실제 `BFF + auth + backend + ai`와 연동하기 전에 필요한 갭을 정리한다.

이번 단계의 기준은 다음과 같다.

- UI 기준선은 현재 Make 구현을 유지한다.
- mock 데이터, mock 성공 처리, 임시 상태만 실제 API 응답으로 교체한다.
- 기존 `frontend/src/api`, `frontend/src/auth`, `frontend/src/hooks`의 재사용 가능 자산을 우선 확인한다.
- 권한, 인증, AI 검색 범위는 서버 계약을 최종 기준으로 본다.

## 1. 현재 구조 요약

### 1.1 현재 렌더링되는 프론트 런타임

- 현재 엔트리포인트는 [frontend/src/main.tsx](/Users/dongjoon/Downloads/MeetingMind/frontend/src/main.tsx:1) 이고, 여기서 [frontend/src/App.tsx](/Users/dongjoon/Downloads/MeetingMind/frontend/src/App.tsx:1) 의 Make UI만 직접 렌더링한다.
- 기존 API 연동용 route 구성인 [frontend/src/routes/AppRoutes.tsx](/Users/dongjoon/Downloads/MeetingMind/frontend/src/routes/AppRoutes.tsx:1) 는 현재 마운트되지 않는다.
- 기존 API 연동 훅인 [frontend/src/hooks/useWorkspaceController.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/hooks/useWorkspaceController.ts:1), [frontend/src/hooks/useWorkspaceMutations.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/hooks/useWorkspaceMutations.ts:1) 도 현재 Make App에서 사용되지 않는다.

### 1.2 현재 서버 경계

- 브라우저 인증 진입점은 BFF의 `/api/v1/auth/*` 이다.
- 브라우저의 업무 API 진입점도 BFF의 `/api/v1/**` 프록시다.
- 실제 로그인/토큰/재인증/모든 기기 로그아웃 원본은 `auth` 서비스의 `/internal/v1/auth/*` 이다.
- Core 업무 도메인 원본은 `backend` 의 `/api/v1/*` 이다.
- AI chat/report/task generation 은 BFF 프록시를 통해 `ai` 또는 `backend` 로 전달된다.

### 1.3 가장 큰 통합 리스크

현재 Make UI는 다음 전제를 가지고 있어 바로 붙지 않는다.

- 로그인 상태를 보지 않고 라우팅한다.
- 거의 모든 화면 데이터가 `App.tsx` 내부 상수/`useState` 다.
- 성공 처리도 대부분 `navigate`, `setState`, `setTimeout` 기반이다.
- mock route id 가 실제 계약과 다르다.
  - Make 예시: `q3-launch`, `m-123`, `space-1`
  - BFF 프록시 허용 패턴: `space-{uuid}`, `meeting-{uuid}`, `report-{uuid}`, `task-{uuid}` 등
  - 기준: [bff/src/main/java/com/meetingmind/bff/proxy/ProxyRouteRegistry.java](/Users/dongjoon/Downloads/MeetingMind/bff/src/main/java/com/meetingmind/bff/proxy/ProxyRouteRegistry.java:1)

## 2. Figma Make 화면별 mock 데이터 / mock 액션

| Route | 화면 | 현재 mock 데이터 | 현재 mock 액션 | 연동 시 주의점 |
| --- | --- | --- | --- | --- |
| `/` | Landing | 정적 카피, 정적 소개 카드 | 없음 | API 연결 불필요 |
| `/login` | 로그인 | local `email`, `password`, `loading`, `error` state | `setTimeout` 후 `/spaces` 이동 | 실제로는 `/api/v1/auth/login`, `/api/v1/auth/google`, `/api/v1/auth/session`, CSRF 필요 |
| `/meeting-access` | 회의 참가 신청 | 정적 회의 제목/호스트/시간 | 이름+이메일 입력 후 `submitted=true` | 실제로는 meeting id/joinCode를 받아 `/api/v1/meetings/join-requests` 호출해야 함 |
| `/spaces` | 워크스페이스 목록 | `Q3 Launch Project`, `Design System 2.0` 등 하드코딩 목록 | 카드 클릭 내비게이션 | 실제 `fetchSpaces`, `fetchDashboardSummary` 로 대체 가능 |
| `/spaces/:spaceId` | 프로젝트 홈/개요 | 프로젝트명, 최근 작업, 멤버, AI drawer 모두 mock | 버튼/카드 이동만 수행 | 현재 breadcrumb/project title 도 mock 문자열 |
| `/spaces/:spaceId/meetings` | 회의 목록 | `m-123` 등 회의 목록 하드코딩 | 행 클릭 이동 | 실제 `fetchMeetings` 필요, id 형식 불일치 해결 필요 |
| `/spaces/:spaceId/meetings/:meetingId` | 회의 상세 개요 | 정적 아젠다, 참여자, 상태 | 탭 이동 | 실제 `fetchMeetingDetail`, `fetchMeetingParticipants` 필요 |
| `/spaces/:spaceId/meetings/:meetingId/transcript` | 전사 | 정적 segment 배열, 검색 state | 검색만 local | 실제 `fetchMeetingDialogue`, loading/error/status 처리 필요 |
| `/spaces/:spaceId/meetings/:meetingId/report` | 회의록 | 정적 요약/의사결정/태스크, status local state | confirm/edit mode local | 실제 `generateReportCandidate`, `fetchMeetingReports`, `updateMeetingReport`, `confirmMeetingReport`, `downloadMeetingReport` 필요 |
| `/spaces/:spaceId/meetings/:meetingId/tasks` | 태스크 후보 | 정적 후보 배열 | local check/add/remove | 실제 `extractTaskCandidates`, `fetchTaskCandidates`, `confirmTaskCandidate`, `dismissTaskCandidate` 필요 |
| `/spaces/:spaceId/meetings/:meetingId/ai` | Meeting AI | 정적 assistant starter, local message append | 질문 입력 시 local answer 추가 | 실제 `chatMeetingAi` 와 source/unsupported/error 상태 필요 |
| `/spaces/:spaceId/tasks` | 프로젝트 칸반 | 정적 컬럼/카드 | local 상태 이동 | 실제 `fetchTasks`, `createTask`, `updateTask`, `deleteTask` 필요 |
| `/spaces/:spaceId/knowledge` | Knowledge 맵/카드 | 정적 노드, 정적 카드 | local pan/search/rename state | 실제 `fetchProjectKnowledge`, `createProjectKnowledge`, `updateProjectKnowledge`, `deleteProjectKnowledge` 필요 |
| `/spaces/:spaceId/ai` | Project AI | 정적 대화 기록, 제안 질문 | local answer append | 실제 `chatProjectAi`, `fetchProjectAiHistory` 필요 |
| `/spaces/:spaceId/calendar` | 프로젝트 캘린더 | 정적 month/meeting map | day 선택 local | 실제 `fetchCalendarEvents` 필요 |
| `/spaces/:spaceId/members` | 멤버/권한 | 정적 member 배열 | invite/role/owner modal local | 실제 `fetchSpaceMembers`, `createSpaceInvitation`, `updateSpaceMemberRole`, `removeSpaceMember`, `transferSpaceOwner` 필요 |
| `/spaces/:spaceId/terms` | 용어 사전 | 정적 용어 배열 | add/edit local | 실제 `fetchDomainTerms`, `createDomainTerm`, `updateDomainTerm`, `archiveDomainTerm`, `explainMeetingTerm` 필요 |
| `/spaces/:spaceId/settings` | 프로젝트 설정 | 정적 project setting form | local toggle/save/danger dialog | 일부는 실제 backend 계약 없음. 프로젝트 수정/삭제만 기존 API로 가능 |
| `/spaces/:spaceId/meetings/:meetingId/live/prejoin` | prejoin | local mic/cam/name state | live route 이동 | 실제 meeting access 확인과 livekit token 선행 필요 |
| `/spaces/:spaceId/meetings/:meetingId/live` | live room | 정적 elapsed/segments/participants | local mic/cam toggle | 실제 token 발급, STT start/stop, ongoing dialogue refresh 필요 |
| `/space-invitations/:spaceId/:invitationId` | Space 초대 응답 | 정적 inviter/space 정보 | local accept/decline state | 실제 fragment token 읽어 `/accept` `/decline` 호출해야 함 |
| `/settings` | 계정 설정 | 정적 profile/session/noti state | local save/revokeAll toggle | 현재 BFF는 session/logout/logout-all만 있음. profile/password/account APIs 없음 |
| `/denied` | Permission denied | 정적 안내 | 뒤로가기/목록 이동 | 실제 403/404 매핑 필요 |
| `/spaces/:spaceId/ui` | modal demo | 완전 mock | local modal | 운영 연동 대상 아님 |

## 3. 기존 frontend API 모듈과 실제 endpoint

### 3.1 인증 / 세션

기준 파일:

- [frontend/src/auth/session.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/auth/session.ts:1)
- [frontend/src/auth/csrf.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/auth/csrf.ts:1)

| 기능 | Frontend 호출 | BFF endpoint | 상태 |
| --- | --- | --- | --- |
| 세션 bootstrap | `bootstrapAuthSession()` | `GET /api/v1/auth/session` | 구현됨 |
| 이메일 회원가입 | `signupWithPassword()` | `POST /api/v1/auth/signup` | 구현됨 |
| 이메일 로그인 | `loginWithPassword()` | `POST /api/v1/auth/login` | 구현됨 |
| Google 로그인 | `loginWithGoogle()` | `POST /api/v1/auth/google` | 구현됨 |
| 현재 로그아웃 | `logoutCurrentSession()` | `POST /api/v1/auth/logout` | 구현됨 |
| 모든 기기 로그아웃 | `logoutAllDevices()` | `POST /api/v1/auth/logout-all` | 구현됨 |
| 재인증 | `reauthenticateWithPassword/Google()` | `POST /api/v1/auth/reauthenticate` | 구현됨 |
| CSRF 토큰 | `bffFetch()` 내부 | `GET /api/v1/auth/csrf` | 구현됨 |

### 3.2 업무 API 모듈

기준 파일:

- [frontend/src/api/workspace.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/workspace.ts:1)
- [frontend/src/api/spaces.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/spaces.ts:1)
- [frontend/src/api/meetings.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/meetings.ts:1)
- [frontend/src/api/meetingAccess.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/meetingAccess.ts:1)
- [frontend/src/api/reports.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/reports.ts:1)
- [frontend/src/api/tasks.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/tasks.ts:1)
- [frontend/src/api/transcripts.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/transcripts.ts:1)
- [frontend/src/api/knowledge.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/knowledge.ts:1)
- [frontend/src/api/terms.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/terms.ts:1)
- [frontend/src/api/ai.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/ai.ts:1)
- [frontend/src/api/live.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/live.ts:1)
- [frontend/src/api/dashboard.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/dashboard.ts:1)
- [frontend/src/api/calendar.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/api/calendar.ts:1)

요약:

- Space: 목록/생성/상세/수정/삭제/멤버/초대/오너이양 API 모듈 존재
- Meeting: 목록/생성/상세/수정/삭제 API 모듈 존재
- Meeting access: 참가자 목록/추가/수정, 참가 신청/승인/거절 API 모듈 존재
- Report: 생성/AI 수정/목록/상세/수정/확정/복원/다운로드 API 모듈 존재
- Task candidate + kanban: 생성/조회/확정/제외 + 프로젝트 task CRUD API 모듈 존재
- Knowledge: 목록/상세/생성/수정/삭제 API 모듈 존재
- Terms: 목록/생성/수정/삭제 + 회의 용어 설명 API 모듈 존재
- AI: Meeting AI / Project AI / Project AI history API 모듈 존재
- Live/STT: livekit token, transcription start/stop, dialogue 조회 모듈 존재

### 3.3 재사용 가능한 기존 연동 훅

기준 파일:

- [frontend/src/hooks/useWorkspaceController.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/hooks/useWorkspaceController.ts:1)
- [frontend/src/hooks/useWorkspaceMutations.ts](/Users/dongjoon/Downloads/MeetingMind/frontend/src/hooks/useWorkspaceMutations.ts:1)

이미 준비된 것:

- 세션이 있으면 `fetchSpaces`, `fetchDashboardSummary`, `fetchMeetings`, `fetchTasks`, `fetchProjectKnowledge`, `fetchSpaceMembers` 를 불러와 기존 상태 모델로 매핑한다.
- 프로젝트 생성/수정/삭제, 회의 생성/수정/삭제, 참가자 관리, 멤버 초대/역할 변경/오너 이양, task/knowledge mutation 이 이미 API 모듈 기준으로 작성돼 있다.

한계:

- 현재 Make UI는 이 훅들을 사용하지 않는다.
- 기존 훅은 이전 상태 모델과 페이지 구조를 전제로 하므로, Make UI 100% 유지 목표에서는 “데이터 로더/액션 로직” 위주로만 재사용해야 한다.

## 4. Backend/BFF에서 이미 구현된 기능

### 4.1 인증 / 세션 / 보안

| 기능 | 구현 근거 | 판정 |
| --- | --- | --- |
| 회원가입/로그인/구글 로그인 | [bff/src/main/java/com/meetingmind/bff/auth/BffAuthController.java](/Users/dongjoon/Downloads/MeetingMind/bff/src/main/java/com/meetingmind/bff/auth/BffAuthController.java:1), [auth/src/main/java/com/meetingmind/auth/runtime/AuthRuntimeController.java](/Users/dongjoon/Downloads/MeetingMind/auth/src/main/java/com/meetingmind/auth/runtime/AuthRuntimeController.java:1) | 구현됨 |
| 세션 조회 | `GET /api/v1/auth/session` | 구현됨 |
| 현재 로그아웃 | `POST /api/v1/auth/logout` | 구현됨 |
| 모든 기기 로그아웃 | `POST /api/v1/auth/logout-all` + `reauthenticate` | 구현됨 |
| CSRF | [bff/src/main/java/com/meetingmind/bff/auth/CsrfController.java](/Users/dongjoon/Downloads/MeetingMind/bff/src/main/java/com/meetingmind/bff/auth/CsrfController.java:1) | 구현됨 |
| BFF same-origin + proxy allowlist | [bff/src/main/java/com/meetingmind/bff/proxy/BffProxyController.java](/Users/dongjoon/Downloads/MeetingMind/bff/src/main/java/com/meetingmind/bff/proxy/BffProxyController.java:1), [bff/src/main/java/com/meetingmind/bff/proxy/ProxyRouteRegistry.java](/Users/dongjoon/Downloads/MeetingMind/bff/src/main/java/com/meetingmind/bff/proxy/ProxyRouteRegistry.java:1) | 구현됨 |

### 4.2 Core 업무 도메인

| 기능 | 구현 근거 | 판정 |
| --- | --- | --- |
| 프로젝트 목록/생성/상세/수정/삭제 | [backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java:1) | 구현됨 |
| 회의 목록/생성/상세/수정/삭제 | [backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java:146), [backend/src/main/java/com/meetingmind/demo/controller/MeetingController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/MeetingController.java:1) | 구현됨 |
| 회의 참가자 조회/추가/수정 | [backend/src/main/java/com/meetingmind/demo/controller/MeetingController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/MeetingController.java:103) | 구현됨 |
| 회의 참가 신청/승인/거절 | same file | 구현됨 |
| 프로젝트 멤버 조회/초대/초대 수락·거절/역할 변경/제거/오너 이양 | [backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java:193) | 구현됨 |
| 대시보드 요약 | [backend/src/main/java/com/meetingmind/demo/controller/DashboardController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/DashboardController.java:1) | 구현됨 |
| 캘린더 이벤트 조회 | [backend/src/main/java/com/meetingmind/demo/controller/CalendarController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/CalendarController.java:1) | 구현됨 |
| 프로젝트 task CRUD | [backend/src/main/java/com/meetingmind/demo/controller/TaskCardController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/TaskCardController.java:1) | 구현됨 |
| Project Knowledge CRUD | [backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/SpaceController.java:320) | 구현됨 |
| 용어 사전 CRUD | [backend/src/main/java/com/meetingmind/demo/controller/DomainTermController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/DomainTermController.java:1) | 구현됨 |

### 4.3 AI / 보고서 / 태스크 후보

| 기능 | 구현 근거 | 판정 |
| --- | --- | --- |
| Meeting AI chat | [backend/src/main/java/com/meetingmind/demo/controller/MeetingAiController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/MeetingAiController.java:1), [backend/src/main/java/com/meetingmind/demo/service/MeetingAiService.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/service/MeetingAiService.java:1) | 구현됨 |
| Project AI chat + history | [backend/src/main/java/com/meetingmind/demo/controller/ProjectAiController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/ProjectAiController.java:1), [backend/src/main/java/com/meetingmind/demo/service/ProjectAiService.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/service/ProjectAiService.java:1) | 구현됨 |
| AI 회의록 생성/AI 수정/조회/수정/확정/복원/다운로드 | [backend/src/main/java/com/meetingmind/demo/controller/MeetingReportController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/MeetingReportController.java:1) | 구현됨 |
| 회의 태스크 후보 생성/조회/확정/제외 | [backend/src/main/java/com/meetingmind/demo/controller/TaskCandidateController.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/controller/TaskCandidateController.java:1), [backend/src/main/java/com/meetingmind/demo/service/TaskCandidateService.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/service/TaskCandidateService.java:1) | 구현됨 |
| 회의 용어 설명 | [backend/src/main/java/com/meetingmind/demo/service/MeetingTermExplanationService.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/service/MeetingTermExplanationService.java:1) | 구현됨 |

### 4.4 저장소 / 영속화

| 항목 | 구현 근거 | 판정 |
| --- | --- | --- |
| Workspace 도메인 JPA/JDBC 저장 | [backend/src/main/java/com/meetingmind/demo/domain/JpaWorkspaceStore.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/domain/JpaWorkspaceStore.java:1), [backend/src/main/java/com/meetingmind/demo/domain/JpaWorkspacePersistence.java](/Users/dongjoon/Downloads/MeetingMind/backend/src/main/java/com/meetingmind/demo/domain/JpaWorkspacePersistence.java:1) | 구현됨 |
| project AI history JPA store | `JpaProjectAiHistoryStore` 존재 | 구현됨 |

## 5. Backend/BFF에서 부분 구현된 기능

| 기능 | 현재 상태 | 부분 구현 이유 |
| --- | --- | --- |
| 실시간 LiveKit 회의 연결 | meeting별 livekit token 발급 가능 | 실제 live room UI, reconnect, participant state, media control 은 프론트 미연동 |
| STT 상태/전사 진행 중 갱신 | `start`, `stop`, `dialogue` 있음 | 진행 중 자막 push/polling contract, session 상태 조회 endpoint, UI 갱신 전략이 부족 |
| Project Knowledge 재색인 상태 | `embeddingStatus`, `embeddingJobId` 필드와 `PENDING` 설정 있음 | 실제 worker/queue/상태 전이 완료 근거가 현재 core API에서 보이지 않음 |
| Project AI context candidate 조회 | `/project-ai/context-candidates` 있음 | Make UI에는 미반영, chat 화면과 연결 안 됨 |
| 회의 취소 | `updateMeeting(status)` 와 `deleteMeeting -> CANCELED` 로 처리 가능 | 별도 cancel UX/API semantics는 없다 |
| 회의 초대 응답 | Space invitation은 존재 | Meeting invitation accept/decline canonical flow는 별도 endpoint로 보이지 않음 |
| loading/empty/error/permission denied 상태 | 서버는 4xx/5xx와 표준 오류 응답 제공 | Make UI는 이 상태를 실제 API와 아직 연결하지 않음 |
| 계정 보안 화면 | session/logout/logout-all/re-auth 는 존재 | profile/password/account-delete API는 현재 BFF 기준으로 없음 |

## 6. Backend/BFF에서 아직 구현되지 않은 기능

| 기능 | 현재 근거 |
| --- | --- |
| 프로젝트 가입 요청 | Space invitation 은 있으나, 사용자가 프로젝트 가입 요청을 보내고 owner/admin 이 승인하는 별도 API 없음 |
| 계정 프로필 조회/수정 API | `/api/v1/auth/*` 와 core API 어디에도 `/me`, profile update endpoint 확인 안 됨 |
| 비밀번호 재설정 메일 요청/토큰 소비 | 현재 BFF/auth 공개 endpoint 기준으로 확인 안 됨 |
| 로그인 상태 비밀번호 변경 | 현재 BFF/auth 공개 endpoint 기준으로 확인 안 됨 |
| 계정 탈퇴 | 현재 BFF/auth/core endpoint 기준으로 확인 안 됨 |
| 캘린더 알림 | 조회 API는 있으나 알림 API/설정/전달 경로 없음 |
| 회의별 초대 수락/거절 canonical URL | 참가 신청 승인 플로우는 있으나 초대 토큰 응답 endpoint 없음 |
| Live STT 진행 상태 조회 전용 API | start/stop/dialogue 외에 session status or live stream API 부재 |

## 7. 페이지별 연동 가능 여부

### 7.1 즉시 연동 가능

프론트 작업만 하면 붙일 수 있는 화면이다.

- `/login`
- `/spaces`
- `/spaces/:spaceId`
- `/spaces/:spaceId/meetings`
- `/spaces/:spaceId/meetings/:meetingId`
- `/spaces/:spaceId/meetings/:meetingId/transcript`
- `/spaces/:spaceId/meetings/:meetingId/report`
- `/spaces/:spaceId/meetings/:meetingId/tasks`
- `/spaces/:spaceId/meetings/:meetingId/ai`
- `/spaces/:spaceId/tasks`
- `/spaces/:spaceId/knowledge`
- `/spaces/:spaceId/ai`
- `/spaces/:spaceId/calendar`
- `/spaces/:spaceId/members`
- `/spaces/:spaceId/terms`
- `/space-invitations/:spaceId/:invitationId`

조건:

- Make mock id 를 실제 `space-{uuid}`, `meeting-{uuid}` 등으로 교체
- 세션 bootstrap + Protected Route 도입
- 각 화면의 local state 성공 처리 제거

### 7.2 Backend 보완 후 연동 가능

- `/meeting-access`
  - joinCode/meetingId source 전달 방식과 비로그인 진입 UX 보완 필요
- `/spaces/:spaceId/settings`
  - 현재 화면의 설정 항목 대부분에 대응 API 없음
- `/spaces/:spaceId/meetings/:meetingId/live/prejoin`
  - livekit token + meeting access + 장치 점검 연결 필요
- `/spaces/:spaceId/meetings/:meetingId/live`
  - live token, STT start/stop, dialogue refresh, 실패 상태 처리 필요
- `/settings`
  - 현재 Make 계정 설정은 profile/security/notification/session 을 한 화면에 담고 있지만, 실제 구현된 서버 기능은 세션 로그아웃 일부뿐

### 7.3 API 계약 또는 route 결정 필요

- `/settings`
  - refactor plan 기준 권장 route 는 `/settings/account`, `/settings/security` 인데, Make 는 `/settings` 단일 route
- 프로젝트 가입 요청
  - 요구사항에는 필요하지만 현재 server API 부재
- 회의 초대 응답 canonical flow
  - 참가 신청 승인만으로 갈지, invitation token 기반 응답도 둘지 결정 필요
- STT live 업데이트 방식
  - polling, SSE, websocket 중 브라우저 계약 정리 필요

## 8. 누락 기능 상세 목록

| 기능명 | 관련 화면/route | 필요한 요청/응답 데이터 | 현재 Backend 구현 상태 | 필요한 Backend 작업 | 권한 조건 | 우선순위 | 선행 작업 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 프로젝트 가입 요청 | 별도 화면 없음, 향후 `/spaces` 또는 invite flow | `spaceId`, 신청자, 상태, 승인/거절 응답 | 미구현 | Space join request API와 상태 모델 추가 | OWNER/ADMIN 승인 | P1 | 정책 결정 |
| 회의 초대 응답 | meeting invite deep link | `invitationId`, `token`, 수락/거절 결과 | 부분 구현 | meeting invitation entity/API 또는 참가 신청 흐름으로 단일화 결정 | HOST/OWNER/ADMIN 발급, 대상자 응답 | P1 | invitation 정책 결정 |
| 계정 프로필 수정 | `/settings` | `displayName`, `pictureUrl`, `/me` 응답 | 미구현 | BFF + auth/core profile read/update API 추가 | 본인만 수정 | P1 | storage 정책 결정 |
| 비밀번호 재설정 | `/login` 의 forgot password, 공개 reset flow | 이메일 요청, reset token, 새 비밀번호 저장 | 미구현 | auth runtime 공개 reset API, 메일 provider 연결 | 공개 진입, token 1회용 | P1 | SMTP/provider 설정 |
| 비밀번호 변경 | `/settings` | 기존 비밀번호, 새 비밀번호 | 미구현 | auth runtime + BFF endpoint 추가 | 로그인 본인, 정책 검증 | P2 | profile/security API 설계 |
| 계정 탈퇴 | `/settings` | 재인증, 탈퇴 결과, 단독 owner 보호 | 미구현 | auth/core 정책 + endpoint 추가 | 본인, recent auth 필요 | P2 | owner transfer 정책 |
| Project Knowledge 재색인 완료 상태 | `/spaces/:spaceId/knowledge` | `embeddingStatus`, `embeddingJobId`, 완료/실패 시각 | 부분 구현 | worker 완료 콜백 또는 job processor, 상태 전이 API/이벤트 연결 | Space member read, member-management write | P1 | embedding provider/worker 연결 |
| STT 진행 중 상태 조회 | live/prejoin/live/transcript | session 상태, last segment cursor, 진행률 또는 live rows | 부분 구현 | session status endpoint 또는 streaming contract 추가 | meeting read/manage 권한 | P1 | live/STT 전달 방식 결정 |
| 캘린더 알림 | dashboard/calendar/settings | 알림 설정, scheduled notification payload | 미구현 | notification scheduler + preference API | 사용자 본인 | P2 | account notification 정책 |
| 프로젝트 설정 세부 항목 | `/spaces/:spaceId/settings` | AI enable, auto-confirm, STT enable 등 | 대부분 미구현 | 실제 설정 모델이 필요한지 먼저 결정, 불필요 항목은 UI 제거 | OWNER/ADMIN | P2 | 제품 정책 정리 |
| loading/empty/error/403 전용 응답 기준 | 전 화면 | 표준 오류 code, empty collection, retry 가능성 | 부분 구현 | 화면별 empty/error semantics 문서화, 일부 404/409 case 보완 | 각 리소스 권한 동일 | P1 | frontend 상태 매핑 정의 |

## 9. 우선순위 기준 실제 시작점

현재 기준에서 바로 들어갈 순서는 다음이 가장 안전하다.

1. 인증 및 세션
   - `/login` 을 실제 BFF auth 에 연결
   - 앱 시작 시 `/api/v1/auth/session` bootstrap
   - Make 보호 route 가 세션 없이 열리지 않도록 막기

2. `/spaces`
   - Make workspace list 를 `fetchSpaces` + `fetchDashboardSummary` 로 교체
   - mock id 대신 실제 `spaceId` 로 라우팅

3. `/spaces/:spaceId` 및 회의 목록
   - 프로젝트명, 멤버 수, 회의 수, 최근 요약을 실제 데이터로 교체
   - `m-123` 같은 mock meeting id 제거

4. 멤버/권한, task, knowledge
   - 기존 API 모듈과 mutation 훅 재사용 가능성이 높음

5. report / task candidate / AI
   - source-aware 화면과 backend 계약이 이미 있으므로, UI만 맞추면 된다

6. live/STT
   - 외부 provider, session status, live update 처리 때문에 마지막에 붙이는 편이 안전하다

## 10. 핵심 결론

- 서버 기준으로는 인증, 프로젝트, 회의, 멤버, task, knowledge, report, task candidate, Meeting AI, Project AI 의 핵심 API가 이미 많이 준비돼 있다.
- 현재 병목은 “백엔드 부재”보다 “Make App이 실제 인증/권한/API 계층을 전혀 쓰지 않고 있는 상태”에 더 가깝다.
- 첫 연동 작업은 새 API를 만드는 것이 아니라, `Make App -> existing frontend auth/api modules -> BFF/core` 연결로 시작하는 것이 맞다.
- 바로 막히는 실제 이슈는 세 가지다.
  - 현재 Make mock route id 형식이 BFF allowlist와 맞지 않다.
  - 현재 Make App은 Protected Route와 session bootstrap이 없다.
  - 현재 Make success state 대부분이 local state 기반이라 API 실패/403/404 를 반영하지 못한다.
