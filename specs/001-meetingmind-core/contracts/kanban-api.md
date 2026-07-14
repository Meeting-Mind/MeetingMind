# Kanban and Task API Contract

칸반의 확정 작업은 `TaskCard`, AI가 제안한 확정 전 작업은 `TaskCandidate`다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Backend |
| Owner | Backend, Frontend |
| Related requirements | FR-KAN-01, FR-KAN-02, FR-KAN-03, FR-KAN-04, FR-KAN-05, FR-KAN-07, FR-KAN-08, FR-TASK-01, FR-TASK-02, FR-TASK-03, FR-TASK-04, NFR-AZ-01, NFR-SEC-06 |
| Related data model | TaskCard, TaskCandidate, Meeting, SpaceMember, AuditLog |

## GET /api/v1/spaces/{spaceId}/tasks

Space 칸반 카드 목록을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요

### Data Scope

- Space scope
- meeting source가 있는 task는 사용자가 접근 가능한 회의의 source metadata만 노출한다.

### Query

- `status`: optional `TODO`, `IN_PROGRESS`, `DONE`
- `assigneeId`: optional
- `keyword`: optional

### Validation

- `status` enum 확인
- `assigneeId` 제공 시 SpaceMember 여부 확인

### Response

```json
{
  "tasks": [
    {
      "id": "task-001",
      "spaceId": "space-001",
      "meetingId": "meeting-001",
      "title": "ERD 수정안 문서화",
      "description": null,
      "status": "TODO",
      "assigneeId": "user-001",
      "dueDate": null,
      "sourceCandidateId": "candidate-001"
    }
  ]
}
```

### Errors

- `400 INVALID_REQUEST`: query 오류
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음

### Audit

- No audit event.

### Requirement Trace

- FR-KAN-01: 칸반 카드 조회
- FR-KAN-08: 카드 필터/검색
- NFR-AZ-01: 접근 가능한 프로젝트 데이터만 노출

### Notes

- 상태 컬럼 순서는 Frontend 표시 정책으로 관리한다.

## POST /api/v1/spaces/{spaceId}/tasks

칸반 카드를 생성한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요
- 담당자 지정 시 대상은 SpaceMember여야 한다.

### Data Scope

- Space scope
- optional meeting source가 있으면 meeting 접근 권한도 확인한다.

### Request

```json
{
  "title": "ERD 수정안 문서화",
  "description": "회의에서 합의된 ERD 수정안을 정리한다.",
  "assigneeId": "user-001",
  "dueDate": null,
  "meetingId": "meeting-001"
}
```

### Validation

- `title`: required, blank 금지
- `assigneeId`: optional SpaceMember
- `meetingId`: optional, 접근 가능한 meeting

### Response

```json
{
  "id": "task-001",
  "status": "TODO"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음
- `403 MEETING_ACCESS_DENIED`: meeting source 접근 권한 없음

### Audit

- `TASK_CARD_CHANGED`

### Requirement Trace

- FR-KAN-02: 카드 생성
- FR-KAN-05: 담당자 지정
- NFR-SEC-06: 서버측 입력 검증

### Notes

- AI 후보에서 확정하는 경우는 별도 confirm endpoint를 사용한다.

## PATCH /api/v1/spaces/{spaceId}/tasks/{taskId}

카드 상세를 수정한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요

### Data Scope

- TaskCard scope

### Request

```json
{
  "title": "ERD 수정안 문서화",
  "description": "수정된 설명",
  "assigneeId": "user-001",
  "dueDate": "2026-07-12",
  "status": "IN_PROGRESS"
}
```

### Validation

- `taskId`가 해당 Space에 속해야 한다.
- `status`: `TODO`, `IN_PROGRESS`, `DONE`
- `assigneeId`: optional SpaceMember

### Response

```json
{
  "id": "task-001",
  "status": "IN_PROGRESS",
  "updatedAt": "2026-07-09T10:20:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 수정 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 task 없음

### Audit

- `TASK_CARD_CHANGED`

### Requirement Trace

- FR-KAN-03: 카드 수정/상태 변경
- FR-KAN-04: 카드 이동
- FR-KAN-05: 담당자 지정
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 상태 전이 제한이 필요하면 `requirements/status-values.md`에 먼저 반영한다.

## DELETE /api/v1/spaces/{spaceId}/tasks/{taskId}

카드를 삭제 또는 보관 상태로 전환한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요

### Data Scope

- TaskCard scope

### Request

None.

### Validation

- `taskId`가 해당 Space에 속해야 한다.

### Response

```json
{
  "deleted": true
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: 삭제 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 task 없음

### Audit

- `TASK_CARD_CHANGED`

### Requirement Trace

- FR-KAN-07: 카드 삭제/보관

### Notes

- soft delete와 archive status 중 하나를 Data owner가 확정한다.

## POST /api/v1/meetings/{meetingId}/task-candidates/generate

회의 transcript와 current confirmed report를 기반으로 태스크 후보를 생성하고 임시 저장한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER`/`ADMIN` 또는 해당 회의의 active `HOST`/`EDITOR`
- 권한 검증은 meeting context 조회와 AI 호출보다 먼저 수행한다.

### Data Scope

- Meeting scope
- Backend가 해당 meeting의 transcript와 current confirmed report만 조립해 AI 서버로 전달한다.

### Request

None.

### Validation

- `meetingId`와 모든 source의 meeting scope가 같아야 한다.
- AI가 반환한 `sourceIds`는 Backend가 전달한 canonical source ID 안에서만 보존한다.
- `unsupported=true` 결과는 저장하지 않는다.
- 담당자 제안은 active meeting participant의 표시 이름과 정확히 일치하고 active SpaceMember인 경우에만 `suggestedAssigneeId`로 연결한다.

### Response

```json
{
  "candidates": [
    {
      "id": "candidate-001",
      "meetingId": "meeting-001",
      "title": "ERD 수정안 문서화",
      "assigneeName": "김진수",
      "suggestedAssigneeId": "user-001",
      "dueDate": null,
      "status": "CANDIDATE",
      "sourceIds": ["segment-001"],
      "createdBy": "user-002",
      "createdAt": "2026-07-13T10:30:00Z"
    }
  ],
  "assignees": [
    {"id": "user-001", "displayName": "김진수"}
  ],
  "canConfirm": true,
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

- `400 INVALID_REQUEST`: 입력 또는 AI 응답 검증 실패
- `403 MEETING_ACCESS_DENIED`: 회의 편집 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음
- `503 AI_PROVIDER_UNAVAILABLE`: AI provider 오류

### Audit

- `AI_REQUESTED`

### Requirement Trace

- FR-TASK-01: AI 회의록/전사 기반 태스크 후보 추출
- NFR-AZ-01, NFR-AZ-04: 권한 선필터와 scope 강제

### Notes

- 후보는 확정 전까지 칸반 카드가 아니다.
- candidate 만료 검증은 `Q-009` 정책 결정 후 추가한다.

## GET /api/v1/meetings/{meetingId}/task-candidates

회의에서 추출된 태스크 후보를 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 회의 접근 권한 필요

### Data Scope

- Meeting scope
- AI가 추출한 candidate와 sourceIds만 반환한다.

### Request

None.

### Validation

- `meetingId` 접근 권한 확인

### Response

```json
{
  "candidates": [
    {
      "id": "candidate-001",
      "meetingId": "meeting-001",
      "title": "ERD 수정안 문서화",
      "assigneeName": "김진수",
      "suggestedAssigneeId": "user-001",
      "dueDate": null,
      "status": "CANDIDATE",
      "sourceIds": ["segment-001"],
      "createdBy": "user-002",
      "createdAt": "2026-07-13T10:30:00Z"
    }
  ],
  "assignees": [
    {"id": "user-001", "displayName": "김진수"}
  ],
  "canConfirm": true
}
```

### Errors

- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: 회의 없음

### Audit

- No audit event.

### Requirement Trace

- FR-TASK-01: AI 회의록 기반 태스크 후보 조회

### Notes

- 후보는 확정 전까지 칸반 카드가 아니다.
- `canConfirm`은 active SpaceMember와 회의 편집 권한을 모두 만족할 때만 true다.
- `assignees`는 `canConfirm=true`일 때만 해당 Space의 active SpaceMember를 반환한다. 회의 게스트와 VIEWER에는 빈 배열을 반환한다.

## POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/confirm

태스크 후보를 확정하고 `TaskCard`로 등록한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- active SpaceMember 필요
- `OWNER`/`ADMIN` 또는 해당 회의의 active `HOST`/`EDITOR`

### Data Scope

- Meeting scope에서 Space TaskCard scope로 승격한다.

### Request

```json
{
  "title": "ERD 수정안 문서화",
  "description": "회의에서 합의한 ERD 제약과 검증 기준을 정리한다.",
  "assigneeId": "user-001",
  "dueDate": null,
  "status": "TODO"
}
```

### Validation

- candidate가 해당 meeting에 속해야 한다.
- candidate는 `CANDIDATE` 상태여야 한다.
- `title`은 공백일 수 없고 `status`는 `TODO`, `IN_PROGRESS`, `DONE` 중 하나여야 한다.
- `description`은 optional이며 공백 문자열은 null로 정규화한다.
- `assigneeId`가 있으면 active SpaceMember여야 한다.

### Response

```json
{
  "taskId": "task-001",
  "sourceCandidateId": "candidate-001"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 또는 candidate 상태 오류
- `403 SPACE_ACCESS_DENIED`: active SpaceMember가 아님
- `403 MEETING_ACCESS_DENIED`: 회의 편집 권한 없음
- `404 MEETING_NOT_FOUND`: meeting 없음
- `404 TASK_CANDIDATE_NOT_FOUND`: candidate 없음 또는 path meeting과 불일치

### Audit

- `TASK_CANDIDATE_CONFIRMED`

### Requirement Trace

- FR-TASK-02: 태스크 후보 확정
- FR-TASK-03: 칸반 자동등록
- FR-TASK-04: 등록 전 편집

### Notes

- 후보 하나에서 카드 하나만 생성되도록 unique 제약을 둔다.
- 확정과 TaskCard 생성은 하나의 domain transition으로 처리하고 candidate 상태를 `CONFIRMED`로 변경한다.
- candidate 만료 검증은 `Q-009` 정책 결정 후 추가한다.
