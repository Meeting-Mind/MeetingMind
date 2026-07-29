package com.meetingmind.demo.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
class JpaSharedGlossaryStore implements SharedGlossaryStore {

    // 구독 행이 없는 기존 Space는 모든 분야, 행이 있는 Space는 enabled=true 분야만 구독한다.
    // 여러 분야에 같은 용어가 있으면 display_order가 앞선 분야를 고른다.
    private static final String FIND_SUBSCRIBED_ACTIVE_EXACT = """
            select t.id, t.term, t.definition, c.slug, c.name
            from shared_domain_terms t
            join glossary_categories c on c.id = t.category_id and c.status = 'ACTIVE'
            where t.status = 'ACTIVE'
              and lower(t.term) = :term
              and (
                  not exists (
                      select 1 from space_glossary_categories configured
                      where configured.space_id = :spaceId
                  )
                  or exists (
                      select 1 from space_glossary_categories selected
                      where selected.space_id = :spaceId
                        and selected.category_id = c.id
                        and selected.enabled = true
                  )
              )
            order by c.display_order, c.id
            limit 1
            """;

    private static final String FIND_ACTIVE_CATEGORIES = """
            select id, slug, name, description, display_order
            from glossary_categories
            where status = 'ACTIVE'
            order by display_order, id
            """;

    private static final String FIND_SUBSCRIBED_ACTIVE = """
            select t.id, t.term, t.definition, c.id, c.name, t.updated_at
            from shared_domain_terms t
            join glossary_categories c on c.id = t.category_id and c.status = 'ACTIVE'
            where t.status = 'ACTIVE'
              and (lower(t.term) like :keyword
                   or lower(t.definition) like :keyword)
              and (
                  not exists (
                      select 1 from space_glossary_categories configured
                      where configured.space_id = :spaceId
                  )
                  or exists (
                      select 1 from space_glossary_categories selected
                      where selected.space_id = :spaceId
                        and selected.category_id = c.id
                        and selected.enabled = true
                  )
              )
            order by c.display_order, c.id, lower(t.term), t.id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<SharedGlossaryMatch> findSubscribedActiveExact(String spaceId, String normalizedTerm) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(FIND_SUBSCRIBED_ACTIVE_EXACT)
                .setParameter("term", normalizedTerm)
                .setParameter("spaceId", spaceId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(new SharedGlossaryMatch(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4]
        ));
    }

    @Override
    public List<GlossaryCategory> findActiveCategories() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(FIND_ACTIVE_CATEGORIES).getResultList();
        return rows.stream().map(row -> new GlossaryCategory(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).intValue()
        )).toList();
    }

    @Override
    public List<SharedGlossaryTerm> findSubscribedActive(String spaceId, String normalizedKeyword) {
        String keyword = normalizedKeyword == null ? "%" : "%" + normalizedKeyword + "%";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(FIND_SUBSCRIBED_ACTIVE)
                .setParameter("spaceId", spaceId)
                .setParameter("keyword", keyword)
                .getResultList();
        return rows.stream().map(row -> new SharedGlossaryTerm(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                toInstant(row[5])
        )).toList();
    }

    @Override
    public void configureSpaceCategories(
            String spaceId,
            String actorUserId,
            List<String> selectedCategoryIds,
            List<String> customCategoryNames,
            Instant now
    ) {
        entityManager.createNativeQuery("delete from space_glossary_categories where space_id = :spaceId")
                .setParameter("spaceId", spaceId)
                .executeUpdate();
        for (GlossaryCategory category : findActiveCategories()) {
            entityManager.createNativeQuery("""
                            insert into space_glossary_categories
                                (space_id, category_id, enabled, updated_at, updated_by_user_id)
                            values (:spaceId, :categoryId, :enabled, :updatedAt, :actorUserId)
                            """)
                    .setParameter("spaceId", spaceId)
                    .setParameter("categoryId", category.id())
                    .setParameter("enabled", selectedCategoryIds.contains(category.id()))
                    .setParameter("updatedAt", now)
                    .setParameter("actorUserId", actorUserId)
                    .executeUpdate();
        }

        entityManager.createNativeQuery("delete from space_custom_glossary_categories where space_id = :spaceId")
                .setParameter("spaceId", spaceId)
                .executeUpdate();
        for (String customName : customCategoryNames) {
            entityManager.createNativeQuery("""
                            insert into space_custom_glossary_categories
                                (id, space_id, name, created_at, created_by_user_id)
                            values (:id, :spaceId, :name, :createdAt, :actorUserId)
                            """)
                    .setParameter("id", "space-glossary-custom-" + UUID.randomUUID())
                    .setParameter("spaceId", spaceId)
                    .setParameter("name", customName)
                    .setParameter("createdAt", now)
                    .setParameter("actorUserId", actorUserId)
                    .executeUpdate();
        }
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((Timestamp) value).toInstant();
    }
}
