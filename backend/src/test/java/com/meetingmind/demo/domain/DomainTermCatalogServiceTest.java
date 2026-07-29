package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTermCatalogServiceTest {

    @Test
    void activeCatalogCombinesSubscribedTermsAndKeepsSpaceDefinitionForDuplicateName() {
        DomainTermService domainTerms = mock(DomainTermService.class);
        SharedGlossaryStore sharedGlossary = mock(SharedGlossaryStore.class);
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        when(domainTerms.list("user-1", "space-1", "ACTIVE", null)).thenReturn(List.of(
                new DomainTerm(
                        "term-kpi", "space-1", "KPI", "우리 팀 KPI",
                        DomainTermStatus.ACTIVE, now, now, null
                )
        ));
        when(sharedGlossary.findSubscribedActive("space-1", null)).thenReturn(List.of(
                new SharedGlossaryStore.SharedGlossaryTerm(
                        "shared-kpi", "kpi", "공용 KPI",
                        "glossary-category-common-business", "공통 비즈니스", now
                ),
                new SharedGlossaryStore.SharedGlossaryTerm(
                        "shared-okr", "OKR", "목표와 핵심결과",
                        "glossary-category-common-business", "공통 비즈니스", now
                )
        ));
        DomainTermCatalogService catalog = new DomainTermCatalogService(domainTerms, sharedGlossary);

        List<DomainTermCatalogService.CatalogTerm> result =
                catalog.list("user-1", "space-1", "ACTIVE", null);

        assertThat(result).extracting(DomainTermCatalogService.CatalogTerm::term)
                .containsExactly("KPI", "OKR");
        assertThat(result.getFirst().definition()).isEqualTo("우리 팀 KPI");
        assertThat(result.getFirst().source()).isEqualTo(DomainTermCatalogService.Source.SPACE);
        assertThat(result.getLast().source()).isEqualTo(DomainTermCatalogService.Source.SHARED);
        assertThat(result.getLast().editable()).isFalse();
    }
}
