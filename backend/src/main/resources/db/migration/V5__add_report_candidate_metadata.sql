alter table meeting_reports
    add column created_by varchar(64) references users(id),
    add column source_ids jsonb not null default '[]'::jsonb;

alter table meeting_reports
    add constraint meeting_reports_source_ids_array
        check (jsonb_typeof(source_ids) = 'array');

create index ix_meeting_reports_created_by
    on meeting_reports (created_by);
