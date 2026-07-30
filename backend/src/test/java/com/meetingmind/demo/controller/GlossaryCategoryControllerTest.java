package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.SharedGlossaryStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlossaryCategoryControllerTest {

    @Test
    void listsActiveGlossaryCategoriesForAuthenticatedUser() {
        AuthService authService = mock(AuthService.class);
        SharedGlossaryStore store = mock(SharedGlossaryStore.class);
        when(authService.currentUser("Bearer token"))
                .thenReturn(new AuthUserResponse("user-1", "user@example.com", "User", null, "active"));
        when(store.findActiveCategories()).thenReturn(List.of(
                new SharedGlossaryStore.GlossaryCategory(
                        "glossary-category-it-software",
                        "it-software",
                        "IT/소프트웨어",
                        "소프트웨어 개발과 서비스 운영에서 쓰이는 용어.",
                        20
                )
        ));

        var response = new GlossaryCategoryController(authService, store).list("Bearer token");

        assertThat(response.categories()).singleElement().satisfies(category -> {
            assertThat(category.id()).isEqualTo("glossary-category-it-software");
            assertThat(category.name()).isEqualTo("IT/소프트웨어");
        });
    }
}
