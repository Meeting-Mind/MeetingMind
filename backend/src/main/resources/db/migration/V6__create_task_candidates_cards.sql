create table task_candidates (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    title varchar(200) not null,
    assignee_name varchar(100),
    suggested_assignee_id varchar(64) references users(id),
    due_date date,
    status varchar(32) not null default 'CANDIDATE',
    source_ids jsonb not null default '[]'::jsonb,
    created_by varchar(64) not null references users(id),
    created_at timestamptz not null default now(),
    confirmed_at timestamptz,
    constraint task_candidates_title_not_blank check (length(trim(title)) > 0),
    constraint task_candidates_assignee_not_blank check (
        assignee_name is null or length(trim(assignee_name)) > 0
    ),
    constraint task_candidates_status_check check (
        status in ('CANDIDATE', 'CONFIRMED', 'DISMISSED')
    ),
    constraint task_candidates_source_ids_array check (jsonb_typeof(source_ids) = 'array'),
    constraint task_candidates_confirmed_at_check check (
        (status = 'CONFIRMED' and confirmed_at is not null)
        or (status <> 'CONFIRMED' and confirmed_at is null)
    )
);

create index ix_task_candidates_meeting_status
    on task_candidates (meeting_id, status);

create index ix_task_candidates_created_by
    on task_candidates (created_by);

create table task_cards (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    meeting_id varchar(64) references meetings(id),
    source_candidate_id varchar(64) unique references task_candidates(id),
    title varchar(200) not null,
    description text,
    status varchar(32) not null default 'TODO',
    assignee_id varchar(64) references users(id),
    due_date date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint task_cards_title_not_blank check (length(trim(title)) > 0),
    constraint task_cards_status_check check (status in ('TODO', 'IN_PROGRESS', 'DONE'))
);

create index ix_task_cards_space_status
    on task_cards (space_id, status);

create index ix_task_cards_assignee_status
    on task_cards (assignee_id, status);
