alter table users
    add column auth_user_id uuid;

update users
set auth_user_id = substring(id from 6)::uuid
where id ~* '^user-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

alter table users
    add constraint users_auth_user_projection_check
    check (
        auth_user_id is null
        or id = 'user-' || auth_user_id::text
    );

create unique index ux_users_auth_user_id
    on users (auth_user_id)
    where auth_user_id is not null;
