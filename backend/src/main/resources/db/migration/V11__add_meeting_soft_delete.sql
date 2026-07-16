alter table meetings
    add column deleted_at timestamptz,
    add column deleted_by varchar(64) references users(id),
    add constraint meetings_soft_delete_metadata_check check (
        (deleted_at is null and deleted_by is null)
        or (deleted_at is not null and deleted_by is not null)
    );

create index ix_meetings_active_space_scheduled_at
    on meetings (space_id, scheduled_at)
    where deleted_at is null;

create index ix_meetings_deleted_at
    on meetings (deleted_at)
    where deleted_at is not null;
