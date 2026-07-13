# Common API Contract

## Base Rules

| Rule | Value |
| --- | --- |
| Current prototype base | 기존 `/api/workspace`, `/api/livekit/token`, AI 서버 직접 `/api/meeting-ai/*`, `/api/project-ai/*` 유지 |
| Target backend base | `/api/v1` |
| Content-Type | 기본 `application/json; charset=utf-8`; 파일 업로드 후보만 `multipart/form-data` |
| Auth | 인증 API와 공개 랜딩을 제외하고 `Authorization: Bearer {accessToken}` 사용 |
| Time | 날짜시간은 ISO-8601, transcript 위치는 `startMs`, `endMs` 밀리초 |
| Empty arrays | 값이 없으면 `null` 대신 `[]` |
| Error body | `code`, `message`, `fieldErrors`, `traceId` 고정 |

## Common Error Response

```json
{
  "code": "MEETING_NOT_FOUND",
  "message": "회의를 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "01J..."
}
```

| HTTP | code | Situation |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청값이 잘못됨 |
| 401 | `UNAUTHORIZED` | 인증 실패 또는 token 만료 |
| 401 | `INVALID_CREDENTIALS` | 이메일/비밀번호 또는 Google credential 검증 실패 |
| 401 | `REFRESH_TOKEN_INVALID` | refresh token 없음, 만료, 위조, 폐기 |
| 403 | `SPACE_ACCESS_DENIED` | Space 접근 권한 없음 |
| 403 | `MEETING_ACCESS_DENIED` | 회의 참여자 또는 명시 권한 없음 |
| 403 | `AI_CONTEXT_FORBIDDEN` | 권한 필터 전 데이터가 AI context로 요청됨 |
| 404 | `SPACE_NOT_FOUND` | Space를 찾을 수 없음 |
| 404 | `MEETING_NOT_FOUND` | 회의를 찾을 수 없음 |
| 404 | `TASK_CANDIDATE_NOT_FOUND` | 태스크 후보를 찾을 수 없거나 path meeting과 불일치 |
| 404 | `SPEAKER_NOT_FOUND` | 발화자를 찾을 수 없음 |
| 409 | `MEETING_NOT_COMPLETED` | 처리 완료 전 transcript/report/summary 요청 |
| 409 | `MEETING_ALREADY_PROCESSING` | 이미 처리 중인 회의에 중복 처리 요청 |
| 409 | `LAST_ACTIVE_HOST_REQUIRED` | 마지막 active HOST 강등, 접근 회수, 제거 요청 |
| 409 | `EMAIL_ALREADY_REGISTERED` | 이미 가입된 이메일 |
| 413 | `AUDIO_FILE_TOO_LARGE` | 파일 업로드 용량 초과 |
| 422 | `TRANSCRIPTION_FAILED` | STT/발화자 구분 처리 실패 |
| 503 | `LIVEKIT_NOT_CONFIGURED` | LiveKit 환경변수 누락 |
| 503 | `STT_PROVIDER_UNAVAILABLE` | 외부 STT 서비스 응답 없음 |
| 503 | `AI_PROVIDER_UNAVAILABLE` | AI provider 응답 없음 |

Endpoint별 문서에는 위 공통 오류 중 해당 endpoint에서 반환 가능한 코드만 `Errors` 섹션에 다시 연결한다.

## Role Values

| Scope | Values | Notes |
| --- | --- | --- |
| `SpaceRole` | `OWNER`, `ADMIN`, `MEMBER` | 프로젝트 단위 RBAC |
| `MeetingRole` | `HOST`, `EDITOR`, `VIEWER` | 회의 단위 ACL |
| `participantType` | `member`, `guest` | 회의 게스트는 특정 회의에만 접근 |

## Status Values

| Entity | Values |
| --- | --- |
| `Meeting.status` | `SCHEDULED`, `IN_PROGRESS`, `ENDED`, `CANCELED` |
| `Transcript.status` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `MeetingReport.status` | `CANDIDATE`, `DRAFT`, `CONFIRMED` |
| `TaskCandidate.status` | `CANDIDATE`, `CONFIRMED`, `DISMISSED` |
| `TaskCard.status` | `TODO`, `IN_PROGRESS`, `DONE` |
| `SpaceInvitation.status` | `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED` |
| `MeetingInvitation.status` | `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED` |
| `MeetingParticipant.accessStatus` | `ACTIVE`, `REVOKED` |
| `ProjectKnowledge.embeddingStatus` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |

## Source Reference Shape

AI 응답과 RAG 결과는 사용자가 근거를 추적할 수 있도록 아래 shape를 사용한다.

```json
{
  "sourceId": "segment-001",
  "type": "transcript",
  "title": "Sprint Planning #12",
  "speaker": "김진수",
  "time": "06:10:03",
  "startMs": 370300,
  "endMs": 390000,
  "text": "ERD 구조를 수정해야 합니다."
}
```

`type` 값은 `transcript`, `meetingSummary`, `decision`, `actionItem`, `report`, `projectKnowledge`, `glossary` 중 하나다.

## Audit Event Baseline

| Event | Required For |
| --- | --- |
| `SPACE_CREATED` | Space 생성 |
| `SPACE_UPDATED` | Space 이름/설명 수정 |
| `SPACE_DELETED` | Space 삭제 또는 비활성화 |
| `SPACE_MEMBER_INVITED` | Space 초대 생성 |
| `SPACE_INVITATION_RESOLVED` | 초대 수락/거절 |
| `MEETING_INVITATION_CREATED` | 회의 초대 생성 |
| `MEETING_INVITATION_RESOLVED` | 회의 초대 수락/거절 |
| `SPACE_MEMBER_ROLE_CHANGED` | Space role 변경 |
| `SPACE_MEMBER_REMOVED` | Space 멤버 제거 |
| `SPACE_OWNER_TRANSFERRED` | 오너 권한 이양 |
| `MEETING_CREATED` | 회의 생성 |
| `MEETING_UPDATED` | 회의 제목/일정/상태 수정 |
| `MEETING_DELETED` | 회의 삭제 또는 취소 |
| `MEETING_PARTICIPANT_CHANGED` | 회의 참여자 추가/role 변경/접근 회수 |
| `TRANSCRIPT_SPEAKER_UPDATED` | 발화자 이름 수정 |
| `REPORT_CONFIRMED` | AI 회의록 공식 확정 |
| `REPORT_UPDATED` | 회의록 수동 수정 |
| `TASK_CARD_CHANGED` | 칸반 카드 생성/수정/삭제 |
| `TASK_CANDIDATE_CONFIRMED` | AI 태스크 후보 확정 |
| `PROJECT_KNOWLEDGE_CREATED` | Project Knowledge 등록 |
| `PROJECT_KNOWLEDGE_UPDATED` | Project Knowledge 수정 |
| `PROJECT_KNOWLEDGE_DELETED` | Project Knowledge 삭제 또는 비활성화 |
| `DOMAIN_TERM_CHANGED` | 용어사전 등록/수정/삭제 |
| `AI_REQUESTED` | Meeting AI/Project AI/보고서/태스크/용어 설명 요청 |
| `LIVE_TOKEN_ISSUED` | LiveKit token 발급 |
| `MEETING_TRANSCRIPTION_STARTED` | STT 시작 |

## Endpoint Section Baseline

모든 endpoint 문서는 `.specify/templates/api-contract-template.md`와 `contracts/README.md`의 `Endpoint Template Rule`을 따른다. `Auth and Permissions`, `Data Scope`, `Errors`, `Audit`, `Requirement Trace`는 비어 있으면 안 된다.
