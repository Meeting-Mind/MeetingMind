# Implement: 분야별 공용 용어 사전

작업일: 2026-07-27 / 브랜치: `feat/glossary-seed-data` (base: `origin/dev`)

## 실제 작업 배정

단일 에이전트(Claude Code)가 T1~T6을 순차 수행했다. 병렬 분할은 하지 않았다.

## 충돌 처리

같은 워킹 트리에서 별도 Codex 세션이 Meeting AI 대화 이력 기능을 병행 작업했다. 작업 중 아래 파일이 외부에서 추가·수정됐다.

- `V29__grant_core_transcript_projection_delete.sql`
- `V31__create_meeting_ai_messages.sql`
- `MeetingAiMessage.java`, `MeetingAiHistoryStore` 3종
- `ReportCandidateService.java` 외 `ai/`, `frontend/`, `specs/001-*` 다수

조치
1. 마이그레이션 번호 `V31`이 충돌해, 본 작업의 시드 마이그레이션만 `V32`로 옮겼다. 상대 파일은 수정하지 않았다.
2. 작업 중 발생한 `ReportCandidateService.renderMarkdown` 컴파일 오류는 상대 세션의 편집 중 상태에서 비롯된 것으로, 본 변경과 무관하다. 상대 작업 종료 후 `--rerun-tasks`로 재검증해 해소를 확인했다.
3. 본 작업 시작 전 워킹 트리에 있던 미커밋 변경은 `stash@{0}`(`wip: workspace store + transcript reconciler before glossary work 2026-07-27`)에 보관했다. `V29`가 트리에 다시 나타났으므로, 이 stash를 pop할 때 `V29` 중복 여부를 먼저 확인해야 한다.

## 문서 갱신

`AGENTS.md`의 "Specs 변경 동반 갱신 규칙"에 따라 확인한 항목이다.

- `requirements/glossary.md` — 표준 용어 3개 추가. `DomainDictionary` 행이 이미 "Space 또는 전역 범위"를 허용하고 있어 기준선 충돌은 없었다.
- `requirements/INDEX.md` — 라우팅 변경 불필요. "용어 설명/용어 사전" 항목이 이미 `glossary.md`와 `FR-TERM-*`를 가리킨다.
- `specs/001-meetingmind-core/contracts/*` — 용어 설명 API의 요청·응답 형태는 바뀌지 않았다. `TermExplanationResponse`의 `model` 값에 `shared-glossary`가 추가됐을 뿐 계약 구조는 동일하다. 별도 계약 문서 갱신은 하지 않았다.

## 검증

`tasks.md`의 검증 결과 표 참조. 요약하면 백엔드 테스트 233개 통과(실패 0), 마이그레이션 V1~V32 순차 적용 성공, 시드 184개 적재 확인, 구독 필터 동작 확인.

미실행 항목
- `MigrationIntegrationTest`는 `CI_POSTGRES_URL` 환경변수가 없어 skip됐다. 대신 `pgvector/pgvector:0.8.2-pg16-bookworm` 컨테이너를 띄워 psql로 전 마이그레이션을 직접 적용해 검증했다. 검증 후 컨테이너는 제거했다.
- 프론트엔드 검증은 하지 않았다. 이번 변경에 프론트 파일이 없다.

## 남은 위험

- 시드 용어 184개의 정의는 초안이다. 사전은 내용이 틀리면 오히려 해가 되므로 도메인 담당자 검수 전에는 잠정으로 취급해야 한다.
- 관리자 CRUD와 구독 설정 경로가 없어, 현재 데이터 변경 수단은 마이그레이션뿐이다.
- 구독 기본값이 "전체 구독"이므로 분야별 분리의 정확도 이점은 사용자가 설정을 조정하기 전까지 발휘되지 않는다. 같은 약어가 여러 분야에 있으면 `display_order`가 앞선 분야의 정의가 선택된다.
