# Test Matrix: Authz and LiveKit Access

이 문서는 T039, T040, T094 구현자가 요구사항의 성공/실패 기준을 단위 테스트로 바로 옮길 수 있도록 작성한 test matrix다.

## Scope

| Task | Target | Test level |
| --- | --- | --- |
| T039 | Space access validation service 또는 policy 계층 | Pure unit test 우선 |
| T040 | Meeting access validation service 또는 policy 계층 | Pure unit test 우선 |
| T094 | LiveKit token 발급 권한 연동 | Service unit test, controller slice test 후보 |
| T168 | Meeting join request 생성/검토 | Domain service unit test, controller unit test |

## Source Criteria

| Source | Criteria used |
| --- | --- |
| `requirements/permissions.md` | SpaceRole과 MeetingParticipant 분리, HOST/삭제/AI 접근 차단 규칙 |
| `requirements/status-values.md` | `Meeting.status`, `MeetingParticipant.accessStatus` canonical values |
| `requirements/policies.md` | default-deny, 회의 삭제 기본 OWNER/HOST 전용 |
| `requirements/functional-requirements-detail.md` | FR-MREG-07, FR-ACL-01~07, FR-CALL-01 성공/실패 기준 |
| `specs/001-meetingmind-core/clarify.md` | D-015~D-020 결정사항 |
| `specs/001-meetingmind-core/contracts/common.md` | 공통 error code |
| `specs/001-meetingmind-core/contracts/meeting-api.md` | Meeting delete, participant role/accessStatus 변경 계약 |
| `specs/001-meetingmind-core/contracts/live-stt-api.md` | Target LiveKit token endpoint 권한 계약 |

## Result Conventions

| Result | Meaning |
| --- | --- |
| `ALLOW` | 권한 검증이 통과하고 후속 조회/수정/토큰 발급으로 진행한다. |
| `DENY_401` | 인증 사용자가 없거나 access token 검증에 실패해 `UNAUTHORIZED`로 거부한다. |
| `DENY_403_SPACE` | Space 권한이 없어 `SPACE_ACCESS_DENIED`로 거부한다. |
| `DENY_403_MEETING` | 회의 접근 또는 회의 작업 권한이 없어 `MEETING_ACCESS_DENIED`로 거부한다. |
| `NOT_FOUND_404` | 존재하지 않거나 접근 범위 밖으로 숨겨야 하는 resource를 not found로 처리한다. |
| `REJECT_400` | role/status 등 입력값 자체가 유효하지 않아 `INVALID_REQUEST`로 거부한다. |
| `REJECT_409` | 마지막 HOST 제거 등 현재 상태와 충돌해 거부한다. |
| `SERVICE_503` | LiveKit 설정 누락 등 외부 service 설정 문제로 거부한다. |
| `ISSUE_TOKEN` | LiveKit token을 발급한다. |

구현 시 예외 타입은 code를 명시적으로 검증한다. 단순히 exception 발생만 확인하면 실패한 테스트로 본다.

## T039 Space Access Validation

### Unit Cases

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| S-001 | OWNER Space 접근 허용 | active `SpaceMember(role=OWNER)` | Space read/access check | `ALLOW` | Permission matrix |
| S-002 | ADMIN Space 접근 허용 | active `SpaceMember(role=ADMIN)` | Space read/access check | `ALLOW` | Permission matrix |
| S-003 | MEMBER Space 기본 접근 허용 | active `SpaceMember(role=MEMBER)` | Space read/access check | `ALLOW` | Permission matrix |
| S-004 | SpaceMember 없음 | user is not member of target Space | Space read/access check | `DENY_403_SPACE` | POL-AUTHZ-01 |
| S-005 | 제거된 SpaceMember | member row removed, inactive, or soft-deleted if such state exists | Space read/access check | `DENY_403_SPACE` | FR-ACL-05 |
| S-006 | Space 없음 | unknown `spaceId` | Space read/access check | `NOT_FOUND_404` | Common errors |
| S-007 | OWNER member 관리 허용 | active `OWNER` | invite, role change, member removal check | `ALLOW` | Permission matrix |
| S-008 | ADMIN member 관리 허용 | active `ADMIN` | invite, role change, member removal check | `ALLOW` | Permission matrix |
| S-009 | MEMBER member 관리 거부 | active `MEMBER` | invite, role change, member removal check | `DENY_403_SPACE` | Permission matrix |
| S-010 | Guest는 Space 권한 없음 | user only has `MeetingParticipant(participantType=guest)` | Project Knowledge or Space member API access | `DENY_403_SPACE` | Permission matrix |

### Pass Criteria

- Space access service는 default-deny로 동작한다.
- SpaceRole과 MeetingParticipant를 섞어 판정하지 않는다.
- guest participant는 특정 meeting 권한만 만들며 Space 전체 권한을 만들지 않는다.
- 권한 실패는 code까지 검증한다.

## T040 Meeting Access Validation

### Read and Context Access

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-001 | ACTIVE HOST 접근 허용 | `MeetingParticipant(role=HOST, accessStatus=ACTIVE)` | meeting detail, transcript, Meeting AI context check | `ALLOW` | FR-MREG-07 |
| M-002 | ACTIVE EDITOR 접근 허용 | `MeetingParticipant(role=EDITOR, accessStatus=ACTIVE)` | meeting detail, transcript, Meeting AI context check | `ALLOW` | FR-MREG-07 |
| M-003 | ACTIVE VIEWER 접근 허용 | `MeetingParticipant(role=VIEWER, accessStatus=ACTIVE)` | meeting detail, transcript, Meeting AI context check | `ALLOW` | FR-MREG-07 |
| M-004 | REVOKED participant 차단 | `MeetingParticipant(accessStatus=REVOKED)` | meeting detail, transcript, LiveKit, Meeting AI context check | `DENY_403_MEETING` | D-017 |
| M-005 | participant 없음 차단 | Space `MEMBER`, no MeetingParticipant | meeting detail or transcript check | `DENY_403_MEETING` | FR-ACL-03 |
| M-006 | Space OWNER override 허용 | active Space `OWNER`, no MeetingParticipant | meeting detail or transcript check | `ALLOW` | FR-ACL-05 |
| M-007 | Space ADMIN override 허용 | active Space `ADMIN`, no MeetingParticipant | meeting detail or transcript check | `ALLOW` | FR-ACL-05 |
| M-008 | 비멤버 차단 | no SpaceMember and no MeetingParticipant | meeting detail or transcript check | `DENY_403_MEETING` | FR-MREG-07 |
| M-009 | guest는 지정 회의만 허용 | `participantType=guest`, same meeting, `ACTIVE` | same meeting access check | `ALLOW` | Permission matrix |
| M-010 | guest의 다른 회의 접근 차단 | `participantType=guest` for another meeting | other meeting access check | `DENY_403_MEETING` | Permission matrix |
| M-011 | 회의 없음 | unknown `meetingId` | meeting access check | `NOT_FOUND_404` | Common errors |

### Role Hierarchy

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-020 | VIEWER 조회 허용 | `role=VIEWER`, `ACTIVE` | view/read permission check | `ALLOW` | FR-ACL-04 |
| M-021 | VIEWER 수정 거부 | `role=VIEWER`, `ACTIVE` | report edit or speaker edit permission check | `DENY_403_MEETING` | FR-ACL-04 |
| M-022 | EDITOR 조회/수정 허용 | `role=EDITOR`, `ACTIVE` | view and edit permission check | `ALLOW` | FR-ACL-04 |
| M-023 | EDITOR 삭제 거부 | `role=EDITOR`, `ACTIVE` | meeting delete permission check | `DENY_403_MEETING` | FR-ACL-07 |
| M-024 | HOST 조회/수정/삭제 허용 | `role=HOST`, `ACTIVE` | view, edit, delete permission check | `ALLOW` | FR-ACL-04, FR-ACL-07 |
| M-025 | Space ADMIN 삭제 기본 거부 | active Space `ADMIN`, no explicit delete exception policy | meeting delete permission check | `DENY_403_MEETING` | D-020 |
| M-026 | Space OWNER 삭제 허용 | active Space `OWNER` | meeting delete permission check | `ALLOW` | FR-ACL-07 |

### Participant Mutation

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-040 | OWNER participant 부여 허용 | actor Space `OWNER`, target active Space member | add participant | `ALLOW` | FR-ACL-01 |
| M-041 | ADMIN participant 부여 허용 | actor Space `ADMIN`, target active Space member | add participant | `ALLOW` | FR-ACL-01 |
| M-042 | HOST participant 부여 허용 | actor `HOST`, target active Space member | add participant | `ALLOW` | FR-ACL-01 |
| M-043 | MEMBER participant 부여 거부 | actor Space `MEMBER`, not HOST | add participant | `DENY_403_MEETING` | FR-ACL-01 |
| M-044 | 비멤버에게 member participant 부여 거부 | target has no SpaceMember | add `participantType=member` | `DENY_403_MEETING` | FR-ACL-01 |
| M-045 | role 값 오류 거부 | role outside `HOST`, `EDITOR`, `VIEWER` | update participant role | `REJECT_400` | Common role values |
| M-046 | accessStatus 값 오류 거부 | status outside `ACTIVE`, `REVOKED` | update participant accessStatus | `REJECT_400` | Status values |
| M-047 | 마지막 active HOST 강등 거부 | exactly one `ACTIVE HOST` remains | change HOST to EDITOR/VIEWER | `REJECT_409` | D-018 |
| M-048 | 마지막 active HOST 회수 거부 | exactly one `ACTIVE HOST` remains | change accessStatus to `REVOKED` | `REJECT_409` | D-018 |
| M-049 | 마지막 active HOST 제거 거부 | exactly one `ACTIVE HOST` remains | remove participant | `REJECT_409` | D-018 |
| M-050 | 다른 HOST가 있으면 기존 HOST 변경 허용 | at least two `ACTIVE HOST` participants | downgrade or revoke one HOST | `ALLOW` | D-018 |
| M-051 | HOST 회의방 퇴장 권한 유지 | HOST leaves WebRTC room without ending meeting | leave room event | participant role/status unchanged | D-018 |
| M-052 | HOST 회의 종료 허용 | actor `HOST`, meeting `IN_PROGRESS` | end meeting | status becomes `ENDED` | D-018 |

### SpaceMember Removal Impact

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-060 | SpaceMember 제거 시 member participant 회의 단독 전환 | removed user has same Space `participantType=member` participants | remove SpaceMember | those participants become `participantType=guest` and remain `ACTIVE` | D-016 |
| M-061 | SpaceMember 제거 후 프로젝트 접근 차단과 회의 접근 유지 | removed user still has active MeetingParticipant | project detail, Project AI, meeting detail, LiveKit, Meeting AI check | project/Project AI denied, meeting-scoped access allowed | D-016, D-017 |
| M-062 | guest participant는 SpaceMember 제거 영향 없음 | guest participant has no SpaceMember row | remove unrelated SpaceMember | guest participant unchanged | D-016 |

### Pass Criteria

- `ACTIVE`만 회의 접근 권한으로 인정한다.
- `REVOKED`는 조회, 수정, LiveKit token, Meeting AI, Project AI meeting context 접근을 모두 차단한다.
- owner/admin override는 읽기와 관리 권한에서만 문서화된 범위로 적용한다.
- ADMIN delete는 explicit exception policy가 없으면 실패해야 한다.
- 마지막 active HOST 보호는 role 변경, accessStatus 변경, participant 제거에 모두 적용한다.

## T094 LiveKit Token Authorization

### Unit and Slice Cases

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| L-001 | 인증 없음 | no access token or invalid access token | request target LiveKit token | `DENY_401` | Common auth rule |
| L-002 | 회의 없음 | authenticated user, unknown `meetingId` | request token | `NOT_FOUND_404` | live-stt API |
| L-003 | ACTIVE participant token 발급 | authenticated user has `MeetingParticipant(accessStatus=ACTIVE)` | request token | `ISSUE_TOKEN` | FR-CALL-01 |
| L-004 | REVOKED participant 차단 | authenticated user has `MeetingParticipant(accessStatus=REVOKED)` | request token | `DENY_403_MEETING` | D-017 |
| L-005 | participant 없음 차단 | authenticated Space `MEMBER`, no MeetingParticipant | request token | `DENY_403_MEETING` | FR-ACL-03 |
| L-006 | Space OWNER override token 발급 | authenticated active Space `OWNER`, no MeetingParticipant | request token | `ISSUE_TOKEN` | FR-ACL-05 |
| L-007 | Space ADMIN override token 발급 | authenticated active Space `ADMIN`, no MeetingParticipant | request token | `ISSUE_TOKEN` | FR-ACL-05 |
| L-008 | guest는 지정 회의 token만 발급 | authenticated guest participant for same meeting | request token | `ISSUE_TOKEN` | Permission matrix |
| L-009 | guest의 다른 회의 token 차단 | guest participant belongs to another meeting | request other meeting token | `DENY_403_MEETING` | Permission matrix |
| L-010 | 종료된 회의 token 차단 | meeting `ENDED` | request token | `DENY_403_MEETING` | FR-CALL-01 |
| L-011 | 취소된 회의 token 차단 | meeting `CANCELED` | request token | `DENY_403_MEETING` | Status values |
| L-012 | LiveKit 설정 누락 | authorized user, missing LiveKit env/config | request token | `SERVICE_503` | live-stt API |
| L-013 | Token identity는 인증 사용자 기준 | authenticated user id differs from request body identity | request token | issued token identity equals authenticated user id | D-015 |
| L-014 | Token room은 meeting 기준 | authorized user | request token | issued token room equals target meeting room, not arbitrary input | live-stt API |

### Pass Criteria

- Target endpoint는 request body의 `identity`를 신뢰하지 않는다.
- LiveKit token 발급 전 access token 사용자와 meeting 권한을 모두 확인한다.
- 발급 token은 Space 전체 권한을 포함하지 않는다.
- `LIVE_TOKEN_ISSUED` audit event는 성공 케이스에서만 기록한다.

## T168 Meeting Join Request Approval

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| J-001 | raw code 신청 | authenticated nonparticipant, valid joinCode | create join request | `PENDING`, target meeting resolved | FR-MREG-02~03 |
| J-002 | URL 신청 | URL contains valid `joinCode` query | create join request | `PENDING`, same target meeting resolved | FR-MREG-02~03 |
| J-003 | 잘못된 코드 차단 | unknown code | create join request | `DENY_403_MEETING`, meeting existence not distinguished | FR-MREG-03 |
| J-004 | 코드 없는 URL 차단 | URL has no `joinCode` query | create join request | `DENY_403_MEETING` | FR-MREG-03 |
| J-005 | pending 중복 차단 | same user/meeting has `PENDING` request | create another request | `REJECT_400` | Data constraint |
| J-006 | 기존 접근권 사용자 신청 차단 | same user has active MeetingParticipant | create join request | `REJECT_400` | FR-MREG-02 |
| J-007 | 승인 전 접근권 없음 | request is `PENDING` | inspect participant/access | no participant, meeting access denied | FR-MREG-02, FR-MREG-07 |
| J-008 | active HOST 승인 | actor is active HOST without Space override | approve pending request | `APPROVED`, VIEWER participant created | D-022 |
| J-009 | OWNER/ADMIN 승인 override | actor is active Space OWNER/ADMIN | approve pending request | `APPROVED`, VIEWER participant created | Permission matrix |
| J-010 | VIEWER/EDITOR 승인 차단 | actor has no participant-management permission | approve/reject request | `DENY_403_MEETING` | FR-ACL-01 |
| J-011 | guest 승인 결과 | applicant is not SpaceMember | approve request | `participantType=guest`, no SpaceMember created | D-008, D-022 |
| J-012 | member 승인 결과 | applicant is active SpaceMember | approve request | `participantType=member` | D-022 |
| J-013 | 승인 재처리 차단 | request is `APPROVED` | approve or reject again | `REJECT_400` | Status transition |
| J-014 | 거절 재처리 차단 | request is `REJECTED` | approve or reject again | `REJECT_400` | Status transition |

### Pass Criteria

- joinCode는 meeting ID에서 결정적으로 만들지 않고 추측하기 어려운 값으로 생성한다.
- JoinRequest에는 joinCode 원문을 복제 저장하지 않는다.
- 승인 전에는 MeetingParticipant, 회의 접근권, SpaceMember가 생기지 않는다.
- 승인 시 기본 role은 `VIEWER`이며 Space membership 여부는 participant type만 결정한다.
- 검토 권한과 상태 전이는 Backend domain 경계에서 강제한다.

## M033 Meeting CRUD PostgreSQL End-to-End

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| C-001 | Space 회의 목록 ACL | OWNER와 active participant, 권한 없는 member가 같은 Space 회의를 조회 | `GET /spaces/{spaceId}/meetings` | actor가 읽을 수 있는 active meeting만 반환 | FR-MREG-05, FR-MREG-07 |
| C-002 | 목록 filter 검증 | valid/invalid status와 from/to | status/date filter 조회 | valid 범위만 반환, invalid 또는 from>to는 `REJECT_400` | FR-MREG-05 |
| C-003 | 상세 조회 | OWNER/ADMIN 또는 active participant | `GET /meetings/{meetingId}` | meeting과 participant, nullable `myRole` 반환 | FR-MREG-06, FR-MREG-07 |
| C-004 | 제목·일정 수정 | meeting `SCHEDULED`, actor OWNER/ADMIN/HOST | PATCH title/scheduledAt | PostgreSQL 갱신 후 재조회 값 일치 | FR-MREG-04 |
| C-005 | 진행 시작·종료 | `SCHEDULED` 또는 `IN_PROGRESS` | PATCH status | `SCHEDULED -> IN_PROGRESS -> ENDED`, startedAt/endedAt 기록 | Status values, D-033 |
| C-006 | 역방향 상태 전이 차단 | `ENDED`, `CANCELED` 또는 canonical 역방향 | PATCH status | `REJECT_400`, 기존 row 불변 | D-033 |
| C-007 | SCHEDULED 삭제 | actor OWNER/HOST, meeting `SCHEDULED` | DELETE | `CANCELED`과 deleted metadata가 같은 transaction에 기록 | FR-MREG-04, D-032 |
| C-008 | 진행 중 삭제 차단 | meeting `IN_PROGRESS` | DELETE | `REJECT_409`, deleted metadata 없음 | D-032 |
| C-009 | ADMIN 삭제 차단 | actor Space ADMIN, not HOST | DELETE | `DENY_403_MEETING` | FR-ACL-07, D-020 |
| C-010 | 삭제 회의 제외 | soft-deleted meeting | list/detail/calendar/Meeting AI/Project AI 조회 | 목록·후보 제외, direct detail/Meeting AI `NOT_FOUND_404` | D-032 |
| C-011 | 재시작 영속성 | meeting create/update 후 Backend restart | login 후 list/detail | 수정 값과 ACL이 PostgreSQL에서 유지 | M032 runtime boundary |
| C-012 | Frontend target 경계 | target Space와 legacy mock Space 존재 | UI create/update/delete | target은 API 재조회만 반영, mock과 혼합 없음 | M033 plan |

### Pass Criteria

- Meeting mutation은 row lock과 audit를 포함한 transaction으로 처리한다.
- `deleted_at`, `deleted_by`는 둘 다 null이거나 둘 다 기록된다.
- soft-deleted meeting은 관계형 조회와 AI context 선필터 단계에서 제외한다.
- hard purge, restore, grace period는 M033 성공으로 간주하지 않고 후속 운영 정책으로 남긴다.

## M038 STT-to-RAG PostgreSQL Flow

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| SR-001 | STT callback dialogue 저장 | active HOST와 `PROCESSING` MeetingTranscript | provider callback text 2건 수신 후 session close | MeetingSpeaker/TranscriptSegment 2건, status `COMPLETED` | FR-STT-01, FR-STT-02, FR-STT-05 |
| SR-002 | 완료 전사 job 생성 | `PROCESSING` transcript와 저장된 segment | `COMPLETED` 전환 | `TRANSCRIPT_COMPLETED` embedding job 정확히 1건 | D-037, V12 trigger |
| SR-003 | 전사 입력 RAG 정합성 | 200개 completed transcript segment | deterministic worker 실행 후 Meeting search 100회 | Meeting/Space scope가 일치하는 결과만 반환 | FR-MBOT-01~04, NFR-DATA-01~02 |
| SR-004 | 로컬 검색 성능 | SR-003과 동일 데이터 | 100회 retrieval latency 측정 | deterministic provider 기준 p95 < 1초 | PERF-RAG-01 |
| SR-005 | 외부 provider smoke | 실제 Clova secret, LiveKit egress, public callback URL | target transcription start -> audio -> stop -> dialogue | 실시간 provider callback, egress, target API를 포함한 실제 흐름 성공 | FR-CALL-01, FR-STT-01~05 |

### Pass Criteria

- target meeting session은 provider callback text를 보존 정책 밖 파일에 복사하지 않는다.
- segment마다 embedding job을 만들지 않고 transcript 완료 transaction에서 하나만 만든다.
- local deterministic p95는 DB/retriever 경계만 측정한다. OpenAI provider 품질·외부 latency와 LiveKit egress는 SR-005 별도 환경에서 측정한다.

### Execution Status

- SR-001~SR-004: local PostgreSQL integration과 deterministic embedding provider로 통과했다. 200개 segment, 100회 Meeting retrieval에서 local p95는 `8.85 ms`였다.
- SR-005: 48 kHz WebSocket -> resampler -> Cloud STT 경로에서 Korean PCM 전사 callback 65건을 확인했다. 별도 opt-in target smoke는 실제 Cloud callback -> JPA dialogue `COMPLETED` -> `TRANSCRIPT_COMPLETED` job 1건을 검증했다. valid LiveKit Cloud credential, 임시 ngrok callback, browser LiveKit client에서 published audio track의 Egress를 시작해 callback transcript 60건을 확인했다. Egress API는 `ws(s)` signalling URL을 `http(s)` API URL로 정규화한다.
- SR-006: 기본 단위 test에서 target Egress WebSocket 종료가 `MeetingTranscript=COMPLETED`로 종결되는지, legacy 세션은 유지되는지, Egress stop 실패가 `FAILED` 종결과 `503 STT_PROVIDER_UNAVAILABLE`로 변환되는지 검증한다.

## Minimum Implementation Order

1. T039 Space access policy를 pure unit test로 먼저 고정한다.
2. T040 Meeting access policy를 pure unit test로 고정한다.
3. T094 LiveKit token service test에서 T040 policy를 mock 또는 fake로 주입한다.
4. Controller/API test는 service unit test가 통과한 뒤 401/403/404/503 응답 shape만 얇게 확인한다.

## Required Verification

| Area | Command |
| --- | --- |
| Backend unit tests | `cd backend && ./gradlew test` |
| Markdown/diff sanity | `git diff --check` |

구현 PR에서 task를 `[x]`로 바꾸려면 위 command 결과 또는 미실행 사유를 `specs/001-meetingmind-core/implement.md`에 기록한다.
