# Project Knowledge and Domain Term API Contract

Project Knowledge는 Project AI가 사용할 공식 프로젝트 지식이다. Domain Term은 회의 중 용어 설명에서 우선 조회하는 프로젝트 용어사전이다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Backend |
| Owner | Backend, Frontend, AI |
| Related requirements | FR-PBOT-01, FR-PBOT-02, FR-PBOT-03, FR-RPT-03, FR-TERM-02, FR-TERM-05, NFR-AZ-01, NFR-AZ-02, NFR-AI-02, NFR-COST-01, NFR-SEC-06 |
| Related data model | ProjectKnowledge, DomainTerm, EmbeddingChunk, SourceReference, AuditLog |

## GET /api/v1/spaces/{spaceId}/knowledge

Space의 공식 Project Knowledge 목록을 조회한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend management screen

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요
- 회의 기반 지식의 원본 회의는 사용자가 접근 가능한 경우에만 source metadata를 노출한다.

### Data Scope

- Space scope
- Project AI가 사용할 수 있는 공식 지식만 반환한다.

### Query

- `type`: optional `report`, `decision`, `manual`, `external`
- `keyword`: optional

### Validation

- `spaceId` 접근 권한 확인
- `type` enum 확인

### Response

```json
{
  "items": [
    {
      "id": "knowledge-001",
      "spaceId": "space-001",
      "type": "manual",
      "title": "권한 설계 메모",
      "contentPreview": "Meeting AI는 회의 접근 권한이 있는 사용자만 사용할 수 있다.",
      "sourceMeetingId": null,
      "embeddingStatus": "COMPLETED",
      "updatedAt": "2026-07-09T10:00:00+09:00"
    }
  ]
}
```

### Errors

- `400 INVALID_REQUEST`: query 오류
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음

### Audit

- No audit event.

### Requirement Trace

- FR-PBOT-01: 프로젝트 지식 기반 질의응답
- FR-PBOT-03: 공식 지식 출처 구분
- NFR-AZ-01: 권한 필터 선적용

### Notes

- 목록 조회는 embedding content 전체를 노출하지 않는다.

## GET /api/v1/spaces/{spaceId}/knowledge/{knowledgeId}

공식 Project Knowledge 원문을 조회한다. 수정 화면은 목록의 `contentPreview` 대신 이 응답을 사용한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend management screen

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요
- `sourceMeetingId`는 사용자가 원본 회의 접근 권한을 가질 때만 반환한다.

### Response

```json
{
  "id": "knowledge-001",
  "spaceId": "space-001",
  "type": "manual",
  "title": "권한 설계 메모",
  "content": "Project AI는 공식 지식과 접근 가능한 회의만 검색한다.",
  "sourceMeetingId": null,
  "embeddingStatus": "COMPLETED",
  "updatedAt": "2026-07-20T10:00:00Z"
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음
- `404 PROJECT_KNOWLEDGE_NOT_FOUND`: knowledge 없음 또는 archive됨

### Notes

- 상세 원문은 화면 편집을 위해서만 조회하며, Project AI 검색 범위는 `PUBLISHED` 상태로 별도 제한된다.

## POST /api/v1/spaces/{spaceId}/knowledge

공식 Project Knowledge를 등록한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend management screen

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- Space scope
- 등록 후 Project AI의 project scope RAG 후보가 된다.

### Request

```json
{
  "type": "manual",
  "title": "권한 설계 메모",
  "content": "Meeting AI는 회의 접근 권한이 있는 사용자만 사용할 수 있다.",
  "sourceMeetingId": null
}
```

### Validation

- `type`: `report`, `decision`, `manual`, `external`
- `title`: required, blank 금지
- `content`: required, blank 금지
- `sourceMeetingId`: optional, 제공 시 접근 가능한 회의여야 한다.

### Response

```json
{
  "id": "knowledge-001",
  "status": "PUBLISHED",
  "embeddingStatus": "PENDING",
  "updatedAt": "2026-07-09T10:00:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 등록 권한 없음
- `403 MEETING_ACCESS_DENIED`: source meeting 접근 권한 없음

### Audit

- `PROJECT_KNOWLEDGE_CREATED`

### Requirement Trace

- FR-PBOT-01: Project AI 공식 지식 등록
- FR-RPT-03: 확정 회의록의 프로젝트 문서화
- NFR-AZ-02: 권한 통과 데이터만 AI 컨텍스트 포함
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 회의록 확정 후 자동 등록되는 지식도 동일한 모델을 사용한다.
- 등록 직후 embedding은 비동기 생성한다. Project AI는 `COMPLETED` chunk만 검색한다.

## PATCH /api/v1/spaces/{spaceId}/knowledge/{knowledgeId}

Project Knowledge를 수정한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend management screen

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- ProjectKnowledge scope
- 수정 후 관련 embedding chunk는 비동기로 재생성한다.

### Request

```json
{
  "title": "권한 설계 메모 v2",
  "content": "Project AI는 공식 지식과 접근 가능한 회의만 검색한다."
}
```

### Validation

- `knowledgeId`가 해당 Space에 속해야 한다.
- `title`, `content`: optional, 제공 시 blank 금지

### Response

```json
{
  "id": "knowledge-001",
  "embeddingStatus": "PENDING",
  "embeddingJobId": "embed-job-001",
  "updatedAt": "2026-07-09T10:10:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 수정 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 knowledge 없음

### Audit

- `PROJECT_KNOWLEDGE_UPDATED`

### Requirement Trace

- FR-PBOT-01: Project AI 공식 지식 관리
- NFR-AI-02: 출처 추적 유지
- NFR-SEC-06: 서버측 입력 검증

### Notes

- ProjectKnowledge 수정 시 embedding은 비동기 재생성한다.
- 기존 chunk는 유지하고 새 embedding이 `COMPLETED`가 되면 교체한다.
- 재생성 실패 시 `embeddingStatus=FAILED`로 남기고 기존 chunk를 계속 사용할 수 있다.

## DELETE /api/v1/spaces/{spaceId}/knowledge/{knowledgeId}

Project Knowledge를 삭제 또는 비활성화한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend management screen

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- ProjectKnowledge scope
- 관련 embedding chunk를 삭제 또는 비활성화한다.

### Request

None.

### Validation

- `knowledgeId`가 해당 Space에 속해야 한다.

### Response

```json
{
  "deleted": true
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: 삭제 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 knowledge 없음

### Audit

- `PROJECT_KNOWLEDGE_DELETED`

### Requirement Trace

- FR-PBOT-01: Project AI 공식 지식 관리
- NFR-AZ-02: 삭제 지식의 AI 컨텍스트 제외

### Notes

- hard delete 여부는 보존 정책과 감사 로그 요구에 맞춘다.

## GET /api/v1/spaces/{spaceId}/terms

Space 용어사전 목록을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요

### Data Scope

- Space DomainTerm scope

### Query

- `keyword`: optional
- `status`: optional `ACTIVE`, `ARCHIVED`

### Validation

- `status` enum 확인

### Response

```json
{
  "terms": [
    {
      "id": "term-001",
      "term": "pgvector",
      "definition": "PostgreSQL에서 vector similarity search를 지원하는 확장입니다.",
      "status": "ACTIVE",
      "updatedAt": "2026-07-09T10:00:00+09:00"
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

- FR-TERM-02: 등록 용어 사전 우선 제공
- FR-TERM-05: 용어 사전 관리
- NFR-COST-01: 등록 용어 LLM 호출 회피

### Notes

- Meeting AI 용어 설명은 이 목록 또는 indexed term cache를 우선 사용한다.

## POST /api/v1/spaces/{spaceId}/terms

용어사전에 용어를 등록한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- Space DomainTerm scope

### Request

```json
{
  "term": "pgvector",
  "definition": "PostgreSQL에서 vector similarity search를 지원하는 확장입니다."
}
```

### Validation

- `term`: required, Space 내 unique
- `definition`: required, blank 금지

### Response

```json
{
  "id": "term-001",
  "status": "ACTIVE"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 등록 권한 없음
- `409 INVALID_REQUEST`: 중복 용어

### Audit

- `DOMAIN_TERM_CHANGED`

### Requirement Trace

- FR-TERM-05: 용어 사전 등록
- FR-TERM-02: 등록 용어 사전 우선 제공
- NFR-SEC-06: 서버측 입력 검증

### Notes

- term normalization 정책은 glossary와 맞춘다.

## PATCH /api/v1/spaces/{spaceId}/terms/{termId}

용어사전 항목을 수정한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- DomainTerm scope

### Request

```json
{
  "term": "pgvector",
  "definition": "PostgreSQL에서 벡터 검색을 제공하는 확장입니다.",
  "status": "ACTIVE"
}
```

### Validation

- `term`: optional, 제공 시 Space 내 unique
- `definition`: optional, 제공 시 blank 금지
- `status`: optional `ACTIVE`, `ARCHIVED`

### Response

```json
{
  "id": "term-001",
  "updatedAt": "2026-07-09T10:10:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 수정 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 term 없음
- `409 INVALID_REQUEST`: 활성 용어명 중복

### Audit

- `DOMAIN_TERM_CHANGED`

### Requirement Trace

- FR-TERM-05: 용어 사전 수정
- NFR-SEC-06: 서버측 입력 검증

### Notes

- 용어 archive 후에도 과거 AI 응답 source reference는 유지한다.

## DELETE /api/v1/spaces/{spaceId}/terms/{termId}

용어사전 항목을 삭제 또는 archive한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend management screen

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- DomainTerm scope

### Request

None.

### Validation

- `termId`가 해당 Space에 속해야 한다.

### Response

```json
{
  "deleted": true
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: 삭제 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 term 없음

### Audit

- `DOMAIN_TERM_CHANGED`

### Requirement Trace

- FR-TERM-05: 용어 사전 삭제/archive

### Notes

- 기본 구현은 hard delete보다 archive를 우선한다.
