alter table meetings
    add column description text,
    add column scheduled_end_at timestamptz;

update meetings
set scheduled_end_at = scheduled_at + interval '1 hour'
where scheduled_end_at is null;

alter table meetings
    alter column scheduled_end_at set not null,
    add constraint meetings_scheduled_range_check check (scheduled_end_at > scheduled_at);
