# API Contract Index: MeetingMind Core Prototype

이 디렉터리는 MeetingMind backend/API 구현의 기준 계약을 기능군별로 나눈다. 기존 `api.md`는 통합 초안과 과거 prototype 기록으로 유지하고, 새 구현은 아래 분리 문서를 우선 확인한다.

## Contract Files

| File | Scope | Primary Owners |
| --- | --- | --- |
| `common.md` | 공통 API 규칙, 오류 응답, role/status enum, source reference | Docs/Contracts |
| `auth-api.md` | 현재 token 기반 회원가입/로그인/refresh/logout 호환 계약. 목표 계약은 `../../002-bff-auth-msa/contracts/README.md` | Auth/Login |
| `space-api.md` | 프로젝트(Space), 대시보드, 캘린더, 멤버/오너 이양 | Backend, Frontend |
| `meeting-api.md` | 회의 생성/초대/삭제, 회의 ACL, transcript/report 조회/수정 | Backend, Frontend |
| `kanban-api.md` | 칸반 보드, task card, AI task candidate 확정 | Backend, Frontend |
| `knowledge-api.md` | Project Knowledge, Domain Term 관리와 Project AI 공식 지식 경계 | Backend, Frontend, AI |
| `ai-api.md` | Meeting AI, Project AI, 보고서 후보 생성, 태스크 후보 추출, 용어 설명 | AI, Backend |
| `live-stt-api.md` | LiveKit token, 회의방, STT/dialogue, speaker 수정 | Backend, Frontend, AI |

## Update Rule

- API 구현, request/response shape, 오류 코드, 권한 조건이 바뀌면 해당 contract 파일을 먼저 갱신한다.
- API 변경이 데이터 구조를 바꾸면 `../erd.md`와 `../data-model.md` 영향도도 함께 확인한다.
- 변경 후 `../implement.md`에 날짜, 변경 파일, 변경 이유, 검증 또는 미실행 사유를 남긴다.
- 다른 팀원이 이 계약과 다르게 구현해야 하면 구현 전에 contract 변경을 먼저 제안하고, 충돌 파일 owner와 합의한다.

## Endpoint Template Rule

새 endpoint 또는 endpoint 변경은 `.specify/templates/api-contract-template.md`를 기준으로 작성한다. 모든 endpoint는 최소한 아래 섹션을 같은 순서로 가진다.

1. `Status`
2. `Auth and Permissions`
3. `Data Scope`
4. `Request` 또는 `Query`
5. `Validation`
6. `Response`
7. `Errors`
8. `Audit`
9. `Requirement Trace`
10. `Notes`

`Request` body가 없는 `GET`/`DELETE` endpoint는 `Request` 대신 `Query` 또는 `Request: none`을 적는다. `Audit` 대상이 아니면 `No audit event`라고 명시한다. `Requirement Trace`는 관련 `FR-*`, `NFR-*`, 정책/권한 문서를 최소 1개 이상 연결한다.

## Contract Status Values

| Status | Meaning |
| --- | --- |
| Current Prototype | 현재 코드 또는 mock fallback이 직접 사용하는 계약 |
| Legacy Compatibility | BFF 점진 전환과 제한된 rollback에만 유지하는 현재 구현 계약 |
| Target Backend | backend `/api/v1` 전환 시 구현 기준이 되는 계약 |
| Backend-to-AI Internal | Backend가 권한 필터 후 AI 서버에 전달하는 내부 계약 |
| Future Draft | 현재 구현 범위 밖이지만 후속 설계 후보로 보관하는 계약 |

## Review Checklist

- endpoint마다 권한 필터 지점이 명시되어 있는가?
- Space/Meeting/Project AI/Meeting AI 데이터 범위가 분리되어 있는가?
- 공통 오류 응답 shape와 endpoint별 오류 코드가 연결되어 있는가?
- write/role/owner/report/knowledge/AI 생성 요청의 audit event가 명시되어 있는가?
- 요구사항 ID와 권한/상태/정책 문서가 추적 가능한가?
- 데이터 구조 변경이면 `../erd.md`와 `../data-model.md` 영향 여부가 기록되어 있는가?
