# AI API Contract

AI API는 현재 FastAPI 서버에 prototype 구현이 있다. Target architecture에서는 Backend가 인증/권한 필터와 context 조립을 수행한 뒤 AI 서버에 already-filtered context만 전달한다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Current Prototype, Backend-to-AI Internal |
| Owner | AI, Backend |
| Related requirements | FR-MBOT-01, FR-MBOT-02, FR-MBOT-03, FR-MBOT-04, FR-PBOT-01, FR-PBOT-02, FR-PBOT-03, FR-PBOT-04, FR-RPT-01, FR-RPT-02, FR-TASK-01, FR-TERM-01, FR-TERM-02, FR-TERM-03, FR-TERM-04, NFR-AI-01, NFR-AI-02, NFR-AZ-01, NFR-AZ-02, NFR-AZ-04, NFR-COST-01 |
| Related data model | TranscriptSegment, MeetingReport, ProjectKnowledge, EmbeddingChunk, SourceReference, TaskCandidate |

## Common AI Rules

- Meeting AI는 단일 `meetingId` 범위만 검색한다.
- Project AI는 `ProjectKnowledge`와 권한 필터를 통과한 meeting chunk만 검색한다.
- 근거가 없으면 `unsupported: true`를 반환하고 추정하지 않는다.
- 근거 0건이면 LLM을 호출하지 않는다.
- 저장성 결과는 `candidate` 상태로만 반환한다. 실제 저장/확정은 Backend API가 담당한다.
- 응답에는 가능한 한 `sources[]`를 포함한다.

## Prototype vs Target Boundary

| Topic | Current Prototype | Target Backend-to-AI |
| --- | --- | --- |
| Auth/permission | AI 서버 직접 호출은 인증/인가를 처리하지 않는다. | Backend가 인증, Space/Meeting 권한, RAG 선필터를 먼저 처리한다. |
| Request strictness | 일부 endpoint는 기존 frontend/prototype 호환을 위해 `meetingId` 또는 `title` fallback을 허용한다. | Backend가 필수 식별자와 source metadata를 채운 strict request만 전달한다. |
| Source trust | 요청에 포함된 transcript/knowledge/source는 already-filtered prototype input으로 간주한다. | AI 서버는 Backend가 필터링한 context만 받으며, 권한 필터 전 데이터를 받으면 오류로 처리한다. |
| Error shape | public/internal 경로 모두 provider 설정·호출·응답 실패를 raw provider detail 없는 `503 AI_PROVIDER_UNAVAILABLE`로 반환한다. 응답 body는 공통 `{code, message, fieldErrors, traceId}` shape를 사용한다. | Backend는 같은 code를 public 응답으로 전달한다. |
| Audit | AI 서버 observability log만 남기며 persistent audit event는 없다. | Backend가 권한 확인 후 `AI_REQUESTED` audit event를 기록한다. |
| Report context in chat | Meeting chat request는 transcript/decision/action 중심이다. | Backend context assembly 이후 report chunk도 source metadata와 함께 포함할 수 있다. |

## POST /api/meeting-ai/explain-term

회의 중 transcript 또는 Domain Dictionary 기준으로 특정 용어를 설명한다.

### Status

- Current Prototype
- Backend-to-AI Internal target 후보

### Auth and Permissions

- AI 서버 직접 호출 시 인증은 prototype 범위 밖이다.
- Target에서는 Backend가 meeting 접근 권한을 확인하고 already-filtered context만 전달한다.

### Data Scope

- Meeting scope
- `glossary`와 현재 회의 transcript만 사용한다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "meetingTitle": "Sprint Planning #12",
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
    {
      "time": "06:10:03",
      "speaker": "김진수",
      "text": "pgvector 기반으로 회의별 임베딩을 분리하겠습니다."
    }
  ]
}
```

### Validation

Current Prototype:

- `term`: required
- `meetingId`: optional. 생략하면 `prototype-meeting`으로 간주한다.
- `selectedText`는 transcript 검색 결과가 없을 때 already-filtered prototype context로 source화될 수 있다.

Target Backend-to-AI:

- `meetingId`, `term`: required
- Backend가 meeting 접근 권한을 검증하고 해당 meeting source만 전달한다.
- 선택 텍스트는 Backend가 source metadata를 붙이거나 이미 필터링된 source로 변환해야 한다.

### Response

```json
{
  "term": "pgvector",
  "explanation": "pgvector는 PostgreSQL 안에서 벡터 검색을 할 수 있게 해주는 확장입니다.",
  "sourceType": "glossary",
  "sources": [
    {
      "sourceId": "glossary-pgvector",
      "type": "glossary",
      "title": "pgvector",
      "text": "PostgreSQL에서 vector similarity search를 지원하는 확장입니다."
    }
  ],
  "unsupported": false,
  "model": "local-glossary"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: Target에서 권한 필터 전 데이터가 전달됨
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- Current Prototype: AI server observability log only
- Target: Backend records `AI_REQUESTED`

### Requirement Trace

- FR-TERM-01: 용어 설명
- FR-TERM-02: 사전 우선 제공
- FR-TERM-03: 미등록 LLM 호출
- FR-TERM-04: 근거 범위 제한
- NFR-AI-01: 근거 없는 답변 방지
- NFR-COST-01: 등록 용어 LLM 호출 회피

### Notes

- glossary exact match는 LLM 호출 없이 응답할 수 있다.

## POST /api/meeting-ai/chat

회의별 챗봇 prototype endpoint다. Current Prototype은 단일 회의 transcript, decision, action item 근거를 사용한다.

### Status

- Current Prototype

### Auth and Permissions

- AI 서버 직접 호출 시 인증은 prototype 범위 밖이다.

### Data Scope

- Meeting scope
- `request.meetingId` 하나에 속한 source만 사용한다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "meetingTitle": "Sprint Planning #12",
  "question": "김진수가 맡은 후속 작업이 뭐야?",
  "transcript": [],
  "decisions": [],
  "actions": [
    {
      "title": "김진수 · ERD 수정안 문서화",
      "meta": "Owner"
    }
  ]
}
```

### Validation

Current Prototype:

- `meetingId`, `question`: required
- 요청에 포함된 transcript, decisions, actions는 이미 해당 meeting 범위로 필터링된 값으로 간주한다.

Target Backend-to-AI는 아래 `POST /api/internal/meeting-ai/chat`을 사용한다.

### Response

```json
{
  "answer": "김진수의 후속 작업 후보는 ERD 수정안 문서화입니다.",
  "sources": [
    {
      "sourceId": "action-001",
      "type": "actionItem",
      "title": "Sprint Planning #12",
      "text": "김진수 · ERD 수정안 문서화"
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- Current Prototype: AI server observability log only

### Requirement Trace

- FR-MBOT-01: 회의별 챗봇
- FR-MBOT-02: 단일 회의 범위 제한
- FR-MBOT-03: 출처 표시
- FR-MBOT-04: 근거 부재 처리
- NFR-AZ-04: Meeting AI/Project AI 검색범위 분리

### Notes

- 검색 결과가 없으면 LLM을 호출하지 않고 `unsupported: true`를 반환한다.

## POST /api/internal/meeting-ai/chat

Backend가 인증/권한 필터와 context 조립을 끝낸 뒤 호출하는 target internal Meeting AI endpoint다.

### Status

- Backend-to-AI Internal target

### Auth and Permissions

- AI 서버는 사용자 인증을 직접 처리하지 않는다.
- Backend가 meeting 접근 권한을 확인하고 already-filtered context만 전달한다.

### Data Scope

- Meeting scope
- `request.meetingId` 하나에 속한 `sources[]`만 사용한다.
- 검색 대상 source type은 `transcript`, `decision`, `actionItem`, `report`다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "meetingTitle": "Sprint Planning #12",
  "question": "김진수가 맡은 후속 작업이 뭐야?",
  "sources": [
    {
      "sourceId": "segment-001",
      "type": "transcript",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12",
      "speaker": "김진수",
      "time": "00:01:05-00:01:10",
      "startMs": 65000,
      "endMs": 70000,
      "text": "ERD 수정안 문서화가 필요합니다."
    },
    {
      "sourceId": "report-001",
      "type": "report",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12 회의록",
      "text": "회의별 ACL 분리와 ERD 수정 필요성이 논의되었습니다."
    }
  ]
}
```

### Validation

- `meetingId`, `question`: required
- `sources[].sourceId`, `sources[].type`, `sources[].meetingId`, `sources[].text`: required
- 모든 source의 `meetingId`는 request `meetingId`와 같아야 한다.
- source type은 `transcript`, `decision`, `actionItem`, `report`, `meetingSummary`, `projectKnowledge`, `glossary` enum을 따르되, Meeting chat 검색은 `transcript`, `decision`, `actionItem`, `report`만 대상으로 한다.

### Response

```json
{
  "answer": "김진수의 후속 작업 후보는 ERD 수정안 문서화입니다.",
  "sources": [
    {
      "sourceId": "action-001",
      "type": "actionItem",
      "title": "Sprint Planning #12",
      "text": "김진수 · ERD 수정안 문서화"
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: 다른 회의 source 포함
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- AI server observability log only
- Backend records `AI_REQUESTED` target audit event

### Requirement Trace

- FR-MBOT-01: 회의별 챗봇
- FR-MBOT-02: 단일 회의 범위 제한
- FR-MBOT-03: 출처 표시
- FR-MBOT-04: 근거 부재 처리
- NFR-AZ-04: Meeting AI/Project AI 검색범위 분리

### Notes

- 검색 결과가 없으면 LLM을 호출하지 않고 `unsupported: true`를 반환한다.

## POST /api/project-ai/chat

프로젝트별 챗봇이다. 공식 프로젝트 지식과 접근 가능한 회의 요약/기록만 사용한다.

### Status

- Current Prototype
- Backend-to-AI Internal target 후보

### Auth and Permissions

- AI 서버 직접 호출 시 인증은 prototype 범위 밖이다.
- Target에서는 Backend가 Space 접근 권한과 meeting ACL을 모두 필터링한다.

### Data Scope

- Space scope
- `ProjectKnowledge`와 권한 필터를 통과한 meeting source만 사용한다.

### Request

```json
{
  "projectId": "project-001",
  "question": "권한 관련 남은 리스크가 뭐야?",
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

### Validation

Current Prototype:

- `projectId`, `question`: required
- 요청의 `projectKnowledge`와 `meetings`는 prototype caller가 이미 허용한 값으로 간주한다.

Target Backend-to-AI는 아래 `POST /api/internal/project-ai/chat`을 사용한다.

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

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: 권한 필터 전 데이터 포함
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- Current Prototype: AI server observability log only
- Target: Backend records `AI_REQUESTED`

### Requirement Trace

- FR-PBOT-01: 프로젝트별 챗봇
- FR-PBOT-02: 권한 범위 검색
- FR-PBOT-03: 회의 근거와 공식 지식 출처 구분
- FR-PBOT-04: 근거 부재 처리
- NFR-AZ-01: 권한 기반 RAG 제한

### Notes

- Guest는 기본적으로 Project AI context에 접근하지 않는다.

## POST /api/internal/project-ai/chat

Backend가 Space 접근과 meeting ACL 선필터를 끝낸 뒤 호출하는 strict Project AI endpoint다.

### Status

- Backend-to-AI Internal

### Auth and Permissions

- AI 서버는 사용자 인증을 직접 처리하지 않는다.
- Backend가 active SpaceMember를 확인하고 meeting별 read 권한을 적용한다.
- meeting guest는 Project AI 호출 대상이 아니다.

### Data Scope

- Project scope
- `projectKnowledge`: 해당 Space의 `PUBLISHED`, `embeddingStatus=COMPLETED` 공식 지식
- `meetingSummary`: `allowedMeetingIds`에 포함된 회의의 current/confirmed report summary
- 실제 transcript/pgvector 검색은 후속 저장소 연동 범위다.

### Request

```json
{
  "projectId": "space-001",
  "question": "권한 관련 남은 리스크가 뭐야?",
  "allowedMeetingIds": ["meeting-001"],
  "sources": [
    {
      "sourceId": "knowledge-001",
      "type": "projectKnowledge",
      "projectId": "space-001",
      "meetingId": null,
      "title": "권한 설계 메모",
      "text": "Project AI는 접근 가능한 회의만 검색한다."
    },
    {
      "sourceId": "report-001",
      "type": "meetingSummary",
      "projectId": "space-001",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12 회의록",
      "text": "회의 ACL과 Project AI 권한 선필터를 논의했다."
    }
  ]
}
```

### Validation

- `projectId`, `question`: required, blank 금지
- `sources[].sourceId`, `sources[].type`, `sources[].projectId`, `sources[].text`: required
- 모든 source의 `projectId`는 request `projectId`와 같아야 한다.
- source type은 `projectKnowledge`, `meetingSummary`만 허용한다.
- `meetingSummary.meetingId`는 required이며 `allowedMeetingIds`에 포함되어야 한다.
- `projectKnowledge.meetingId`는 null이어야 한다.

### Response

```json
{
  "answer": "남은 리스크는 Project AI 권한 선필터와 실제 pgvector 저장소 연동입니다.",
  "sources": [
    {
      "sourceId": "knowledge-001",
      "type": "projectKnowledge",
      "title": "권한 설계 메모",
      "text": "Project AI는 접근 가능한 회의만 검색한다."
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

### Errors

- `400 INVALID_REQUEST`: schema validation 실패
- `403 AI_CONTEXT_FORBIDDEN`: project 불일치, 허용되지 않은 meeting source, 잘못된 source type
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- AI server observability log only
- Backend persistent `AI_REQUESTED` event는 후속 구현

### Requirement Trace

- FR-PBOT-01: 프로젝트 질의응답
- FR-PBOT-02: 권한 범위 검색
- FR-PBOT-03: 공식 지식/회의 기록 출처 구분
- FR-PBOT-04: 근거 부재 처리
- NFR-AZ-01: 검색 전 권한 선필터
- NFR-AZ-02: 권한 통과 데이터만 AI context에 포함
- NFR-AZ-04: Meeting AI/Project AI scope 분리

### Notes

- 검색 결과가 없으면 LLM을 호출하지 않고 `unsupported: true`, `model: context-only`를 반환한다.

## POST /api/meeting-ai/generate-report

회의 transcript, decision, action item을 기반으로 회의록 후보를 생성한다.

### Status

- Current Prototype
- Backend-to-AI Internal target 후보

### Auth and Permissions

- AI 서버 직접 호출 시 인증은 prototype 범위 밖이다.
- Target에서는 Backend가 meeting 접근 권한과 report 생성 권한을 확인한다.

### Data Scope

- Meeting scope
- 생성 결과는 저장 전 `candidate`다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "title": "Sprint Planning #12",
  "transcript": [],
  "decisions": [],
  "actions": [],
  "format": "markdown"
}
```

### Validation

Current Prototype:

- `meetingId`: required
- `title`: optional. 생략하면 `meetingId`를 제목 fallback으로 사용한다.
- `format`: `markdown`

Target Backend-to-AI:

- `meetingId`, `title`: required
- `format`: `markdown`
- source는 해당 meeting 범위여야 한다.

### Response

```json
{
  "summary": "회의 요약",
  "decisions": [],
  "actionItems": [],
  "markdown": "## 요약\n회의 요약",
  "sources": [
    {
      "sourceId": "segment-001",
      "type": "transcript",
      "title": "Sprint Planning #12",
      "text": "회의 요약 근거"
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: 다른 회의 source 포함
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- Current Prototype: AI server observability log only
- Target: Backend records `AI_REQUESTED`

### Requirement Trace

- FR-RPT-01: AI 회의록 생성
- FR-RPT-02: 회의록 candidate 반환
- NFR-AI-02: 회의 근거 출처 표시

### Notes

- 공식 저장/확정은 `meeting-api.md`의 report endpoint가 담당한다.
- 기존 Frontend/AI prototype 호환용 endpoint다. Target Frontend는 직접 호출하지 않는다.

## POST /api/internal/meeting-ai/generate-report

Backend가 권한 검증과 단일 회의 source 선필터를 완료한 뒤 호출하는 strict 회의록 생성 endpoint다.

### Status

- Target Backend-to-AI Internal

### Auth and Permissions

- 외부 사용자 직접 호출 금지
- Backend는 `OWNER`/`ADMIN` 또는 해당 회의 `HOST`/`EDITOR` 권한을 먼저 확인한다.

### Data Scope

- 단일 Meeting scope
- 허용 source type: `transcript`, `decision`, `actionItem`

### Request

```json
{
  "projectId": "space-001",
  "meetingId": "meeting-001",
  "title": "Sprint Planning #12",
  "format": "markdown",
  "sources": [
    {
      "sourceId": "segment-001",
      "type": "transcript",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12",
      "speaker": "김철수",
      "startMs": 1000,
      "endMs": 5000,
      "text": "권한 필터를 먼저 적용합니다."
    }
  ]
}
```

### Validation

- `projectId`, `meetingId`, `title`: required
- `format`: `markdown` only
- 모든 source의 `meetingId`는 request `meetingId`와 같아야 한다.
- 허용되지 않은 source type이나 다른 meeting source는 `403 AI_CONTEXT_FORBIDDEN`으로 거부한다.

### Response

- 기존 `POST /api/meeting-ai/generate-report`의 `GenerateReportResponse`와 같다.
- source가 없으면 LLM을 호출하지 않고 `unsupported=true`, `model=context-only`를 반환한다.

### Errors

- `400 INVALID_REQUEST`: 필수값 또는 format 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: source scope/type 검증 실패
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- AI server observability log
- Backend가 public 요청에 대해 `AI_REQUESTED` 기록 예정

### Requirement Trace

- FR-RPT-01: 단일 회의 근거 기반 AI 회의록 생성
- FR-RPT-02: candidate 반환
- NFR-AZ-01, NFR-AZ-04: 권한 선필터와 scope 강제

## POST /api/internal/meeting-ai/extract-tasks

Backend가 권한 선필터 후 조립한 단일 회의 source에서 태스크 후보를 추출한다.

### Status

- Target Backend-to-AI Internal

### Auth and Permissions

- 외부 사용자에게 직접 노출하지 않는다.
- Backend가 public route에서 회의 편집 권한을 먼저 검증한다.

### Data Scope

- request `meetingId`와 같은 source만 허용한다.
- 허용 source type은 `transcript`, `report`, `decision`, `actionItem`이다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "title": "Sprint Planning #12",
  "participants": [
    {"name": "김진수", "role": "EDITOR"}
  ],
  "sources": [
    {
      "sourceId": "segment-001",
      "type": "transcript",
      "projectId": "project-001",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12",
      "text": "ERD 수정안 문서화가 필요합니다."
    }
  ]
}
```

### Validation

- `projectId`, `meetingId`, `title`: required
- 모든 source의 `projectId`, `meetingId`는 request scope와 같아야 한다.
- source type allowlist 위반 또는 다른 meeting/project source는 `403 AI_CONTEXT_FORBIDDEN`이다.
- 응답 task의 `sourceIds`는 request source ID allowlist로 다시 필터링한다.

### Response

- 기존 `POST /api/meeting-ai/extract-tasks`의 `ExtractTasksResponse`와 같다.
- source가 없으면 LLM을 호출하지 않고 `unsupported=true`, `model=context-only`를 반환한다.

### Errors

- `400 INVALID_REQUEST`: 필수값 또는 format 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: source scope/type 검증 실패
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- AI server observability log
- Backend가 public 요청에 대해 `AI_REQUESTED` 기록 예정

### Requirement Trace

- FR-TASK-01: 단일 회의 근거 기반 태스크 후보 추출
- NFR-AZ-01, NFR-AZ-04: 권한 선필터와 scope 강제

## POST /api/meeting-ai/extract-tasks

회의 transcript와 summary에서 태스크 후보를 추출한다.

### Status

- Current Prototype
- prototype 호환 경로. Target 내부 호출은 `/api/internal/meeting-ai/extract-tasks`를 사용한다.

### Auth and Permissions

- AI 서버 직접 호출 시 인증은 prototype 범위 밖이다.
- Target에서는 Backend가 meeting 접근 권한과 task 생성 가능 여부를 확인한다.

### Data Scope

- Meeting scope
- 결과는 `TaskCandidate` 후보이며 칸반 카드가 아니다.

### Request

```json
{
  "projectId": "project-001",
  "meetingId": "meeting-001",
  "title": "Sprint Planning #12",
  "transcript": [],
  "summary": "ERD 수정안 문서화가 필요합니다.",
  "participants": [
    {
      "name": "김진수",
      "role": "VIEWER"
    }
  ]
}
```

### Validation

Current Prototype:

- `meetingId`: required
- `title`: optional. 생략하면 `meetingId`를 제목 fallback으로 사용한다.
- `participants`는 요청에 포함된 already-filtered prototype context로 간주한다.

Target Backend-to-AI:

- `meetingId`, `title`: required
- participants는 해당 meeting context에서 파생된 값이어야 한다.

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
      "title": "Sprint Planning #12",
      "text": "ERD 수정안 문서화가 필요합니다."
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 AI_CONTEXT_FORBIDDEN`: 다른 회의 source 포함
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- Current Prototype: AI server observability log only
- Target: Backend records `AI_REQUESTED`

### Requirement Trace

- FR-TASK-01: AI 회의록 기반 태스크 후보 생성
- FR-TASK-02: candidate 검토 전 상태
- NFR-AI-02: source 기반 후보 추적

### Notes

- 확정과 저장은 `kanban-api.md`의 task candidate confirm endpoint가 담당한다.

## Legacy POST /api/meeting-ai/ask

기존 frontend prototype이 호출하는 단순 Meeting AI endpoint다. 신규 구현은 `POST /api/meeting-ai/chat`을 우선 사용한다.

### Status

- Current Prototype
- Legacy compatibility

### Auth and Permissions

- Prototype 직접 호출
- Target에서는 사용하지 않는다.

### Data Scope

- Meeting scope로 간주하지만 source metadata가 부족하다.

### Request

Legacy prototype shape.

### Validation

- 기존 frontend 호환만 유지한다.

### Response

Legacy prototype shape.

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `503 AI_PROVIDER_UNAVAILABLE`: provider 설정 누락, timeout, HTTP/connection 오류, 응답 형식 오류

### Audit

- No audit event for legacy endpoint. Observability log only.

### Requirement Trace

- NFR-MNT-03: prototype/mock 호환성

### Notes

- 신규 구현에서는 사용하지 않는다.
