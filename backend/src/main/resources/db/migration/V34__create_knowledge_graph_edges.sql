create table knowledge_graph_edges (
    id varchar(128) primary key,
    space_id varchar(64) not null references spaces(id),
    from_node_id varchar(192) not null,
    to_node_id varchar(192) not null,
    similarity double precision not null default 0.5,
    created_at timestamptz not null default now(),
    constraint knowledge_graph_edges_from_not_blank check (length(trim(from_node_id)) > 0),
    constraint knowledge_graph_edges_to_not_blank check (length(trim(to_node_id)) > 0),
    constraint knowledge_graph_edges_distinct_nodes check (from_node_id < to_node_id),
    constraint knowledge_graph_edges_similarity_check check (similarity >= 0.0 and similarity <= 1.0),
    constraint ux_knowledge_graph_edges_space_nodes unique (space_id, from_node_id, to_node_id)
);

create index ix_knowledge_graph_edges_space
    on knowledge_graph_edges (space_id, from_node_id, to_node_id);

comment on table knowledge_graph_edges is
    'Knowledge 화면에 유지되는 Space 단위 무방향 연결. from/to는 정렬된 graph node ID다.';

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant select on table knowledge_graph_edges to meetingmind_core_app;
    end if;
end
$$;
