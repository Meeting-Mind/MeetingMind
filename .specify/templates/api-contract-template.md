이 문서는 기능의 API 계약을 정의하기 위한 Markdown 템플릿이다. MeetingMind의 API 문서는 endpoint마다 아래 항목을 동일한 순서로 작성한다.

# API Contracts: [FEATURE_NAME]

## Document Status

| Field | Value |
| --- | --- |
| Status | [Current Prototype / Target Backend / Backend-to-AI Internal / Future Draft] |
| Owner | [workstream owner] |
| Related requirements | [FR-000, NFR-000, POL-000] |
| Related data model | [Entity names] |

## [METHOD] [PATH]

[엔드포인트 목적]

### Status

- [Current Prototype / Target Backend / Backend-to-AI Internal / Future Draft]
- [mock fallback, migration, legacy compatibility 여부]

### Auth and Permissions

- [필요한 인증/권한]
- [권한 필터 적용 지점]

### Data Scope

- Space scope: [spaceId 기준]
- Meeting scope: [meetingId 기준]
- AI/RAG scope: [검색 전 권한 필터와 source 범위]

### Request

```json
{
  "field": "value"
}
```

### Validation

- [필수 필드, 길이, enum, 상태 전이, 소유 관계 검증]

### Response

```json
{
  "field": "value"
}
```

### Errors

- `400`: [입력 검증 실패]
- `401`: [인증 실패]
- `403`: [권한 없음]
- `404`: [리소스 없음]

### Audit

- [감사 로그 대상 여부와 event/action 이름]

### Requirement Trace

- [FR-000]: [반영 내용]
- [NFR-000]: [반영 내용]

### Notes

- [mock fallback, 호환성, 전환 계획]
