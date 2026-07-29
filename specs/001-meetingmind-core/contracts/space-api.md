# Space, Dashboard, Calendar, Permission API Contract

프로젝트는 코드/DB에서 `Space`로 표현한다. 화면 문구는 "프로젝트"를 사용한다.

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Backend |
| Owner | Backend, Frontend |
| Related requirements | FR-DASH-01, FR-DASH-02, FR-DASH-03, FR-DASH-04, FR-DASH-05, FR-DASH-06, FR-DASH-07, FR-CAL-01, FR-CAL-02, FR-CAL-03, FR-CAL-04, FR-PERM-01, FR-PERM-02, FR-PERM-03, FR-PERM-04, FR-PERM-05, FR-OWN-01, FR-OWN-02, FR-OWN-03, NFR-SEC-06 |
| Related data model | Space, SpaceMember, SpaceInvitation, Meeting, AuditLog |

## GET /api/v1/spaces

사용자가 참여 중인 Space 목록을 조회한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend calendar query

### Auth and Permissions

- 인증 필요
- `SpaceMember` 기준 필터링
- 삭제/비활성 Space 제외

### Data Scope

- User scope
- 사용자의 활성 Space membership만 반환한다.

### Query

- `keyword`: optional, name/description 검색
- `role`: optional `OWNER`, `ADMIN`, `MEMBER`
- `sort`: optional `updatedAtDesc`, `nameAsc`

### Validation

- `role`, `sort`는 허용 enum만 받는다.

### Response

```json
{
  "spaces": [
    {
      "id": "space-001",
      "name": "MeetingMind",
      "description": "AI 회의 지식화 프로젝트",
      "role": "OWNER",
      "meetingCount": 12,
      "updatedAt": "2026-07-09T10:00:00+09:00"
    }
  ]
}
```

### Errors

- `401 UNAUTHORIZED`: 인증 실패
- `400 INVALID_REQUEST`: query enum 오류

### Audit

- No audit event.

### Requirement Trace

- FR-DASH-02: 프로젝트 목록
- FR-DASH-06: 검색/필터
- NFR-MNT-03: mock/실 API 응답 shape 일관성

### Notes

- 빈 목록은 `spaces: []`로 반환한다.

## POST /api/v1/spaces

새 Space를 생성한다. 생성자는 `OWNER`가 된다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 생성자는 자동으로 `SpaceMember.role=OWNER`

### Data Scope

- User scope에서 새 Space scope를 생성한다.

### Request

```json
{
  "name": "MeetingMind",
  "description": "AI 회의 지식화 프로젝트",
  "glossaryCategoryIds": ["glossary-category-common-business", "glossary-category-it-software"],
  "customGlossaryCategories": ["반도체 설계"]
}
```

### Validation

- `name`: required, blank 금지
- `description`: optional
- `glossaryCategoryIds`: optional array. 명시하면 활성 카테고리를 여러 개 선택할 수 있으며 ID 중복과 알 수 없는 ID를 거부한다.
- `customGlossaryCategories`: optional array. trim 후 1~100자, 대소문자 무시 중복 불가, 기존 카테고리명과 중복 불가.
- 입력 상한: `glossaryCategoryIds` 50개, `customGlossaryCategories` 10개.
- 두 카테고리 필드가 모두 생략되면 기존 클라이언트 호환을 위해 전체 공용 분야를 구독한다.

### Response

```json
{
  "id": "space-001",
  "name": "MeetingMind",
  "description": "AI 회의 지식화 프로젝트",
  "role": "OWNER",
  "createdAt": "2026-07-09T10:00:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `401 UNAUTHORIZED`: 인증 실패

### Audit

- `SPACE_CREATED`

### Requirement Trace

- FR-DASH-01: 프로젝트 생성
- NFR-SEC-06: 서버측 입력 검증

### Notes

- Space 생성과 owner membership 생성은 하나의 transaction으로 처리한다.

## GET /api/v1/spaces/{spaceId}

Space 상세와 프로젝트 개요 데이터를 조회한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend dashboard summary display

### Auth and Permissions

- 인증 필요
- 해당 Space의 활성 `SpaceMember`

### Data Scope

- Space scope
- Meeting 요약은 사용자가 접근 가능한 회의만 포함한다.

### Request

None.

### Validation

- `spaceId` 존재 및 접근 권한 확인

### Response

```json
{
  "id": "space-001",
  "name": "MeetingMind",
  "description": "AI 회의 지식화 프로젝트",
  "role": "OWNER",
  "upcomingMeetings": [],
  "recentReports": [],
  "actionItems": [],
  "aiEntrypoints": ["project-ai", "meeting-ai"]
}
```

### Errors

- `401 UNAUTHORIZED`: 인증 실패
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음

### Audit

- No audit event.

### Requirement Trace

- FR-DASH-03: 프로젝트 상세
- FR-PBOT-01: Project AI 진입점

### Notes

- `recentReports`는 회의 ACL을 통과한 회의록만 포함한다.

## GET /api/v1/spaces/{spaceId}/ai/usage

Space 단위 AI 사용량과 quota 소진 현황을 조회한다. Overview 카드의 운영 지표용 API다.

### Status

- Target BFF + Core/AI aggregation
- Frontend type/client placeholder added

### Auth and Permissions

- 인증 필요
- 해당 Space의 활성 `SpaceMember`
- meeting guest는 호출할 수 없다.

### Data Scope

- Space scope
- 현재 사용자가 속한 Space의 집계만 반환한다.
- prompt, transcript, answer 원문은 집계에 포함하지 않고 token/request count만 반환한다.

### Query

- `window`: optional `day`, `week`, `month`
- 기본값은 `month`

### Validation

- `spaceId` 존재 및 접근 권한 확인
- `window`는 허용 enum만 받는다.

### Response

```json
{
  "window": "month",
  "limit": 500000,
  "totalRequests": 182,
  "totalInputTokens": 214533,
  "totalOutputTokens": 84127,
  "usagePercent": 60,
  "features": [
    {
      "feature": "meeting-ai",
      "requests": 71,
      "inputTokens": 80342,
      "outputTokens": 29401
    },
    {
      "feature": "project-ai",
      "requests": 89,
      "inputTokens": 101420,
      "outputTokens": 41712
    },
    {
      "feature": "report-ai",
      "requests": 22,
      "inputTokens": 32771,
      "outputTokens": 13014
    }
  ]
}
```

### Errors

- `401 UNAUTHORIZED`: 인증 실패
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음
- `503 AI_USAGE_UNAVAILABLE`: 집계 소스 또는 provider usage 수집 상태 문제

### Audit

- No audit event.

### Requirement Trace

- NFR-LOG-01~02: 원문 비노출 집계
- PERF-OBS-01: token usage 관측 가능
- NFR-COST-01~03: usage/quota 가시화

### Notes

- `limit`이 없으면 `null`을 반환한다.
- `usagePercent`는 `limit`이 있는 경우에만 계산한다.
- feature 집계는 `meeting-ai`, `project-ai`, `report-ai`부터 시작하고 후속 feature가 추가될 수 있다.
- 현재 BFF는 `project-ai`, `meeting-ai`, `report-ai` 요청을 AI 서비스로 직접 프록시하므로 backend 단독 집계는 불가능하다.
- 운영 구현은 BFF가 AI 응답의 `usage` 메트릭을 수집하고, Core의 usage aggregate store에 기록한 뒤 이 endpoint에서 Space 단위로 조회한다.
- usage 집계 기록 실패는 원 요청을 실패시키지 않고 observability 이벤트로만 남긴다.
- prompt/answer raw text는 저장하지 않는다.

## POST /api/v1/spaces/{spaceId}/ai/chat

Backend가 인증과 Project AI 권한 선필터를 적용한 뒤 AI 서버를 호출한다.

### Status

- Target Backend
- Backend-to-AI integration slice

### Auth and Permissions

- 인증 필요
- active `SpaceMember`만 호출할 수 있다.
- meeting guest는 SpaceMember가 아니므로 Project AI를 호출할 수 없다.
- Backend는 Space 접근을 확인한 뒤 OWNER/ADMIN 또는 active MeetingParticipant가 읽을 수 있는 회의만 선필터한다.

### Data Scope

- Space scope
- 해당 Space의 `PUBLISHED`, `embeddingStatus=COMPLETED` ProjectKnowledge
- 사용자가 읽을 수 있는 회의의 current/confirmed report summary
- Frontend는 source context를 직접 전달하지 않는다.

### Request

```json
{
  "question": "권한 관련 남은 리스크가 뭐야?"
}
```

### Validation

- `spaceId`: path required
- `question`: required, blank 금지
- Backend-to-AI request에는 `allowedMeetingIds`와 already-filtered `sources[]`가 포함되어야 한다.

### Response

```json
{
  "answer": "남은 리스크는 실제 pgvector 저장소 연동입니다.",
  "sources": [
    {
      "sourceId": "knowledge-001",
      "type": "projectKnowledge",
      "title": "권한 설계 메모",
      "text": "Project AI는 접근 가능한 회의만 검색한다."
    }
  ],
  "unsupported": false,
  "model": "gpt-4.1-mini"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `401 UNAUTHORIZED`: 인증 실패
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음 또는 meeting guest 호출
- `404 SPACE_NOT_FOUND`: Space 없음
- `503 AI_PROVIDER_UNAVAILABLE`: AI provider 응답 없음

### Audit

- Target: `AI_REQUESTED`
- Current integration slice: persistent audit log 미구현

### Requirement Trace

- FR-PBOT-01: 프로젝트 질의응답
- FR-PBOT-02: 접근 가능한 회의만 검색
- FR-PBOT-03: 공식 지식/회의 기록 출처 구분
- FR-PBOT-04: 근거 부재 처리
- NFR-AZ-01: RAG 검색 전 권한 선필터
- NFR-AZ-02: 권한 통과 데이터만 AI context에 포함
- NFR-AZ-04: Meeting AI/Project AI 범위 분리

### Notes

- AI 내부 endpoint는 `contracts/ai-api.md`의 `POST /api/internal/project-ai/chat`을 사용한다.
- 실제 PostgreSQL/pgvector retriever와 대화 이력은 후속 작업이다.

## PATCH /api/v1/spaces/{spaceId}

Space 정보를 수정한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- Space scope

### Request

```json
{
  "name": "MeetingMind Core",
  "description": "프로젝트 설명",
  "imageUrl": "https://cdn.example.com/spaces/space-001/cover.webp"
}
```

### Validation

- `name`: optional, 제공 시 blank 금지
- `description`: optional
- `imageUrl`: optional nullable HTTPS URL. 이미지를 제거할 때는 `null`을 보낸다.

### Response

```json
{
  "id": "space-001",
  "name": "MeetingMind Core",
  "description": "프로젝트 설명",
  "imageUrl": "https://cdn.example.com/spaces/space-001/cover.webp",
  "updatedAt": "2026-07-09T10:10:00+09:00"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 수정 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음

### Audit

- `SPACE_UPDATED`

### Requirement Trace

- FR-DASH-04: 프로젝트 수정
- NFR-SEC-06: 서버측 입력 검증

### Notes

- name 중복 허용 여부는 Backend owner가 결정한다.

## POST /api/v1/spaces/{spaceId}/image

Space 대표 이미지를 업로드하고 공개 URL을 반환한다. 실제 Space 수정은 위 `PATCH` 요청의
`imageUrl`로 수행한다.

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Request

- `multipart/form-data`의 `file`
- JPEG, PNG, WebP만 허용하며 최대 5MB

### Response

```json
{
  "imageUrl": "/api/v1/assets/images/spaces/space-001/cover.webp"
}
```

### Errors

- `400`: 파일 형식 또는 크기 오류
- `403 SPACE_ACCESS_DENIED`: 수정 권한 없음
- `503`: 이미지 저장소 일시 실패

## DELETE /api/v1/spaces/{spaceId}

Space를 soft delete 한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 전용
- 진행 중 회의가 있으면 정책에 따라 거부 가능

### Data Scope

- Space scope
- 삭제 시 meeting/report/embedding/vector/cache 삭제 또는 비활성화 정책을 Data owner와 맞춘다.

### Request

None.

### Validation

- `spaceId` 존재 및 owner 권한 확인
- 진행 중 회의 존재 여부 확인

### Response

```json
{
  "deleted": true
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: 삭제 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음
- `409 MEETING_ALREADY_PROCESSING`: 진행 중 회의 또는 처리 중 데이터가 있어 삭제 거부

### Audit

- `SPACE_DELETED`

### Requirement Trace

- FR-DASH-05: 프로젝트 삭제
- POL-RETENTION-01: 삭제/보존 정책

### Notes

- 실제 hard delete는 보존 정책 문서와 migration 결정 이후 확정한다.

## GET /api/v1/dashboard

대시보드 홈 요약을 조회한다.

### Status

- Implemented: Core API + BFF allowlist + Frontend dashboard summary display

### Auth and Permissions

- 인증 필요
- 사용자가 접근 가능한 Space/Meeting만 집계한다.

### Data Scope

- User scope

### Request

None.

### Validation

- 인증 사용자 확인

### Response

```json
{
  "todayMeetings": [
    {
      "id": "meeting-001",
      "spaceId": "space-001",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12",
      "startsAt": "2026-07-20T10:00:00+09:00",
      "endsAt": "2026-07-20T11:00:00+09:00",
      "status": "SCHEDULED"
    }
  ],
  "recentActivities": [
    {
      "id": "task-001",
      "spaceId": "space-001",
      "title": "API 명세 정리 태스크 업데이트",
      "occurredAt": "2026-07-20T09:00:00Z",
      "type": "task"
    }
  ],
  "spaces": [
    {
      "id": "space-001",
      "name": "MeetingMind",
      "description": "AI 회의 지식화 프로젝트",
      "role": "OWNER",
      "meetingCount": 12,
      "updatedAt": "2026-07-20T09:00:00Z"
    }
  ],
  "actionItems": [
    {
      "id": "task-001",
      "spaceId": "space-001",
      "meetingId": "meeting-001",
      "title": "API 명세 정리",
      "description": null,
      "status": "TODO",
      "assigneeId": null,
      "dueDate": null,
      "sourceCandidateId": null
    }
  ],
  "latestReports": [
    {
      "id": "report-001",
      "spaceId": "space-001",
      "meetingId": "meeting-001",
      "meetingTitle": "Sprint Planning #12",
      "title": "Sprint Planning #12 회의록",
      "summary": "스프린트 범위와 담당자를 확정했습니다.",
      "version": 2,
      "confirmedAt": "2026-07-20T10:30:00Z"
    }
  ]
}
```

### Errors

- `401 UNAUTHORIZED`: 인증 실패

### Audit

- No audit event.

### Requirement Trace

- FR-DASH-07: 메인 대시보드
- FR-CAL-02: 오늘 회의 표시

### Notes

- 오늘은 제품 시간대 `Asia/Seoul`을 기준으로 한다. `endsAt`은 Meeting의 예정 종료 시각 `scheduledEndAt`을 반환하며, 실제 회의 종료 시각 `endedAt`과 구분한다.
- 최근 활동은 현재 권한이 확인된 Space 변경, Task 변경, 읽을 수 있는 회의록 생성으로 구성한다. 공통 audit-event read model은 후속 확장 경계다.
- `actionItems`는 `DONE` 이외 카드 중 마감일·수정시각 순 상위 10건이다. 연결 Meeting을 읽을 수 없으면 `meetingId`, `sourceCandidateId`는 `null`이다.
- `latestReports`는 Meeting ACL을 통과한 회의의 current `CONFIRMED` report만 확정 시각 내림차순 상위 5건으로 반환한다. `CANDIDATE`, `DRAFT`, 이전 버전 report는 포함하지 않는다.

## GET /api/v1/calendar/events

사용자가 접근 가능한 회의 일정을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space membership 및 meeting ACL 필터 적용

### Data Scope

- User scope
- optional `spaceId`를 받으면 해당 Space 내 접근 가능한 Meeting만 반환한다.

### Query

- `from`: ISO-8601
- `to`: ISO-8601
- `spaceId`: optional

### Validation

- `from`, `to`: required, ISO-8601
- `from <= to`
- `spaceId` 제공 시 접근 권한 확인

### Response

```json
{
  "events": [
    {
      "id": "meeting-001",
      "spaceId": "space-001",
      "meetingId": "meeting-001",
      "title": "Sprint Planning #12",
      "startsAt": "2026-07-10T10:00:00+09:00",
      "endsAt": "2026-07-10T11:00:00+09:00",
      "status": "SCHEDULED"
    }
  ]
}
```

### Errors

- `400 INVALID_REQUEST`: 기간 형식 오류
- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음

### Audit

- No audit event.

### Requirement Trace

- FR-CAL-01: 캘린더 일정 조회
- FR-CAL-02: 회의 일정 표시
- FR-CAL-03: 일정에서 회의 상세 이동

### Notes

- 일정 생성은 `POST /api/v1/spaces/{spaceId}/meetings`를 사용한다. 별도 calendar event 생성 API가 필요하면 Meeting API와 분리 여부를 다시 결정한다.

## GET /api/v1/spaces/{spaceId}/members

Space 멤버 목록을 조회한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- Space 접근 권한 필요

### Data Scope

- Space scope

### Request

None.

### Validation

- `spaceId` 접근 권한 확인

### Response

```json
{
  "members": [
    {
      "id": "member-001",
      "userId": "user-001",
      "displayName": "이미주",
      "email": "miju@meetingmind.ai",
      "role": "OWNER",
      "joinedAt": "2026-07-09T10:00:00+09:00"
    }
  ]
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: Space 접근 권한 없음
- `404 SPACE_NOT_FOUND`: Space 없음

### Audit

- No audit event.

### Requirement Trace

- FR-PERM-01: 프로젝트 멤버/권한 조회

### Notes

- 게스트 회의 참여자는 SpaceMember 목록에 포함하지 않는다.

## POST /api/v1/spaces/{spaceId}/invitations

Space 초대 링크를 생성한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 `ADMIN`

### Data Scope

- Space scope

### Request

```json
{
  "email": "new@meetingmind.ai",
  "role": "MEMBER"
}
```

### Validation

- `email`: required, email format
- `role`: `ADMIN` 또는 `MEMBER`; `OWNER` 초대 금지

### Response

```json
{
  "invitationId": "space-invitation-001",
  "status": "PENDING",
  "expiresAt": "2026-07-16T10:00:00+09:00",
  "inviteToken": "returned-only-once"
}
```

### Errors

- `400 INVALID_REQUEST`: 입력 검증 실패
- `403 SPACE_ACCESS_DENIED`: 초대 권한 없음

### Audit

- `SPACE_MEMBER_INVITED`

### Requirement Trace

- FR-PERM-02: 멤버 초대
- NFR-SEC-06: 서버측 입력 검증

### Notes

- token 원문은 SHA-256 hash만 저장하고 생성 응답에서 초대 권한이 있는 호출자에게 한 번만 반환한다. 만료 기간은 생성 시점부터 7일이다.
- 수락/거절은 인증 사용자 이메일과 초대 이메일, token hash가 모두 일치해야 한다.
- Space invitation은 수락 시 `SpaceMember`를 생성한다. 회의 단독 초대는 `meeting-api.md`의 Meeting invitation endpoint를 사용한다.
- Browser 수락 링크는 `/space-invitations/{spaceId}/{invitationId}#token={token}`을 사용한다. token은 query가 아닌 fragment에 두며, Browser는 해당 fragment를 accept/decline body의 `token`으로만 전달한다.

## POST /api/v1/spaces/{spaceId}/invitations/{invitationId}/accept

초대를 수락하고 SpaceMember로 등록한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 초대 대상 이메일 또는 초대 token 정책과 일치해야 한다.

### Data Scope

- SpaceInvitation scope에서 SpaceMember scope로 전환한다.

### Request

```json
{
  "token": "invite-token"
}
```

### Validation

- 초대 상태가 `PENDING`
- 만료되지 않은 초대
- 이미 멤버인 사용자는 idempotent 처리 또는 `409` 정책 중 하나를 선택한다.

### Response

```json
{
  "memberId": "member-002",
  "role": "MEMBER",
  "status": "ACCEPTED"
}
```

### Errors

- `400 INVALID_REQUEST`: token 누락
- `403 SPACE_ACCESS_DENIED`: 초대 대상 불일치
- `404 SPACE_NOT_FOUND`: Space 없음
- `409 INVALID_REQUEST`: 만료 또는 이미 처리된 초대

### Audit

- `SPACE_INVITATION_RESOLVED`

### Requirement Trace

- FR-PERM-05: 초대 수락

### Notes

- 공개 초대 링크 방식이면 인증 전 token 확인과 가입/로그인 후 수락 흐름을 분리할 수 있다.
- Space invitation 수락은 Space membership을 생성한다. Meeting guest 권한은 생성하지 않는다.

## POST /api/v1/spaces/{spaceId}/invitations/{invitationId}/decline

초대를 거절한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 초대 대상자 또는 초대 token 보유자

### Data Scope

- SpaceInvitation scope

### Request

```json
{
  "token": "invite-token"
}
```

### Validation

- 초대 상태가 `PENDING`

### Response

```json
{
  "invitationId": "space-invitation-001",
  "status": "DECLINED"
}
```

### Errors

- `400 INVALID_REQUEST`: token 누락 또는 상태 오류
- `403 SPACE_ACCESS_DENIED`: 초대 대상 불일치

### Audit

- `SPACE_INVITATION_RESOLVED`

### Requirement Trace

- FR-PERM-05: 초대 거절

### Notes

- 거절 후 같은 이메일 재초대 허용 정책은 Backend owner가 결정한다.

## PATCH /api/v1/spaces/{spaceId}/members/{memberId}

멤버 role을 변경한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 전용

### Data Scope

- SpaceMember scope

### Request

```json
{
  "role": "ADMIN"
}
```

### Validation

- `role`: `ADMIN` 또는 `MEMBER`
- 자기 자신을 `OWNER`에서 낮추는 변경은 owner transfer API를 사용한다.

### Response

```json
{
  "memberId": "member-002",
  "role": "ADMIN"
}
```

### Errors

- `400 INVALID_REQUEST`: role 값 오류
- `403 SPACE_ACCESS_DENIED`: role 변경 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 member 없음

### Audit

- `SPACE_MEMBER_ROLE_CHANGED`

### Requirement Trace

- FR-PERM-03: 역할 변경

### Notes

- owner 이양은 `POST /owner-transfer`만 사용한다.

## DELETE /api/v1/spaces/{spaceId}/members/{memberId}

멤버를 제거한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- `OWNER` 또는 정책상 허용된 `ADMIN`

### Data Scope

- SpaceMember scope
- 제거된 사용자의 같은 Space 내 `participantType=member` MeetingParticipant는 `participantType=guest`로 전환한다.
- SpaceMember 제거는 프로젝트 전체 접근권만 제거하며, 특정 회의 접근권은 MeetingParticipant ACL로 별도 유지하거나 revoke한다.

### Request

None.

### Validation

- owner 제거 금지
- 대상 member가 해당 Space에 속해야 한다.

### Response

```json
{
  "removed": true
}
```

### Errors

- `403 SPACE_ACCESS_DENIED`: 제거 권한 없음
- `404 SPACE_NOT_FOUND`: Space 또는 member 없음
- `409 INVALID_REQUEST`: owner 제거 시도

### Audit

- `SPACE_MEMBER_REMOVED`

### Requirement Trace

- FR-PERM-04: 멤버 제거

### Notes

- 회의별 guest participant는 이 API로 제거하지 않는다. 특정 회의 접근을 끊으려면 MeetingParticipant revoke API를 사용한다.
- SpaceMember 제거 후 해당 사용자의 프로젝트, Project Knowledge, Project AI 접근은 즉시 차단한다.
- 제거된 사용자가 active MeetingParticipant를 갖고 있으면 해당 회의, LiveKit, Meeting AI 접근은 회의 ACL 범위에서 유지된다.

## POST /api/v1/spaces/{spaceId}/owner-transfer

오너 권한을 다른 멤버에게 이양한다.

### Status

- Target Backend

### Auth and Permissions

- 인증 필요
- 현재 `OWNER`
- 대상은 활성 SpaceMember

### Data Scope

- SpaceMember role scope

### Request

```json
{
  "targetMemberId": "member-002",
  "confirmationText": "TRANSFER OWNER"
}
```

### Validation

- `targetMemberId`: required, active member
- `confirmationText`: exact match
- transaction 내 기존 owner와 신규 owner role을 함께 갱신한다.

### Response

```json
{
  "transferred": true,
  "newOwnerMemberId": "member-002",
  "previousOwnerRole": "ADMIN"
}
```

### Errors

- `400 INVALID_REQUEST`: 확인 문구 또는 대상 오류
- `403 SPACE_ACCESS_DENIED`: owner 아님
- `404 SPACE_NOT_FOUND`: Space 또는 member 없음

### Audit

- `SPACE_OWNER_TRANSFERRED`

### Requirement Trace

- FR-OWN-01: 오너 권한 이양
- FR-OWN-02: 이양 확인 절차
- FR-OWN-03: 기존 오너 강등
- NFR-SEC-06: 확인 문구 검증

### Notes

- owner transfer는 되돌리기 어려운 작업이므로 프론트에서도 별도 확인 UI를 둔다.
