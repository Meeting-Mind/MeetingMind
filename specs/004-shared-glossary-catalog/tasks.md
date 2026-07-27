# Tasks: 분야별 공용 용어 사전

owner: 사용자 / agent: Claude Code (단일 workstream)

## Milestone 1 — 공용 사전 스키마와 데이터

검증 목표: 마이그레이션이 순서대로 적용되고 분야별 용어가 조회된다.

- [x] **T1. 표준 용어 확정** (docs)
  - 파일: `requirements/glossary.md`
  - `SharedDomainTerm`, `GlossaryCategory`, `SpaceGlossaryCategory` 추가. `DomainTerm` 행에 우선순위 규칙 반영.
  - 완료 기준: 신규 DB/API 이름이 요구사항 기준선에 등재된다.

- [x] **T2. 스키마 마이그레이션** (data, shared contract)
  - 파일: `db/migration/V30__create_shared_glossary_catalog.sql`
  - 테이블 3개, 제약조건, 인덱스, `meetingmind_core_app` 권한.
  - 완료 기준: V1~V30이 PostgreSQL에 순서대로 적용된다.

- [x] **T3. 시드 데이터** (data)
  - 파일: `db/migration/V32__seed_shared_glossary_terms.sql`
  - dependency: T2
  - 분야 9개, 용어 184개. `on conflict do nothing`으로 멱등 처리.
  - 완료 기준: 분야별 용어 수가 의도대로 적재된다.

## Milestone 2 — 조회 연동

검증 목표: 구독 분야의 용어는 LLM 없이 답하고, 미구독 분야는 폴백한다.

- [x] **T4. 조회 store** (backend)
  - 파일: `domain/SharedGlossaryStore.java`, `domain/JpaSharedGlossaryStore.java`, `domain/InMemorySharedGlossaryStore.java`
  - dependency: T2
  - 완료 기준: 구독 필터가 SQL 조건에 포함된다.

- [x] **T5. 조회 경로 확장** (backend)
  - 파일: `service/MeetingTermExplanationService.java`
  - dependency: T4
  - Space 용어 → 공용 사전 → AI 폴백 3단계.
  - 완료 기준: 공용 사전 히트 시 게이트웨이가 호출되지 않는다.

- [x] **T6. 테스트** (test)
  - 파일: `domain/MeetingTermExplanationServiceTest.java`
  - dependency: T5
  - 완료 기준: 3가지 경로가 각각 검증된다.

## 검증 결과 (2026-07-27)

| 항목 | 명령 | 결과 |
| --- | --- | --- |
| 백엔드 테스트 | `cd backend && ./gradlew test --rerun-tasks` | 233 tests, 0 failures, 15 skipped |
| 용어 조회 테스트 | `--tests "*MeetingTermExplanationServiceTest*"` | 6 tests 통과 |
| 마이그레이션 | psql로 V1~V32 순차 적용 (pgvector/pgvector:0.8.2-pg16) | 32개 전부 성공 |
| 시드 데이터 | `select count(*) from shared_domain_terms` | 184개, 분야별 수 일치 |
| 구독 필터 | 분야 비활성화 전후 조회 쿼리 비교 | 활성 시 조회됨, 비활성 시 결과 없음 |

skip된 15개는 `CI_POSTGRES_URL` 등 외부 자원이 필요한 통합 테스트다. `MigrationIntegrationTest`가 여기 포함되므로 마이그레이션은 컨테이너를 직접 띄워 psql로 검증했다.

## 남은 작업

- [ ] **T7. 시드 용어 검수** — 정의는 초안이다. 특히 의료·건축·금융은 도메인 담당자 확인이 필요하다.
- [ ] **T8. 관리자 CRUD API** — 현재 데이터 투입 경로는 마이그레이션뿐이다.
- [ ] **T9. 구독 설정 API·화면** — 스키마만 준비됨. 기본값(전체 구독)으로만 동작한다.
- [ ] **T10. 동의어 매칭** — `FR-TERM-02`가 요구하나 현재는 완전 일치만 지원한다.
- [ ] **T11. 공용 용어 embedding 색인** — `SourceType.GLOSSARY` 생성 경로 없음.
