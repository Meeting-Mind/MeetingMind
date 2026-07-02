이 문서는 MeetingMind Core Prototype의 API 계약을 정의하기 위한 Markdown 문서이다.

# API Contracts: MeetingMind Core Prototype

## GET /api/workspace

현재 프로토타입용 통합 응답이다. 프론트엔드 데모 화면 전체 데이터를 반환한다.

### Response

- `workspaceHome`
- `liveMeeting`
- `meetingAi`
- `projectOverview`
- `reportAgent`

### Notes

- 실제 구현에서는 Space, Meeting, Report, Knowledge API로 분리한다.
- mock fallback과 동일한 shape를 유지해 UI 전환 비용을 낮춘다.

## POST /api/livekit/token

LiveKit 회의방 입장을 위한 JWT를 발급한다.

### Request

```json
{
  "roomName": "MM-03A",
  "identity": "user-123",
  "name": "이미주"
}
```

### Response

```json
{
  "serverUrl": "wss://...",
  "token": "...",
  "roomName": "MM-03A",
  "identity": "user-123",
  "name": "이미주"
}
```

### Errors

- `503`: LiveKit 환경변수가 설정되지 않음
- `400`: request validation 실패

## POST /api/meeting-ai/ask

현재 회의 컨텍스트만 기반으로 질문에 답한다.

### Request

```json
{
  "question": "김진수가 API 구조에 대해 정확히 어떤 의견을 냈어?",
  "transcript": [
    { "time": "06:10:03", "speaker": "김진수", "text": "ERD 구조를 수정해야 합니다." }
  ],
  "decisions": [
    { "title": "회의/프로젝트 권한 분리", "meta": "06:12:21" }
  ],
  "actions": [
    { "title": "김진수 · ERD 수정안 문서화", "meta": "Owner" }
  ]
}
```

### Response

```json
{
  "answer": "김진수는 ERD 구조 수정이 필요하다고 언급했습니다. 근거: 06:10:03",
  "model": "gpt-4.1-mini"
}
```

### Rules

- 서버가 전달받은 컨텍스트 밖의 내용을 추정하지 않는다.
- 답변은 한국어로 작성한다.
- 가능하면 출처를 포함한다.
