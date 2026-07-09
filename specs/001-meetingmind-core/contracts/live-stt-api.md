# Live Meeting and STT API Contract

실시간 회의는 LiveKit/WebRTC 연결과 Meeting 상태를 분리해서 다룬다. 음성 원본은 기본 장기 보관하지 않는다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Current Prototype, Target Backend, Future Draft |
| Owner | Backend, Frontend, AI |
| Related requirements | FR-CALL-01, FR-CALL-02, FR-CALL-03, FR-CALL-04, FR-CALL-06, FR-STT-01, FR-STT-02, FR-STT-03, FR-STT-05, FR-STT-06, NFR-AZ-03, NFR-SEC-06, PERF-EXT-03 |
| Related data model | Meeting, MeetingParticipant, TranscriptSegment, MeetingSpeaker, AuditLog |

## POST /api/livekit/token

현재 prototype endpoint다. Target에서는 `/api/v1/meetings/{meetingId}/livekit-token`로 이동한다.

### Status

- Current Prototype

### Auth and Permissions

- Prototype에서는 최소 입력 기반으로 token을 발급한다.
- Target 구현에서는 사용하지 않는다.

### Data Scope

- Prototype room scope

### Request

```json
{
  "roomName": "meeting-001",
  "identity": "user-001",
  "name": "이미주"
}
```

### Validation

- `roomName`, `identity`, `name`: required

### Response

```json
{
  "serverUrl": "wss://...",
  "participantToken": "jwt",
  "roomName": "meeting-001",
  "identity": "user-001",
  "name": "이미주"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `503 LIVEKIT_NOT_CONFIGURED`: LiveKit 환경변수 누락

### Audit

- No audit event for prototype.

### Requirement Trace

- FR-CALL-01: 실시간 화상 회의 prototype
- FR-CALL-02: 실시간 오디오/비디오 송수신
- PERF-EXT-03: LiveKit token

### Notes

- Target 전환 후 legacy endpoint로만 남긴다.

## POST /api/v1/meetings/{meetingId}/livekit-token

Target endpoint. 인증 사용자와 회의 접근 권한을 기준으로 token을 발급한다.

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

- `meetingId` 접근 권한 확인
- token TTL은 짧게 설정한다.

### Response

```json
{
  "serverUrl": "wss://...",
  "participantToken": "jwt",
  "roomName": "meeting-001",
  "identity": "user-001",
  "name": "이미주",
  "expiresIn": 3600
}
```

### Errors

- `401 UNAUTHORIZED`: 인증 실패
- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음
- `503 LIVEKIT_NOT_CONFIGURED`: LiveKit 환경변수 누락

### Audit

- `LIVE_TOKEN_ISSUED`

### Requirement Trace

- FR-CALL-01: 실시간 화상 회의
- FR-CALL-02: 실시간 오디오/비디오 송수신
- NFR-AZ-03: 회의 ACL
- PERF-EXT-03: LiveKit token

### Notes

- token에는 meeting room과 사용자 identity만 포함하고 Space 전체 권한은 포함하지 않는다.

## POST /api/v1/meetings/{meetingId}/start

회의 상태를 `IN_PROGRESS`로 전환한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- Meeting scope

### Request

None.

### Validation

- `SCHEDULED` 상태에서만 시작 가능

### Response

```json
{
  "meetingId": "meeting-001",
  "status": "IN_PROGRESS"
}
```

### Errors

- `400 INVALID_REQUEST`: 상태 전이 오류
- `403 MEETING_ACCESS_DENIED`: 시작 권한 없음

### Audit

- `MEETING_UPDATED`

### Requirement Trace

- FR-CALL-01: 회의 시작
- FR-CALL-06: 회의 종료/퇴장 상태 관리

### Notes

- 실제 WebRTC room 생성은 LiveKit token 발급과 분리한다.
- HOST가 회의방에서 일시 퇴장해도 MeetingParticipant role과 accessStatus는 유지된다.

## POST /api/v1/meetings/{meetingId}/end

회의 상태를 `ENDED`로 전환하고 STT/보고서 후처리 후보를 생성한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- Meeting scope

### Request

None.

### Validation

- `IN_PROGRESS` 상태에서만 종료 가능

### Response

```json
{
  "meetingId": "meeting-001",
  "status": "ENDED"
}
```

### Errors

- `400 INVALID_REQUEST`: 상태 전이 오류
- `403 MEETING_ACCESS_DENIED`: 종료 권한 없음

### Audit

- `MEETING_UPDATED`

### Requirement Trace

- FR-CALL-01: 회의 종료
- FR-CALL-06: 회의 종료/퇴장

### Notes

- HOST의 회의 종료는 Meeting status를 `ENDED`로 전환하는 관리 동작이다. 회의방 일시 퇴장과 구분한다.

- 보고서/태스크 후보 생성은 비동기 처리 후보로 둔다.

## POST /api/v1/meetings/{meetingId}/transcription/start

실시간 STT 또는 후처리 STT를 시작한다.

### Status

- Future Draft

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- Meeting scope

### Request

```json
{
  "mode": "realtime"
}
```

### Validation

- `mode`: `realtime`, `postprocess`
- 실제 provider, 오디오 업로드 방식, async 처리 방식은 후속 결정 대상이다.

### Response

```json
{
  "meetingId": "meeting-001",
  "transcriptStatus": "PROCESSING"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 또는 상태 오류
- `403 MEETING_ACCESS_DENIED`: STT 시작 권한 없음
- `503 STT_PROVIDER_UNAVAILABLE`: 외부 STT 서비스 오류

### Audit

- `MEETING_TRANSCRIPTION_STARTED`

### Requirement Trace

- FR-STT-01: 실시간 STT 전사
- FR-STT-02: 발화자 식별
- FR-STT-05: 다이얼로그 저장
- NFR-AVAIL-02: 외부 API 실패 graceful degradation

### Notes

- `Transcript.status`는 `PENDING` -> `PROCESSING` -> `COMPLETED` 또는 `FAILED`로 전이한다.

## GET /api/v1/meetings/{meetingId}/dialogue

화면용 다이얼로그를 조회한다. 저장 모델은 `TranscriptSegment`다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 해당 회의 접근 권한 필요

### Data Scope

- Meeting transcript scope

### Request

None.

### Validation

- `meetingId` 접근 권한 확인

### Response

```json
{
  "meetingId": "meeting-001",
  "status": "COMPLETED",
  "rows": [
    {
      "segmentId": "segment-001",
      "speakerId": "speaker-001",
      "speakerLabel": "화자 1",
      "speakerName": "김진수",
      "startMs": 0,
      "endMs": 4200,
      "text": "안녕하세요. 회의 시작하겠습니다."
    }
  ]
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음
- `409 MEETING_NOT_COMPLETED`: transcript 미완료

### Audit

- No audit event.

### Requirement Trace

- FR-STT-03: 실시간 자막 표시
- FR-STT-05: 다이얼로그 저장

### Notes

- speaker displayName 변경은 `meeting-api.md`의 speaker 수정 endpoint를 사용한다.

## GET /api/v1/meetings/{meetingId}/transcript/download

권한자가 transcript를 다운로드한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 해당 회의 접근 권한 필요

### Data Scope

- Meeting transcript scope

### Query

- `format`: `txt`, `json`, `csv`

### Validation

- `format` enum 확인

### Response

Binary file response.

### Errors

- `400 INVALID_REQUEST`: format 오류
- `403 MEETING_ACCESS_DENIED`: 다운로드 권한 없음
- `409 MEETING_NOT_COMPLETED`: transcript 미완료

### Audit

- No audit event by default. 정책상 다운로드 추적이 필요하면 `TRANSCRIPT_DOWNLOADED`를 추가한다.

### Requirement Trace

- FR-STT-06: 다이얼로그 다운로드

### Notes

- 음성 원본 다운로드 API는 기본 제공하지 않는다.
