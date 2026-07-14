이 문서는 MeetingMind Core Prototype의 데이터 모델과 권한 규칙을 정리하기 위한 Markdown 문서이다.

# Data Model: MeetingMind Core Prototype

관계 도식은 `erd.md`를 우선 확인한다. 이 문서는 엔티티별 필드, RAG 논리 구조, 권한/보존 규칙을 설명한다.

## Entity Overview

### User

- `id`
- `email`
- `displayName`
- `pictureUrl`
- `status`: active, disabled
- `createdAt`
- `lastLoginAt`

Backend가 발급하는 MeetingMind access token의 subject는 `User.id`다. 사용자는 여러 인증 방식을 가질 수 있으므로 Google OAuth와 자체 계정 정보는 `AuthIdentity`로 분리한다.

### AuthIdentity

- `id`
- `userId`
- `provider`: google, local
- `providerUserId`: Google `sub` 또는 local email
- `passwordHash`: local provider에서만 사용, Google provider는 null
- `createdAt`
- `lastUsedAt`

### AuthSession

- `id`
- `userId`
- `refreshTokenHash`
- `issuedAt`
- `expiresAt`
- `revokedAt`
- `userAgent`

Frontend는 access token과 refresh token 원문을 `sessionStorage`에 저장한다. Backend는 refresh token 원문을 저장하지 않고 hash와 revoke 상태만 저장한다.

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
- `role`: OWNER, ADMIN, MEMBER
- `joinedAt`

### Meeting

- `id`
- `spaceId`
- `title`
- `scheduledAt`
- `startedAt`
- `endedAt`
- `status`: SCHEDULED, IN_PROGRESS, ENDED, CANCELED
- `failureReason`
- `retentionPolicy`

### MeetingParticipant

- `id`
- `meetingId`
- `userId`
- `role`: HOST, EDITOR, VIEWER
- `participantType`: member, guest
- `accessStatus`: ACTIVE, REVOKED

회의 게스트는 SpaceMember가 아닐 수 있지만 특정 회의의 `MeetingParticipant`로 등록된다. 회의 게스트는 지정된 회의 밖의 STT, 보고서, Meeting AI, 회의 파일, Project Knowledge, Project AI에 기본 접근할 수 없다.

### MeetingSpeaker

- `id`
- `meetingId`
- `label`: 자동 발화자 구분 식별명
- `displayName`: 사용자가 지정한 실제 이름, 미지정 시 null
- `createdAt`

### MeetingTranscript

- `meetingId`: PK이자 Meeting FK, 회의당 하나의 논리 전사
- `status`: PENDING, PROCESSING, COMPLETED, FAILED
- `provider`: STT provider 식별자, 작업 시작 전에는 null 가능
- `language`: BCP 47 language tag 후보, 미확정이면 null
- `startedAt`
- `completedAt`
- `failureReason`: FAILED일 때만 사용
- `retentionUntil`: 7일/30일 보존 만료 시각, PERMANENT면 null
- `legalHold`: 보존 만료 자동 삭제 예외 여부
- `purgedAt`: 원문 segment 삭제 완료 시각
- `createdAt`
- `updatedAt`

`MeetingTranscript`는 전사 작업과 보존 상태를 관리한다. 음성 원본 경로나 blob은 이 엔티티에 장기 저장하지 않는다.

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
- `status`: CANDIDATE, DRAFT, CONFIRMED
- `title`
- `summary`
- `markdown`
- `decisions`
- `actionItems`
- `sourceIds`: 보고서 요약과 전체 candidate가 참조한 source ID
- `createdBy`: candidate 생성 요청 사용자
- `version`
- `isCurrent`: 회의당 현재 공식 CONFIRMED report는 하나만 true
- `createdAt`
- `confirmedAt`: `CONFIRMED` 전환 시각, 확정 전에는 null

### TaskCandidate

- `id`
- `meetingId`
- `title`
- `assigneeName`: AI가 제안한 담당자 표시 이름, 없으면 null
- `suggestedAssigneeId`: 표시 이름이 active participant이자 active SpaceMember와 정확히 일치할 때의 사용자 id
- `dueDate`: AI가 제안한 마감일, 없으면 null
- `status`: CANDIDATE, CONFIRMED, DISMISSED
- `sourceIds`: Backend가 AI에 전달한 canonical source 중 후보 근거 ID
- `createdBy`: 후보 생성 요청 사용자
- `createdAt`
- `confirmedAt`: TaskCard 생성과 함께 CONFIRMED로 전환된 시각, 확정 전에는 null

### TaskCard

- `id`
- `spaceId`
- `meetingId`: 회의 후보에서 생성되지 않은 일반 카드면 null 가능
- `sourceCandidateId`: AI 후보에서 생성되지 않은 일반 카드면 null, 값이 있으면 unique
- `title`
- `description`
- `status`: TODO, IN_PROGRESS, DONE
- `assigneeId`: active SpaceMember 사용자 id, 미지정 시 null
- `dueDate`
- `createdAt`
- `updatedAt`

### ProjectKnowledge

- `id`
- `spaceId`
- `type`: report, decision, manual, external
- `title`
- `content`
- `sourceMeetingId`: 회의 기반 지식이면 원본 회의 id, 수동/외부 지식이면 null 가능
- `approvedBy`: 공식 지식 등록/승인 사용자
- `status`: PUBLISHED, ARCHIVED
- `embeddingStatus`: PENDING, PROCESSING, COMPLETED, FAILED
- `embeddingJobId`: 비동기 embedding 재생성 작업 id
- `createdAt`
- `updatedAt`
- `deletedAt`: soft delete 또는 archive 추적 후보

### DomainTerm

- `id`
- `spaceId`
- `term`
- `definition`
- `status`: ACTIVE, ARCHIVED
- `createdAt`
- `updatedAt`
- `archivedAt`

### EmbeddingChunk

- `id`
- `spaceId`
- `meetingId`
- `sourceType`
- `sourceId`
- `content`
- `embeddingJobId`
- `generation`
- `isActive`
- `replacedAt`
- `embedding`
- `createdAt`

### EmbeddingJob

- `id`
- `spaceId`
- `projectKnowledgeId`: 지식 재색인 작업이면 required
- `meetingId`: 회의 전사/보고서 재색인 작업이면 required
- `status`: PENDING, PROCESSING, COMPLETED, FAILED
- `model`: 실제 embedding model 식별자
- `dimension`: 생성 vector 차원
- `generation`: 동일 source의 교체 세대
- `attemptCount`
- `failureCode`: provider 원문 대신 내부 정규화 코드
- `createdAt`
- `startedAt`
- `completedAt`

### Data Constraints

- `User.email`은 unique다.
- `AuthIdentity(provider, providerUserId)`는 unique다.
- `SpaceMember(spaceId, userId)`는 active member 기준 unique다.
- Space당 active `OWNER`는 정확히 1명이어야 한다.
- `MeetingParticipant(meetingId, userId)`는 active participant 기준 unique다.
- `MeetingSpeaker(meetingId, label)`은 unique다.
- `MeetingTranscript.meetingId`는 PK/FK이며 회의당 최대 하나다.
- `MeetingTranscript.status=COMPLETED`이면 `completedAt`이 required이고, `FAILED`이면 `failureReason`이 required다.
- `MeetingTranscript.retentionUntil`은 `Meeting.retentionPolicy=PERMANENT`이면 null이고 기간 보존이면 설정한다.
- `TranscriptSegment(meetingId, sequence)`은 unique다.
- `MeetingReport(meetingId, version)`은 unique다.
- `MeetingReport(meetingId)` 기준 `status=CONFIRMED and isCurrent=true`는 최대 1개다.
- `MeetingReport.status=CANDIDATE`는 임시 저장되지만 기본 공식 회의록 조회와 Project AI source에서 제외한다.
- AI가 근거 부족으로 `unsupported=true`를 반환한 결과는 `MeetingReport`로 저장하지 않는다.
- `CANDIDATE` 또는 `DRAFT`만 `CONFIRMED`로 전환할 수 있고, 중복 확정은 거부한다.
- 같은 meeting에 더 높은 version이 존재하면 오래된 candidate 확정을 거부한다.
- 새 report를 확정할 때 기존 `CONFIRMED and isCurrent=true` report를 `isCurrent=false`로 전환하고 새 report만 `isCurrent=true`로 둔다.
- `TaskCard.sourceCandidateId`는 nullable이지만 값이 있으면 unique다.
- `TaskCandidate.status`는 `CANDIDATE`, `CONFIRMED`, `DISMISSED` 중 하나다.
- `TaskCandidate`는 AI가 반환한 source ID를 Backend canonical source allowlist로 필터링해 저장한다.
- `TaskCandidate.CANDIDATE`만 TaskCard로 확정할 수 있고 확정과 카드 생성은 하나의 domain transition으로 처리한다.
- `TaskCandidate.suggestedAssigneeId`와 `TaskCard.assigneeId`는 active SpaceMember만 허용한다.
- TaskCandidate 생성/조회 응답의 담당자 선택지는 해당 Space의 active SpaceMember에서 파생하며 별도 entity로 저장하지 않는다.
- TaskCandidate 만료 검증은 `Q-009` 정책 결정 후 추가한다.
- `DomainTerm(spaceId, term)`은 active term 기준 unique다.
- `EmbeddingChunk(spaceId, scope, sourceType, sourceId)`는 RAG 권한 필터와 재색인을 위해 index를 둔다.
- `EmbeddingJob`은 `projectKnowledgeId`와 `meetingId` 중 최소 하나를 참조해야 한다.
- 동일 source의 active `EmbeddingChunk`는 최신 완료 generation만 사용한다. 새 generation 완료 전에는 기존 active chunk를 유지한다.
- embedding model과 vector 차원/index 방식은 `Q-010` 결정 후 고정한다.

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
- `embeddingJobId`, `generation`, `isActive`: 비동기 재색인 세대와 현재 검색 대상 여부
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
- 실제 pgvector 검색은 `isActive=true`이고 완료된 `EmbeddingJob`에 속한 chunk만 대상으로 한다.

## Permission Rules

- Space 접근은 `SpaceMember`로 판단한다.
- 회의 접근은 `MeetingParticipant`로 판단한다.
- SpaceRole은 `OWNER`, `ADMIN`, `MEMBER`를 기본값으로 한다.
- MeetingRole은 `HOST`, `EDITOR`, `VIEWER`를 기본값으로 한다.
- Meeting AI는 `meetingId` 하나에 속한 데이터만 사용한다.
- Project AI는 `ProjectKnowledge`와 사용자가 접근 가능한 `meetingId` 목록의 chunk만 사용한다. 회의 게스트는 Project AI를 기본 사용할 수 없다.
- Project Knowledge는 SpaceMember가 조회하고 오너/관리자가 수정한다. 회의 게스트는 기본 접근할 수 없다.
- Project Knowledge 수정 시 embedding은 비동기 재생성한다. 기존 chunk는 유지하고 새 embedding chunk가 `COMPLETED`가 되면 교체한다.
- 발화자 이름 수정은 회의 `HOST` 또는 `EDITOR` 권한이 있는 사용자만 수행한다.
- transcript, report, summary 조회는 `MeetingParticipant` 권한 확인 후 허용한다.
- AI 서버로 전달되는 transcript segment는 Backend 권한 필터 이후에 구성한다.
- `MeetingParticipant.accessStatus=ACTIVE`만 회의 접근 권한으로 인정한다. `REVOKED`는 조회, 수정, LiveKit token, Meeting AI, Project AI meeting context 접근을 모두 차단한다.
- SpaceMember 제거 시 같은 Space에 속한 `participantType=member` MeetingParticipant는 `REVOKED`로 전환한다. `participantType=guest`는 SpaceMember가 아니므로 이 정책으로 회수하지 않는다.
- HOST의 회의방 일시 퇴장은 `MeetingParticipant` 권한을 바꾸지 않는다. 마지막 active HOST의 role 강등, `REVOKED` 전환, participant 제거는 거부한다.

## API Representation Rules

- transcript segment 위치는 API에서 `startMs`, `endMs` 밀리초로 표현한다.
- 날짜시간은 ISO-8601로 표현한다.
- 배열 응답은 값이 없으면 `null` 대신 `[]`를 반환한다.

## Retention

- 음성 원본: 기본 장기 보관 없음
- STT 원문: 회의별 `retentionPolicy`에 따른 삭제 대상. DB 값은 `DAYS_7`, `DAYS_30`, `PERMANENT`이며 기본값은 `DAYS_30`이다. `legalHold=true`이면 자동 삭제를 보류한다.
- 보고서/공식 지식: Space 정책에 따른 보존
