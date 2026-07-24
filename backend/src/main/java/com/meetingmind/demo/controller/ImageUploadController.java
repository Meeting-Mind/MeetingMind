package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.service.S3ImageStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ImageUploadController {
    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final S3ImageStorageService storageService;

    public ImageUploadController(AuthService authService, WorkspaceDomainService workspaceDomainService, S3ImageStorageService storageService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.storageService = storageService;
    }

    @PostMapping("/assets/profile-image")
    public ImageUploadResponse uploadProfileImage(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam("file") MultipartFile file
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        return new ImageUploadResponse(storageService.uploadProfileImage(user.id(), file));
    }

    @PostMapping("/spaces/{spaceId}/image")
    public ImageUploadResponse uploadSpaceImage(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam("file") MultipartFile file
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.requireSpaceMemberManagement(user.id(), spaceId);
        return new ImageUploadResponse(storageService.uploadSpaceImage(spaceId, file));
    }

    public record ImageUploadResponse(String imageUrl) {}
}
