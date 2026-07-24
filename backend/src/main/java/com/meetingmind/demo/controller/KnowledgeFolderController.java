package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.dto.KnowledgeFolderDtos;
import com.meetingmind.demo.service.KnowledgeFolderService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile({"local", "db"})
@RequestMapping("/api/v1/spaces/{spaceId}/knowledge/folders")
public class KnowledgeFolderController {
    private final AuthService authService;
    private final KnowledgeFolderService service;

    public KnowledgeFolderController(AuthService authService, KnowledgeFolderService service) { this.authService = authService; this.service = service; }

    @GetMapping
    public KnowledgeFolderDtos.ListResponse list(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String spaceId) {
        AuthUserResponse user = authService.currentUser(authorization);
        return new KnowledgeFolderDtos.ListResponse(service.list(user.id(), spaceId));
    }

    @PostMapping
    public KnowledgeFolderDtos.MutationResponse create(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String spaceId, @Valid @RequestBody KnowledgeFolderDtos.NameRequest request) {
        AuthUserResponse user = authService.currentUser(authorization);
        return new KnowledgeFolderDtos.MutationResponse(service.create(user.id(), spaceId, request.name()));
    }

    @PatchMapping("/{folderId}")
    public KnowledgeFolderDtos.MutationResponse rename(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String spaceId, @PathVariable String folderId, @Valid @RequestBody KnowledgeFolderDtos.NameRequest request) {
        AuthUserResponse user = authService.currentUser(authorization);
        return new KnowledgeFolderDtos.MutationResponse(service.rename(user.id(), spaceId, folderId, request.name()));
    }

    @PutMapping("/{folderId}/nodes/{knowledgeId}")
    public KnowledgeFolderDtos.MutationResponse assign(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String spaceId, @PathVariable String folderId, @PathVariable String knowledgeId) {
        AuthUserResponse user = authService.currentUser(authorization);
        return new KnowledgeFolderDtos.MutationResponse(service.assign(user.id(), spaceId, folderId, knowledgeId));
    }

    @DeleteMapping("/{folderId}/nodes/{knowledgeId}")
    public void remove(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String spaceId, @PathVariable String folderId, @PathVariable String knowledgeId) {
        AuthUserResponse user = authService.currentUser(authorization);
        service.remove(user.id(), spaceId, folderId, knowledgeId);
    }

    @DeleteMapping("/{folderId}")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String spaceId, @PathVariable String folderId) {
        AuthUserResponse user = authService.currentUser(authorization);
        service.delete(user.id(), spaceId, folderId);
    }
}
