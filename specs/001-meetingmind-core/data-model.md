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
