create table space_custom_glossary_categories (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    name varchar(100) not null,
    created_at timestamptz not null default now(),
    created_by_user_id varchar(64) not null references users(id),
    constraint space_custom_glossary_category_name_not_blank check (length(trim(name)) > 0)
);

create unique index ux_space_custom_glossary_category_name
    on space_custom_glossary_categories (space_id, lower(name));

create index ix_space_custom_glossary_category_space
    on space_custom_glossary_categories (space_id, created_at);

comment on table space_custom_glossary_categories is
    'Space 생성 시 기타로 직접 입력한 사용자 정의 용어 분야. 전역 공용 카탈로그에는 포함되지 않는다.';

comment on table space_glossary_categories is
    'Space별 분야 선택. Space 행이 하나도 없으면 전체 구독, 행이 있으면 enabled=true 분야만 구독한다.';

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant select, insert, update, delete on table space_custom_glossary_categories to meetingmind_core_app;
    end if;
end
$$;
