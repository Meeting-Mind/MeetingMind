package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.domain.SharedGlossaryStore;
import com.meetingmind.demo.dto.GlossaryCategoryListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/glossary/categories")
public class GlossaryCategoryController {

    private final AuthService authService;
    private final SharedGlossaryStore sharedGlossaryStore;

    public GlossaryCategoryController(AuthService authService, SharedGlossaryStore sharedGlossaryStore) {
        this.authService = authService;
        this.sharedGlossaryStore = sharedGlossaryStore;
    }

    @GetMapping
    public GlossaryCategoryListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.currentUser(authorizationHeader);
        return new GlossaryCategoryListResponse(sharedGlossaryStore.findActiveCategories().stream()
                .map(category -> new GlossaryCategoryListResponse.Category(
                        category.id(), category.slug(), category.name(), category.description()
                ))
                .toList());
    }
}
