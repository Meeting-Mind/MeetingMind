# Plan: 분야별 공용 용어 사전

## 기술 결정

### 1. 별도 테이블 vs domain_terms 통합

`shared_domain_terms`를 신설했다. `domain_terms`에 `space_id`를 nullable로 바꾸고 `category_id`를 추가하는 통합안도 검토했으나 다음 이유로 제외했다.

- `ux_domain_terms_active_term`이 `(space_id, lower(term))` 유니크라, `space_id`가 NULL이 되면 전역 용어끼리의 유일성 판정이 깨진다.
- Space 등록 용어와 관리자 공용 용어는 쓰기 권한 주체가 다르다. 한 테이블이면 런타임 역할에 공용 용어 쓰기 권한까지 열어야 한다.
- 기존 `DomainTermService`, `DomainTermController`, DTO 5종이 `space_id` NOT NULL을 전제한다. 통합하면 이 경로 전체를 함께 수정해야 한다.

### 2. JPA 엔티티 없이 네이티브 쿼리

이번 범위는 조회 한 건뿐이고 공용 용어 CRUD는 비범위다. 엔티티 3개를 추가하면 `ddl-auto: validate` 대상이 늘어나는 대신 얻는 것이 없으므로, `EntityManager.createNativeQuery`로 조회하는 store 하나만 두었다. 관리자 CRUD를 붙일 때 엔티티 도입을 재검토한다.

### 3. Space 생성 경로를 건드리지 않음

구독 기본값을 "행 없음 = 구독 중"으로 정의해 `WorkspaceStore`, `JdbcWorkspaceStore`, `InMemoryWorkspaceStore`, `WorkspaceDomainService` 변경을 피했다. 이 파일들은 다른 작업에서 자주 수정되는 공유 지점이라 충돌 비용이 크다.

### 4. 마이그레이션 분리

스키마(V30)와 시드 데이터(V32)를 나눴다. 시드 데이터는 이후 용어 추가·수정 시 별도 마이그레이션이 이어지므로 스키마 정의와 섞지 않는다.

## 병렬 작업 기록

- 참여: 사용자 1명, 에이전트 1개(Claude Code). 단일 workstream.
- 같은 워킹 트리에서 다른 Codex 세션이 Meeting AI 대화 이력 기능(`V31__create_meeting_ai_messages.sql`, `MeetingAiHistoryStore` 등)을 병행 작업했다.
- 마이그레이션 번호가 `V31`로 충돌해, 본 작업의 시드 마이그레이션을 `V32`로 옮겼다. 상대 workstream 파일은 수정하지 않았다.
- 파일 경계: 본 작업은 `glossary`/`SharedGlossary` 접두 파일과 `MeetingTermExplanationService`만 수정한다.

## 변경 파일

| 영역 | 파일 | 성격 |
| --- | --- | --- |
| data | `db/migration/V30__create_shared_glossary_catalog.sql` | 신규 |
| data | `db/migration/V32__seed_shared_glossary_terms.sql` | 신규 |
| backend | `domain/SharedGlossaryStore.java` | 신규 |
| backend | `domain/JpaSharedGlossaryStore.java` | 신규 |
| backend | `domain/InMemorySharedGlossaryStore.java` | 신규 |
| backend | `service/MeetingTermExplanationService.java` | 조회 단계 추가 |
| test | `domain/MeetingTermExplanationServiceTest.java` | 테스트 3개 추가 |
| docs | `requirements/glossary.md` | 표준 용어 3개 추가 |

## 후속 작업

1. 관리자 공용 용어 CRUD API. 현재는 마이그레이션으로만 데이터를 넣는다.
2. Space 구독 설정 API와 화면. 스키마는 준비되어 있고 기본값으로만 동작한다.
3. 공용 용어의 `embedding_chunks` 색인. `SourceType.GLOSSARY`가 이미 있으나 생성 경로가 없다.
4. 동의어 매칭. `FR-TERM-02`가 "정확/동의어 매칭 규칙"을 요구하지만 현재는 완전 일치만 지원한다.
5. 시드 용어 정의 검수. 초안이므로 도메인 담당자 확인이 필요하다.
