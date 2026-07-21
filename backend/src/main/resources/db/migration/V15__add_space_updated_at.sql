alter table spaces
    add column updated_at timestamptz not null default now();

create index ix_spaces_active_updated_at
    on spaces (updated_at desc)
    where deleted_at is null;
