package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Space가 직접 등록한 용어와 구독 중인 공용 용어를 하나의 읽기 모델로 합친다.
 * 동명 용어는 Space 정의를 우선한다.
 */
@Service
public class DomainTermCatalogService {

    private final DomainTermService domainTermService;
    private final SharedGlossaryStore sharedGlossaryStore;

    public DomainTermCatalogService(
            DomainTermService domainTermService,
            SharedGlossaryStore sharedGlossaryStore
    ) {
        this.domainTermService = domainTermService;
        this.sharedGlossaryStore = sharedGlossaryStore;
    }

    @Transactional(readOnly = true)
    public List<CatalogTerm> list(
            String actorUserId,
            String spaceId,
            String status,
            String keyword
    ) {
        List<CatalogTerm> spaceTerms = domainTermService.list(actorUserId, spaceId, status, keyword)
                .stream()
                .map(CatalogTerm::from)
                .toList();
        if (status != null && !status.isBlank() && !"ACTIVE".equalsIgnoreCase(status)) {
            return spaceTerms;
        }

        Set<String> overriddenTerms = new HashSet<>();
        spaceTerms.forEach(term -> overriddenTerms.add(normalize(term.term())));
        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? null
                : normalize(keyword);
        List<CatalogTerm> sharedTerms = sharedGlossaryStore
                .findSubscribedActive(spaceId, normalizedKeyword)
                .stream()
                .filter(term -> !overriddenTerms.contains(normalize(term.term())))
                .map(CatalogTerm::from)
                .toList();
        return Stream.concat(spaceTerms.stream(), sharedTerms.stream()).toList();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public enum Source {
        SPACE,
        SHARED
    }

    public record CatalogTerm(
            String id,
            String term,
            String definition,
            DomainTermStatus status,
            Instant updatedAt,
            Source source,
            String categoryId,
            String categoryName,
            boolean editable
    ) {
        private static CatalogTerm from(DomainTerm term) {
            return new CatalogTerm(
                    term.id(), term.term(), term.definition(), term.status(), term.updatedAt(),
                    Source.SPACE, null, null, true
            );
        }

        private static CatalogTerm from(SharedGlossaryStore.SharedGlossaryTerm term) {
            return new CatalogTerm(
                    term.id(), term.term(), term.definition(), DomainTermStatus.ACTIVE, term.updatedAt(),
                    Source.SHARED, term.categoryId(), term.categoryName(), false
            );
        }
    }
}
