package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 관리자가 운영하는 분야별 공용 용어 사전 조회.
 * Space가 직접 등록한 {@link DomainTerm}과 달리 전역에 저장되고, Space는 분야 단위로 구독을 끈다.
 */
public interface SharedGlossaryStore {

    /**
     * 해당 Space가 구독 중인 분야에서 용어를 완전 일치로 찾는다.
     * 구독하지 않은 분야의 용어는 결과에 포함하지 않는다.
     */
    Optional<SharedGlossaryMatch> findSubscribedActiveExact(String spaceId, String normalizedTerm);

    List<GlossaryCategory> findActiveCategories();

    List<SharedGlossaryTerm> findSubscribedActive(String spaceId, String normalizedKeyword);

    void configureSpaceCategories(
            String spaceId,
            String actorUserId,
            List<String> selectedCategoryIds,
            List<String> customCategoryNames,
            Instant now
    );

    record SharedGlossaryMatch(
            String termId,
            String term,
            String definition,
            String categorySlug,
            String categoryName
    ) {
    }

    record GlossaryCategory(
            String id,
            String slug,
            String name,
            String description,
            int displayOrder
    ) {
    }

    record SharedGlossaryTerm(
            String id,
            String term,
            String definition,
            String categoryId,
            String categoryName,
            Instant updatedAt
    ) {
    }
}
