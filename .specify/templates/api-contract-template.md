이 문서는 기능의 API 계약을 정의하기 위한 Markdown 템플릿이다.

# API Contracts: [FEATURE_NAME]

## [METHOD] [PATH]

[엔드포인트 목적]

### Auth and Permissions

- [필요한 인증/권한]
- [권한 필터 적용 지점]

### Request

```json
{
  "field": "value"
}
```

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

### Notes

- [mock fallback, 호환성, 전환 계획]
