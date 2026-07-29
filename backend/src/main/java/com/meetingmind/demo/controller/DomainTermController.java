package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.DomainTerm;
import com.meetingmind.demo.domain.DomainTermCatalogService;
import com.meetingmind.demo.domain.DomainTermService;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateDomainTermRequest;
import com.meetingmind.demo.dto.DeleteDomainTermResponse;
import com.meetingmind.demo.dto.DomainTermListResponse;
import com.meetingmind.demo.dto.DomainTermMutationResponse;
import com.meetingmind.demo.dto.UpdateDomainTermRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/terms")
public class DomainTermController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final DomainTermService domainTermService;
    private final DomainTermCatalogService domainTermCatalogService;

    public DomainTermController(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            DomainTermService domainTermService,
            DomainTermCatalogService domainTermCatalogService
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.domainTermService = domainTermService;
        this.domainTermCatalogService = domainTermCatalogService;
    }

    @GetMapping
    public DomainTermListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new DomainTermListResponse(domainTermCatalogService.list(user.id(), spaceId, status, keyword)
                .stream()
                .map(DomainTermController::toResponse)
                .toList());
    }

    @PostMapping
    public DomainTermMutationResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @Valid @RequestBody CreateDomainTermRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return mutation(domainTermService.create(user.id(), spaceId, request.term(), request.definition()));
    }

    @PatchMapping("/{termId}")
    public DomainTermMutationResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String termId,
            @RequestBody UpdateDomainTermRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return mutation(domainTermService.update(
                user.id(), spaceId, termId,
                request.term(), request.termPresent(),
                request.definition(), request.definitionPresent(),
                request.status(), request.statusPresent()
        ));
    }

    @DeleteMapping("/{termId}")
    public DeleteDomainTermResponse archive(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String termId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new DeleteDomainTermResponse(domainTermService.archive(user.id(), spaceId, termId));
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }

    private static DomainTermListResponse.Term toResponse(DomainTermCatalogService.CatalogTerm term) {
        return new DomainTermListResponse.Term(
                term.id(), term.term(), term.definition(), term.status().name(), term.updatedAt(),
                term.source().name(), term.categoryId(), term.categoryName(), term.editable()
        );
    }

    private static DomainTermMutationResponse mutation(DomainTerm term) {
        return new DomainTermMutationResponse(term.id(), term.status().name(), term.updatedAt());
    }
}
