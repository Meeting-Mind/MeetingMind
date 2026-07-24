package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.service.LocalImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final LocalImageStorageService storageService;

    public ImageUploadController(AuthService authService, WorkspaceDomainService workspaceDomainService, LocalImageStorageService storageService) {
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

    @GetMapping("/assets/images/{category}/{ownerId}/{filename}")
    public ResponseEntity<Resource> image(
            @PathVariable String category,
            @PathVariable String ownerId,
            @PathVariable String filename
    ) {
        return ResponseEntity
                .ok()
                .contentType(contentType(filename))
                .body(storageService.image(category, ownerId, filename));
    }

    private MediaType contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public record ImageUploadResponse(String imageUrl) {}
}
