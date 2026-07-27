-- 관리자가 운영하는 분야별 공용 용어 사전(SharedDomainDictionary).
-- Space가 직접 등록하는 domain_terms와 분리해 저장하고, Space는 분야 단위로 구독을 조정한다.

create table glossary_categories (
    id varchar(64) primary key,
    slug varchar(64) not null,
    name varchar(100) not null,
    description text,
    display_order integer not null default 0,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint glossary_categories_slug_not_blank check (length(trim(slug)) > 0),
    constraint glossary_categories_slug_format check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint glossary_categories_name_not_blank check (length(trim(name)) > 0),
    constraint glossary_categories_description_not_blank check (
        description is null or length(trim(description)) > 0
    ),
    constraint glossary_categories_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

create unique index ux_glossary_categories_slug
    on glossary_categories (slug);

create index ix_glossary_categories_active_order
    on glossary_categories (display_order, id)
    where status = 'ACTIVE';

create table shared_domain_terms (
    id varchar(64) primary key,
    category_id varchar(64) not null references glossary_categories(id),
    term varchar(200) not null,
    definition text not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    archived_at timestamptz,
    constraint shared_domain_terms_term_not_blank check (length(trim(term)) > 0),
    constraint shared_domain_terms_definition_not_blank check (length(trim(definition)) > 0),
    constraint shared_domain_terms_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint shared_domain_terms_archived_at_check check (
        (status = 'ACTIVE' and archived_at is null)
        or (status = 'ARCHIVED' and archived_at is not null)
    )
);

-- domain_terms와 같은 규칙: 활성 용어만 분야 안에서 대소문자 무시 유일하고, 보관된 동명 용어는 남길 수 있다.
create unique index ux_shared_domain_terms_active_term
    on shared_domain_terms (category_id, lower(term))
    where status = 'ACTIVE';

-- 용어 조회는 항상 lower(term) 완전 일치로 들어온다.
create index ix_shared_domain_terms_active_lookup
    on shared_domain_terms (lower(term), category_id)
    where status = 'ACTIVE';

-- 행이 없는 Space는 모든 분야를 구독한 것으로 해석한다.
-- Space가 구독을 저장하면 분야별로 행이 생기고, 전부 끄면 enabled = false 행만 남는다.
create table space_glossary_categories (
    space_id varchar(64) not null references spaces(id),
    category_id varchar(64) not null references glossary_categories(id),
    enabled boolean not null,
    updated_at timestamptz not null default now(),
    updated_by_user_id varchar(64) references users(id),
    primary key (space_id, category_id)
);

create index ix_space_glossary_categories_disabled
    on space_glossary_categories (space_id, category_id)
    where enabled = false;

comment on table glossary_categories is
    '관리자가 정의하는 공용 용어 사전의 분야 카탈로그.';

comment on table shared_domain_terms is
    'Space 소유가 아닌 전역 공용 용어. Space 자체 등록 용어는 domain_terms에 남는다.';

comment on table space_glossary_categories is
    'Space별 분야 구독 상태. 행이 없으면 해당 분야를 구독 중으로 본다.';

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant select on table glossary_categories to meetingmind_core_app;
        grant select on table shared_domain_terms to meetingmind_core_app;
        grant select, insert, update, delete on table space_glossary_categories to meetingmind_core_app;
    end if;
end
$$;
