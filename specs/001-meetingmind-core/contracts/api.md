이 문서는 MeetingMind Core Prototype의 API 계약을 정의하기 위한 Markdown 문서이다.

# API Contracts: MeetingMind Core Prototype

## Contract Status

- Current Prototype: 현재 저장소에 존재하거나 mock fallback과 직접 연결된 계약이다.
- Target Contract: Core Prototype 이후 실제 구현 전환 시 우선 맞출 계약이다.
- Future Draft: 현재 `spec.md`에서 Out of Scope인 실제 STT/파일 업로드 흐름의 후속 설계 후보다.

## Common API Rules

| Rule | Value |
| --- | --- |
| Base URL | Current Prototype은 기존 경로 유지. Target Contract는 `/api/v1` 후보로 검토한다. |
| Content-Type | 기본 `application/json; charset=utf-8`. 파일 업로드 후보 API만 `multipart/form-data`. |
| Auth | 인증 방식은 `clarify.md` Q-001 결정 전까지 확정하지 않는다. 인증 도입 후에는 `Authorization: Bearer {accessToken}` 후보를 우선 검토한다. |
| Time | API 날짜시간은 ISO-8601, transcript 위치는 `startMs`, `endMs` 밀리초를 사용한다. |
| Empty arrays | 배열 값이 없으면 `null` 대신 `[]`를 반환한다. |
| Error body | 클라이언트 분기를 위해 고정 `code`를 포함한다. 내부 예외명, stack trace, SQL은 응답에 포함하지 않는다. |

### Common Error Response

```json
{
  "code": "MEETING_NOT_FOUND",
  "message": "회의를 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "01J..."
}
```

| HTTP | code | Situation |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청값이 잘못됨 |
| 401 | `UNAUTHORIZED` | 인증 실패 또는 토큰 만료 |
| 403 | `SPACE_ACCESS_DENIED` | Space 접근 권한 없음 |
| 403 | `MEETING_ACCESS_DENIED` | 회의 참여자 또는 명시 권한 없음 |
| 403 | `AI_CONTEXT_FORBIDDEN` | 권한 필터 전 데이터가 AI 컨텍스트로 요청됨 |
| 404 | `SPACE_NOT_FOUND` | Space를 찾을 수 없음 |
| 404 | `MEETING_NOT_FOUND` | 회의를 찾을 수 없음 |
| 404 | `SPEAKER_NOT_FOUND` | 발화자를 찾을 수 없음 |
| 409 | `MEETING_NOT_COMPLETED` | 처리 완료 전 전사본/보고서/요약 요청 |
| 409 | `MEETING_ALREADY_PROCESSING` | 이미 처리 중인 회의에 중복 처리 요청 |
| 413 | `AUDIO_FILE_TOO_LARGE` | 향후 파일 업로드 용량 초과 |
| 422 | `TRANSCRIPTION_FAILED` | 향후 STT/발화자 구분 처리 실패 |
| 503 | `LIVEKIT_NOT_CONFIGURED` | LiveKit 환경변수가 설정되지 않음 |
| 503 | `STT_PROVIDER_UNAVAILABLE` | 향후 외부 STT 서비스 응답 없음 |
| 503 | `AI_PROVIDER_UNAVAILABLE` | AI provider 응답 없음 |

## Meeting Status

| status | Meaning |
| --- | --- |
| `CREATED` | 회의가 생성되었지만 시작/처리 전 |
| `SCHEDULED` | 회의가 예약됨 |
| `LIVE` | 실시간 회의 중 |
| `PROCESSING` | STT, 보고서, 요약, embedding 등 비동기 처리 중 |
| `COMPLETED` | 회의 처리 완료, transcript/report/summary 조회 가능 |
| `FAILED` | 처리 실패. `failureReason` 참조 |

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

- `503 LIVEKIT_NOT_CONFIGURED`: LiveKit 환경변수가 설정되지 않음
- `400 INVALID_REQUEST`: request validation 실패

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

## AI Prototype Contracts

이 섹션은 현재 AI 담당 workstream의 prototype 계약이다. Backend 권한 필터와 저장 API가 준비되기 전까지 AI 서버는 `VITE_AI_API_BASE_URL`로 직접 호출 가능한 계약을 제공한다. 실제 Frontend 화면 연결은 Frontend 담당 작업으로 남기며, 모든 요청 컨텍스트는 mock 데이터 또는 이미 권한 필터링된 데모 데이터로 간주한다.

### Common AI Source Metadata

```json
{
  "sourceId": "segment-001",
  "type": "transcript",
  "title": "Sprint Planning #12",
  "speaker": "김진수",
  "time": "06:10:03",
  "startMs": 370300,
  "endMs": 374100,
  "text": "ERD 구조를 수정해야 합니다."
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `sourceId` | Yes | prototype에서는 transcript index 또는 mock id 사용 가능 |
| `type` | Yes | `transcript`, `decision`, `action`, `projectKnowledge`, `glossary` |
| `title` | No | 회의명, 문서명, 또는 지식 항목명 |
| `speaker` | No | transcript 출처일 때 사용 |
| `time` | No | 현재 mock transcript의 표시 시간 |
| `startMs`, `endMs` | No | target transcript 계약 전환 후 사용 |
| `text` | Yes | AI 답변 근거로 전달된 최소 원문 |

### Prototype Rule

- AI 서버는 요청에 포함된 context 밖의 내용을 추정하지 않는다.
- 근거가 없으면 `unsupported: true`와 함께 확인 불가 응답을 반환한다.
- Project AI context에는 프로젝트 지식과 접근 가능한 회의 요약만 포함해야 한다. 실제 접근 권한 필터링은 Backend 담당자 작업 전까지 mock/prototype 전제다.
- 저장이 필요한 결과는 candidate로만 반환한다. 실제 report/action item/task 저장은 Backend API 확정 후 연결한다.

### RAG Prototype Chunk

실제 STT/DB/pgvector 구현 전에는 AI 서버가 요청으로 받은 mock transcript와 project context를 아래 논리 구조로 변환해 in-memory retriever에서 사용한다.

```json
{
  "chunkId": "meeting-001:transcript:0001",
  "scope": "meeting",
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "sourceType": "transcript",
  "sourceId": "segment-001",
  "sourceSegmentIds": ["segment-001", "segment-002"],
  "title": "3회차 API 설계 회의",
  "speakerNames": ["김진수", "이미주"],
  "startMs": 370300,
  "endMs": 390000,
  "content": "김진수: ERD 구조를 수정해야 합니다.\n이미주: 권한 관리는 회의 단위로 분리하는 것이 좋겠습니다.",
  "embeddingText": "회의: 3회차 API 설계 회의\n범위: meeting\n출처: transcript\n시간: 06:10:03-06:30:00\n발화자: 김진수, 이미주\n내용:\n김진수: ERD 구조를 수정해야 합니다.\n이미주: 권한 관리는 회의 단위로 분리하는 것이 좋겠습니다.",
  "metadata": {
    "language": "ko",
    "visibility": "already_filtered",
    "createdFrom": "stt",
    "tags": ["ERD", "권한"]
  }
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `chunkId` | Yes | sourceType과 sequence를 포함하는 안정적 id |
| `scope` | Yes | `meeting` 또는 `project` |
| `projectId` | Yes | Space/Project 식별자. prototype에서는 mock id 가능 |
| `meetingId` | No | ProjectKnowledge-only chunk는 null 가능 |
| `sourceType` | Yes | `transcript`, `meetingSummary`, `decision`, `actionItem`, `report`, `projectKnowledge`, `glossary` |
| `sourceId` | Yes | 원본 segment/report/knowledge id |
| `sourceSegmentIds` | No | transcript window chunk가 포함한 segment id 목록 |
| `speakerNames` | No | transcript chunk 발화자 목록 |
| `startMs`, `endMs` | No | transcript chunk 시간 범위 |
| `content` | Yes | 출처 표시용 원문 또는 요약 |
| `embeddingText` | Yes | embedding provider에 전달할 정규화 텍스트 |
| `metadata` | No | 권한 필터 이후 상태, language, tags, 승인 상태 등 |

### RAG Prototype Search Rules

- STT 원본 저장 단위는 발화자 + 발화 내용 + 시간 범위의 `TranscriptSegment`다.
- 임베딩 단위는 여러 transcript segment를 묶은 `RagChunk`다. 짧은 발화 1개만 단독 임베딩하지 않는다.
- `meeting` scope 검색은 단일 `meetingId` chunk만 사용한다.
- `project` scope 검색은 `projectKnowledge`와 already-filtered meeting chunk만 사용한다.
- Project AI는 공식 지식(`projectKnowledge`)과 회의 기록(`transcript`, `meetingSummary`, `decision`, `actionItem`)을 응답 출처에서 구분한다.
- Backend 권한 필터가 구현되기 전까지 prototype 요청 context는 already-filtered로 간주하고, AI 서버 내부에서 권한 확대를 시도하지 않는다.

## POST /api/meeting-ai/explain-term

회의 중 transcript 또는 Domain Dictionary 기준으로 특정 단어의 의미를 설명한다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "meetingTitle": "3회차 API 설계 회의",
  "term": "pgvector",
  "selectedText": "pgvector 기반으로 회의별 임베딩을 분리하겠습니다.",
  "glossary": [
    {
      "term": "pgvector",
      "definition": "PostgreSQL에서 vector similarity search를 지원하는 확장입니다.",
      "sourceId": "glossary-pgvector"
    }
  ],
  "transcript": [
    { "time": "06:10:03", "speaker": "김진수", "text": "pgvector 기반으로 회의별 임베딩을 분리하겠습니다." }
  ]
}
```

### Response

```json
{
  "term": "pgvector",
  "explanation": "pgvector는 PostgreSQL 안에서 벡터 검색을 할 수 있게 해주는 확장입니다. 이 회의에서는 회의별 임베딩 검색 저장소 후보로 언급되었습니다.",
  "sourceType": "glossary",
  "sources": [
    {
      "sourceId": "glossary-pgvector",
      "type": "glossary",
      "title": "Domain Dictionary",
      "text": "PostgreSQL에서 vector similarity search를 지원하는 확장입니다."
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

## POST /api/meeting-ai/generate-report

회의 transcript를 기반으로 요약과 보고서 초안을 생성한다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "title": "Sprint Planning #12",
  "transcript": [
    { "time": "06:10:03", "speaker": "김진수", "text": "ERD 구조를 수정해야 합니다." }
  ],
  "decisions": [
    { "title": "회의/프로젝트 권한 분리", "meta": "06:12:21" }
  ],
  "actions": [
    { "title": "김진수 · ERD 수정안 문서화", "meta": "Owner" }
  ],
  "format": "markdown"
}
```

### Response

```json
{
  "summary": "회의/프로젝트 권한 분리와 ERD 수정 필요성이 논의되었습니다.",
  "decisions": [
    {
      "title": "회의/프로젝트 권한 분리",
      "rationale": "회의 접근 권한과 프로젝트 지식 접근 범위를 분리해야 하기 때문입니다.",
      "sourceIds": ["decision-001"]
    }
  ],
  "actionItems": [
    {
      "title": "ERD 수정안 문서화",
      "assignee": "김진수",
      "dueDate": null,
      "sourceIds": ["action-001"],
      "confirmationState": "candidate"
    }
  ],
  "markdown": "## 요약\n회의/프로젝트 권한 분리와 ERD 수정 필요성이 논의되었습니다.",
  "sources": [],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

## POST /api/meeting-ai/chat

회의별 챗봇이다. 단일 회의 transcript, decisions, actions만 context로 사용한다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "meetingTitle": "Sprint Planning #12",
  "question": "김진수가 맡은 후속 작업이 뭐야?",
  "transcript": [
    { "time": "06:10:03", "speaker": "김진수", "text": "ERD 구조를 수정해야 합니다." }
  ],
  "decisions": [],
  "actions": [
    { "title": "김진수 · ERD 수정안 문서화", "meta": "Owner" }
  ]
}
```

### Response

```json
{
  "answer": "김진수의 후속 작업 후보는 ERD 수정안 문서화입니다.",
  "sources": [
    {
      "sourceId": "action-001",
      "type": "actionItem",
      "text": "김진수 · ERD 수정안 문서화"
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

## POST /api/project-ai/chat

프로젝트별 챗봇이다. 프로젝트 지식과 접근 가능한 회의 요약만 context로 사용한다. prototype 단계에서는 호출자가 전달한 프로젝트 context를 이미 필터링된 것으로 간주한다.

### Request

```json
{
  "projectId": "project-001",
  "question": "이번 프로젝트에서 권한 관련 남은 리스크가 뭐야?",
  "projectKnowledge": [
    {
      "sourceId": "knowledge-001",
      "title": "권한 설계 메모",
      "text": "Meeting AI는 회의 접근 권한이 있는 사용자만 사용할 수 있다."
    }
  ],
  "meetings": [
    {
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12",
      "summary": "회의/프로젝트 권한 분리와 ERD 수정 필요성이 논의되었습니다."
    }
  ]
}
```

### Response

```json
{
  "answer": "남은 리스크는 Backend 권한 필터와 Project AI context 조립이 아직 구현되지 않았다는 점입니다.",
  "sources": [
    {
      "sourceId": "knowledge-001",
      "type": "projectKnowledge",
      "title": "권한 설계 메모",
      "text": "Meeting AI는 회의 접근 권한이 있는 사용자만 사용할 수 있다."
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

## POST /api/meeting-ai/extract-tasks

회의 종료 시점에 transcript와 보고서 초안에서 태스크 후보를 추출한다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "title": "Sprint Planning #12",
  "transcript": [
    { "time": "06:10:03", "speaker": "김진수", "text": "제가 ERD 수정안을 문서화하겠습니다." }
  ],
  "summary": "ERD 수정안 문서화가 필요합니다.",
  "participants": [
    { "name": "김진수", "role": "participant" }
  ]
}
```

### Response

```json
{
  "tasks": [
    {
      "title": "ERD 수정안 문서화",
      "assignee": "김진수",
      "dueDate": null,
      "sourceIds": ["segment-001"],
      "confirmationState": "candidate"
    }
  ],
  "sources": [
    {
      "sourceId": "segment-001",
      "type": "transcript",
      "speaker": "김진수",
      "time": "06:10:03",
      "text": "제가 ERD 수정안을 문서화하겠습니다."
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

## Target Contract: Transcript 조회

`GET /api/v1/meetings/{meetingId}/transcript`

회의 접근 권한이 있는 사용자만 조회할 수 있다. `status`가 `COMPLETED`가 아니면 `409 MEETING_NOT_COMPLETED`를 반환한다.

### Response

```json
{
  "meetingId": "meeting-uuid",
  "language": "ko",
  "speakers": [
    { "id": "speaker-1", "label": "화자 1", "displayName": "김철수" },
    { "id": "speaker-2", "label": "화자 2", "displayName": null }
  ],
  "segments": [
    {
      "id": "segment-uuid",
      "speakerId": "speaker-1",
      "startMs": 0,
      "endMs": 4200,
      "text": "안녕하세요. 회의 시작하겠습니다."
    }
  ]
}
```

### Rules

- `speakers[].label`은 자동 발화자 구분 식별명이다.
- `speakers[].displayName`은 사용자가 지정한 실제 이름이며, 미지정 시 `null`이다.
- `segments`는 `startMs` 오름차순으로 반환한다.
- transcript 데이터는 Meeting AI 컨텍스트 후보가 될 수 있으므로 Backend 권한 필터 이후에만 AI 서버로 전달한다.

## Target Contract: Speaker 이름 수정

`PATCH /api/v1/meetings/{meetingId}/speakers/{speakerId}`

회의 `host` 또는 `editor` 권한을 가진 사용자만 자동 구분된 발화자 이름을 수정할 수 있다.

### Request

```json
{
  "displayName": "김철수"
}
```

### Response

```json
{
  "id": "speaker-1",
  "label": "화자 1",
  "displayName": "김철수"
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의 접근 또는 speaker 수정 권한 없음
- `404 MEETING_NOT_FOUND`: 회의를 찾을 수 없음
- `404 SPEAKER_NOT_FOUND`: 발화자를 찾을 수 없음

## Future Draft: Async STT Processing

이 섹션은 실제 오디오 업로드, STT, 발화자 구분, 원본 요약 생성을 위한 후속 API 후보이다. 현재 Core Prototype에서는 실제 STT 파이프라인이 Out of Scope이므로 구현 계약으로 확정하지 않는다.

### 후보 흐름

1. `POST /api/v1/spaces/{spaceId}/meetings`로 회의 메타데이터를 생성한다.
2. `POST /api/v1/meetings/{meetingId}/audio`로 오디오 파일을 업로드한다.
3. 서버는 `PROCESSING` 상태로 전환하고 STT, 발화자 구분, 요약을 비동기 처리한다.
4. 클라이언트는 `GET /api/v1/meetings/{meetingId}`로 상태를 polling한다.
5. `COMPLETED` 이후 transcript, report, summary를 조회한다.

### Candidate Endpoints

| Function | Method | URI | Success |
| --- | --- | --- | --- |
| 회의 생성 | POST | `/api/v1/spaces/{spaceId}/meetings` | 201 Created |
| 오디오 업로드 | POST | `/api/v1/meetings/{meetingId}/audio` | 202 Accepted |
| 회의 상세/상태 조회 | GET | `/api/v1/meetings/{meetingId}` | 200 OK |
| 전사본 조회 | GET | `/api/v1/meetings/{meetingId}/transcript` | 200 OK |
| 요약 조회 | GET | `/api/v1/meetings/{meetingId}/summary` | 200 OK |
| 요약 재생성 | POST | `/api/v1/meetings/{meetingId}/summary` | 202 Accepted |
| 발화자 이름 수정 | PATCH | `/api/v1/meetings/{meetingId}/speakers/{speakerId}` | 200 OK |

### Draft Constraints

- 오디오 업로드 지원 형식, 최대 용량, presigned URL 전환 여부는 후속 결정으로 둔다.
- 요약 응답은 원본 transcript 근거 또는 source metadata를 포함해야 한다.
- Space/Meeting 권한 필터는 업로드, 조회, 재생성, speaker 수정 전에 적용한다.
