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

## 확장 구현: Space 분야 선택과 공용 사전 노출 (2026-07-27)

### 구현 결과

- Space 생성 요청에 `glossaryCategoryIds`와 `customGlossaryCategories`를 추가했다. 두 필드가 생략된 기존 클라이언트는 전체 분야 구독을 유지한다.
- 생성 화면의 두 진입점에서 공용 분야를 checkbox로 여러 개 선택하고 `기타 (직접 입력)`을 사용할 수 있다. 새 UI는 공통 비즈니스를 기본 선택한다.
- 서버는 중복/알 수 없는 category ID, 빈·100자 초과 기타 입력, 대소문자 무시 기타 중복, 기존 카테고리명과의 중복을 거부한다.
- V33에서 `space_custom_glossary_categories`를 추가했다. 사용자 입력은 Space 범위로만 저장하고 전역 `glossary_categories`에는 추가하지 않는다.
- 기존 Space는 구독 행이 없으면 전체 분야를 사용한다. 명시적으로 설정된 Space는 `enabled=true` 분야만 사용하므로 향후 신규 전역 분야가 자동 구독되지 않는다.
- `GET /api/v1/glossary/categories`와 BFF allowlist를 추가했다.
- Space 용어 목록은 Space 등록 용어를 우선하고 구독 공용 용어를 읽기 전용으로 합친다. 같은 용어가 양쪽에 있으면 Space 항목 하나만 반환한다.
- 용어사전 화면은 공용 분야 badge와 읽기 전용 설명을 표시한다. Live Transcript는 같은 ACTIVE 통합 목록을 사용하므로 공용 용어도 하이라이트·클릭 설명 대상이 된다.

### 문서 영향 확인

- `requirements/glossary.md`, `functional-requirements-detail.md`: 사용자 정의 분야와 생성/노출 요구를 반영했다.
- `requirements/INDEX.md`: 기존 프로젝트/Space 및 용어사전 라우팅이 필요한 문서를 이미 가리켜 변경하지 않았다.
- `requirements/permissions.md`, `status-values.md`: 기존 Space 접근 권한과 ACTIVE/ARCHIVED 상태를 그대로 사용해 변경하지 않았다.
- `space-api.md`, `knowledge-api.md`, `data-model.md`, `erd.md`: 요청/응답과 V33 관계를 함께 갱신했다.
- `analyze.md`: 기존 검증 이슈의 상태를 바꾸지 않아 갱신하지 않았다.

### 검증

- Backend 전체 248 tests 중 실패 0, 환경 의존 18개 skip.
- 테스트 전용 PostgreSQL에서 V1~V33 migration과 `space_custom_glossary_categories` runtime 권한을 확인했다.
- 실제 JPA 경로에서 금융만 선택한 Space는 `ROE`를 반환하고 공통 비즈니스 `KPI`는 제외하며, `반도체 설계` 기타 분야가 저장되는 것을 확인했다.
- BFF route test 통과.
- Frontend build 및 101 unit tests 통과.
- Frontend lint 오류 0. 기존 `shellContext` hook dependency와 `TranscriptTerm` fast-refresh 경고 2개는 이번 변경과 무관해 유지했다.
- `git diff --check` 통과.

### 남은 범위

- 생성 후 Space 설정 화면에서 분야 선택을 다시 바꾸는 UI/API는 아직 없다.
- 사용자 정의 분야는 분류 정보만 저장하며, 해당 분야의 공용 용어를 자동 생성하지 않는다.

## 확장 구현: 용어사전 Knowledge 노드 (2026-07-27)

### 구현 결과

- `DomainTermCatalogService`를 추가해 Space 등록 용어 우선, 구독 공용 용어 병합, ACTIVE/ARCHIVED 처리 규칙을 Controller 밖의 공통 읽기 경계로 이동했다.
- 용어 목록 API와 Knowledge graph가 같은 통합 카탈로그를 사용하므로 동명 공용 용어가 graph에서 다시 나타나지 않는다.
- Core Knowledge graph 응답에 안정적인 `glossary:<termId>` 노드를 추가했다. 노드 제목은 용어명, additive `description`은 정의이며 embedding 전 상태는 `null`이다.
- `nodeTypes=GLOSSARY` 필터를 추가했다. 기존 Project scope 권한 검사를 먼저 통과한 뒤 Space의 통합 용어를 읽는다.
- Frontend graph view model이 `GLOSSARY`를 용어 그룹으로 매핑하고 선택 패널에서 정의를 표시한다. 기존 단일 노드 표시 기본값을 사용하므로 아직 edge가 없는 용어도 보인다.

### 계약·데이터 영향

- `knowledge-api.md`에 `GLOSSARY`, `description`, `embeddingStatus=null` 규칙을 추가했다.
- 저장 테이블, ERD, migration은 바뀌지 않았다. 이번 구현은 기존 `domain_terms`와 `shared_domain_terms`를 합치는 graph read model 확장이다.
- 용어 embedding과 의미 유사도 edge, Project AI RAG 편입은 T11 범위로 남겼다. 현재 노드를 검색 가능하다고 오인하지 않도록 `COMPLETED` 상태를 부여하지 않는다.

### 검증

- Backend 관련 테스트와 전체 테스트를 실행했다. 전체 250건 중 실패 0, 환경 의존 18건 skip이다.
- Frontend unit은 10 files/102 tests, production build는 통과했다.
- Frontend lint는 오류 0이며 기존 `App.tsx` hook dependency와 `TranscriptTerm.tsx` fast-refresh 경고 2개만 남았다.
- `git diff --check`를 통과했다.
