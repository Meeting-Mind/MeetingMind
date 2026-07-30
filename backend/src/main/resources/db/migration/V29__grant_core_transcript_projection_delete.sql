do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant delete on table transcript_segments, meeting_speakers to meetingmind_core_app;
    end if;
end
$$;

comment on table transcript_segments is
    'Meeting transcript projection; Core may replace a meeting snapshot atomically.';

comment on table meeting_speakers is
    'Meeting speaker projection; Core may replace speakers with an authoritative STT snapshot.';
