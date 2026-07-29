# Data Model: 분야별 공용 용어 사전

기존 `domain_terms`는 변경하지 않는다. 아래 3개 테이블을 신설한다.

## glossary_categories

공용 용어를 묶는 업무 분야 카탈로그. 관리자가 관리한다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | `glossary-category-<slug>` |
| slug | varchar(64) | NOT NULL, UNIQUE | 소문자·숫자·하이픈만 허용 |
| name | varchar(100) | NOT NULL | 화면 표시명 |
| description | text | 공백 불가 | 분야 설명 |
| display_order | integer | NOT NULL, default 0 | 값이 작을수록 우선 |
| status | varchar(32) | ACTIVE / ARCHIVED | |
| created_at, updated_at | timestamptz | NOT NULL | |

인덱스
- `ux_glossary_categories_slug` — slug 유일
- `ix_glossary_categories_active_order` — `(display_order, id)`, `status = 'ACTIVE'` 부분 인덱스

## shared_domain_terms

전역 공용 용어. Space를 소유자로 갖지 않는다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | `shared-term-<slug>-<seq>` |
| category_id | varchar(64) | NOT NULL, FK → glossary_categories | |
| term | varchar(200) | NOT NULL, 공백 불가 | |
| definition | text | NOT NULL, 공백 불가 | |
| status | varchar(32) | ACTIVE / ARCHIVED | |
| created_at, updated_at | timestamptz | NOT NULL | |
| archived_at | timestamptz | 상태와 정합 | ACTIVE면 NULL, ARCHIVED면 NOT NULL |

인덱스
- `ux_shared_domain_terms_active_term` — `(category_id, lower(term))`, `status = 'ACTIVE'` 부분 유니크. `domain_terms`와 같은 규칙으로, 분야 안에서 활성 용어는 대소문자 무시 유일하되 보관된 동명 용어는 남길 수 있다.
- `ix_shared_domain_terms_active_lookup` — `(lower(term), category_id)`, 조회가 항상 완전 일치이므로 이 형태로 둔다.

## space_glossary_categories

Space별 분야 구독 상태.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| space_id | varchar(64) | PK, FK → spaces | |
| category_id | varchar(64) | PK, FK → glossary_categories | |
| enabled | boolean | NOT NULL | |
| updated_at | timestamptz | NOT NULL | |
| updated_by_user_id | varchar(64) | FK → users | |

**해당 Space의 행이 하나도 없으면 전체 분야를 구독 중으로 해석한다.** 행이 하나라도 있으면 `enabled = true`인 분야만 구독한다. 이 구분으로 기존 Space는 전체 구독을 유지하고, 명시적으로 선택한 신규 Space는 이후 추가되는 전역 분야가 자동 구독되지 않는다.

기존 Space는 백필 없이 기본값(전체 구독)을 얻는다. 신규 Space는 활성 분야 전체에 대해 true/false 행을 저장하므로 선택 범위와 전부 끈 상태를 모두 표현할 수 있다.

기존 Space는 구독 행이 없으면 전체 구독으로 해석한다. 신규 Space가 생성 요청에 `glossaryCategoryIds`를 명시하면 모든 활성 분야에 대해 선택 여부를 `enabled`로 저장한다.

## space_custom_glossary_categories (V33)

Space 생성 시 `기타`로 입력한 사용자 정의 분야다. 전역 공용 카탈로그에는 포함되지 않는다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | `space-glossary-custom-<uuid>` |
| space_id | varchar(64) | NOT NULL, FK → spaces | |
| name | varchar(100) | NOT NULL, 공백 불가 | Space 표시용 분야명 |
| created_at | timestamptz | NOT NULL | |
| created_by_user_id | varchar(64) | NOT NULL, FK → users | |

- `ux_space_custom_glossary_category_name` — `(space_id, lower(name))` unique.
- 사용자 정의 분야는 해당 Space 분류 정보이며, 연결된 `shared_domain_terms`가 없으므로 공용 용어 조회 범위를 넓히지 않는다.

## 조회 쿼리

```sql
select t.id, t.term, t.definition, c.slug, c.name
from shared_domain_terms t
join glossary_categories c on c.id = t.category_id and c.status = 'ACTIVE'
where t.status = 'ACTIVE'
  and lower(t.term) = :term
  and (
      not exists (
          select 1 from space_glossary_categories configured
          where configured.space_id = :spaceId
      )
      or exists (
          select 1 from space_glossary_categories selected
          where selected.space_id = :spaceId
            and selected.category_id = c.id
            and selected.enabled = true
      )
  )
order by c.display_order, c.id
limit 1
```

## 런타임 권한

`meetingmind_core_app` 역할에 부여하는 권한은 V26~V28의 최소 권한 패턴을 따른다.

| 테이블 | 권한 | 이유 |
| --- | --- | --- |
| glossary_categories | select | 카탈로그 관리는 런타임 작업이 아니다 |
| shared_domain_terms | select | 용어 편집은 마이그레이션 또는 후속 관리자 경로로만 한다 |
| space_glossary_categories | select, insert, update, delete | Space가 구독을 직접 바꾼다 |
| space_custom_glossary_categories | select, insert, update, delete | Space 생성 시 직접 입력한 기타 분야를 해당 Space 범위에서 관리한다 |

## 시드 데이터 (V32)

| 분야 | slug | display_order | 용어 수 |
| --- | --- | --- | --- |
| 공통 비즈니스 | common-business | 10 | 20 |
| IT/소프트웨어 | it-software | 20 | 24 |
| 마케팅/영업 | marketing-sales | 30 | 20 |
| 금융 | finance | 40 | 20 |
| 의료 | healthcare | 50 | 20 |
| 연구 | research | 60 | 20 |
| 교육 | education | 70 | 20 |
| 건축 | construction | 80 | 20 |
| 패션/리테일 | fashion-retail | 90 | 20 |

합계 184개. 분야 무관 용어인 `공통 비즈니스`를 `display_order` 최상위에 둬서, 여러 분야에 중복된 용어는 일반적인 정의가 먼저 선택되게 한다.
