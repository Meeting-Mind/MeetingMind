revoke all
    on auth_users,
       auth_identities,
       auth_sessions,
       auth_refresh_credentials,
       session_audits,
       auth_outbox_events
    from meetingmind_auth_app;

alter default privileges in schema public
    revoke select, insert, update, delete on tables from meetingmind_auth_app;

grant select, insert, update
    on auth_users,
       auth_identities,
       auth_sessions,
       auth_refresh_credentials,
       auth_outbox_events
    to meetingmind_auth_app;

grant select, insert
    on session_audits
    to meetingmind_auth_app;

revoke all on table flyway_schema_history from meetingmind_auth_app;
revoke create on schema public from meetingmind_auth_app;
