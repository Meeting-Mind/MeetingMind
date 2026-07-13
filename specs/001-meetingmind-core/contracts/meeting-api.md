# Meeting, ACL, Transcript, Report API Contract

회의 데이터 접근은 `SpaceMember`보다 좁은 `MeetingParticipant` ACL을 우선한다. `OWNER`/`ADMIN` override는 권한 매트릭스를 따른다.
사용자-facing 회의 참가 흐름은 `joinCode` 또는 회의 URL로 참가 신청을 만든 뒤 active `HOST`가 승인하는 방식이다. `OWNER`/`ADMIN`은 ACL 관리 override로 검토할 수 있고, `POST /participants`는 관리자/호스트의 수동 ACL 조정용이다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Backend |
| Owner | Backend, Frontend |
| Related requirements | FR-MREG-01, FR-MREG-02, FR-MREG-04, FR-MREG-05, FR-MREG-06, FR-MREG-07, FR-ACL-01, FR-ACL-02, FR-ACL-03, FR-ACL-05, FR-ACL-06, FR-RPT-03, FR-RPT-04, FR-RPT-05, FR-RPT-06, FR-RPT-07, FR-STT-04, FR-STT-05, NFR-AZ-01, NFR-AZ-03, NFR-SEC-06 |
| Related data model | Meeting, MeetingJoinRequest, MeetingParticipant, MeetingSpeaker, TranscriptSegment, MeetingReport, SourceReference, AuditLog |

## GET /api/v1/spaces/{spaceId}/meetings

Space 내 접근 가능한 회의 목록을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한과 meeting ACL을 모두 적용한다.

### Data Scope

- Space scope
- 사용자가 접근 가능한 Meeting만 반환한다.

### Query

- `status`: optional `SCHEDULED`, `IN_PROGRESS`, `ENDED`, `CANCELED`
- `from`: optional ISO-8601
- `to`: optional ISO-8601

### Validation

- `spaceId` 접근 권한 확인
- `status` enum 확인

### Response

```json
{
  "meetings": [
    {
      "id": "meeting-001",
      "spaceId": "space-001",
      "title": "Sprint Planning #12",
      "scheduledAt": "2026-07-10T10:00:00+09:00",
      "status": "SCHEDULED",
      "myRole": "HOST"
    }
  ]
}
```

### Errors

- `400 INVALID_REQUEST`: query 형식 오류
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음

### Audit

- No audit event.

### Requirement Trace

- FR-MREG-05: 회의 목록
- FR-MREG-07: 회의별 접근제어
- NFR-AZ-03: 회의 권한을 Space보다 좁게 적용

### Notes

- Project owner/admin override 범위는 `requirements/permissions.md`를 따른다.

## POST /api/v1/spaces/{spaceId}/meetings

회의를 생성한다. 캘린더 일정 생성 요구사항도 이 endpoint로 처리한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- Space scope
- 생성된 회의에는 생성자를 `HOST` participant로 등록한다.

### Request

```json
{
  "title": "Sprint Planning #12",
  "scheduledAt": "2026-07-10T10:00:00+09:00",
  "participantUserIds": []
}
```

### Validation

- `title`: required, blank 금지
- `scheduledAt`: required, ISO-8601
- `participantUserIds`: optional, 운영상 초기 ACL 지정용이다. 일반 사용자 참여는 회의 생성 후 URL/코드 참가 신청을 사용한다. 대상은 기존 사용자여야 하며 SpaceMember가 아니면 회의 단독 `guest` participant로 등록한다.
- 회의 생성 결과의 `joinCode` 또는 `joinUrl`은 이후 참가 신청에 사용한다.

### Response

```json
{
  "id": "meeting-001",
  "status": "SCHEDULED",
  "joinCode": "4f97c8e2a58f4d58a4476bcb6b65c208",
  "joinUrl": "/meetings/meeting-001?joinCode=4f97c8e2a58f4d58a4476bcb6b65c208"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 회의 생성 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음

### Audit

- `MEETING_CREATED`

### Requirement Trace

- FR-MREG-01: 회의 생성
- FR-CAL-04: 일정 생성
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 별도 CalendarEvent 엔티티가 필요해지면 ERD의 Draft Gap을 갱신한다.

## GET /api/v1/meetings/{meetingId}

회의 상세를 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 `MeetingParticipant`

### Data Scope

- Meeting scope

### Request

None.

### Validation

- `meetingId` 존재 및 접근 권한 확인

### Response

```json
{
  "id": "meeting-001",
  "spaceId": "space-001",
  "title": "Sprint Planning #12",
  "status": "SCHEDULED",
  "scheduledAt": "2026-07-10T10:00:00+09:00",
  "startedAt": null,
  "endedAt": null,
  "myRole": "HOST",
  "participants": []
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- No audit event.

### Requirement Trace

- FR-MREG-05: 회의 상세
- FR-MREG-07: 회의별 접근제어
- NFR-AZ-03: 회의 ACL 적용

### Notes

- 회의 게스트는 해당 meeting scope만 접근한다.

## PATCH /api/v1/meetings/{meetingId}

회의 title, schedule, status 후보 필드를 수정한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- Meeting scope

### Request

```json
{
  "title": "Sprint Planning #12",
  "scheduledAt": "2026-07-10T10:30:00+09:00",
  "status": "SCHEDULED"
}
```

### Validation

- `title`: optional, 제공 시 blank 금지
- `scheduledAt`: optional ISO-8601
- `status`: 허용 상태 전이만 가능

### Response

```json
{
  "id": "meeting-001",
  "title": "Sprint Planning #12",
  "scheduledAt": "2026-07-10T10:30:00+09:00",
  "status": "SCHEDULED"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 또는 상태 전이 오류
- `403 MEETING_ACCESS_DENIED`: 수정 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- `MEETING_UPDATED`

### Requirement Trace

- FR-MREG-06: 회의 상태/일정 관리
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 시작/종료는 live-stt API의 start/end endpoint를 우선 사용한다.

## DELETE /api/v1/meetings/{meetingId}

회의를 삭제 또는 취소 상태로 전환한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 기본 `OWNER` 또는 `HOST`
- `ADMIN` 삭제는 기본 권한이 아니며, 명시적 예외 정책이 있을 때만 허용한다.
- 진행 중/종료 회의 삭제 가능 범위는 정책으로 제한

### Data Scope

- Meeting scope
- transcript/report/embedding 삭제 또는 비활성화 정책을 함께 적용한다.

### Request

None.

### Validation

- 삭제 권한 확인
- 상태별 삭제 가능 여부 확인

### Response

```json
{
  "deleted": true
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 삭제 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음
- `409 MEETING_ALREADY_PROCESSING`: 처리 중 회의 삭제 거부

### Audit

- `MEETING_DELETED`

### Requirement Trace

- FR-MREG-04: 회의 삭제
- FR-ACL-07: 삭제 권한 제한
- POL-RETENTION-01: 회의 데이터 보존/삭제

### Notes

- soft delete와 cancel 상태 전환 기준은 Backend/Data owner가 확정한다.
- `ADMIN`은 회의 생성/참여자 관리/수정 override를 가질 수 있지만, 삭제는 기본 권한에 포함하지 않는다.

## GET /api/v1/meetings/{meetingId}/participants

회의 참여자와 role을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 `MeetingParticipant`

### Data Scope

- MeetingParticipant scope

### Request

None.

### Validation

- `meetingId` 접근 권한 확인

### Response

```json
{
  "participants": [
    {
      "id": "participant-001",
      "userId": "user-001",
      "displayName": "이미주",
      "role": "HOST",
      "participantType": "member",
      "accessStatus": "ACTIVE"
    }
  ]
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- No audit event.

### Requirement Trace

- FR-MREG-02: 회의 참여자 조회
- FR-MREG-07: 회의별 접근제어
- NFR-AZ-03: 회의 ACL 적용

### Notes

- guest participant는 SpaceMember가 아니어도 이 목록에 포함될 수 있다.

## POST /api/v1/meetings/{meetingId}/participants

회의 참여자를 추가한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- MeetingParticipant scope

### Request

```json
{
  "userId": "user-002",
  "role": "VIEWER",
  "participantType": "member"
}
```

### Validation

- `userId`: required
- `role`: `HOST`, `EDITOR`, `VIEWER`
- `participantType`: optional `member`, `guest`; 기본값은 `guest`
- `member` participant로 등록하려면 이미 SpaceMember여야 한다. `guest` participant는 특정 회의 접근권만 갖고 SpaceMember 또는 프로젝트 접근권을 생성하지 않는다.

### Response

```json
{
  "participantId": "participant-002",
  "role": "VIEWER",
  "accessStatus": "ACTIVE"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 MEETING_ACCESS_DENIED`: 참여자 추가 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- `MEETING_PARTICIPANT_CHANGED`

### Requirement Trace

- FR-MREG-02: 회의 초대/참여자 관리
- FR-ACL-01: 회의 권한 부여
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 직접 추가는 host/admin의 수동 ACL 조정용이다. 일반 사용자 참가 흐름은 join request와 host 승인으로 처리한다.
- 프로젝트 전체 접근권은 SpaceMember API 또는 Space invitation 수락으로만 생성한다.

## GET /api/v1/meetings/{meetingId}/join-requests

회의 참가 신청 목록을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- MeetingJoinRequest scope

### Request

None.

### Validation

- `meetingId` 접근 권한 확인

### Response

```json
{
  "requests": [
    {
      "id": "join-request-001",
      "userId": "user-002",
      "status": "PENDING",
      "requestedAt": "2026-07-10T10:00:00+09:00"
    }
  ]
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- No audit event.

### Requirement Trace

- FR-MREG-02: 회의 참가 신청/승인
- FR-MREG-07: 회의별 접근제어

### Notes

- Host는 pending 요청을 승인/거절하기 전에 먼저 확인한다.

## POST /api/v1/meetings/join-requests

회의 참가 신청을 만든다. 사용자는 회의 URL 또는 joinCode 하나만 입력하며 Backend가 대상 회의를 식별한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- joinCode 또는 회의 URL 일치

### Data Scope

- MeetingJoinRequest scope

### Request

```json
{
  "joinCodeOrUrl": "4f97c8e2a58f4d58a4476bcb6b65c208"
}
```

### Validation

- `joinCodeOrUrl`: required
- 입력값은 joinCode 또는 `joinCode` query가 포함된 회의 URL일 수 있다.
- joinCode는 추측하기 어려운 난수여야 하며 회의 ID에서 결정적으로 만들지 않는다.
- 유효한 code로 식별한 meeting에 이미 active participant가 있으면 거부한다.
- 같은 사용자와 meeting의 `PENDING` 신청은 최대 1개다.

### Response

```json
{
  "requestId": "join-request-001",
  "meetingId": "meeting-001",
  "status": "PENDING"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 MEETING_ACCESS_DENIED`: joinCode 또는 URL이 유효하지 않음. 회의 존재 여부를 별도로 노출하지 않는다.

### Audit

- `MEETING_JOIN_REQUEST_CREATED`

### Requirement Trace

- FR-MREG-02: 회의 참가 신청
- FR-MREG-03: 초대 알림/링크
- FR-MREG-07: 회의별 접근제어

### Notes

- join request는 SpaceMember를 만들지 않는다.
- 승인 전까지는 회의 데이터 접근이 없다.

## POST /api/v1/meetings/{meetingId}/join-requests/{requestId}/approve

회의 host가 참가 신청을 승인한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- MeetingJoinRequest scope
- 승인 시 MeetingParticipant scope를 생성한다.

### Request

None.

### Validation

- request 상태가 `PENDING`
- 승인 시 이미 participant가 있으면 거부

### Response

```json
{
  "requestId": "join-request-001",
  "status": "APPROVED",
  "participantId": "participant-002",
  "participantType": "guest"
}
```

### Errors

- `400 INVALID_REQUEST`: 상태 오류 또는 중복 참가
- `403 MEETING_ACCESS_DENIED`: 승인 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 또는 request 없음

### Audit

- `MEETING_JOIN_REQUEST_RESOLVED`

### Requirement Trace

- FR-MREG-02: 회의 참가 신청/승인
- FR-MREG-07: 회의별 접근제어

### Notes

- 승인 후 참여자 role은 기본 `VIEWER`로 등록한다. 프로젝트 membership이 있으면 `participantType=member`, 아니면 `guest`로 만든다.

## POST /api/v1/meetings/{meetingId}/join-requests/{requestId}/reject

회의 host가 참가 신청을 거절한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- MeetingJoinRequest scope

### Request

None.

### Validation

- request 상태가 `PENDING`

### Response

```json
{
  "requestId": "join-request-001",
  "status": "REJECTED"
}
```

### Errors

- `400 INVALID_REQUEST`: 상태 오류
- `403 MEETING_ACCESS_DENIED`: 거절 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 또는 request 없음

### Audit

- `MEETING_JOIN_REQUEST_RESOLVED`

### Requirement Trace

- FR-MREG-02: 회의 참가 신청/승인
- FR-MREG-07: 회의별 접근제어

### Notes

- 거절 후 동일 joinCode 재신청은 정책에 따라 허용할 수 있다.

## POST /api/v1/meetings/{meetingId}/invitations

회의 초대 링크를 생성한다. 회의 초대는 특정 회의 접근권만 부여하며 SpaceMember를 만들지 않는다.

> Superseded: D-022에 따라 현재 사용자-facing 흐름에서는 사용하지 않는다. URL/코드 기반 `MeetingJoinRequest`가 대체한다.

### Status

- Superseded

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- MeetingInvitation scope

### Request

```json
{
  "email": "guest@meetingmind.ai",
  "meetingRole": "VIEWER",
  "participantType": "guest"
}
```

### Validation

- `email`: required, email format
- `meetingRole`: `HOST`, `EDITOR`, `VIEWER`
- `participantType`: `member`, `guest`
- `guest` participant는 SpaceMember를 생성하지 않는다.

### Response

```json
{
  "invitationId": "meeting-invite-001",
  "status": "PENDING",
  "expiresAt": "2026-07-16T10:00:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 MEETING_ACCESS_DENIED`: 회의 초대 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- Superseded. No runtime event.

### Requirement Trace

- FR-MREG-02: 회의 참여자 초대
- FR-MREG-03: 초대 알림/링크
- FR-MREG-07: 회의별 접근제어
- NFR-SEC-06: 서버측 입력 검증

### Notes

- Space 초대와 분리한다. 회의 guest는 Project Knowledge와 Project AI 접근권을 기본으로 얻지 않는다.

## POST /api/v1/meetings/{meetingId}/invitations/{invitationId}/accept

회의 초대를 수락하고 `MeetingParticipant`를 생성한다.

> Superseded: D-022에 따라 현재 사용자-facing 흐름에서는 사용하지 않는다.

### Status

- Superseded

### Auth and Permissions

- 인증 필요
- 초대 대상 이메일 또는 초대 token 정책과 일치해야 한다.

### Data Scope

- MeetingInvitation scope에서 MeetingParticipant scope로 전환한다.

### Request

```json
{
  "token": "meeting-invite-token"
}
```

### Validation

- 초대 상태가 `PENDING`
- 만료되지 않은 초대
- guest 초대 수락 시 SpaceMember 생성 금지

### Response

```json
{
  "participantId": "participant-guest-001",
  "role": "VIEWER",
  "participantType": "guest",
  "status": "ACCEPTED"
}
```

### Errors

- `400 INVALID_REQUEST`: token 누락 또는 상태 오류
- `403 MEETING_ACCESS_DENIED`: 초대 대상 불일치
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- Superseded. No runtime event.

### Requirement Trace

- FR-MREG-02: 회의 참여자 초대
- FR-MREG-07: 회의별 접근제어

### Notes

- Meeting invitation은 회의 ACL만 생성한다.

## POST /api/v1/meetings/{meetingId}/invitations/{invitationId}/decline

회의 초대를 거절한다.

> Superseded: D-022에 따라 현재 사용자-facing 흐름에서는 사용하지 않는다.

### Status

- Superseded

### Auth and Permissions

- 인증 필요
- 초대 대상자 또는 초대 token 보유자

### Data Scope

- MeetingInvitation scope

### Request

```json
{
  "token": "meeting-invite-token"
}
```

### Validation

- 초대 상태가 `PENDING`

### Response

```json
{
  "invitationId": "meeting-invite-001",
  "status": "DECLINED"
}
```

### Errors

- `400 INVALID_REQUEST`: token 누락 또는 상태 오류
- `403 MEETING_ACCESS_DENIED`: 초대 대상 불일치

### Audit

- Superseded. No runtime event.

### Requirement Trace

- FR-MREG-02: 회의 참여자 초대

### Notes

- 거절 후 재초대 허용 정책은 Backend owner가 결정한다.

## PATCH /api/v1/meetings/{meetingId}/participants/{participantId}

회의 role을 변경하거나 접근을 회수한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- MeetingParticipant scope

### Request

```json
{
  "role": "EDITOR",
  "accessStatus": "ACTIVE"
}
```

### Validation

- `role`: optional `HOST`, `EDITOR`, `VIEWER`
- `accessStatus`: optional `ACTIVE`, `REVOKED`
- 변경 후에도 active `HOST`가 최소 1명 남아야 한다. 마지막 active `HOST`의 role 강등, `REVOKED` 전환, participant 제거는 거부한다.

### Response

```json
{
  "participantId": "participant-001",
  "role": "EDITOR",
  "accessStatus": "ACTIVE"
}
```

### Errors

- `400 INVALID_REQUEST`: role/status 오류
- `403 MEETING_ACCESS_DENIED`: role 변경 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 또는 participant 없음
- `409 LAST_ACTIVE_HOST_REQUIRED`: 마지막 active HOST 강등, 접근 회수, 제거 요청

### Audit

- `MEETING_PARTICIPANT_CHANGED`

### Requirement Trace

- FR-ACL-02: 회의 권한 변경/회수
- FR-ACL-06: 권한 변경 감사 추적

### Notes

- Space role 변경과 Meeting role 변경은 분리한다.
- 마지막 HOST를 없애려면 다른 active participant를 먼저 `HOST`로 승격한 뒤 기존 HOST를 변경한다.

## GET /api/v1/meetings/{meetingId}/transcript

회의 transcript를 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 해당 회의 접근 권한 필요
- `Transcript.status=COMPLETED` 전에는 `409 MEETING_NOT_COMPLETED`

### Data Scope

- Meeting scope
- Meeting AI context와 동일하게 현재 회의 transcript만 반환한다.

### Request

None.

### Validation

- `meetingId` 접근 권한 확인
- transcript 처리 상태 확인

### Response

```json
{
  "meetingId": "meeting-001",
  "language": "ko",
  "status": "COMPLETED",
  "speakers": [
    {
      "id": "speaker-1",
      "label": "화자 1",
      "displayName": "김철수"
    }
  ],
  "segments": [
    {
      "id": "segment-001",
      "speakerId": "speaker-1",
      "startMs": 0,
      "endMs": 4200,
      "text": "안녕하세요. 회의 시작하겠습니다."
    }
  ]
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: transcript 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음
- `409 MEETING_NOT_COMPLETED`: transcript 미완료

### Audit

- No audit event.

### Requirement Trace

- FR-STT-05: 다이얼로그 저장
- FR-MBOT-01: Meeting AI context 근거

### Notes

- transcript 원문 보존 기간은 정책 문서를 따른다.

## PATCH /api/v1/meetings/{meetingId}/speakers/{speakerId}

발화자 displayName을 수정한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 회의 `HOST`/`EDITOR`

### Data Scope

- MeetingSpeaker scope

### Request

```json
{
  "displayName": "김철수"
}
```

### Validation

- `displayName`: required, blank 금지

### Response

```json
{
  "speakerId": "speaker-1",
  "displayName": "김철수"
}
```

### Errors

- `400 INVALID_REQUEST`: displayName 오류
- `403 MEETING_ACCESS_DENIED`: speaker 수정 권한 없음
- `404 SPEAKER_NOT_FOUND`: speaker 없음

### Audit

- `TRANSCRIPT_SPEAKER_UPDATED`

### Requirement Trace

- FR-STT-04: 발화자 이름 수정
- FR-RPT-04: 회의록 근거 정정

### Notes

- speaker 수정은 transcript text 자체를 바꾸지 않는다.

## GET /api/v1/meetings/{meetingId}/reports

회의록 목록 또는 현재 회의록을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 해당 회의 접근 권한 필요

### Data Scope

- MeetingReport scope

### Query

- `status`: optional `CANDIDATE`, `DRAFT`, `CONFIRMED`

### Validation

- `status` enum 확인

### Response

```json
{
  "reports": [
    {
      "id": "report-001",
      "meetingId": "meeting-001",
      "status": "CONFIRMED",
      "title": "Sprint Planning #12 회의록",
      "summary": "권한 분리와 ERD 수정이 논의되었습니다.",
      "version": 1,
      "isCurrent": true,
      "createdAt": "2026-07-09T10:00:00+09:00"
    }
  ]
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의록 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- No audit event.

### Requirement Trace

- FR-RPT-03: 회의록 확정/저장 조회
- FR-RPT-06: 버전 관리

### Notes

- 버전별 상세 조회가 필요하면 `GET /reports/{reportId}`를 추가한다.
- 회의당 `status=CONFIRMED`이고 `isCurrent=true`인 report는 최대 1개다.

## POST /api/v1/meetings/{meetingId}/reports/{reportId}/confirm

AI가 만든 `CANDIDATE` 회의록을 공식 회의록으로 확정한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 회의 `HOST`/`EDITOR`

### Data Scope

- MeetingReport scope

### Request

None.

### Validation

- report가 해당 meeting에 속해야 한다.
- `status=CANDIDATE` 또는 `DRAFT`만 확정 가능하다.
- 새 report를 확정하면 기존 current confirmed report는 `isCurrent=false`가 된다.

### Response

```json
{
  "id": "report-001",
  "status": "CONFIRMED",
  "version": 1,
  "isCurrent": true
}
```

### Errors

- `400 INVALID_REQUEST`: 상태 전이 오류
- `403 MEETING_ACCESS_DENIED`: 확정 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 또는 report 없음

### Audit

- `REPORT_CONFIRMED`

### Requirement Trace

- FR-RPT-03: AI 회의록 확정/저장
- FR-RPT-06: 버전 관리

### Notes

- 회의당 `status=CONFIRMED`이고 `isCurrent=true`인 report는 최대 1개만 허용한다.

## PATCH /api/v1/meetings/{meetingId}/reports/{reportId}

회의록 초안을 수동 편집한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 회의 `HOST`/`EDITOR`

### Data Scope

- MeetingReport scope

### Request

```json
{
  "title": "Sprint Planning #12 회의록",
  "summary": "수정된 요약",
  "markdown": "## 요약\n수정된 요약"
}
```

### Validation

- report가 해당 meeting에 속해야 한다.
- 수정 가능 상태인지 확인한다.

### Response

```json
{
  "id": "report-001",
  "status": "DRAFT",
  "version": 2
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 또는 상태 오류
- `403 MEETING_ACCESS_DENIED`: 수정 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 또는 report 없음

### Audit

- `REPORT_UPDATED`

### Requirement Trace

- FR-RPT-04: AI 회의록 수정
- FR-RPT-05: 수동 편집
- FR-RPT-06: 버전 관리

### Notes

- AI 대화형 수정은 AI API에서 후보를 만들고 이 endpoint로 저장한다.

## GET /api/v1/meetings/{meetingId}/reports/{reportId}/download

회의록을 파일로 내보낸다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 해당 회의 접근 권한 필요

### Data Scope

- MeetingReport scope

### Query

- `format`: `markdown`, `pdf`, `docx`

### Validation

- `format` enum 확인
- report가 해당 meeting에 속해야 한다.

### Response

Binary file response.

### Errors

- `400 INVALID_REQUEST`: format 오류
- `403 MEETING_ACCESS_DENIED`: 다운로드 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 또는 report 없음

### Audit

- No audit event by default. 정책상 다운로드 추적이 필요하면 `REPORT_DOWNLOADED`를 추가한다.

### Requirement Trace

- FR-RPT-07: 회의록 다운로드/내보내기

### Notes

- PDF/DOCX 생성 방식은 별도 구현 task에서 확정한다.
