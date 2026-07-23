-- STT owns these tables independently of Core's database. meeting_id is an
-- external identifier only (no FK to a local meetings table — Core owns that).

create table meeting_speakers (
    id varchar(64) primary key,
    meeting_id varchar(64) not null,
    label varchar(80) not null,
    display_name varchar(100),
    created_at timestamptz not null default now(),
    constraint meeting_speakers_label_not_blank check (length(trim(label)) > 0),
    constraint meeting_speakers_display_name_not_blank check (
        display_name is null or length(trim(display_name)) > 0
    )
);

create unique index ux_meeting_speakers_meeting_label
    on meeting_speakers (meeting_id, label);

create table meeting_transcripts (
    meeting_id varchar(64) primary key,
    status varchar(32) not null default 'PENDING',
    provider varchar(64),
    language varchar(35),
    started_at timestamptz,
    completed_at timestamptz,
    failure_reason text,
    retention_until timestamptz,
    legal_hold boolean not null default false,
    purged_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint meeting_transcripts_status_check check (
        status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    constraint meeting_transcripts_provider_not_blank check (
        provider is null or length(trim(provider)) > 0
    ),
    constraint meeting_transcripts_language_not_blank check (
        language is null or length(trim(language)) > 0
    ),
    constraint meeting_transcripts_started_at_check check (
        started_at is null or started_at >= created_at
    ),
    constraint meeting_transcripts_completed_at_check check (
        completed_at is null or started_at is null or completed_at >= started_at
    ),
    constraint meeting_transcripts_purged_at_check check (
        purged_at is null or completed_at is null or purged_at >= completed_at
    )
);

create index ix_meeting_transcripts_status
    on meeting_transcripts (status, updated_at);

create index ix_meeting_transcripts_retention_due
    on meeting_transcripts (retention_until)
    where retention_until is not null and legal_hold = false and purged_at is null;

create table transcript_segments (
    id varchar(64) primary key,
    meeting_id varchar(64) not null,
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

-- Durable session metadata (see SttSessionRegistry) — the live gRPC stream
-- object stays in the owning Pod's memory; this row is what lets a stop
-- request or a restart-reconciliation sweep find/close a session anywhere.
create table transcription_sessions (
    session_id varchar(64) primary key,
    meeting_id varchar(64) not null,
    room_name varchar(200) not null,
    track_id varchar(200),
    egress_id varchar(200),
    status varchar(32) not null default 'ACTIVE',
    request_id varchar(200),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint transcription_sessions_status_check check (
        status in ('ACTIVE', 'STOPPING', 'COMPLETED', 'FAILED')
    )
);

create unique index ux_transcription_sessions_request_id
    on transcription_sessions (request_id)
    where request_id is not null;

create index ix_transcription_sessions_meeting_status
    on transcription_sessions (meeting_id, status);

-- Outbox for TranscriptCompleted/TranscriptFailed/TranscriptPurged/SpeakerUpdated
-- (see 이벤트 경계). Publishing is out of scope for this step — schema placeholder only.
create table transcript_outbox (
    id varchar(64) primary key,
    event_type varchar(64) not null,
    meeting_id varchar(64) not null,
    payload jsonb not null,
    occurred_at timestamptz not null default now(),
    published_at timestamptz
);

create index ix_transcript_outbox_unpublished
    on transcript_outbox (occurred_at)
    where published_at is null;
