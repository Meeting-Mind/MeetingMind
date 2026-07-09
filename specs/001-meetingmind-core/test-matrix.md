# Test Matrix: Authz and LiveKit Access

이 문서는 T039, T040, T094 구현자가 요구사항의 성공/실패 기준을 단위 테스트로 바로 옮길 수 있도록 작성한 test matrix다.

## Scope

| Task | Target | Test level |
| --- | --- | --- |
| T039 | Space access validation service 또는 policy 계층 | Pure unit test 우선 |
| T040 | Meeting access validation service 또는 policy 계층 | Pure unit test 우선 |
| T094 | LiveKit token 발급 권한 연동 | Service unit test, controller slice test 후보 |

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
| M-060 | SpaceMember 제거 시 member participant 회수 | removed user has same Space `participantType=member` participants | remove SpaceMember | those participants become `REVOKED` | D-016 |
| M-061 | SpaceMember 제거 후 회의 접근 차단 | participant became `REVOKED` | meeting detail, LiveKit, AI context check | `DENY_403_MEETING` | D-016, D-017 |
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
