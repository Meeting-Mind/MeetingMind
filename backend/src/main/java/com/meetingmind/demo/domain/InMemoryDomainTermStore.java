package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !db")
class InMemoryDomainTermStore implements DomainTermStore {

    private final Map<String, DomainTerm> termsById = new LinkedHashMap<>();

    @Override
    public synchronized DomainTerm save(DomainTerm term) {
        termsById.put(term.id(), term);
        return term;
    }

    @Override
    public synchronized Optional<DomainTerm> findById(String spaceId, String termId) {
        return Optional.ofNullable(termsById.get(termId)).filter(term -> term.spaceId().equals(spaceId));
    }

    @Override
    public synchronized Optional<DomainTerm> findActiveExact(String spaceId, String normalizedTerm) {
        return termsById.values().stream()
                .filter(term -> term.spaceId().equals(spaceId))
                .filter(term -> term.status() == DomainTermStatus.ACTIVE)
                .filter(term -> term.term().trim().equalsIgnoreCase(normalizedTerm))
                .findFirst();
    }

    @Override
    public synchronized List<DomainTerm> findBySpaceId(String spaceId, DomainTermStatus status, String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return termsById.values().stream()
                .filter(term -> term.spaceId().equals(spaceId))
                .filter(term -> status == null || term.status() == status)
                .filter(term -> normalizedKeyword == null
                        || term.term().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || term.definition().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(DomainTerm::updatedAt).reversed().thenComparing(DomainTerm::id))
                .toList();
    }

    @Override
    public synchronized boolean existsActiveTerm(String spaceId, String normalizedTerm, String excludedTermId) {
        return termsById.values().stream().anyMatch(term -> term.spaceId().equals(spaceId)
                && term.status() == DomainTermStatus.ACTIVE
                && !term.id().equals(excludedTermId)
                && term.term().trim().equalsIgnoreCase(normalizedTerm));
    }

    @Override
    public void recordChange(String spaceId, String actorUserId, String termId, String beforeValue, String afterValue, Instant createdAt) {
        // Test profile keeps DomainTerm behavior independent of the Workspace audit implementation.
    }
}
