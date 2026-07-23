# Frontend Refactor Plan

이 문서는 프론트엔드 대규모 변경을 사용자 흐름 기준으로 진행하기 위한 작업 기준이다.

화면 파일을 먼저 나누지 않는다. 사용자가 MeetingMind에 들어와 프로젝트를 만들고, 회의에 참여하고, 회의 결과를 지식과 업무로 바꾸는 전체 흐름을 먼저 고정한다. 각 리팩토링 단계는 이 흐름 중 하나를 완성하고 검증해야 한다.

## 1. 목표와 기준

### 목표

- 사용자가 현재 위치와 다음 행동을 쉽게 이해할 수 있게 한다.
- Space, Meeting, AI 기능의 범위를 URL과 화면 구조에서 분명하게 나눈다.
- 실제 API 실패를 mock 성공처럼 보이지 않게 한다.
- 기존 초대 링크와 회의 참가 링크가 리팩토링 중에도 계속 동작하게 한다.
- 큰 화면과 전역 상태를 사용자 흐름 단위로 나눈다.

### 고정 기준

- 제품 화면에서는 `Space`를 "프로젝트"로 표시한다.
- `/`만 공개 화면으로 사용한다. 비밀번호 재설정도 `/`의 공개 인증 mode에서 처리하고 나머지 화면은 로그인이 필요하다.
- 비로그인 사용자가 보호 화면에 접근하면 로그인 후 원래 주소로 돌아온다.
- Space ID와 Meeting ID는 path parameter로 전달한다.
- 검색, 필터, 탭처럼 화면 표시만 바꾸는 값은 query parameter로 전달한다.
- BFF same-origin `/api` 호출, CSRF 처리, 서버 세션 구조를 유지한다.
- 브라우저 JavaScript에 access/refresh token을 노출하지 않는다.
- API 권한 검사를 최종 기준으로 사용한다. 화면의 버튼 숨김은 보조 처리다.
- Meeting AI는 현재 Meeting만 사용한다.
- Project AI는 현재 Space의 공식 지식과 사용자가 접근 가능한 Meeting만 사용한다.
- AI 답변은 출처를 표시하고, 근거가 없으면 답변할 수 없음을 표시한다.
- target API 실패 시 mock 또는 local state로 성공 처리하지 않는다.
- 새 상태 관리 라이브러리는 이번 리팩토링에서 추가하지 않는다. React context와 hook으로 시작하고, 실제 문제가 확인되면 별도 결정한다.

## 2. 사용자 구분

| 사용자 | 가능한 주요 행동 |
| --- | --- |
| 비로그인 사용자 | 랜딩 확인, 로그인/회원가입 시작, 초대·참가 주소 진입 후 로그인 |
| 로그인 사용자 | 프로젝트 목록 조회, 프로젝트 생성, 초대 응답, 회의 참가 신청 |
| Space OWNER | 프로젝트 수정/삭제, 멤버·역할 관리, 오너 이양, 회의 생성, 전체 프로젝트 기능 사용 |
| Space ADMIN | 허용된 프로젝트·멤버·회의 관리, Project Knowledge와 용어 관리 |
| Space MEMBER | 허용된 프로젝트 기능 조회, 자신에게 허용된 회의와 Project AI 사용 |
| Meeting HOST | 회의 운영, 참가 신청 처리, 회의 산출물 편집 |
| Meeting EDITOR | 허용된 회의 산출물 조회·편집, AI 태스크 후보 처리 |
| Meeting VIEWER/게스트 | 허용된 단일 회의의 실시간 회의, STT, 회의록, Meeting AI 조회 |

한 사용자가 Space role과 Meeting role을 동시에 가질 수 있다. 화면은 둘을 합쳐 하나의 역할처럼 처리하지 않는다.

## 3. 사용자 기준 시스템 흐름

### UF-01 로그인과 원래 화면 복귀

```text
보호 화면 직접 접근
→ 세션 확인
→ 세션 없음
→ 로그인
→ 세션 생성
→ 처음 요청한 path + query + hash로 복귀
```

- 일반 로그인 성공 후 별도 복귀 주소가 없으면 `/app`으로 이동한다.
- `/app`은 세션을 다시 확인한 뒤 `/spaces`로 이동한다.
- 현재 API에는 기본 Space 개념이 없으므로 임의의 첫 Space로 자동 이동하지 않는다.
- 세션 만료 시 현재 주소를 보존하고 로그인 화면을 연다.
- `401`은 세션 정리 후 로그인 흐름으로 보낸다.

### UF-02 프로젝트 조회와 생성

```text
/spaces
→ 참여 프로젝트 조회
→ 프로젝트 없음: 생성 화면
→ 프로젝트 생성
→ /spaces/{spaceId}
```

- 프로젝트 목록은 활성 `SpaceMember` 범위만 표시한다.
- 프로젝트 생성자는 OWNER가 된다.
- 목록 일부 데이터가 실패해도 성공한 데이터를 유지하고 실패 영역을 따로 표시한다.
- 프로젝트 선택 결과는 URL의 `spaceId`로 고정한다.

### UF-03 Space 초대 수락과 거절

```text
/space-invitations/{spaceId}/{invitationId}#token=...
→ 로그인 필요 시 전체 주소 보존
→ 초대 대상 확인
→ 수락 또는 거절
→ 수락: /spaces/{spaceId}
→ 거절: /spaces
```

- API 명세가 발급하는 `/space-invitations/{spaceId}/{invitationId}#token=...`을 canonical 주소로 유지한다.
- token은 fragment에서 읽어 API body에만 전달한다.
- token을 query, log, 전역 상태에 저장하지 않는다.
- `/invitations/spaces/*` 주소는 새 canonical 주소로 사용하지 않는다.
- 만료, 사용 완료, 이메일 불일치는 각각 명확한 실패 상태를 표시한다.

### UF-04 회의 생성과 관리

```text
/spaces/{spaceId}/meetings
→ 회의 생성
→ 참여자 지정
→ /spaces/{spaceId}/meetings/{meetingId}
→ 일정·설명·권한 관리
```

- OWNER 또는 허용된 관리자가 회의를 생성한다.
- 회의 상세는 Space role과 Meeting role을 각각 표시한다.
- 회의 수정, 삭제, 참가자 역할 변경은 서버 응답 성공 후 화면에 반영한다.
- 권한 없는 버튼은 숨기거나 비활성화하지만, API `403`도 별도로 처리한다.

### UF-05 회의 링크 또는 코드로 참가

```text
초대받은 /meetings/{meetingId}?joinCode=...
또는 /meeting-access에서 코드 입력
→ 로그인
→ 참가 신청
→ 승인 대기
→ HOST/OWNER/ADMIN 승인
→ 회의 상세 조회로 spaceId 확인
→ /spaces/{spaceId}/meetings/{meetingId}/live/prejoin
```

- Meeting API가 반환하는 `/meetings/{meetingId}?joinCode=...`를 호환 진입 주소로 유지한다.
- 이 주소는 `meetingId`와 `joinCode`를 `/meeting-access` 흐름으로 전달한다.
- 승인 전에는 Meeting 데이터, STT, 회의록, AI 컨텍스트를 조회하지 않는다.
- 회의 참가 승인은 Meeting 접근만 만들고 Space membership을 만들지 않는다.
- 승인 후 Meeting 상세 응답으로 `spaceId`를 확인한 뒤 target route로 이동한다.

### UF-06 실시간 회의와 STT

```text
회의 상세
→ prejoin 장치 점검
→ LiveKit 입장
→ STT 시작
→ 실시간 자막 표시
→ 회의 종료
→ transcript PROCESSING
→ transcript COMPLETED
```

- prejoin에서 카메라, 마이크, 표시 이름을 점검한다.
- Live room 진입 전에 Meeting 접근 권한을 확인한다.
- 실시간 화면은 일반 AppShell 대신 회의 집중 레이아웃을 사용할 수 있다.
- STT 처리 중에는 진행 상태를 표시하고 이전 회의 자막을 대신 표시하지 않는다.
- STT 실패 시 재시도 가능 여부와 오류를 표시한다.
- 회의 종료 후 같은 Meeting의 transcript 화면으로 이동한다.

### UF-07 회의록과 태스크 생성

```text
transcript COMPLETED
→ AI 회의록 candidate 생성
→ 검토·수정
→ 공식 회의록 확정
→ 태스크 candidate 생성
→ 검토·확정
→ /spaces/{spaceId}/tasks
```

- 회의록 candidate와 공식 회의록을 구분한다.
- 과거 회의록 이력 조회와 현재 편집 대상을 분리한다.
- 복원은 기존 이력을 덮어쓰지 않고 새 draft를 만든다.
- Markdown, DOCX, PDF 다운로드를 제공한다.
- 회의 게스트는 프로젝트 칸반 태스크를 확정할 수 없다.

### UF-08 Meeting AI와 Project AI

```text
Meeting AI
→ /spaces/{spaceId}/meetings/{meetingId}/ai
→ 현재 Meeting 근거만 검색

Project AI
→ /spaces/{spaceId}/ai
→ 공식 Project Knowledge + 접근 가능한 Meeting 검색
```

- 두 AI는 화면, URL, 요청 API, 출처 표시를 분리한다.
- 출처를 선택하면 현재 권한 범위 안의 원문 위치로 이동한다.
- 근거 부족, 권한 부족, AI 서비스 오류를 서로 다른 상태로 표시한다.
- 이전 대화가 현재 권한 밖 데이터를 다시 사용하지 않게 한다.

### UF-09 프로젝트 운영

```text
프로젝트 홈
├─ 캘린더
├─ 칸반
├─ Project AI
├─ Project Knowledge
├─ 멤버·권한
├─ 용어 사전
└─ 프로젝트 설정
```

- 프로젝트 수정은 OWNER/ADMIN, 삭제는 OWNER만 가능하다.
- 멤버 역할 변경, 제거, 오너 이양은 확인 절차를 거친다.
- Project Knowledge와 용어 사전은 조회 권한과 수정 권한을 나눠 표시한다.
- 프로젝트 삭제 후 목록, 검색, AI 검색 결과에서 제외됐음을 확인한다.

### UF-10 계정과 세션 관리

```text
계정 설정
→ 프로필 수정
→ 비밀번호 변경
→ 현재 기기 로그아웃 / 모든 기기 로그아웃
→ 필요 시 계정 탈퇴
```

- 본인 계정 정보만 수정한다.
- 모든 기기 로그아웃 성공 후 현재 브라우저 세션도 제거한다.
- 계정 탈퇴 전 단독 OWNER 프로젝트가 있으면 오너 이양을 안내한다.

비밀번호를 잊은 사용자는 별도 공개 흐름을 사용한다.

```text
/
→ 비밀번호 재설정 메일 요청
→ SMTP 메일의 1회용 링크
→ /?mode=password-reset#token=...
→ 새 비밀번호 저장
→ 로그인
```

- 재설정 token은 query가 아닌 fragment로 전달하고 API body에서만 사용한다.
- 존재하는 이메일인지 외부에 구분해서 알리지 않는다.
- 만료되거나 이미 사용된 token은 다시 사용할 수 없다.
- Auth API와 SMTP 메일 링크 계약을 먼저 갱신한 뒤 화면을 구현한다.

## 4. 목표 주소 구조

### 기본 및 계정

| 주소 | 목적 | 보호 |
| --- | --- | --- |
| `/` | 랜딩, 로그인·회원가입 진입 | 공개 |
| `/?mode=password-reset#token=...` | 비밀번호 재설정 | 공개, 1회용 token |
| `/app` | 로그인 후 분기 | 로그인 |
| `/settings/account` | 프로필과 계정 관리 | 로그인 |
| `/settings/security` | 비밀번호와 세션 관리 | 로그인 |

### 프로젝트

| 주소 | 목적 |
| --- | --- |
| `/spaces` | 프로젝트 목록과 전체 대시보드 |
| `/spaces/:spaceId` | 프로젝트 홈 |
| `/spaces/:spaceId/calendar` | 프로젝트 일정 |
| `/spaces/:spaceId/meetings` | 회의 목록 |
| `/spaces/:spaceId/tasks` | 프로젝트 칸반 |
| `/spaces/:spaceId/ai` | Project AI |
| `/spaces/:spaceId/knowledge` | Project Knowledge |
| `/spaces/:spaceId/members` | 멤버와 Space 권한 |
| `/spaces/:spaceId/terms` | 용어 사전 |
| `/spaces/:spaceId/settings` | 프로젝트 수정, 삭제, 오너 이양 |

### 회의

| 주소 | 목적 |
| --- | --- |
| `/spaces/:spaceId/meetings/:meetingId` | 회의 상세 |
| `/spaces/:spaceId/meetings/:meetingId/live/prejoin` | 장치 점검 |
| `/spaces/:spaceId/meetings/:meetingId/live` | 실시간 회의 |
| `/spaces/:spaceId/meetings/:meetingId/transcript` | STT 기록 |
| `/spaces/:spaceId/meetings/:meetingId/report` | 회의록과 이력 |
| `/spaces/:spaceId/meetings/:meetingId/tasks` | AI 태스크 후보 검토 |
| `/spaces/:spaceId/meetings/:meetingId/ai` | Meeting AI |

### 초대와 참가

| 주소 | 목적 | 처리 |
| --- | --- | --- |
| `/space-invitations/:spaceId/:invitationId` | Space 초대 응답 | canonical, hash token 유지 |
| `/meetings/:meetingId` | API가 발급한 회의 참가 링크 | `/meeting-access` 흐름으로 연결 |
| `/meeting-access` | URL·코드 참가 신청과 승인 상태 확인 | query의 `meetingId`, `joinCode` 허용 |

## 5. 기존 주소 전환 규칙

기존 주소는 target 화면이 준비된 뒤 redirect alias로 전환한다. 필요한 ID가 없으면 mock 값으로 보완하지 않고 `/spaces` 또는 `/meeting-access`로 이동하며 안내를 표시한다.

| 기존 주소 | target 주소 |
| --- | --- |
| `/project-overview?spaceId={spaceId}` | `/spaces/{spaceId}` |
| `/team-members?spaceId={spaceId}` | `/spaces/{spaceId}/members` |
| `/terms?spaceId={spaceId}` | `/spaces/{spaceId}/terms` |
| `/live-meeting?spaceId={spaceId}&meetingId={meetingId}` | `/spaces/{spaceId}/meetings/{meetingId}/live/prejoin` |
| `/live-room?meetingId={meetingId}` | Meeting 상세로 `spaceId` 확인 후 target live 주소 |
| `/meeting-ai?meetingId={meetingId}` | Meeting 상세로 `spaceId` 확인 후 target AI 주소 |
| `/report-agent?meetingId={meetingId}` | Meeting 상세로 `spaceId` 확인 후 target report 주소 |

- redirect는 path, 필요한 query, 초대 fragment를 보존한다.
- `/space-invitations/*`, `/meetings/:meetingId`, `/meeting-access`는 호환 주소 자체가 사용자 계약이므로 제거하지 않는다.
- alias 사용량을 확인할 수 있도록 개발 환경에서 경고를 남길 수 있지만 token과 개인정보는 기록하지 않는다.

## 6. 화면 상태와 오류 흐름

모든 데이터 화면은 아래 상태를 구분한다.

| 상태 | 화면 처리 |
| --- | --- |
| loading | 기존 다른 사용자·Space 데이터를 표시하지 않고 로딩 상태 표시 |
| empty | 생성 또는 다음 행동을 안내하는 빈 상태 표시 |
| error | 오류 메시지와 재시도 제공, 성공으로 처리하지 않음 |
| forbidden | 권한 없음 안내와 돌아갈 주소 제공 |
| not found | 삭제됐거나 없는 대상 안내 |
| conflict | 최신 데이터 재조회 후 사용자가 다시 결정하도록 안내 |
| session expired | 현재 주소 보존 후 로그인 |

공통 오류 기준은 다음과 같다.

- `400`: 입력 항목 오류 표시
- `401`: 세션 정리 후 로그인
- `403`: 권한 없음 화면
- `404`: 대상 없음 화면
- `409`: 충돌 안내 후 서버 데이터 재조회
- `5xx` 또는 네트워크 오류: 재시도 제공, mock fallback 금지

React Error Boundary는 렌더링 오류만 처리한다. API 오류는 각 loader/hook이 `DataState`로 전달한다.

## 7. 상태와 API 구조

### 상태 소유 기준

| 상태 | 소유 위치 |
| --- | --- |
| 로그인 세션 | `AuthProvider`, `useAuthSession` |
| 현재 Space | route `spaceId`, `SpaceLayout` |
| 현재 Meeting | route `meetingId`, `MeetingLayout` |
| 목록·상세 데이터 | 각 route page hook |
| modal/form 임시값 | 해당 컴포넌트 local state |
| LiveKit 연결과 장치 상태 | `useLiveRoom` |

- Space와 Meeting 선택을 이름이나 배열 첫 항목으로 추정하지 않는다.
- 수정 성공 후 관련 목록 또는 상세를 서버에서 다시 조회한다.
- route parameter가 바뀌면 이전 요청을 취소하거나 늦게 도착한 응답을 버린다.
- 같은 데이터를 `App.tsx`와 page local state에 중복 저장하지 않는다.
- 이번 단계에서는 별도 client cache 라이브러리를 추가하지 않는다.

### API 파일 분리

| 파일 | 범위 |
| --- | --- |
| `api/client.ts` | `bffFetch`, JSON/error 변환 공통 처리 |
| `api/spaces.ts` | Space CRUD, 멤버, 초대, 오너 이양 |
| `api/dashboard.ts` | 대시보드 요약 |
| `api/calendar.ts` | 캘린더 조회 |
| `api/meetings.ts` | Meeting CRUD와 상세 |
| `api/meetingAccess.ts` | participant, join request, invitation |
| `api/live.ts` | LiveKit token과 live 상태 |
| `api/transcripts.ts` | STT 시작·종료·dialogue·speaker |
| `api/reports.ts` | 회의록 생성·확정·이력·다운로드 |
| `api/tasks.ts` | project task와 task candidate |
| `api/ai.ts` | Meeting AI와 Project AI |
| `api/knowledge.ts` | Project Knowledge |
| `api/terms.ts` | 용어 사전과 용어 설명 |

분리 중에는 `api/workspace.ts`가 기존 export를 다시 내보내는 임시 facade 역할을 한다. 모든 호출부 전환과 테스트가 끝난 뒤 제거한다.

## 8. 레이아웃과 공통 컴포넌트

### 레이아웃

- `AppProviders`: auth와 공통 렌더링 오류 처리
- `ProtectedRoute`: 세션 확인, 로그인 연결, 원래 주소 보존
- `AppShell`: sidebar, topbar, 프로젝트 선택, page outlet
- `SpaceLayout`: Space 정보, Space role, 프로젝트 메뉴
- `MeetingLayout`: Meeting 정보, Meeting role/status, 회의 메뉴
- `LiveMeetingLayout`: 실시간 회의 집중 화면

### 공통 컴포넌트

| 컴포넌트 | 목적 |
| --- | --- |
| `PageHeader` | 제목, 보조 정보, 주요 행동 |
| `Toolbar` | 검색, 필터, 정렬, 보기 전환 |
| `DataState` | loading, empty, error, forbidden, not found |
| `ConfirmDialog` | 삭제, 오너 이양, 모든 기기 로그아웃 |
| `FormField` | label, 설명, 입력 오류 |
| `IconButton` | 아이콘 버튼과 tooltip |
| `SegmentedControl` | 월/주/일 등 보기 전환 |
| `StatusBadge` | Meeting, transcript, report, task 상태 |
| `RoleBadge` | Space role과 Meeting role |
| `SourceCitationList` | AI 답변 출처 |
| `TranscriptPanel` | STT segment 목록 |
| `ReportEditor` | 공식 회의록 편집 |
| `KanbanBoard` | TaskCard 컬럼과 drag-and-drop |

## 9. 현재 구조와 변경 대상

### 요구사항 연결

| 사용자 흐름 | 주요 요구사항 |
| --- | --- |
| UF-01 로그인과 복귀 | `FR-AUTH-01`~`FR-AUTH-10`, `FR-AUTH-14`, `FR-AUTH-16` |
| UF-02 프로젝트 조회와 생성 | `FR-DASH-01`~`FR-DASH-07` |
| UF-03 Space 초대 | `FR-PERM-02`, `FR-PERM-05` |
| UF-04 회의 생성과 관리 | `FR-MREG-*`, `FR-ACL-*` |
| UF-05 회의 참가 | `FR-MREG-03`, `FR-ACL-*`의 참가 신청·승인 기준 |
| UF-06 실시간 회의와 STT | `FR-CALL-*`, `FR-STT-*` |
| UF-07 회의록과 태스크 | `FR-RPT-*`, `FR-TASK-*`, `FR-KAN-*` |
| UF-08 Meeting/Project AI | `FR-MBOT-*`, `FR-PBOT-*` |
| UF-09 프로젝트 운영 | `FR-CAL-*`, `FR-PERM-*`, `FR-OWN-*`, `FR-TERM-*` |
| UF-10 계정과 세션 | `FR-AUTH-09`, `FR-AUTH-11`~`FR-AUTH-13`, `FR-AUTH-17`, `FR-AUTH-18` |

구현 task는 위 사용자 흐름과 요구사항 ID를 함께 표시한다.

| 파일 | 현재 문제 | 변경 방향 |
| --- | --- | --- |
| `frontend/src/App.tsx` | route, auth, mock, loader, mutation을 모두 담당 | route bootstrap만 남김 |
| `frontend/src/api/workspace.ts` | 모든 도메인 API가 한 파일에 있음 | 도메인별 API 파일로 분리 후 facade 제거 |
| `frontend/src/types.ts` | legacy mock과 target type 혼합 | 공통·도메인·legacy type 분리 |
| `frontend/src/styles/app.css` | 모든 화면 스타일 혼합 | shell, space, meeting, live, report, ai로 분리 |
| `frontend/src/pages/ProjectOverviewPage.tsx` | 프로젝트 기능 대부분을 한 화면에서 처리 | 사용자 흐름별 route page로 분리 |
| `frontend/src/pages/ReportAgentPage.tsx` | report, history, AI edit, task candidate 혼합 | Meeting report와 task candidate 화면으로 분리 |
| `frontend/src/components/WorkspaceSidebar.tsx` | query 주소 생성과 프로젝트 생성까지 담당 | AppShell 탐색 역할만 유지 |

제거 대상은 다음과 같다.

- 로그인 후 target 화면의 mock 성공 fallback
- `App.tsx`의 mock seed map과 대량 mutation handler
- 신규 target 화면의 `/api/workspace` 의존
- app shell의 구독 만료 마케팅 패널
- 화면별 query string 조립 중복
- Backend API가 있는 동작의 page-local fake success

## 10. 구현 순서

각 단계는 독립적으로 build와 핵심 흐름 검증을 통과한 뒤 다음 단계로 이동한다.

| 단계 | 작업 | 완료 기준 |
| --- | --- | --- |
| 0 | 기존 흐름 보호 테스트 작성 | 로그인 복귀, 초대 hash, 참가 링크, 기존 주요 주소 테스트 통과 |
| 1 | API client와 type을 도메인별로 분리 | 동작 변경 없이 unit test와 build 통과 |
| 2 | `AuthProvider`, `DataState`, 공통 오류 처리 추가 | 401/403/404/409/5xx 화면 상태 검증 |
| 3 | `AppShell`, `SpaceLayout`, target Space route 추가 | 프로젝트 목록→생성→상세 흐름 통과 |
| 4 | 기존 Space 화면을 meetings/tasks/ai/knowledge/members/terms/settings로 분리 | Space role별 행동과 직접 주소 새로고침 통과 |
| 5 | `MeetingLayout`과 Meeting target route 추가 | 회의 상세→prejoin→live→transcript 흐름 통과 |
| 6 | report/tasks/Meeting AI를 Meeting 하위로 이동 | transcript→report→task, Meeting AI 범위 검증 |
| 7 | 기존 주소를 redirect alias로 전환 | 필요한 ID와 fragment 보존, 오래된 링크 동작 |
| 8 | mock fallback과 legacy 전역 상태 제거 | API 실패가 성공처럼 보이지 않고 `/api/workspace` 미사용 |
| 9 | CSS를 화면 영역별로 분리 | desktop/mobile에서 겹침과 잘림 없음 |
| 10 | 전체 사용자 흐름 E2E | 아래 E2E 기준 통과 |

구현 전 `tasks.md`에 다음을 기록한다.

- 사용자 흐름별 task
- owner와 agent
- dependency
- 예상 수정 파일
- 완료 기준과 검증 명령

## 11. 검증 기준

### 자동 검증

- `cd frontend && npm run lint`
- `cd frontend && npm run test`
- `cd frontend && npm run build`
- `cd frontend && npm run test:e2e`

E2E 파일은 기존 `frontend/e2e`에 추가한다.

### 필수 사용자 흐름 E2E

- 비로그인 사용자가 보호 주소 접근 후 로그인하면 원래 주소로 복귀한다.
- Space 초대 주소의 hash token이 로그인과 화면 전환 중 노출·유실되지 않는다.
- Space가 없는 사용자가 프로젝트를 만들고 프로젝트 홈으로 이동한다.
- 프로젝트 수정·삭제와 멤버·오너 관리가 role에 맞게 동작한다.
- API가 발급한 `/meetings/{meetingId}?joinCode=...` 주소로 참가 신청할 수 있다.
- 승인 전 Meeting 데이터 접근이 차단되고 승인 후 prejoin으로 이동한다.
- 회의 상세→prejoin→live→STT→transcript 흐름이 같은 Meeting ID를 유지한다.
- transcript 완료 후 회의록 생성·확정·수정·이력·다운로드가 동작한다.
- 태스크 candidate 확정 후 프로젝트 칸반에 표시된다.
- Meeting AI는 다른 Meeting의 근거를 표시하지 않는다.
- Project AI는 접근 가능한 Meeting과 공식 Project Knowledge만 사용한다.
- `401`, `403`, `404`, `409`, 네트워크 오류가 mock 성공으로 바뀌지 않는다.
- 기존 주요 주소가 target 주소로 올바르게 연결된다.

## 12. 완료 조건

- 사용자의 주요 흐름이 target route만으로 끝까지 이어진다.
- `App.tsx`가 route와 bootstrap 외 도메인 상태를 소유하지 않는다.
- target 화면이 `/api/workspace`와 mock fallback을 사용하지 않는다.
- Space role과 Meeting role이 화면에서 분리되어 표시된다.
- 새로고침과 직접 주소 접근에서도 같은 데이터를 표시한다.
- 초대, 참가, 권한, AI 범위 회귀 테스트가 통과한다.
- 관련 `tasks.md`와 `implement.md`에 작업 상태와 검증 결과가 기록된다.

## 13. Product Design Read

이번 리팩토링은 단순 시각 개선이 아니라 MeetingMind를 실제 업무형 SaaS로 재구성하는 작업이다.

현재 코드 기준 주요 관찰은 다음과 같다.

| 항목 | 현재 코드 기준 | 설계 판단 |
| --- | --- | --- |
| Route | `App.tsx`가 `/spaces`, `/project-overview`, `/team-members`, `/terms`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`를 직접 연결한다. | route가 업무 컨텍스트를 충분히 표현하지 못한다. target path parameter 구조로 전환한다. |
| App 상태 | `App.tsx`가 auth, mock seed, API loader, mutation handler, page prop 조립을 함께 담당한다. | route bootstrap과 provider만 남기고 page별 data hook으로 분리한다. |
| Project 화면 | `ProjectOverviewPage.tsx`가 회의, ACL, 칸반, Project AI, Knowledge, 설정을 한 화면에서 처리한다. | Project Home은 요약 화면으로 축소하고 기능 화면은 하위 route로 분리한다. |
| Report 화면 | `ReportAgentPage.tsx`가 report, AI edit, version, download, task candidate를 함께 처리한다. | Meeting Report와 Task Candidate를 분리하되, 같은 MeetingLayout 안에서 연결한다. |
| Navigation | `WorkspaceSidebar.tsx`가 query 주소 생성, 프로젝트 생성 modal, 구독 패널까지 담당한다. | AppShell 탐색 역할만 남기고 생성 modal은 page action 또는 command로 이동한다. |
| 타입 | `types.ts`에 target enum과 legacy mock type이 함께 존재한다. | 상태 모델은 target enum 기준으로 쓰고, legacy type은 제거 전까지 adapter 경계에 둔다. |
| Design system | 현재 `motion`, React Router, vanilla CSS 기반이다. Tailwind와 shadcn/ui는 아직 dependency에 없다. | token은 Tailwind와 shadcn/ui로 이식 가능한 형태로 정의하되, 실제 도입은 별도 dependency 결정 후 진행한다. |

설계 모드는 `Redesign - Overhaul with functionality preserved`로 본다.

- UX 근거: 사용자가 현재 위치, 권한, 다음 행동을 항상 알 수 있어야 한다.
- Product 근거: MeetingMind의 가치는 회의가 Project Knowledge와 Task로 축적되는 흐름이다.
- 유지보수 근거: route, layout, 상태, 컴포넌트를 분리하지 않으면 기능 추가 때 `App.tsx`와 대형 page 파일이 계속 커진다.

## 14. State Model

화면은 서버 entity status를 그대로 노출하기보다, status와 권한을 조합한 `view state`로 동작해야 한다. 상태 전이는 API 성공 응답을 기준으로 확정하며, local optimistic success는 사용하지 않는다.

### 14.1 Project / Space

현재 frontend type은 `SpaceSummary.role`을 가지지만 Space 자체 status는 노출하지 않는다. 따라서 UI 상태는 조회 결과와 권한으로 계산한다.

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| Empty | `/spaces` 조회 결과가 0건 | 프로젝트 생성 성공 | 프로젝트 생성 | Active |
| Active | 사용자가 active SpaceMember이고 Space 조회 성공 | 삭제, 멤버 제거, 세션 만료 | 상세 조회, 회의 조회, Task 조회, AI 진입 | Updating, Deleting, Forbidden |
| Updating | OWNER/ADMIN이 프로젝트 정보 저장 요청 | update API 성공 또는 실패 | 저장 취소, 재시도 | Active |
| Deleting | OWNER가 삭제 확인 후 delete 요청 | delete API 성공 또는 실패 | 삭제 확정, 취소 | Deleted, Active |
| Deleted | delete API 성공 | 없음 | 목록으로 이동 | Empty 또는 Active |
| Forbidden | SpaceMember가 아니거나 권한 없음 | 권한 부여 또는 다른 프로젝트 이동 | 돌아가기, 접근 요청 안내 | Active |
| NotFound | Space가 없거나 삭제됨 | 없음 | `/spaces`로 이동 | Empty 또는 Active |

### 14.2 Member / Space Role

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| OWNER | Space 생성자 또는 오너 이양 수신자 | 오너 이양 성공 | 모든 Project 관리, 멤버 관리, 삭제 | ADMIN 또는 MEMBER |
| ADMIN | OWNER가 부여 | OWNER가 role 변경 또는 제거 | 회의 생성, 멤버 초대, Knowledge 관리 | OWNER, MEMBER, Removed |
| MEMBER | 초대 수락 또는 role 변경 | role 변경 또는 제거 | Project 조회, 허용된 회의 접근, Project AI | ADMIN, Removed |
| Removed | SpaceMember 제거 성공 | 재초대 수락 | 접근 불가 안내 | MEMBER |

### 14.3 Space Invitation

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| PENDING | 초대 생성 직후 | 수락, 거절, 만료 | 수락, 거절 | ACCEPTED, DECLINED, EXPIRED |
| ACCEPTED | 초대 대상 수락 성공 | 없음 | 프로젝트로 이동 | Space Active |
| DECLINED | 초대 대상 거절 성공 | 없음 | `/spaces`로 이동 | 없음 |
| EXPIRED | 만료 시간 도달 | 재초대 | 재초대 요청 안내 | PENDING |

### 14.4 Meeting

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| SCHEDULED | 회의 생성 성공 | 시작, 취소, 삭제 | 정보 수정, 참여자 관리, prejoin 진입 | IN_PROGRESS, CANCELED, Deleted |
| IN_PROGRESS | LiveKit 회의 시작 또는 status 변경 | 종료 | live 입장, STT 시작/조회, 참여자 상태 확인 | ENDED |
| ENDED | HOST 종료 또는 시스템 종료 | 없음 | transcript 조회, report 생성, Meeting AI | 없음 |
| CANCELED | 예정 회의 취소 | 없음 | 조회, 재생성 안내 | 없음 |
| Deleted | delete API 성공 | 없음 | 회의 목록으로 이동 | 없음 |
| Forbidden | MeetingParticipant가 없고 override도 없음 | 권한 부여 | 참가 신청, 돌아가기 | SCHEDULED, IN_PROGRESS, ENDED |

### 14.5 Meeting Join Request

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| PENDING | URL 또는 코드로 참가 신청 생성 | 승인, 거절 | 신청 상태 확인 | APPROVED, REJECTED |
| APPROVED | HOST 또는 OWNER/ADMIN 승인 | MeetingParticipant 생성 | prejoin 이동 | Meeting 접근 가능 |
| REJECTED | 승인자가 거절 | 재신청 정책에 따름 | 안내 확인, 다른 회의 참가 | PENDING |

### 14.6 Meeting Participant

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| ACTIVE VIEWER | 승인 또는 수동 부여 | role 변경, revoke | 회의 조회, live 입장, transcript/report/Meeting AI 조회 | ACTIVE EDITOR, ACTIVE HOST, REVOKED |
| ACTIVE EDITOR | role 변경 또는 수동 부여 | role 변경, revoke | 산출물 편집, task candidate 추출 | ACTIVE VIEWER, ACTIVE HOST, REVOKED |
| ACTIVE HOST | 회의 생성자 또는 role 변경 | role 변경, revoke | 회의 운영, 종료, 신청 승인 | ACTIVE EDITOR, REVOKED |
| REVOKED | 접근 회수 | 재부여 | 접근 불가 안내 | ACTIVE VIEWER |

마지막 ACTIVE HOST 제거는 허용하지 않는다.

### 14.7 Transcript

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| PENDING | 회의 생성 후 STT 시작 전 또는 회의 종료 직후 | STT 시작 | STT 시작, 대기 안내 | PROCESSING |
| PROCESSING | STT 작업 시작 | 완료 또는 실패 | 진행 상태 보기, 새로고침, polling | COMPLETED, FAILED |
| COMPLETED | segment 저장 완료 | 없음 | 조회, 검색, 다운로드, report 생성 | 없음 |
| FAILED | STT provider 또는 저장 실패 | 재시도 성공 | 오류 보기, 재시도 | PROCESSING |

### 14.8 Report

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| NotGenerated | transcript 완료 전 또는 report 없음 | candidate 생성 | 생성 안내 | CANDIDATE |
| CANDIDATE | AI 회의록 생성 성공 | 확정, 편집, 폐기 | 검토, AI 수정, 수동 편집, 확정 | DRAFT, CONFIRMED |
| DRAFT | 사용자가 편집 중 | 저장, 확정 | 수동 저장, 버전 복원, 다운로드 | DRAFT, CONFIRMED |
| CONFIRMED | 공식 회의록 확정 | 새 draft 생성 | 조회, 다운로드, 새 초안 복원 | DRAFT |
| Failed | 생성 또는 저장 실패 | 재시도 성공 | 재시도, 오류 확인 | CANDIDATE, DRAFT |

회의별 current CONFIRMED report는 최대 1개라는 ERD 기준을 따른다.

### 14.9 Task Candidate

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| NotExtracted | 후보 없음 | 후보 추출 | 추출 시작 | CANDIDATE |
| CANDIDATE | AI 태스크 후보 생성 | 확정, 제외, 만료 | 제목/담당자/기한 수정, 확정, 제외 | CONFIRMED, DISMISSED, Expired |
| CONFIRMED | TaskCard 생성과 함께 확정 | 없음 | 연결된 Task 보기 | TaskCard TODO |
| DISMISSED | 사용자가 등록 제외 | 필요 시 재추출 | 제외 상태 표시 | CANDIDATE |
| Expired | 생성 후 정책상 확정 가능 기간 경과 | 재추출 | 재추출 안내 | CANDIDATE |

### 14.10 Task Card

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| TODO | 카드 생성 기본 상태 | 상태 변경, 삭제 | 편집, 담당자 지정, 진행으로 이동 | IN_PROGRESS, DONE, Deleted |
| IN_PROGRESS | 드래그 또는 상태 변경 | 상태 변경, 삭제 | 편집, 완료 이동 | TODO, DONE, Deleted |
| DONE | 완료 상태 변경 | 상태 변경, 삭제 | 편집, 재오픈 | TODO, IN_PROGRESS, Deleted |
| Deleted | 삭제 성공 | 없음 | 목록에서 제거 | 없음 |

### 14.11 Project Knowledge

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| PUBLISHED + PENDING | 공식 지식 생성 또는 수정 직후 | embedding worker 시작 | 조회, 수정 제한 안내 | PUBLISHED + PROCESSING |
| PUBLISHED + PROCESSING | embedding 작업 중 | 완료 또는 실패 | 조회, AI 검색 준비 중 표시 | PUBLISHED + COMPLETED, PUBLISHED + FAILED |
| PUBLISHED + COMPLETED | chunk 생성 완료 | 수정, archive | 조회, Project AI source 사용 | PUBLISHED + PENDING, ARCHIVED |
| PUBLISHED + FAILED | embedding 실패 | 재시도 | 오류 보기, 재색인 재시도 | PUBLISHED + PROCESSING |
| ARCHIVED | 삭제 또는 보관 처리 | 복구 정책에 따름 | 목록 제외 또는 archive 보기 | PUBLISHED + PENDING |

### 14.12 Domain Term

| 상태 이름 | 진입 조건 | 종료 조건 | 허용 Action | 다음 상태 |
| --- | --- | --- | --- | --- |
| ACTIVE | 용어 등록 또는 복구 | archive | 조회, 수정, 자막에서 설명 제공 | ARCHIVED |
| ARCHIVED | 용어 보관 | 복구 | archive 목록 조회 | ACTIVE |

## 15. Navigation Architecture

현재 `WorkspaceSidebar`는 전역 탐색, 프로젝트 생성, legacy query routing을 동시에 담당한다. 새 구조에서는 탐색 계층을 분리한다.

### 15.1 Global Navigation

| 위치 | 포함 항목 | 사용 시점 | 이유 |
| --- | --- | --- | --- |
| AppShell 좌측 또는 상단 | 프로젝트 목록, 현재 프로젝트, 계정, 로그아웃 | 로그인 후 전체 | GitHub와 Linear처럼 작업 공간 전환을 전역 계층으로 둔다. |
| Landing Header | 제품 소개, 시작, 회의 참가 | 공개 화면 | 랜딩은 제품 이해와 인증 진입만 담당한다. |
| Account Settings | 프로필, 보안, 세션 | `/settings/*` | 계정은 특정 프로젝트에 종속되지 않는다. |

Global Navigation은 업무 기능을 모두 나열하지 않는다. 현재 위치를 잡아주는 최소 구조여야 한다.

### 15.2 Project Navigation

ProjectLayout에서만 노출한다.

| 메뉴 | Route | 목적 |
| --- | --- | --- |
| 홈 | `/spaces/:spaceId` | 최근 회의, 열린 태스크, 최신 회의록, Project AI 진입 |
| 캘린더 | `/spaces/:spaceId/calendar` | 회의 일정 확인 |
| 회의 | `/spaces/:spaceId/meetings` | 회의 목록, 생성, 관리 |
| 태스크 | `/spaces/:spaceId/tasks` | 칸반 |
| Project AI | `/spaces/:spaceId/ai` | 프로젝트 범위 질문 |
| Knowledge | `/spaces/:spaceId/knowledge` | 공식 지식 관리 |
| 멤버 | `/spaces/:spaceId/members` | 멤버와 Space role |
| 용어 | `/spaces/:spaceId/terms` | 용어 사전 |
| 설정 | `/spaces/:spaceId/settings` | 프로젝트 수정, 삭제, 오너 이양 |

UX 근거: 한 화면에 모든 기능을 붙이지 않고, 사용자의 목적별 진입점을 분명히 한다.

Product 근거: Project AI와 Knowledge가 Space 단위라는 점을 route에서 드러낸다.

유지보수 근거: 각 route page가 독립 data hook과 오류 상태를 가진다.

### 15.3 Meeting Navigation

MeetingLayout에서만 노출한다.

| 메뉴 | Route | 목적 |
| --- | --- | --- |
| 개요 | `/spaces/:spaceId/meetings/:meetingId` | 일정, 상태, 참여자, 산출물 상태 |
| 준비 | `/spaces/:spaceId/meetings/:meetingId/live/prejoin` | 장치 점검 |
| Live | `/spaces/:spaceId/meetings/:meetingId/live` | 실시간 회의 |
| Transcript | `/spaces/:spaceId/meetings/:meetingId/transcript` | STT 기록 |
| Report | `/spaces/:spaceId/meetings/:meetingId/report` | 회의록 |
| Tasks | `/spaces/:spaceId/meetings/:meetingId/tasks` | 태스크 후보 |
| Meeting AI | `/spaces/:spaceId/meetings/:meetingId/ai` | 현재 회의 질문 |

Meeting AI와 Project AI는 메뉴, URL, 설명 문구를 분리한다.

### 15.4 Breadcrumb

Breadcrumb는 항상 현재 컨텍스트를 설명한다.

| 화면 | Breadcrumb |
| --- | --- |
| Project Home | `프로젝트 목록 / {projectName}` |
| Meeting Detail | `프로젝트 목록 / {projectName} / 회의 / {meetingTitle}` |
| Meeting Report | `프로젝트 목록 / {projectName} / {meetingTitle} / 회의록` |
| Project AI | `프로젝트 목록 / {projectName} / Project AI` |

Breadcrumb item은 실제 route로 이동 가능해야 한다. 단, token fragment를 포함한 초대 흐름에서는 token이 다른 route에 노출되지 않게 한다.

### 15.5 Back Navigation

Back 버튼은 브라우저 history가 아니라 제품 계층을 기준으로 한다.

| 현재 화면 | 기본 Back 대상 |
| --- | --- |
| Meeting 하위 화면 | Meeting Detail |
| Meeting Detail | Project Meetings |
| Project 하위 화면 | Project Home |
| Project Home | Spaces |
| Invitation 처리 완료 | Spaces 또는 target Project |
| 403/404 | 상위 안전 route |

### 15.6 Deep Link

Deep link는 직접 주소 접근과 새로고침을 기준으로 설계한다.

- `spaceId`와 `meetingId`는 path parameter로만 신뢰한다.
- query는 filter, tab, search처럼 화면 표시 상태만 사용한다.
- `/meeting-access`는 `meetingId`, `joinCode` query를 받을 수 있다.
- `/space-invitations/:spaceId/:invitationId#token=...`은 fragment token을 보존한다.
- legacy route는 target 준비 후 redirect alias로만 유지한다.

## 16. Interaction Guideline

Interaction은 화면마다 새로 만들지 않고 공통 규칙을 따른다.

| 패턴 | 규칙 | UX 근거 | Product / 유지보수 근거 |
| --- | --- | --- | --- |
| Button | primary는 화면당 1개 원칙. secondary, ghost, text action을 구분한다. | 다음 행동을 명확히 한다. | CTA 중복을 줄이고 테스트 기준이 쉬워진다. |
| Icon Button | tooltip과 aria-label 필수. | 의미 없는 아이콘 클릭을 막는다. | 반복 UI를 공통화한다. |
| Modal | 짧고 즉시 끝나는 입력에만 사용한다. | 컨텍스트 이탈을 줄인다. | 복잡한 form은 route page 또는 drawer로 이동한다. |
| Drawer | 상세 편집, side inspection에 사용한다. | 목록 컨텍스트를 유지한다. | Meeting participant, task detail에 재사용 가능하다. |
| Dialog | 삭제, 오너 이양, 모든 기기 로그아웃 같은 확인 작업에 사용한다. | 위험 action을 명확히 한다. | `ConfirmDialog` 하나로 통일한다. |
| Toast | 저장 완료, 복사 완료 같은 일시 feedback에만 사용한다. | 화면 상태를 가리지 않는다. | 오류는 toast만 쓰지 않고 inline state를 유지한다. |
| Dropdown | 정렬, role 변경, 상태 변경에 사용한다. | 선택지가 많은 action을 정리한다. | role/status enum과 연결된다. |
| Confirm | irreversible 또는 권한 영향 action에만 사용한다. | 실수 방지. | 프로젝트 삭제, 회의 삭제, role 회수에 일관 적용한다. |
| Danger Action | 붉은 계열 token, 별도 confirmation, loading lock을 쓴다. | 위험도를 즉시 인식한다. | destructive API 중복 호출을 방지한다. |
| Undo | 서버에서 복구 가능한 soft action에만 제공한다. | 실수 회복. | hard delete에는 undo를 제공하지 않는다. |
| Loading | page loading, section loading, inline saving을 구분한다. | 기존 데이터를 새 데이터처럼 보이지 않게 한다. | API별 pending state를 추적하기 쉽다. |
| Skeleton | 최종 layout과 같은 크기로 표시한다. | CLS와 혼란을 줄인다. | 테스트에서 layout shift를 잡기 쉽다. |
| Empty State | 왜 비었는지와 다음 action 1개를 제공한다. | 사용자가 막히지 않는다. | 도메인별 empty state를 재사용한다. |
| Permission Denied | 숨길 수 있는 action은 숨기고, 접근한 화면은 403 state로 설명한다. | 권한을 오류가 아니라 제품 상태로 이해한다. | API 403과 UI role 계산이 같은 컴포넌트를 쓴다. |
| 404 | 삭제, 없음, 접근 불가를 구분한다. | 사용자가 다음 이동을 알 수 있다. | NotFound page와 forbidden page를 분리한다. |
| 500 | 재시도, status 유지, 지원 정보 표시. | 실패를 성공으로 오인하지 않는다. | mock fallback 금지 원칙과 맞는다. |
| Retry | 같은 request를 다시 보내되, form 입력값은 보존한다. | 입력 손실 방지. | data hook에서 공통 처리 가능하다. |
| Auto Save | report draft처럼 장기 편집에만 사용한다. 상태 표시 필수. | 저장 여부 불안을 줄인다. | 일반 form은 manual save로 단순화한다. |
| Manual Save | 설정, role, 회의 정보처럼 명시 결정이 필요한 곳에 사용한다. | 사용자가 변경 확정을 인식한다. | API 호출 지점을 명확히 한다. |
| Refresh | stale data 가능성이 있는 목록에 제공한다. | 수동 확인 가능. | polling 남용을 줄인다. |
| Polling | transcript processing, live status, join request status에만 제한한다. | 진행 중 상태를 알 수 있다. | background traffic을 통제한다. |
| Streaming | STT dialogue, AI answer에 사용한다. | 생성 중임을 자연스럽게 보여준다. | partial state와 completed state를 분리한다. |

## 17. Permission UX

권한은 API에서 최종 검증한다. UI는 사용자가 가능한 행동과 불가능한 이유를 미리 이해하도록 돕는 보조 계층이다.

### 17.1 Role Display

| Role | UI 표시 | 위치 |
| --- | --- | --- |
| Space OWNER | `프로젝트 오너` | ProjectHeader, Members |
| Space ADMIN | `프로젝트 관리자` | ProjectHeader, Members |
| Space MEMBER | `프로젝트 멤버` | ProjectHeader |
| Meeting HOST | `회의 호스트` | MeetingHeader, Participant list |
| Meeting EDITOR | `회의 편집자` | MeetingHeader, Participant list |
| Meeting VIEWER | `회의 열람자` | MeetingHeader, Participant list |
| Guest | `회의 게스트` | MeetingHeader, Participant list |

Space role과 Meeting role은 합쳐서 한 badge로 만들지 않는다.

### 17.2 Screen Permission Matrix

| 화면 | OWNER | ADMIN | MEMBER | Meeting HOST | Meeting EDITOR | Meeting VIEWER | Guest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Project Home | 조회/관리 | 조회/관리 일부 | 조회 | 회의 권한 있으면 관련 회의 조회 | 회의 권한 있으면 관련 회의 조회 | 회의 권한 있으면 관련 회의 조회 | 접근 불가 |
| Project Settings | 수정/삭제/이양 | 제한 조회 또는 수정 정책 | 접근 불가 | 접근 불가 | 접근 불가 | 접근 불가 | 접근 불가 |
| Members | 전체 관리 | 초대/관리 일부 | 목록 조회 정책 | 접근 불가 | 접근 불가 | 접근 불가 | 접근 불가 |
| Meetings | 생성/수정/ACL | 생성/수정/ACL | 접근 허용 회의 목록 | 자신 회의 운영 | 자신 회의 조회 | 자신 회의 조회 | 접근 허용 회의만 |
| Live | override 입장 | override 입장 | MeetingParticipant 필요 | 운영 가능 | 입장 가능 | 입장 가능 | 해당 회의만 |
| Transcript | 조회/수정 | 조회/수정 | MeetingParticipant 필요 | 조회/수정 | 조회/수정 | 조회 | 해당 회의만 조회 |
| Report | 조회/편집/확정 | 조회/편집/확정 | Meeting role 필요 | 조회/편집/확정 | 조회/편집 | 조회 | role에 따라 해당 회의만 |
| Task Candidate | 추출/확정 | 추출/확정 | SpaceMember + EDITOR/HOST 필요 | SpaceMember면 확정 가능 | SpaceMember면 확정 가능 | 조회 | 확정 불가 |
| Project Tasks | 전체 관리 | 전체 관리 | 조회/수정 정책 | SpaceMember면 관련 task 조회 | SpaceMember면 관련 task 조회 | SpaceMember면 관련 task 조회 | 접근 불가 |
| Project AI | 접근 가능 회의 + Knowledge | 접근 가능 회의 + Knowledge | 접근 가능 회의 + Knowledge | SpaceMember 아니면 접근 불가 | SpaceMember 아니면 접근 불가 | SpaceMember 아니면 접근 불가 | 접근 불가 |
| Meeting AI | 해당 Meeting only | 해당 Meeting only | 해당 Meeting only | 해당 Meeting only | 해당 Meeting only | 해당 Meeting only | 해당 Meeting only |
| Knowledge | 조회/수정 | 조회/수정 | 조회 | SpaceMember 아니면 접근 불가 | SpaceMember 아니면 접근 불가 | SpaceMember 아니면 접근 불가 | 접근 불가 |

### 17.3 Hide, Disable, Explain

| 상황 | 처리 |
| --- | --- |
| 사용자가 기능 존재 자체를 몰라도 되는 action | 숨김 |
| 기능은 보이지만 조건이 부족한 action | disabled + 이유 표시 |
| 직접 URL 접근으로 권한 없는 화면 진입 | `PermissionDenied` state |
| 권한 회수 후 기존 화면에 남아 있음 | session/data refresh 후 403 state |
| AI scope가 제한됨 | 입력창 주변에 검색 범위 badge 표시 |

## 18. Information Hierarchy

각 화면은 사용자가 가장 먼저 판단해야 하는 정보부터 배치한다.

| 화면 | 1순위 | 2순위 | 3순위 | 이유 |
| --- | --- | --- | --- | --- |
| Landing | MeetingMind가 무엇인지 | 시작 CTA | 실제 제품 흐름 | 구매 전 사용자는 가치와 진입만 필요하다. |
| Spaces | 참여 프로젝트 | 오늘 회의 | 최근 활동 | 로그인 후 가장 먼저 어느 프로젝트로 갈지 결정한다. |
| Project Home | 프로젝트 상태 요약 | 다음 회의/열린 태스크 | 최근 회의록/Project AI | 프로젝트는 현재 상황 파악이 먼저다. |
| Calendar | 날짜별 회의 | 회의 상태 | 회의 상세 이동 | 일정 화면은 시간 판단이 우선이다. |
| Meetings | 회의 목록과 상태 | 생성/필터 | 참가 코드/ACL | 회의 찾기와 생성이 주 목적이다. |
| Meeting Detail | 회의 상태와 다음 action | 참여자/권한 | 산출물 상태 | 회의 전후로 사용자가 해야 할 일이 달라진다. |
| Prejoin | 장치 상태 | 회의 정보 | 입장 버튼 | 입장 실패를 줄이는 것이 목적이다. |
| Live | 영상/음성 상태 | 실시간 자막 | 회의 제어/AI | 회의 중에는 집중과 실시간 상태가 우선이다. |
| Transcript | 발화 기록 | 검색/필터 | speaker 수정 | 근거 원문 확인이 목적이다. |
| Report | 공식/후보 상태 | 본문 편집 | 버전/다운로드 | 회의록은 확정 여부가 가장 중요하다. |
| Task Candidates | 후보 목록 | 편집 필드 | 확정/제외 | 칸반 등록 전 검토가 목적이다. |
| Project Tasks | 칸반 상태 | 필터 | 카드 상세 | 업무 진행 상태가 먼저 보여야 한다. |
| Meeting AI | 검색 범위 | 답변/출처 | 후속 질문 | 단일 회의 범위를 계속 인식해야 한다. |
| Project AI | 검색 범위 | 답변/출처 | Knowledge/회의 source | 공식 지식과 회의 기록 구분이 중요하다. |
| Knowledge | 공식 지식 목록 | embedding 상태 | 등록/수정 | AI 검색 준비 상태를 보여야 한다. |
| Members | 멤버와 role | 초대/신청 | 오너 이양 | 권한 관리 화면이다. |
| Settings | 프로젝트 정보 | 위험 action | 보존 정책 | 설정은 변경과 삭제를 명확히 나눠야 한다. |

## 19. Design Token Plan

현재 프로젝트는 vanilla CSS 기반이고 Tailwind/shadcn/ui가 설치되어 있지 않다. 아래 token은 향후 Tailwind config와 shadcn/ui CSS variable로 이식 가능한 목표값이다. 실제 dependency 추가는 별도 작업에서 결정한다.

### 19.1 Color

| Token | 값 | 용도 |
| --- | --- | --- |
| `background` | `#f8fbff` | 앱 전체 배경 |
| `foreground` | `#102a5c` | 본문 주요 텍스트 |
| `muted` | `#5b7198` | 보조 텍스트 |
| `card` | `#ffffff` | 주요 surface |
| `border` | `#d8e5f7` | 경계선 |
| `primary` | `#175ab7` | 주요 CTA, active nav |
| `primary-foreground` | `#ffffff` | primary 위 텍스트 |
| `secondary` | `#eaf3ff` | 약한 강조 surface |
| `success` | `#16856a` | confirmed, completed |
| `warning` | `#b7791f` | pending, processing |
| `danger` | `#c2414b` | 삭제, revoke, failed |
| `info` | `#2368c4` | AI source, link |

색상 근거: 사용자가 최근 파랑/흰 계열 통일을 요청했고, B2B SaaS에서는 신뢰와 집중을 위해 과한 다색 팔레트보다 한 계열 중심이 유지보수에 유리하다.

### 19.2 Typography

| Token | 값 |
| --- | --- |
| `font-sans` | `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` |
| `font-mono` | `"SFMono-Regular", Consolas, "Liberation Mono", monospace` |
| `text-xs` | `12px / 16px` |
| `text-sm` | `14px / 20px` |
| `text-base` | `16px / 24px` |
| `text-lg` | `18px / 28px` |
| `heading-sm` | `20px / 28px / 600` |
| `heading-md` | `24px / 32px / 650` |
| `heading-lg` | `32px / 40px / 700` |
| `display` | `48px / 56px / 750` |

Korean-first UI이므로 외부 display font보다 시스템 폰트 안정성을 우선한다.

### 19.3 Spacing, Grid, Container

| Token | 값 |
| --- | --- |
| `space-1` | `4px` |
| `space-2` | `8px` |
| `space-3` | `12px` |
| `space-4` | `16px` |
| `space-6` | `24px` |
| `space-8` | `32px` |
| `space-10` | `40px` |
| `space-12` | `48px` |
| `container-sm` | `720px` |
| `container-md` | `960px` |
| `container-lg` | `1200px` |
| `container-xl` | `1440px` |
| `grid` | `12 columns, 24px gap desktop, 16px gap tablet, 12px gap mobile` |

### 19.4 Radius, Shadow, Elevation

| Token | 값 | 용도 |
| --- | --- | --- |
| `radius-xs` | `4px` | badge, small control |
| `radius-sm` | `6px` | input, button |
| `radius-md` | `8px` | card, panel |
| `radius-lg` | `12px` | dialog, drawer |
| `shadow-1` | `0 1px 2px rgba(16, 42, 92, 0.06)` | low card |
| `shadow-2` | `0 8px 24px rgba(16, 42, 92, 0.10)` | dialog, popover |
| `shadow-focus` | `0 0 0 3px rgba(23, 90, 183, 0.20)` | focus |

큰 카드와 버튼은 과한 pill을 피하고 8px 중심으로 통일한다.

### 19.5 Motion

| Token | 값 |
| --- | --- |
| `duration-fast` | `120ms` |
| `duration-normal` | `200ms` |
| `duration-slow` | `320ms` |
| `ease-standard` | `cubic-bezier(0.16, 1, 0.3, 1)` |
| `hover-lift` | `translateY(-2px)` |
| `pressed` | `scale(0.98)` |

모든 motion은 `prefers-reduced-motion`을 따른다. 앱 내부에서는 상태 변화와 feedback 목적의 짧은 motion만 사용한다.

### 19.6 Z-index

| Token | 값 |
| --- | --- |
| `z-base` | `0` |
| `z-sticky` | `10` |
| `z-dropdown` | `20` |
| `z-drawer` | `30` |
| `z-modal` | `40` |
| `z-toast` | `50` |
| `z-tooltip` | `60` |

### 19.7 Sizes

| Token | 값 |
| --- | --- |
| `button-sm` | `32px height, 12px horizontal padding` |
| `button-md` | `40px height, 16px horizontal padding` |
| `button-lg` | `48px height, 20px horizontal padding` |
| `input-md` | `40px height` |
| `textarea-md` | `120px min-height` |
| `icon-sm` | `16px` |
| `icon-md` | `20px` |
| `icon-lg` | `24px` |
| `card-padding` | `24px desktop, 16px mobile` |

### 19.8 Breakpoints

| Token | 값 |
| --- | --- |
| `sm` | `640px` |
| `md` | `768px` |
| `lg` | `1024px` |
| `xl` | `1280px` |
| `2xl` | `1536px` |

## 20. AppShell Architecture

### 20.1 Layout Types

| Layout | 사용 route | 포함 영역 | 목적 |
| --- | --- | --- | --- |
| LandingLayout | `/` | public header, hero, auth entry | 비로그인 제품 이해 |
| AuthLayout | `/?mode=password-reset` | auth panel | 공개 인증 흐름 |
| AppShell | `/spaces`, `/settings/*` | global nav, header, content | 로그인 후 기본 앱 |
| ProjectLayout | `/spaces/:spaceId/*` | AppShell + project nav + project header | 프로젝트 컨텍스트 고정 |
| MeetingLayout | `/spaces/:spaceId/meetings/:meetingId/*` | ProjectLayout + meeting header + meeting nav | 회의 컨텍스트 고정 |
| LiveMeetingLayout | live route | focused live surface | 회의 집중 모드 |
| ErrorLayout | 403, 404, 500 | safe nav + state | 복구 가능한 오류 |

### 20.2 AppShell Slots

| Slot | 역할 |
| --- | --- |
| Sidebar | global navigation, project switcher |
| Header | current title, breadcrumb, global action |
| Breadcrumb | 현재 위치와 상위 route |
| Content | route outlet |
| Right Panel optional | AI source, selected task, participant detail |
| Footer optional | 앱 내부에서는 기본 미사용 |

Right Panel은 항상 선택 사항이다. 작은 화면에서는 drawer로 전환한다.

### 20.3 ProjectLayout

ProjectLayout은 Space data와 Space role을 로드한다.

- ProjectHeader: 이름, 설명, role badge, 주요 action
- ProjectNavigation: Home, Calendar, Meetings, Tasks, AI, Knowledge, Members, Terms, Settings
- Outlet: 하위 route
- Forbidden state: SpaceMember가 아닐 때

### 20.4 MeetingLayout

MeetingLayout은 Meeting detail과 participant role을 로드한다.

- MeetingHeader: 제목, status, time, Meeting role, Space role
- MeetingNavigation: Overview, Preparation, Live, Transcript, Report, Tasks, Meeting AI
- ScopeBadge: Meeting AI는 current meeting only
- Outlet: 하위 route
- Forbidden state: MeetingParticipant가 없고 override도 없을 때

### 20.5 LiveMeetingLayout

Live 화면은 일반 AppShell 밀도를 줄인다.

- 상단: meeting title, elapsed time, connection state
- 중앙: video or participant grid
- 우측 또는 하단: live transcript
- 하단: mic, camera, screen share, leave, end
- 보조: Meeting AI quick panel

## 21. Component Architecture

Atomic Design 원칙을 따르되, 실제 개발에서는 과도한 원자화보다 domain component 재사용을 우선한다.

### 21.1 Atoms

| Component | 역할 |
| --- | --- |
| `Button` | variant, size, loading, disabled reason |
| `IconButton` | icon action, tooltip, aria-label |
| `Badge` | status, role, source type |
| `Input` | label과 분리된 입력 primitive |
| `Textarea` | report, knowledge content 입력 |
| `Select` | role, status, sort |
| `Spinner` | inline loading에만 사용 |
| `Skeleton` | page와 section loading |
| `Toast` | transient feedback |

### 21.2 Molecules

| Component | 역할 |
| --- | --- |
| `FormField` | label, helper, error, required |
| `StatusBadge` | Meeting, Transcript, Report, Task 상태 표시 |
| `RoleBadge` | SpaceRole, MeetingRole 표시 |
| `PermissionBadge` | 접근 범위 설명 |
| `SourceCitation` | AI 출처 단일 항목 |
| `EmptyState` | 빈 상태 + next action |
| `LoadingState` | skeleton group |
| `ErrorState` | retry, safe navigation |
| `ConfirmDialog` | destructive confirm |
| `CommandMenu` | 빠른 이동, 추후 선택 |

### 21.3 Organisms

| Component | 역할 |
| --- | --- |
| `AppSidebar` | global nav와 project switcher |
| `AppHeader` | breadcrumb, user action |
| `ProjectHeader` | project title, role, action |
| `ProjectNav` | project route navigation |
| `MeetingHeader` | meeting title, status, role |
| `MeetingNav` | meeting route navigation |
| `MeetingTimeline` | 회의 상태와 산출물 진행 |
| `TranscriptPanel` | STT segment 목록 |
| `TranscriptItem` | 발화 단위 표시 |
| `ReportEditor` | report markdown 편집 |
| `ReportVersionList` | version history |
| `TaskCard` | 칸반 카드 |
| `KanbanBoard` | task columns |
| `TaskCandidateList` | 후보 검토 |
| `KnowledgeCard` | 공식 지식 항목 |
| `AIAnswer` | 답변, 출처, unsupported state |
| `AIComposer` | 질문 입력, scope 표시 |
| `MemberTable` | role 관리 |
| `InvitationPanel` | 초대 생성과 상태 |

### 21.4 Templates

| Template | 역할 |
| --- | --- |
| `ListPageTemplate` | 목록, toolbar, empty/error |
| `DetailPageTemplate` | header, metadata, primary action |
| `EditorPageTemplate` | document editor + side panel |
| `AIPageTemplate` | answer stream + sources |
| `SettingsPageTemplate` | grouped settings + danger zone |

### 21.5 Pages

Pages는 route와 data hook을 연결한다. 공통 UI 로직을 직접 만들지 않는다.

| Page | 주요 organism |
| --- | --- |
| `SpacesPage` | AppSidebar, ListPageTemplate |
| `ProjectHomePage` | ProjectHeader, MeetingTimeline |
| `ProjectMeetingsPage` | Meeting list, toolbar |
| `MeetingDetailPage` | MeetingHeader, MeetingTimeline |
| `MeetingTranscriptPage` | TranscriptPanel |
| `MeetingReportPage` | ReportEditor, ReportVersionList |
| `MeetingTaskCandidatesPage` | TaskCandidateList |
| `MeetingAiPage` | AIPageTemplate with meeting scope |
| `ProjectAiPage` | AIPageTemplate with project scope |
| `KnowledgePage` | KnowledgeCard, editor drawer |
| `MembersPage` | MemberTable, InvitationPanel |
| `ProjectSettingsPage` | SettingsPageTemplate |

## 22. Refactoring Priority

구현은 화면 예쁨보다 구조 안정성을 먼저 해결한다.

| 순서 | 작업 | 이유 | 완료 기준 |
| --- | --- | --- | --- |
| 1 | `DataState`, `ConfirmDialog`, `StatusBadge`, `RoleBadge` 기준 확정 | 모든 화면 상태와 권한 표현의 기반이다. | 기존 화면에 영향 없이 공통 컴포넌트 추가 |
| 2 | `AppShell`과 `ProtectedRoute` 정리 | 사용자가 어디에 있는지 알기 위한 기본 골격이다. | `/spaces`가 AppShell 안에서 동작 |
| 3 | `SpaceLayout`과 target `/spaces/:spaceId` 추가 | Project context를 URL로 고정한다. | 직접 주소 접근, 새로고침 동작 |
| 4 | legacy `/project-overview`를 Project Home으로 축소 | 현재 가장 큰 인지 부하를 줄인다. | 요약, 최근 회의, 열린 task, AI 진입만 남김 |
| 5 | Project 하위 route 분리 | 회의, task, AI, knowledge, member 목적을 나눈다. | 기존 기능이 target route에서 동작 |
| 6 | `MeetingLayout` 추가 | Meeting context와 role을 고정한다. | meeting detail에서 하위 nav 동작 |
| 7 | Live, Transcript, Report, Task Candidate 분리 | 회의 후속 흐름을 상태 모델에 맞춘다. | transcript to report to task 흐름 |
| 8 | Meeting AI와 Project AI 화면 분리 강화 | AI scope 오해를 막는다. | source와 scope badge 검증 |
| 9 | legacy redirect alias | 기존 링크를 보존한다. | 기존 주요 주소가 target으로 이동 |
| 10 | mock fallback 제거 | 실패를 성공처럼 보이지 않게 한다. | target 화면 `/api/workspace` 미사용 |
| 11 | Landing 정리 | 내부 앱 구조가 잡힌 뒤 제품 가치를 정확히 표현한다. | 공개 CTA와 route 유지 |
| 12 | E2E와 접근성 검증 | 회귀 방지. | 필수 사용자 흐름 통과 |

Landing을 먼저 완성하지 않는 이유는 현재 제품 리스크가 색감보다 앱 내부 IA와 권한 컨텍스트에 있기 때문이다. 다만 사용자와 화면을 보며 진행할 때는 Landing을 별도 preview로 병행할 수 있다.

## 23. Implementation Guardrails

- 기존 비즈니스 로직, API 계약, 인증 방식은 이 문서만으로 변경하지 않는다.
- route 변경은 legacy redirect와 함께 진행한다.
- Space role과 Meeting role을 합쳐서 판단하지 않는다.
- Meeting AI와 Project AI의 scope 문구는 모든 관련 화면에 표시한다.
- target API 실패 시 mock fallback으로 성공 처리하지 않는다.
- 새 dependency는 `AGENT.md`의 구현 판단 순서를 따른다.
- Tailwind와 shadcn/ui 도입은 별도 task에서 package 영향과 migration 범위를 검토한다.
- 각 구현 PR은 `tasks.md`와 `implement.md`에 검증 결과를 남긴다.
