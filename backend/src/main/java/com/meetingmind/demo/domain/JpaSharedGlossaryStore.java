package com.meetingmind.demo.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
class JpaSharedGlossaryStore implements SharedGlossaryStore {

    // 구독 행이 없는 Space는 모든 분야를 구독한 것으로 본다. 명시적으로 끈 분야만 제외한다.
    // 여러 분야에 같은 용어가 있으면 display_order가 앞선 분야를 고른다.
    private static final String FIND_SUBSCRIBED_ACTIVE_EXACT = """
            select t.id, t.term, t.definition, c.slug, c.name
            from shared_domain_terms t
            join glossary_categories c on c.id = t.category_id and c.status = 'ACTIVE'
            where t.status = 'ACTIVE'
              and lower(t.term) = :term
              and not exists (
                  select 1 from space_glossary_categories s
                  where s.space_id = :spaceId
                    and s.category_id = c.id
                    and s.enabled = false
              )
            order by c.display_order, c.id
            limit 1
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
}
