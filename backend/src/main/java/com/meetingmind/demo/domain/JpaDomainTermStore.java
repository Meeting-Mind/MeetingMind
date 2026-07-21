package com.meetingmind.demo.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
class JpaDomainTermStore implements DomainTermStore {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public DomainTerm save(DomainTerm term) {
        return entityManager.merge(term);
    }

    @Override
    public Optional<DomainTerm> findById(String spaceId, String termId) {
        return first(entityManager.createQuery(
                        "select term from DomainTerm term where term.spaceId = :spaceId and term.id = :termId",
                        DomainTerm.class)
                .setParameter("spaceId", spaceId)
                .setParameter("termId", termId)
                .setMaxResults(1)
                .getResultList());
    }

    @Override
    public Optional<DomainTerm> findActiveExact(String spaceId, String normalizedTerm) {
        return first(entityManager.createQuery(
                        "select term from DomainTerm term where term.spaceId = :spaceId "
                                + "and term.status = 'ACTIVE' and lower(term.term) = :term "
                                + "order by term.updatedAt desc, term.id",
                        DomainTerm.class)
                .setParameter("spaceId", spaceId)
                .setParameter("term", normalizedTerm)
                .setMaxResults(1)
                .getResultList());
    }

    @Override
    public List<DomainTerm> findBySpaceId(String spaceId, DomainTermStatus status, String keyword) {
        return entityManager.createQuery(
                        "select term from DomainTerm term "
                                + "where term.spaceId = :spaceId "
                                + "and (:status is null or term.status = :status) "
                                + "and (:keyword is null or lower(term.term) like :keyword or lower(term.definition) like :keyword) "
                                + "order by term.updatedAt desc, term.id",
                        DomainTerm.class)
                .setParameter("spaceId", spaceId)
                .setParameter("status", status == null ? null : status.name())
                .setParameter("keyword", keyword == null ? null : "%" + keyword + "%")
                .getResultList();
    }

    @Override
    public boolean existsActiveTerm(String spaceId, String normalizedTerm, String excludedTermId) {
        return entityManager.createQuery(
                        "select count(term) from DomainTerm term where term.spaceId = :spaceId "
                                + "and term.status = 'ACTIVE' and lower(term.term) = :term "
                                + "and (:excludedTermId is null or term.id <> :excludedTermId)",
                        Long.class)
                .setParameter("spaceId", spaceId)
                .setParameter("term", normalizedTerm)
                .setParameter("excludedTermId", excludedTermId)
                .getSingleResult() > 0;
    }

    @Override
    public void recordChange(String spaceId, String actorUserId, String termId, String beforeValue, String afterValue, Instant createdAt) {
        AuditEvent audit = new AuditEvent();
        audit.id = "audit-" + java.util.UUID.randomUUID();
        audit.spaceId = spaceId;
        audit.actorUserId = actorUserId;
        audit.action = "DOMAIN_TERM_CHANGED";
        audit.targetType = "DOMAIN_TERM";
        audit.targetId = termId;
        audit.beforeValue = beforeValue == null ? null : Map.of("value", beforeValue);
        audit.afterValue = afterValue == null ? null : Map.of("value", afterValue);
        audit.createdAt = createdAt;
        entityManager.persist(audit);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }
}
