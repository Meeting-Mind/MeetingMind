create table transcript_segments (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    speaker_id varchar(64) not null references meeting_speakers(id),
    speaker_label varchar(80) not null,
    speaker_name varchar(100),
    sequence integer not null,
    start_ms integer not null,
    end_ms integer not null,
    text text not null,
    source varchar(32) not null default 'stt',
    constraint transcript_segments_speaker_label_not_blank check (length(trim(speaker_label)) > 0),
    constraint transcript_segments_text_not_blank check (length(trim(text)) > 0),
    constraint transcript_segments_sequence_check check (sequence >= 0),
    constraint transcript_segments_time_check check (start_ms >= 0 and end_ms >= start_ms),
    constraint transcript_segments_speaker_name_not_blank check (
        speaker_name is null or length(trim(speaker_name)) > 0
    )
);

create unique index ux_transcript_segments_meeting_sequence
    on transcript_segments (meeting_id, sequence);

create index ix_transcript_segments_meeting_start_ms
    on transcript_segments (meeting_id, start_ms);

create index ix_transcript_segments_speaker
    on transcript_segments (speaker_id);

create table meeting_reports (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    status varchar(32) not null default 'CANDIDATE',
    title varchar(200) not null,
    summary text not null,
    markdown text,
    version integer not null,
    is_current boolean not null default false,
    created_at timestamptz not null default now(),
    confirmed_at timestamptz,
    constraint meeting_reports_status_check check (status in ('CANDIDATE', 'DRAFT', 'CONFIRMED')),
    constraint meeting_reports_title_not_blank check (length(trim(title)) > 0),
    constraint meeting_reports_summary_not_blank check (length(trim(summary)) > 0),
    constraint meeting_reports_version_check check (version > 0),
    constraint meeting_reports_current_confirmed_check check (
        is_current = false or status = 'CONFIRMED'
    )
);

create unique index ux_meeting_reports_meeting_version
    on meeting_reports (meeting_id, version);

create unique index ux_meeting_reports_current_confirmed
    on meeting_reports (meeting_id)
    where status = 'CONFIRMED' and is_current = true;

create index ix_meeting_reports_meeting_status
    on meeting_reports (meeting_id, status);

create table report_decisions (
    id varchar(64) primary key,
    report_id varchar(64) not null references meeting_reports(id),
    decision_order integer not null,
    title varchar(200) not null,
    rationale text,
    source_ids jsonb not null default '[]'::jsonb,
    constraint report_decisions_order_check check (decision_order >= 0),
    constraint report_decisions_title_not_blank check (length(trim(title)) > 0),
    constraint report_decisions_source_ids_array check (jsonb_typeof(source_ids) = 'array')
);

create unique index ux_report_decisions_report_order
    on report_decisions (report_id, decision_order);

create table report_action_items (
    id varchar(64) primary key,
    report_id varchar(64) not null references meeting_reports(id),
    item_order integer not null,
    title varchar(200) not null,
    assignee_name varchar(100),
    due_date date,
    confirmation_state varchar(32) not null default 'candidate',
    source_ids jsonb not null default '[]'::jsonb,
    constraint report_action_items_order_check check (item_order >= 0),
    constraint report_action_items_title_not_blank check (length(trim(title)) > 0),
    constraint report_action_items_assignee_not_blank check (
        assignee_name is null or length(trim(assignee_name)) > 0
    ),
    constraint report_action_items_confirmation_state_check check (
        confirmation_state in ('candidate', 'confirmed', 'dismissed')
    ),
    constraint report_action_items_source_ids_array check (jsonb_typeof(source_ids) = 'array')
);

create unique index ux_report_action_items_report_order
    on report_action_items (report_id, item_order);
