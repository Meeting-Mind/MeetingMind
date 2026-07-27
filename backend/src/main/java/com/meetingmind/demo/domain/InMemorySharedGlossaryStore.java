package com.meetingmind.demo.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !db")
class InMemorySharedGlossaryStore implements SharedGlossaryStore {

    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> disabledCategories = new HashSet<>();

    @Override
    public synchronized Optional<SharedGlossaryMatch> findSubscribedActiveExact(String spaceId, String normalizedTerm) {
        return entries.stream()
                .filter(entry -> entry.term.trim().equalsIgnoreCase(normalizedTerm))
                .filter(entry -> !disabledCategories.contains(disabledKey(spaceId, entry.categorySlug)))
                .min(Comparator.comparingInt((Entry entry) -> entry.displayOrder).thenComparing(entry -> entry.termId))
                .map(entry -> new SharedGlossaryMatch(
                        entry.termId, entry.term, entry.definition, entry.categorySlug, entry.categoryName
                ));
    }

    synchronized void register(
            String termId,
            String term,
            String definition,
            String categorySlug,
            String categoryName,
            int displayOrder
    ) {
        entries.add(new Entry(termId, term, definition, categorySlug, categoryName, displayOrder));
    }

    synchronized void disableCategory(String spaceId, String categorySlug) {
        disabledCategories.add(disabledKey(spaceId, categorySlug));
    }

    private static String disabledKey(String spaceId, String categorySlug) {
        return spaceId + "\u0000" + categorySlug;
    }

    private record Entry(
            String termId,
            String term,
            String definition,
            String categorySlug,
            String categoryName,
            int displayOrder
    ) {
    }
}
