-- NonProd 데이터 시드: 현재 RDS의 source-level embedding node와 통합 ACTIVE 용어 node를
-- Space별로 안정적인 의사 무작위 순서에 놓고 인접 노드를 연결한다. 재실행해도 같은
-- node pair가 생성되고 unique constraint + on conflict로 중복되지 않는다.
with embedded_nodes as (
    select distinct
        chunks.space_id,
        case
            when chunks.project_knowledge_id is not null
                then 'knowledge:' || chunks.project_knowledge_id
            else chunks.source_type || ':' || chunks.source_id
        end as node_id
    from embedding_chunks chunks
    join embedding_jobs jobs on jobs.id = chunks.embedding_job_id
    left join project_knowledge knowledge on knowledge.id = chunks.project_knowledge_id
    where chunks.is_active = true
      and jobs.status = 'COMPLETED'
      and chunks.embedding is not null
      and chunks.source_type in (
          'projectKnowledge', 'meetingSummary', 'decision', 'actionItem', 'report', 'glossary'
      )
      and (
          chunks.source_type <> 'projectKnowledge'
          or (knowledge.status = 'PUBLISHED' and knowledge.deleted_at is null)
      )
), space_terms as (
    select terms.space_id, 'glossary:' || terms.id as node_id
    from domain_terms terms
    join spaces on spaces.id = terms.space_id and spaces.deleted_at is null
    where terms.status = 'ACTIVE'
), shared_terms as (
    select spaces.id as space_id, 'glossary:' || terms.id as node_id
    from spaces
    join shared_domain_terms terms on terms.status = 'ACTIVE'
    join glossary_categories categories
      on categories.id = terms.category_id
     and categories.status = 'ACTIVE'
    where spaces.deleted_at is null
      and (
          not exists (
              select 1
              from space_glossary_categories configured
              where configured.space_id = spaces.id
          )
          or exists (
              select 1
              from space_glossary_categories selected
              where selected.space_id = spaces.id
                and selected.category_id = categories.id
                and selected.enabled = true
          )
      )
      and not exists (
          select 1
          from domain_terms overridden
          where overridden.space_id = spaces.id
            and overridden.status = 'ACTIVE'
            and lower(overridden.term) = lower(terms.term)
      )
), available_nodes as (
    select space_id, node_id from embedded_nodes
    union
    select space_id, node_id from space_terms
    union
    select space_id, node_id from shared_terms
), ordered_nodes as (
    select
        space_id,
        node_id,
        lead(node_id) over (
            partition by space_id
            order by md5(space_id || ':' || node_id), node_id
        ) as next_node_id
    from available_nodes
), seeded_edges as (
    select
        space_id,
        least(node_id, next_node_id) as from_node_id,
        greatest(node_id, next_node_id) as to_node_id
    from ordered_nodes
    where next_node_id is not null
      and node_id <> next_node_id
)
insert into knowledge_graph_edges (
    id, space_id, from_node_id, to_node_id, similarity
)
select
    'graph-edge-' || md5(space_id || ':' || from_node_id || ':' || to_node_id),
    space_id,
    from_node_id,
    to_node_id,
    0.5
from seeded_edges
on conflict (space_id, from_node_id, to_node_id) do nothing;
