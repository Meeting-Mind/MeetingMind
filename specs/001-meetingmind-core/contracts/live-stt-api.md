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

- Current Prototype, explicit `legacy-livekit` profile only

### Auth and Permissions

- Prototype에서는 최소 입력 기반으로 token을 발급한다. `legacy-livekit` profile을 명시한 수동 호환 환경에서만 controller가 등록된다.
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

- 기본 `local`/`db` runtime에는 등록하지 않는다. Target 전환 후 수동 호환용 `legacy-livekit` profile에서만 남긴다.

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
- `meeting.roomCode`가 있으면 해당 값을 roomName으로 사용하고, 없으면 기존처럼 `meetingId`를 roomName으로 사용한다.
- 즉시 회의는 `roomCode`를 재사용해 같은 Space 회의방으로 반복 입장할 수 있지만, transcript/report/task/AI scope는 새 `meetingId`별로 유지한다.

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

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 `HOST`

### Data Scope

- Meeting scope

### Request

```json
{
  "mode": "realtime",
  "trackId": "livekit-audio-track-id"
}
```

### Validation

- `mode`: 현재 `realtime`만 지원한다. `postprocess`는 오디오 업로드 결정(T027) 이후 추가한다.
- `trackId`: `realtime`일 때 required. LiveKit track egress 대상이다.
- Core는 인증된 참여자의 `displayName`을 Core→STT 내부 시작 요청에 전달한다. STT session UUID나
  egress/track 식별자는 사용자용 화자명으로 저장하거나 표시하지 않는다.
- 같은 회의의 참가자는 각자 microphone `trackId`로 독립 STT session/track egress를 시작할 수 있다.
  회의 단위 `MeetingTranscript`가 이미 `PROCESSING`이면 새 row를 만들거나 409로 거부하지 않고 기존
  aggregate를 공유한다.

### Response

```json
{
  "meetingId": "meeting-001",
  "transcriptStatus": "PROCESSING",
  "sessionId": "stt-session-id",
  "egressId": "livekit-egress-id"
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
- 첫 `PROCESSING` 시작 시 `meeting_transcripts`를 저장한다. 참가자별 provider callback의 segment는 같은
  transcript에 speaker별로 즉시 저장하고, 마지막 활성 session이 stop/provider complete될 때만
  `COMPLETED`로 전환한다.
- Realtime STT가 별도 서비스인 `remote` gateway mode에서는 위 status/segment의 authoritative row가 STT DB에 있다. Core `/dialogue`는 사용자와 Meeting ACL을 먼저 검증한 뒤 Core→STT mTLS 내부 API의 transcript/status/partials를 반환한다. Core DB의 시작 row만 조회해 빈 segment를 성공 응답으로 반환하지 않는다.
- 회의방 `Leave`는 Meeting 종료가 아니다. 현재 브라우저가 소유한 track egress/STT session만 정상
  종료하고, 재참여 시 새 LiveKit microphone track으로 새 session을 시작한다. 다른 참가자의 활성
  session과 기존 segment는 유지한다.
- LiveKit Egress WebSocket이 정상 종료되면 마지막 audio를 provider에 flush한 뒤 해당 STT session만
  종료한다. 같은 meeting의 다른 `ACTIVE`/`STOPPING` session이 없을 때만 aggregate transcript를
  `COMPLETED`로 전환한다. legacy `/api/stt/*` 세션은 기존 수동 transcript 조회 호환을 위해 registry에서 제거하지 않는다.
- legacy `/api/stt/*` HTTP controller는 기본 `local`/`db` runtime에 등록하지 않는다. 수동 smoke에서만 `legacy-stt` profile을 명시적으로 함께 활성화하며, 제품 UI와 운영 환경은 target Meeting API만 사용한다.
- `COMPLETED` 전환은 DB trigger로 `TRANSCRIPT_COMPLETED` embedding job을 하나 생성한다. segment마다 job을 생성하지 않는다.
- 분리 배포의 stop 성공 후 Core는 같은 meeting의 `GET /internal/v1/meetings/{meetingId}/transcript`를 mTLS로 즉시 조회해 report/task/AI/RAG용 derived projection을 하나의 Core DB transaction으로 교체한다.
- projection은 STT `speakerId`, `segmentId`, sequence와 시간 범위를 보존하고 같은 snapshot 재시도를 no-op으로 처리한다. 이미 `COMPLETED`된 snapshot의 내용이 바뀐 경우만 `FULL_REINDEX`를 생성한다.
- STT stop은 terminal session에 멱등이므로 STT 완료 후 Core projection 전에 장애가 난 경우 동일 stop 요청이 snapshot projection을 다시 시도한다. STT와 Core DB 사이에 FK나 cross-DB runtime read를 추가하지 않는다.
- LiveKit `stopEgress`의 HTTP 412는 `FAILED_PRECONDITION`과 `EGRESS_FAILED`/`EGRESS_COMPLETE`/`EGRESS_ABORTED`가 함께 확인된 경우에만 이미 종료된 멱등 성공으로 처리한다. 다른 412 또는 5xx는 성공으로 완화하지 않는다.
- 개별 durable session이 `FAILED`여도 같은 meeting의 다른 활성 session이 있으면 aggregate transcript는
  `PROCESSING`을 유지한다. 마지막 활성 session 자체가 실패한 경우에만 `FAILED`로 전환한다.

## POST /api/v1/meetings/{meetingId}/transcription/{sessionId}/stop

현재 참가자의 실시간 STT 세션을 종료한다. 다른 활성 session이 없을 때만 저장된 transcript를
`COMPLETED`로 전환한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 회의 `HOST`

### Validation

- `sessionId`는 `meetingId`로 시작된 활성 STT 세션이어야 한다.

### Response

```json
{
  "meetingId": "meeting-001",
  "transcriptStatus": "COMPLETED"
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 종료 권한 없음
- `404 STT_SESSION_NOT_FOUND`: 세션이 없거나 다른 회의 세션
- `409 TRANSCRIPTION_NOT_PROCESSING`: 이미 완료되었거나 실패한 전사
- `503 STT_PROVIDER_UNAVAILABLE`: LiveKit Egress 종료 실패. 해당 session은 `FAILED`로 전환하되 다른
  활성 session이 있으면 공유 transcript는 `PROCESSING`을 유지한다.

## POST /api/v1/meetings/{meetingId}/transcription/stop

브라우저가 `sessionId`를 복구하지 못한 경우에도, 해당 회의의 서버 활성 STT 세션을 찾아 종료한다. 일반 종료는 `/{sessionId}/stop`을 우선 사용하며 이 endpoint는 종료 복구용이다.

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 회의 `HOST`

### Response

```json
{
  "meetingId": "meeting-001",
  "transcriptStatus": "COMPLETED"
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 종료 권한 없음
- `404 STT_SESSION_NOT_FOUND`: 서버에 활성 STT 세션이 없음
- `503 STT_PROVIDER_UNAVAILABLE`: LiveKit Egress 종료 실패

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
  "status": "PROCESSING",
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
  ],
  "partials": [
    {
      "speakerLabel": "화자 1",
      "speakerName": "김진수",
      "text": "현재 발화 중인 문장입니다"
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

- FR-STT-03: 실시간 자막 표시
- FR-STT-05: 다이얼로그 저장

### Notes

- `PROCESSING`에서도 이미 저장된 segment를 반환해 실시간 자막 polling을 지원한다. `COMPLETED`와 `FAILED`도 같은 shape으로 반환하며, segment가 없으면 `rows`는 빈 배열이다.
- `partials`는 아직 확정되지 않은 현재 발화이며 DB에 저장하지 않는다. 같은 session/track/provider segment ID의 후속 partial `text`는 이전 문자열에 덧붙이는 delta가 아니라 해당 발화의 최신 전체 snapshot으로 교체한다. provider delta는 mapper가 snapshot으로 조립한 뒤 assembler에 전달한다. `rawEvents`와 provider 원문 이벤트는 사용자 API에 포함하지 않는다.
- 원시 provider 이벤트는 기본 profile에서 노출하지 않는다. 진단이 필요할 때만 `stt-debug` profile과 `STT_DEBUG_TOKEN`을 명시해 `GET /internal/stt/sessions/{sessionId}/raw-events`를 사용한다. 세션 종료 후에는 in-memory 이벤트도 제거된다.
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
