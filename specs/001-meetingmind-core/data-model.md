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
- `status`
- `retentionPolicy`

### MeetingParticipant

- `id`
- `meetingId`
- `userId`
- `role`: host, editor, participant, viewer
- `accessStatus`

### TranscriptSegment

- `id`
- `meetingId`
- `speakerUserId`
- `startTime`
- `endTime`
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

## Retention

- 음성 원본: 기본 장기 보관 없음
- STT 원문: 회의별 `retentionPolicy`에 따른 삭제 대상
- 보고서/공식 지식: Space 정책에 따른 보존
