package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !db")
class InMemorySharedGlossaryStore implements SharedGlossaryStore {

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, GlossaryCategory> categoriesById = new LinkedHashMap<>();
    private final Set<String> disabledCategories = new HashSet<>();
    private final Map<String, Set<String>> selectedCategoryIdsBySpace = new LinkedHashMap<>();
    private final Map<String, List<String>> customCategoriesBySpace = new LinkedHashMap<>();

    InMemorySharedGlossaryStore() {
        addCategory("common-business", "공통 비즈니스", "분야와 관계없이 쓰이는 일반 업무 용어.", 10);
        addCategory("it-software", "IT/소프트웨어", "소프트웨어 개발과 서비스 운영에서 쓰이는 용어.", 20);
        addCategory("marketing-sales", "마케팅/영업", "고객 확보와 판매 활동에서 쓰이는 용어.", 30);
        addCategory("finance", "금융", "재무, 회계, 금융 업무에서 쓰이는 용어.", 40);
        addCategory("healthcare", "의료", "진료와 의료기관 운영에서 쓰이는 용어.", 50);
        addCategory("research", "연구", "연구 설계, 수행, 발표 과정에서 쓰이는 용어.", 60);
        addCategory("education", "교육", "교육과정 운영과 학습 평가에서 쓰이는 용어.", 70);
        addCategory("construction", "건축", "설계, 시공, 현장 관리에서 쓰이는 용어.", 80);
        addCategory("fashion-retail", "패션/리테일", "상품 기획, 생산, 유통에서 쓰이는 용어.", 90);
    }

    @Override
    public synchronized Optional<SharedGlossaryMatch> findSubscribedActiveExact(String spaceId, String normalizedTerm) {
        return entries.stream()
                .filter(entry -> entry.term.trim().equalsIgnoreCase(normalizedTerm))
                .filter(entry -> categoryEnabled(spaceId, entry))
                .min(Comparator.comparingInt((Entry entry) -> entry.displayOrder).thenComparing(entry -> entry.termId))
                .map(entry -> new SharedGlossaryMatch(
                        entry.termId, entry.term, entry.definition, entry.categorySlug, entry.categoryName
                ));
    }

    @Override
    public synchronized List<GlossaryCategory> findActiveCategories() {
        return categoriesById.values().stream()
                .sorted(Comparator.comparingInt(GlossaryCategory::displayOrder).thenComparing(GlossaryCategory::id))
                .toList();
    }

    @Override
    public synchronized List<SharedGlossaryTerm> findSubscribedActive(String spaceId, String normalizedKeyword) {
        return entries.stream()
                .filter(entry -> categoryEnabled(spaceId, entry))
                .filter(entry -> normalizedKeyword == null
                        || entry.term.toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || entry.definition.toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparingInt((Entry entry) -> entry.displayOrder)
                        .thenComparing(entry -> entry.term.toLowerCase(Locale.ROOT))
                        .thenComparing(entry -> entry.termId))
                .map(entry -> new SharedGlossaryTerm(
                        entry.termId,
                        entry.term,
                        entry.definition,
                        entry.categoryId,
                        entry.categoryName,
                        entry.updatedAt
                ))
                .toList();
    }

    @Override
    public synchronized void configureSpaceCategories(
            String spaceId,
            String actorUserId,
            List<String> selectedCategoryIds,
            List<String> customCategoryNames,
            Instant now
    ) {
        disabledCategories.removeIf(key -> key.startsWith(spaceId + "\u0000"));
        selectedCategoryIdsBySpace.put(spaceId, Set.copyOf(selectedCategoryIds));
        for (GlossaryCategory category : categoriesById.values()) {
            if (!selectedCategoryIds.contains(category.id())) {
                disabledCategories.add(disabledKey(spaceId, category.slug()));
            }
        }
        customCategoriesBySpace.put(spaceId, List.copyOf(customCategoryNames));
    }

    synchronized void register(
            String termId,
            String term,
            String definition,
            String categorySlug,
            String categoryName,
            int displayOrder
    ) {
        String categoryId = "glossary-category-" + categorySlug;
        categoriesById.putIfAbsent(categoryId, new GlossaryCategory(
                categoryId, categorySlug, categoryName, null, displayOrder
        ));
        entries.add(new Entry(
                termId, term, definition, categoryId, categorySlug, categoryName, displayOrder, Instant.EPOCH
        ));
    }

    synchronized void disableCategory(String spaceId, String categorySlug) {
        disabledCategories.add(disabledKey(spaceId, categorySlug));
    }

    synchronized List<String> customCategories(String spaceId) {
        return customCategoriesBySpace.getOrDefault(spaceId, List.of());
    }

    private static String disabledKey(String spaceId, String categorySlug) {
        return spaceId + "\u0000" + categorySlug;
    }

    private boolean categoryEnabled(String spaceId, Entry entry) {
        Set<String> selectedCategoryIds = selectedCategoryIdsBySpace.get(spaceId);
        return (selectedCategoryIds == null || selectedCategoryIds.contains(entry.categoryId))
                && !disabledCategories.contains(disabledKey(spaceId, entry.categorySlug));
    }

    private void addCategory(String slug, String name, String description, int displayOrder) {
        String id = "glossary-category-" + slug;
        categoriesById.put(id, new GlossaryCategory(id, slug, name, description, displayOrder));
    }

    private record Entry(
            String termId,
            String term,
            String definition,
            String categoryId,
            String categorySlug,
            String categoryName,
            int displayOrder,
            Instant updatedAt
    ) {
    }
}
