package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainTermService {

    private final DomainTermStore store;
    private final WorkspaceDomainService workspaceDomainService;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final Clock clock;

    @Autowired
    public DomainTermService(
            DomainTermStore store,
            WorkspaceDomainService workspaceDomainService,
            SpaceAccessPolicy spaceAccessPolicy
    ) {
        this(store, workspaceDomainService, spaceAccessPolicy, Clock.systemUTC());
    }

    DomainTermService(
            DomainTermStore store,
            WorkspaceDomainService workspaceDomainService,
            SpaceAccessPolicy spaceAccessPolicy,
            Clock clock
    ) {
        this.store = store;
        this.workspaceDomainService = workspaceDomainService;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DomainTerm> list(String actorUserId, String spaceId, String status, String keyword) {
        spaceAccessPolicy.requireSpaceAccess(workspaceDomainService.spaceAccessContext(spaceId, actorUserId));
        DomainTermStatus parsedStatus = status == null || status.isBlank() ? null : DomainTermStatus.parse(status);
        String normalizedKeyword = normalizeOptional(keyword);
        return store.findBySpaceId(spaceId, parsedStatus, normalizedKeyword == null ? null : normalizedKeyword.toLowerCase(Locale.ROOT));
    }

    @Transactional
    public DomainTerm create(String actorUserId, String spaceId, String term, String definition) {
        spaceAccessPolicy.requireMemberManagement(workspaceDomainService.spaceAccessContext(spaceId, actorUserId));
        String normalizedTerm = requireText(term, "용어는 필수입니다.");
        String normalizedDefinition = requireText(definition, "용어 설명은 필수입니다.");
        ensureNoActiveDuplicate(spaceId, normalizedTerm, null);
        Instant now = Instant.now(clock);
        DomainTerm created = store.save(new DomainTerm(
                "term-" + UUID.randomUUID(),
                spaceId,
                normalizedTerm,
                normalizedDefinition,
                DomainTermStatus.ACTIVE,
                now,
                now,
                null
        ));
        store.recordChange(spaceId, actorUserId, created.id(), null, created.term(), now);
        return created;
    }

    @Transactional
    public DomainTerm update(
            String actorUserId,
            String spaceId,
            String termId,
            String term,
            boolean termPresent,
            String definition,
            boolean definitionPresent,
            String status,
            boolean statusPresent
    ) {
        if (!termPresent && !definitionPresent && !statusPresent) {
            throw invalidRequest("수정할 용어 필드가 필요합니다.");
        }
        spaceAccessPolicy.requireMemberManagement(workspaceDomainService.spaceAccessContext(spaceId, actorUserId));
        DomainTerm current = findRequired(spaceId, termId);
        String nextTerm = termPresent ? requireText(term, "용어는 blank일 수 없습니다.") : current.term();
        String nextDefinition = definitionPresent ? requireText(definition, "용어 설명은 blank일 수 없습니다.") : current.definition();
        DomainTermStatus nextStatus = statusPresent ? DomainTermStatus.parse(status) : current.status();
        if (nextStatus == DomainTermStatus.ACTIVE) {
            ensureNoActiveDuplicate(spaceId, nextTerm, current.id());
        }
        Instant now = Instant.now(clock);
        DomainTerm updated = store.save(current.updated(nextTerm, nextDefinition, nextStatus, now));
        store.recordChange(spaceId, actorUserId, updated.id(), current.term(), updated.term(), now);
        return updated;
    }

    @Transactional
    public boolean archive(String actorUserId, String spaceId, String termId) {
        spaceAccessPolicy.requireMemberManagement(workspaceDomainService.spaceAccessContext(spaceId, actorUserId));
        DomainTerm current = findRequired(spaceId, termId);
        if (current.status() == DomainTermStatus.ARCHIVED) {
            return true;
        }
        Instant now = Instant.now(clock);
        DomainTerm archived = store.save(current.updated(
                current.term(), current.definition(), DomainTermStatus.ARCHIVED, now
        ));
        store.recordChange(spaceId, actorUserId, archived.id(), current.term(), "ARCHIVED", now);
        return true;
    }

    private DomainTerm findRequired(String spaceId, String termId) {
        return store.findById(spaceId, termId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "DOMAIN_TERM_NOT_FOUND", "용어를 찾을 수 없습니다."
        ));
    }

    private void ensureNoActiveDuplicate(String spaceId, String term, String excludedTermId) {
        if (store.existsActiveTerm(spaceId, term.toLowerCase(Locale.ROOT), excludedTermId)) {
            throw new AuthorizationException(HttpStatus.CONFLICT, "INVALID_REQUEST", "같은 용어가 이미 등록되어 있습니다.");
        }
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw invalidRequest(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static AuthorizationException invalidRequest(String message) {
        return new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
