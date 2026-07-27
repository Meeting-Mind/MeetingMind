do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant select, insert on table ai_usage_events to meetingmind_core_app;
    end if;
end
$$;

comment on table ai_usage_events is
    'Core-owned AI usage ledger; the dedicated Core runtime role has append and read access only.';
