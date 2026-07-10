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
      "dueDate": null,
      "status": "CANDIDATE",
      "sourceIds": ["segment-001"]
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

- FR-TASK-01: AI 회의록 기반 태스크 후보 조회

### Notes

- 후보는 확정 전까지 칸반 카드가 아니다.

## POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/confirm

태스크 후보를 확정하고 `TaskCard`로 등록한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요
- 후보가 속한 회의 접근 권한 필요

### Data Scope

- Meeting scope에서 Space TaskCard scope로 승격한다.

### Request

```json
{
  "title": "ERD 수정안 문서화",
  "assigneeId": "user-001",
  "dueDate": null,
  "status": "TODO"
}
```

### Validation

- candidate가 해당 meeting에 속해야 한다.
- candidate는 아직 확정되지 않은 상태여야 한다.
- `assigneeId`는 SpaceMember여야 한다.

### Response

```json
{
  "taskId": "task-001",
  "sourceCandidateId": "candidate-001"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 또는 candidate 상태 오류
- `403 MEETING_ACCESS_DENIED`: 회의 접근 권한 없음
- `404 MEETING_NOT_FOUND`: meeting 또는 candidate 없음

### Audit

- `TASK_CANDIDATE_CONFIRMED`

### Requirement Trace

- FR-TASK-02: 태스크 후보 확정
- FR-TASK-03: 칸반 자동등록
- FR-TASK-04: 등록 전 편집

### Notes

- 후보 하나에서 카드 하나만 생성되도록 unique 제약을 둔다.
