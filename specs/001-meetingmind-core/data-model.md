이 문서는 MeetingMind Core Prototype의 데이터 모델과 권한 규칙을 정리하기 위한 Markdown 문서이다.

# Data Model: MeetingMind Core Prototype

관계 도식은 `erd.md`를 우선 확인한다. 이 문서는 엔티티별 필드, RAG 논리 구조, 권한/보존 규칙을 설명한다.

> 인증 모델 안내: 아래 `AuthSession.refreshTokenHash`와 Frontend token 저장은 현재 Core 호환 구현이다. 목표 BFF/Auth 모델과 저장소 소유권은 `../002-bff-auth-msa/data-model.md`와 `../002-bff-auth-msa/erd.md`를 따른다.

## Entity Overview

### User

- `id`
- `authUserId`: nullable UUID unique projection. canonical `user-{UUID}`만 V13에서 backfill하며 Auth DB와 물리 FK를 만들지 않는다.
- `email`
- `displayName`
- `pictureUrl`
- `status`: active, disabled
- `createdAt`
- `lastLoginAt`

legacy Backend access token의 subject는 `User.id`다. T034 이후 목표 Auth access token의 UUID `sub`는 `authUserId`로 Core User를 찾고, Space/Meeting 등 기존 업무 FK는 문자열 `User.id`를 계속 사용한다. 신규 Auth User는 BFF가 target Core access와 workload identity로 internal projection을 동기 멱등 생성하며 `id = "user-" + authUserId` ownership 충돌은 거부한다. 사용자는 여러 인증 방식을 가질 수 있으므로 Google OAuth와 자체 계정 정보는 `AuthIdentity`로 분리한다.

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

현재 Frontend는 access token과 refresh token 원문을 `sessionStorage`에 저장하고 Backend는 refresh hash/revoke를 저장한다. 이 흐름은 BFF 전환 중 legacy compatibility로만 유지하며 목표 브라우저는 token을 저장하지 않는다.

### Space

- `id`
- `name`
- `description`
- `imageUrl`: nullable 대표 이미지 공개 URL. 객체 파일은 S3 또는 CDN에 저장하며 DB에는 URL만 저장한다.
- `createdBy`
- `createdAt`
- `updatedAt`: Space 이름 또는 설명 수정 시각
- `deletedAt`: soft delete 시각. 일반 목록과 접근 검증에서 제외한다.

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
- `description`: 일정 설명, null 가능
- `scheduledAt`
- `scheduledEndAt`: 캘린더용 예정 종료 시각. `scheduledAt`보다 이후여야 하며 실제 종료 시각 `endedAt`과 구분한다.
- `startedAt`
- `endedAt`
- `status`: SCHEDULED, IN_PROGRESS, ENDED, CANCELED
- `joinCode`: in-memory prototype의 추측하기 어려운 원문 코드. 영속화 시에는 원문 대신 hash 저장을 우선한다.
- `failureReason`
- `retentionPolicy`
- `deletedAt`: soft delete 시각. active Meeting은 null이다.
- `deletedBy`: soft delete를 실행한 User ID. `deletedAt`과 함께 null 또는 함께 값이 있어야 한다.

회의 삭제는 관계 산출물을 즉시 물리 삭제하지 않는다. `deletedAt`이 있는 Meeting은 일반 목록/상세/캘린더와 Meeting/Project AI context에서 제외한다. `SCHEDULED` 삭제는 `CANCELED`로 전환하고, `IN_PROGRESS` 삭제는 거부하며, `ENDED` 삭제는 상태를 유지한다.

### MeetingParticipant

- `id`
- `meetingId`
- `userId`
- `role`: HOST, EDITOR, VIEWER
- `participantType`: member, guest
- `accessStatus`: ACTIVE, REVOKED

회의 게스트는 SpaceMember가 아닐 수 있지만 특정 회의의 `MeetingParticipant`로 등록된다. 회의 게스트는 지정된 회의 밖의 STT, 보고서, Meeting AI, 회의 파일, Project Knowledge, Project AI에 기본 접근할 수 없다.

### MeetingJoinRequest

- `id`
- `meetingId`
- `userId`
- `status`: PENDING, APPROVED, REJECTED
- `requestedAt`
- `reviewedAt`
- `reviewedBy`

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

### MeetingMessage

- `id`
- `meetingId`
- `authorUserId`: 작성자 User scalar ID
- `text`: null 가능. attachment-only message는 null이고, text와 attachment가 모두 비어 있으면 안 된다.
- `createdAt`
- `deletedAt`: soft delete 시각. 기본 조회와 AI context에서는 제외한다.

### Deferred: MeetingAttachment

- `id`
- `meetingId`
- `messageId`: upload session·업로드 검증·추출 중에는 null일 수 있고, 게시 성공 뒤 같은 Meeting의 `MeetingMessage` FK로 설정한다.
- `uploadedBy`: 업로드 session 생성자 User scalar ID
- `originalFilename`: 제어문자를 제거한 표시 이름. storage key나 filesystem path로 사용하지 않는다.
- `contentType`, `sizeBytes`, `sha256`
- `objectKey`: private object storage의 opaque key. API response·audit·AI context에 노출하지 않는다.
- `status`: `PENDING_UPLOAD`, `PROCESSING`, `READY`, `UNSUPPORTED`, `FAILED`, `DELETED`, `EXPIRED`
- `extractionFailureCode`: provider raw error가 아닌 정규화된 코드. `UNSUPPORTED`/`FAILED`일 때만 사용한다.
- `extractedTextObjectKey`: 추출 성공한 정규화 text의 private derived object key; DB에는 원문 추출 text를 중복 저장하지 않는다.
- `extractedChars`, `retentionUntil`, `createdAt`, `uploadExpiresAt`, `completedAt`, `deletedAt`, `expiredAt`

이 모델은 M035 재개 시 사용할 future draft다. 현재 Flyway schema, API, RAG source에는 포함하지 않는다. 원본 object는 private S3-compatible storage에만 두며, 완료 검증 전 attachment는 message에 연결할 수 없고, `READY` attachment만 RAG source가 된다. PNG/JPEG와 image-only PDF는 `UNSUPPORTED`로 공유/다운로드만 가능하다.

### Deferred: AttachmentChunkAnchor

- `id`
- `chunkId`
- `attachmentId`
- `pageNumber`: TXT/Markdown이면 null, PDF text chunk면 1-based page
- `charStart`, `charEnd`: extracted text 내 citation 범위

이 테이블은 `EmbeddingChunk`와 attachment extract의 직접 관계다. `SourceReference`를 위한 범용 다형 FK를 만들지 않고, transcript의 `ChunkSourceSegment`와 별도로 위치 정보를 보존한다.

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
- 수동 수정은 기존 행을 바꾸지 않고 `DRAFT` 새 version을 생성해 이력을 보존한다.
- `CANDIDATE`는 생성 후 7일 동안만 확정하거나 후보 기반 편집으로 새 `DRAFT`를 만들 수 있다. 만료 여부는 `createdAt`과 정책값으로 계산한다.

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
- `status`: TODO, IN_PROGRESS, IN_REVIEW, DONE
- `priority`: LOW, MEDIUM, HIGH. 일반 카드와 AI 후보 확정 카드의 기본값은 MEDIUM
- `labels`: 순서가 유지되는 사용자 지정 문자열 목록. 카드당 최대 10개, 각 trim 후 1~40자이며 대소문자 무시 중복은 허용하지 않음
- `assigneeId`: active SpaceMember 사용자 id, 미지정 시 null
- `dueDate`
- `createdAt`
- `updatedAt`
- `deletedAt`: 일반 카드 삭제 시 soft delete 시각. 기본 조회에서는 제외한다.

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

### ProjectAiMessage

- `id`
- `spaceId`
- `userId`: 대화를 생성하고 조회할 인증 사용자
- `role`: USER, ASSISTANT
- `content`
- `createdAt`

Project AI 이력은 `(spaceId, userId)`로 격리한다. 목록은 최신 50개를 시간순으로 반환하고, 다음 AI 요청에는 최근 10개만 비신뢰 대화 문맥으로 전달한다. 이력은 RAG source나 citation이 아니므로 현재 요청의 권한 scope와 source allowlist를 대체할 수 없다.

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
- `scope`: source 소유 범위. meeting 산출물은 `meeting`, ProjectKnowledge는 `project`
- `sourceType`
- `sourceId`
- `content`
- `embeddingJobId`
- `generation`
- `isActive`
- `replacedAt`
- `embedding`: target은 `vector(1536)`
- `createdAt`

### EmbeddingJob

- `id`
- `spaceId`
- `projectKnowledgeId`: 지식 재색인 작업이면 required
- `meetingId`: 회의 전사/보고서 재색인 작업이면 required
- `attachmentId`: MeetingAttachment 색인 작업이면 required
- `status`: PENDING, PROCESSING, COMPLETED, FAILED
- `model`: 실제 embedding model 식별자
- `dimension`: 생성 vector 차원
- `generation`: 동일 source의 교체 세대
- `attemptCount`
- `triggerReason`: KNOWLEDGE_CHANGED, TRANSCRIPT_COMPLETED, SPEAKER_UPDATED, REPORT_CONFIRMED, ATTACHMENT_READY, FULL_REINDEX
- `contentHash`: 같은 source 내용의 중복 작업 회피와 stale generation 확인용 hash
- `nextAttemptAt`: retry 가능한 다음 시각
- `leaseExpiresAt`: worker 장애 시 작업을 다시 선점하기 위한 lease 만료 시각
- `failureCode`: provider 원문 대신 내부 정규화 코드
- `createdAt`
- `startedAt`
- `completedAt`

### KnowledgeGraph (Read Model)

- 영속 entity를 추가하지 않는다. Space의 active `EmbeddingChunk`를 요청 시 source 단위 centroid로 집계하는 API projection이다.
- `KnowledgeCluster`: `id`, `label`, `sourceCount`, `nodes[]`
- `KnowledgeGraphNode`: `id`, `sourceType`, `title`, `sourceMeetingId`, `embeddingStatus`
- `KnowledgeGraphEdge`: `from`, `to`, `similarity`
- Core permission prefilter 이후 AI가 전달받은 `spaceId`, `allowedMeetingIds`를 SQL scope에 강제한다. 원본 chunk content/vector와 권한 없는 meeting source는 응답에 포함하지 않는다.

### Data Constraints

- `User.email`은 unique다.
- `AuthIdentity(provider, providerUserId)`는 unique다.
- `SpaceMember(spaceId, userId)`는 active member 기준 unique다.
- Space당 active `OWNER`는 정확히 1명이어야 한다.
- `MeetingParticipant(meetingId, userId)`는 active participant 기준 unique다.
- `MeetingJoinRequest(meetingId, userId, status)`는 `PENDING` 기준 unique다.
- `Meeting.joinCode`는 unique이고 회의 ID에서 결정적으로 만들지 않는다. DB 전환 시 lookup용 `joinCodeHash` 저장을 사용한다.
- `MeetingSpeaker(meetingId, label)`은 unique다.
- `MeetingTranscript.meetingId`는 PK/FK이며 회의당 최대 하나다.
- `MeetingTranscript.status=COMPLETED`이면 `completedAt`이 required이고, `FAILED`이면 `failureReason`이 required다.
- `MeetingTranscript.retentionUntil`은 `Meeting.retentionPolicy=PERMANENT`이면 null이고 기간 보존이면 설정한다.
- `MeetingMessage`는 `text is not null` 또는 연결된 active `MeetingAttachment`가 하나 이상이어야 한다.
- `MeetingAttachment.meetingId`는 `MeetingMessage.meetingId`와 같아야 한다. `messageId`가 null인 attachment는 upload session·업로드 검증·추출 중이거나 게시되지 않은 완료 attachment일 수 있으며, 기본 message 목록에는 포함하지 않는다.
- `MeetingAttachment.sha256`은 64-character lowercase hex이고 `(meetingId, sha256)`는 active attachment 기준 unique로 두어 같은 byte의 재업로드를 막는다.
- `MeetingAttachment.status=READY`이면 `extractedTextObjectKey`와 `completedAt`이 required다. `UNSUPPORTED`/`FAILED`이면 `extractionFailureCode`가 required다. `DELETED`/`EXPIRED`는 download와 retrieval 대상이 아니다.
- `MeetingAttachment.retentionUntil`은 Meeting의 retention policy에서 계산하며, `PERMANENT`이면 null이다.
- `AttachmentChunkAnchor(chunkId, attachmentId, pageNumber, charStart, charEnd)`는 unique다.
- `TranscriptSegment(meetingId, sequence)`은 unique다.
- `MeetingReport(meetingId, version)`은 unique다.
- `MeetingReport(meetingId)` 기준 `status=CONFIRMED and isCurrent=true`는 최대 1개다.
- `MeetingReport.status=CANDIDATE`는 임시 저장되지만 기본 공식 회의록 조회와 Project AI source에서 제외한다.
- AI가 근거 부족으로 `unsupported=true`를 반환한 결과는 `MeetingReport`로 저장하지 않는다.
- `CANDIDATE` 또는 `DRAFT`만 `CONFIRMED`로 전환할 수 있고, 중복 확정은 거부한다.
- MeetingReport `CANDIDATE`는 생성 후 7일이 지나면 확정 또는 후보 기반 draft 생성을 거부한다. 기존 이력과 근거 source는 보존한다.
- 같은 meeting에 더 높은 version이 존재하면 오래된 candidate 확정을 거부한다.
- 새 report를 확정할 때 기존 `CONFIRMED and isCurrent=true` report를 `isCurrent=false`로 전환하고 새 report만 `isCurrent=true`로 둔다.
- `TaskCard.sourceCandidateId`는 nullable이지만 값이 있으면 unique다.
- `TaskCard.priority`는 `LOW`, `MEDIUM`, `HIGH` 중 하나이며 기본값은 `MEDIUM`이다.
- `TaskCard.status`는 `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `DONE` 중 하나다.
- `TaskCard.labels`는 PostgreSQL `text[]`로 저장하고 application layer에서 최대 개수·문자 수·대소문자 무시 중복을 검증한다.
- `TaskCard.deletedAt`이 null인 행만 일반 칸반 목록에 노출한다. AI candidate 중복 확정 검증은 삭제된 카드도 포함해 source uniqueness를 보존한다.
- `TaskCandidate.status`는 `CANDIDATE`, `CONFIRMED`, `DISMISSED` 중 하나다.
- `TaskCandidate`는 AI가 반환한 source ID를 Backend canonical source allowlist로 필터링해 저장한다.
- `TaskCandidate.CANDIDATE`만 TaskCard로 확정할 수 있고 확정과 카드 생성은 하나의 domain transition으로 처리한다.
- `TaskCandidate.suggestedAssigneeId`와 `TaskCard.assigneeId`는 active SpaceMember만 허용한다.
- TaskCandidate 생성/조회 응답의 담당자 선택지는 해당 Space의 active SpaceMember에서 파생하며 별도 entity로 저장하지 않는다.
- TaskCandidate는 생성 후 7일 동안만 TaskCard로 확정할 수 있다. 만료 여부는 `createdAt`과 정책값으로 계산하며 별도 status를 추가하지 않는다.
- `DomainTerm(spaceId, term)`은 active term 기준 unique다.
- `ProjectAiMessage(spaceId, userId, createdAt)` index는 사용자별 Space 대화 조회에 사용한다.
- `EmbeddingChunk(spaceId, scope, sourceType, sourceId)`는 RAG 권한 필터와 재색인을 위해 index를 둔다.
- `EmbeddingJob`은 `projectKnowledgeId`, `meetingId`, `attachmentId` 중 정확히 하나만 참조해야 한다.
- `EmbeddingJob(projectKnowledgeId, generation)`, `EmbeddingJob(meetingId, generation)`, `EmbeddingJob(attachmentId, generation)`은 source별 unique다.
- 동일 source의 active `EmbeddingChunk`는 최신 완료 generation만 사용한다. 새 generation 완료 전에는 기존 active chunk를 유지한다.
- embedding model은 `text-embedding-3-small`, 차원은 1536, 거리는 cosine으로 고정한다. MVP는 exact search를 사용하고 후보 5,000개 초과 또는 검색 p95 1초 지속 초과 시 HNSW를 검토한다.

## RAG Chunk Shape

Backend는 아래 논리 구조의 `TranscriptSegment` 원천을 PostgreSQL에 저장한다. internal Meeting/Project AI는 Backend가 확정한 scope envelope를 받아 active/completed `EmbeddingChunk`를 pgvector에서 검색한다. legacy source 전달 경로는 전환 호환용으로만 유지한다. `TranscriptSegment`는 원본 저장 단위이고, `EmbeddingChunk`는 검색/임베딩 단위다.

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
- `scope`: AI query mode가 아닌 source 소유 범위. `transcript`, `meetingSummary`, `decision`, `actionItem`, `report`는 `meeting`, `projectKnowledge`는 `project`
- `sourceType`: `transcript`, `meetingSummary`, `decision`, `actionItem`, `report`, `meetingAttachment`, `projectKnowledge`, `glossary`
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
- 회의별 챗봇은 단일 `meetingId`에 속한 chunk와 `READY` attachment chunk만 검색한다.
- 프로젝트별 챗봇은 `ProjectKnowledge`와 권한 필터를 통과한 meeting chunk(READY attachment 포함)만 검색한다.
- Backend는 active SpaceMember와 MeetingParticipant를 PostgreSQL 조회에서 선필터하고 AI 서버에는 `already_filtered` context와 `allowedMeetingIds`만 전달한다.
- Meeting AI query mode는 `spaceId`, 단일 `meetingId`, `scope=meeting`을 모두 만족하는 chunk만 검색한다.
- Project AI query mode는 같은 `spaceId`의 `scope=project/sourceType=projectKnowledge` chunk와 `allowedMeetingIds`에 포함된 `scope=meeting` chunk를 함께 검색한다.
- `allowedMeetingIds=[]`는 모든 회의를 의미하지 않는다. ProjectKnowledge만 검색하고 meeting chunk는 모두 제외한다.
- 실제 pgvector 검색은 `isActive=true`이고 완료된 `EmbeddingJob`에 속한 chunk만 대상으로 한다.

### Retrieval and Grounding Rules

- Backend가 권한을 평가하고 AI는 전달받은 검색 범위를 SQL에서 강제한다. AI는 SpaceRole 또는 MeetingParticipant를 재평가하지 않는다.
- vector cosine 후보와 `pg_trgm` 후보는 각각 원점수를 보존하고 RRF로 순위를 결합한다.
- 관련도 threshold는 embedding model별 설정으로 관리하고 한국어 근거 있음/없음 평가 질의 30-50건으로 보정한다.
- 근거 0건 또는 관련도 미달이면 LLM을 호출하지 않고 `unsupported=true`를 반환한다.
- supported 답변은 검색 결과에 존재하는 `sourceIds`를 최소 1개 포함해야 한다. public `sources`에는 실제 인용된 source만 반환한다.
- report decision/action item과 task candidate는 valid source ID가 없는 항목을 저장하지 않는다. 모두 제거되면 전체 결과를 unsupported로 처리한다.

### Reindex Trigger Rules

- ProjectKnowledge 생성/수정/복원은 knowledge generation을 만든다. archive/delete는 기존 chunk를 즉시 비활성화한다.
- transcript segment마다 job을 만들지 않는다. `MeetingTranscript.status=COMPLETED`일 때 최초 meeting generation을 만든다.
- 발화자명 또는 meeting title처럼 `embeddingText`에 포함되는 값이 바뀌면 meeting generation을 만든다. 일정, 권한, 참여자 변경은 재임베딩하지 않는다.
- report candidate/draft 편집 중에는 job을 만들지 않고 current confirmed report가 바뀔 때 meeting generation을 만든다.
- attachment complete 후 extractor가 text를 만들면 `ATTACHMENT_READY` generation을 만든다. 같은 hash의 재시도는 최신 generation만 교체한다. 이미지·image-only PDF의 `UNSUPPORTED`는 job을 만들지 않는다.
- attachment 삭제 또는 보존 만료는 physical object cleanup보다 먼저 관련 `EmbeddingChunk.isActive=false`와 `AttachmentChunkAnchor` 조회 제외를 같은 transaction으로 처리한다.
- transcript/attachment 보존 만료와 meeting 삭제는 관련 chunk와 source 연결을 즉시 제거한다.
- worker는 `PENDING` job을 `FOR UPDATE SKIP LOCKED`로 선점하고 lease 만료 작업을 재처리한다. retry는 최대 3회, 1분/5분/15분 간격을 기본값으로 둔다.
- 새 generation 전환 transaction은 요청된 최신 generation인지 확인한 뒤 기존 active chunk를 replaced 처리하고 새 chunk만 active로 만든다.

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
- MeetingMessage, MeetingAttachment 조회·download·upload도 같은 MeetingParticipant ACL을 사용한다. 업로드자는 자신의 attachment를, OWNER/ADMIN/active HOST는 모든 meeting attachment를 삭제할 수 있다.
- AI 서버로 전달되는 transcript segment는 Backend 권한 필터 이후에 구성한다.
- `MeetingParticipant.accessStatus=ACTIVE`만 회의 접근 권한으로 인정한다. `REVOKED`는 조회, 수정, LiveKit token, Meeting AI, Project AI meeting context 접근을 모두 차단한다.
- SpaceMember 제거 시 같은 Space에 속한 `participantType=member` MeetingParticipant는 `participantType=guest`로 전환한다. 프로젝트 접근권 제거와 회의 접근권 revoke는 분리하며, 회의 접근 차단은 MeetingParticipant `REVOKED`로 처리한다.
- 회의 참가 신청은 URL 또는 코드만으로 대상을 식별해 `MeetingJoinRequest`로 기록하고, active HOST 승인 후 기본 `VIEWER` MeetingParticipant가 생성된다. OWNER/ADMIN은 ACL 관리 override로 검토할 수 있다.
- HOST의 회의방 일시 퇴장은 `MeetingParticipant` 권한을 바꾸지 않는다. 마지막 active HOST의 role 강등, `REVOKED` 전환, participant 제거는 거부한다.

## API Representation Rules

- transcript segment 위치는 API에서 `startMs`, `endMs` 밀리초로 표현한다.
- 날짜시간은 ISO-8601로 표현한다.
- 배열 응답은 값이 없으면 `null` 대신 `[]`를 반환한다.

## Retention

- 음성 원본: 기본 장기 보관 없음
- STT 원문: 회의별 `retentionPolicy`에 따른 삭제 대상. DB 값은 `DAYS_7`, `DAYS_30`, `PERMANENT`이며 기본값은 `DAYS_30`이다. `legalHold=true`이면 자동 삭제를 보류한다.
- 보고서/공식 지식: Space 정책에 따른 보존

## Knowledge Graph Read Model Extension

Phase 1에서는 `GraphNode`, `GraphEdge`, `GraphCluster`, `Topic`을 영속 엔티티로
추가하지 않는다. 기존 Meeting, MeetingReport, Decision, ActionItem, TaskCard,
ProjectKnowledge, DomainTerm와 active `EmbeddingChunk`를 권한 필터링한 뒤 그래프
read model로 투영한다.

- `GraphNode`: opaque `id`, `entityId`, `nodeType`, `spaceId`, optional `meetingId`,
  표시용 title/summary, canonical status, occurredAt, clusterIds, connectionCount,
  source references, detail target
- `GraphEdge`: opaque `id`, `from`, `to`, `edgeType`, weight, source references
- `GraphCluster`: id, label, clusterBy, nodeIds, nodeCount, keywords, colorKey
- Topic: Phase 1에서는 서버 계산 파생 결과다. 별도 사용자 편집 Topic entity와
  topic table은 후속 결정 없이는 만들지 않는다.
- Participant node: 개인정보와 회의 참가 범위 정책이 확정되기 전에는 생성하지
  않는다. 이름·이메일·프로필 이미지를 그래프 metadata에 기본 포함하지 않는다.
- 그래프 filter는 조회 편의 기능이며 권한 경계가 아니다. `allowedMeetingIds`는
  Backend가 현재 사용자 권한으로 계산하고 요청 meetingIds와 교집합한다.
- node detail 조회는 목록 응답 이후에도 동일한 권한을 재검사한다.
- 대규모 응답은 서버 `maxNodes`/edge 상한과 `partial`/`truncated` 상태로 표현한다.
- 그래프 위치, zoom, 접힌 cluster 상태는 사용자별 UI preference이며 도메인 모델과
  분리한다.
- 회의 채팅 첨부: Meeting의 retention policy를 상속한다. `DAYS_7`/`DAYS_30`이면 `retentionUntil`을 기록하고, `PERMANENT`이면 null이다. 삭제/만료 시 object URL 발급과 RAG 검색을 즉시 중단하고 physical cleanup은 retry 가능한 비동기 작업으로 남긴다.
