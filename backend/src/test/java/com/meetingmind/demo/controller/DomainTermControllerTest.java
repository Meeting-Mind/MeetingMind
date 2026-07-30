package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.DomainTermCatalogService;
import com.meetingmind.demo.domain.DomainTermService;
import com.meetingmind.demo.domain.DomainTermStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTermControllerTest {

    @Test
    void activeListMapsCatalogSourcesAndEditability() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspace = mock(WorkspaceDomainService.class);
        DomainTermService domainTerms = mock(DomainTermService.class);
        DomainTermCatalogService catalog = mock(DomainTermCatalogService.class);
        when(authService.currentUser("Bearer token"))
                .thenReturn(new AuthUserResponse("user-1", "user@example.com", "User", null, "active"));
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        when(catalog.list("user-1", "space-1", "ACTIVE", null)).thenReturn(List.of(
                new DomainTermCatalogService.CatalogTerm(
                        "term-1", "KPI", "우리 팀 KPI", DomainTermStatus.ACTIVE, now,
                        DomainTermCatalogService.Source.SPACE, null, null, true
                ),
                new DomainTermCatalogService.CatalogTerm(
                        "shared-okr", "OKR", "목표와 핵심결과", DomainTermStatus.ACTIVE, now,
                        DomainTermCatalogService.Source.SHARED,
                        "glossary-category-common-business", "공통 비즈니스", false
                )
        ));

        DomainTermController controller = new DomainTermController(
                authService, workspace, domainTerms, catalog
        );
        var response = controller.list("Bearer token", "space-1", null, "ACTIVE");

        assertThat(response.terms()).hasSize(2);
        assertThat(response.terms()).filteredOn(term -> term.term().equals("KPI"))
                .singleElement()
                .satisfies(term -> {
                    assertThat(term.source()).isEqualTo("SPACE");
                    assertThat(term.editable()).isTrue();
                    assertThat(term.definition()).isEqualTo("우리 팀 KPI");
                });
        assertThat(response.terms()).filteredOn(term -> term.term().equals("OKR"))
                .singleElement()
                .satisfies(term -> {
                    assertThat(term.source()).isEqualTo("SHARED");
                    assertThat(term.editable()).isFalse();
                    assertThat(term.categoryName()).isEqualTo("공통 비즈니스");
                });
    }
}
