package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DomainTermStore {
    DomainTerm save(DomainTerm term);

    Optional<DomainTerm> findById(String spaceId, String termId);

    Optional<DomainTerm> findActiveExact(String spaceId, String normalizedTerm);

    List<DomainTerm> findBySpaceId(String spaceId, DomainTermStatus status, String keyword);

    boolean existsActiveTerm(String spaceId, String normalizedTerm, String excludedTermId);

    void recordChange(String spaceId, String actorUserId, String termId, String beforeValue, String afterValue, Instant createdAt);
}
