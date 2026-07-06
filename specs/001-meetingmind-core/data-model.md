이 문서는 MeetingMind Core Prototype의 데이터 모델과 권한 규칙을 정리하기 위한 Markdown 문서이다.

# Data Model: MeetingMind Core Prototype

## Entity Overview

### User

- `id`
- `email`
- `displayName`
- `provider`
- `createdAt`

### Space

- `id`
- `name`
- `description`
- `createdBy`
- `createdAt`

### SpaceMember

- `id`
- `spaceId`
- `userId`
- `role`: owner, admin, member
- `joinedAt`

### Meeting

- `id`
- `spaceId`
- `title`
- `scheduledAt`
- `startedAt`
- `endedAt`
- `status`: CREATED, SCHEDULED, LIVE, PROCESSING, COMPLETED, FAILED
- `failureReason`
- `retentionPolicy`

### MeetingParticipant

- `id`
- `meetingId`
- `userId`
- `role`: host, editor, participant, viewer
- `accessStatus`

### MeetingSpeaker

- `id`
- `meetingId`
- `label`: 자동 발화자 구분 식별명
- `displayName`: 사용자가 지정한 실제 이름, 미지정 시 null
- `createdAt`

### TranscriptSegment

- `id`
- `meetingId`
- `speakerId`
- `startMs`
- `endMs`
- `text`
- `source`

### MeetingReport

- `id`
- `meetingId`
- `title`
- `summary`
- `decisions`
- `actionItems`
- `version`
- `createdAt`

### ProjectKnowledge

- `id`
- `spaceId`
- `type`: overview, tech_stack, decision, schedule, role, document
- `title`
- `content`
- `sourceMeetingId`
- `approvedBy`
- `updatedAt`

### EmbeddingChunk

- `id`
- `spaceId`
- `meetingId`
- `sourceType`
- `sourceId`
- `content`
- `embedding`
- `createdAt`

## RAG Chunk Shape

실제 STT/DB/pgvector가 구현되기 전 AI prototype은 아래 논리 구조를 기준으로 mock transcript를 chunk로 변환한다. `TranscriptSegment`는 원본 저장 단위이고, `EmbeddingChunk`는 검색/임베딩 단위다.

### TranscriptSegment Source

STT 기반 회의 다이얼로그 원천 데이터는 발화자와 발화 내용 중심으로 쌓인다.

- `id`: segment id
- `meetingId`
- `speakerId`
- `speakerLabel`: 자동 발화자 label, 예: `화자 1`
- `speakerName`: 사용자 지정 이름, 미지정 시 null
- `startMs`
- `endMs`
- `text`
- `sequence`

### EmbeddingChunk Logical Fields

- `id`: `meeting-001:transcript:0001` 같은 안정적인 chunk id
- `spaceId`
- `projectId`: prototype에서는 `spaceId`와 같은 프로젝트 식별자로 취급 가능
- `meetingId`: ProjectKnowledge-only chunk면 null 가능
- `scope`: `meeting` 또는 `project`
- `sourceType`: `transcript`, `meetingSummary`, `decision`, `actionItem`, `report`, `projectKnowledge`, `glossary`
- `sourceId`: 원본 segment/report/knowledge id
- `sourceSegmentIds`: transcript window chunk가 포함한 segment id 목록
- `title`: 회의명 또는 문서명
- `speakerNames`: transcript chunk에 포함된 발화자 목록
- `startMs`, `endMs`: transcript chunk 시간 범위
- `content`: 사용자에게 출처로 보여줄 원문 또는 요약
- `embeddingText`: embedding provider에 전달할 정규화된 텍스트
- `metadata`: language, visibility, tags, createdFrom, approvedState 등 검색 필터용 값
- `embedding`: pgvector 저장 시 vector 값
- `createdAt`

### Embedding Text Rule

`embeddingText`는 원문만 넣지 않고 검색 품질을 위해 최소 컨텍스트를 포함한다.

```text
회의: 3회차 API 설계 회의
범위: meeting
출처: transcript
시간: 06:10:03-06:14:08
발화자: 김진수, 이미주
내용:
김진수: ERD 구조를 수정해야 합니다.
이미주: 권한 관리는 회의 단위로 분리하는 것이 좋겠습니다.
```

짧은 STT 발화는 한 줄씩 임베딩하지 않고 3-8개 발화 또는 약 300-800 tokens 단위로 묶는다. 단, 출처 표시를 위해 원본 `sourceSegmentIds`, 시간 범위, 발화자 목록은 유지한다.

### RAG Scope Rules

- 회의 중 용어 설명은 `glossary`, 현재 회의 transcript window, 현재 회의 decision/action/report chunk만 검색한다.
- 회의별 챗봇은 단일 `meetingId`에 속한 chunk만 검색한다.
- 프로젝트별 챗봇은 `ProjectKnowledge`와 권한 필터를 통과한 meeting chunk만 검색한다.
- prototype 단계에서는 Backend 권한 필터가 없으므로 프론트/AI 서버가 받는 context를 already-filtered mock context로 간주한다.

## Permission Rules

- Space 접근은 `SpaceMember`로 판단한다.
- 회의 접근은 `MeetingParticipant`로 판단한다.
- Meeting AI는 `meetingId` 하나에 속한 데이터만 사용한다.
- Project AI는 `ProjectKnowledge`와 사용자가 접근 가능한 `meetingId` 목록의 chunk만 사용한다.
- 발화자 이름 수정은 회의 `host` 또는 `editor` 권한이 있는 사용자만 수행한다.
- transcript, report, summary 조회는 `MeetingParticipant` 권한 확인 후 허용한다.
- AI 서버로 전달되는 transcript segment는 Backend 권한 필터 이후에 구성한다.

## API Representation Rules

- transcript segment 위치는 API에서 `startMs`, `endMs` 밀리초로 표현한다.
- 날짜시간은 ISO-8601로 표현한다.
- 배열 응답은 값이 없으면 `null` 대신 `[]`를 반환한다.

## Retention

- 음성 원본: 기본 장기 보관 없음
- STT 원문: 회의별 `retentionPolicy`에 따른 삭제 대상
- 보고서/공식 지식: Space 정책에 따른 보존
