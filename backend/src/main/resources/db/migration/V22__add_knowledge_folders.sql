create table knowledge_folders (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    name varchar(120) not null,
    created_by varchar(64) not null references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint knowledge_folders_name_not_blank check (length(trim(name)) > 0),
    constraint ux_knowledge_folders_space_name unique (space_id, name)
);

create table knowledge_folder_nodes (
    folder_id varchar(64) not null references knowledge_folders(id) on delete cascade,
    knowledge_id varchar(64) not null references project_knowledge(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (folder_id, knowledge_id)
);

create index ix_knowledge_folders_space on knowledge_folders(space_id);
create index ix_knowledge_folder_nodes_knowledge on knowledge_folder_nodes(knowledge_id);
