package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateMeetingRequest;
import com.meetingmind.demo.dto.CreateMeetingResponse;
import com.meetingmind.demo.dto.CreateSpaceRequest;
import com.meetingmind.demo.dto.CreateSpaceResponse;
import com.meetingmind.demo.dto.SpaceListResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;

    public SpaceController(AuthService authService, WorkspaceDomainService workspaceDomainService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
    }

    @GetMapping
    public SpaceListResponse listSpaces(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        List<SpaceListResponse.SpaceSummary> spaces = workspaceDomainService.listSpaces(user.id())
                .stream()
                .map(summary -> new SpaceListResponse.SpaceSummary(
                        summary.space().id(),
                        summary.space().name(),
                        summary.space().description(),
                        summary.role().name(),
                        summary.meetingCount(),
                        summary.space().createdAt().toString()
                ))
                .toList();
        return new SpaceListResponse(spaces);
    }

    @PostMapping
    public CreateSpaceResponse createSpace(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody CreateSpaceRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.SpaceCreationResult result = workspaceDomainService.createSpace(
                user.id(),
                request.name(),
                request.description()
        );
        return new CreateSpaceResponse(
                result.space().id(),
                result.space().name(),
                result.space().description(),
                result.owner().role().name(),
                result.space().createdAt().toString()
        );
    }

    @PostMapping("/{spaceId}/meetings")
    public CreateMeetingResponse createMeeting(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @Valid @RequestBody CreateMeetingRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.MeetingCreationResult result = workspaceDomainService.createMeeting(
                user.id(),
                spaceId,
                request.title(),
                request.scheduledAt(),
                request.participantUserIds()
        );
        return new CreateMeetingResponse(result.meeting().id(), result.meeting().status().name());
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }
}
