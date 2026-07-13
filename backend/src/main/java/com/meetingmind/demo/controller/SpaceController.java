package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateMeetingRequest;
import com.meetingmind.demo.dto.CreateMeetingResponse;
import com.meetingmind.demo.dto.CreateSpaceRequest;
import com.meetingmind.demo.dto.CreateSpaceResponse;
import com.meetingmind.demo.dto.ProjectAiContextCandidatesResponse;
import com.meetingmind.demo.dto.RemoveSpaceMemberResponse;
import com.meetingmind.demo.dto.SpaceListResponse;
import com.meetingmind.demo.dto.SpaceMembersResponse;
import com.meetingmind.demo.dto.TransferOwnerRequest;
import com.meetingmind.demo.dto.TransferOwnerResponse;
import com.meetingmind.demo.dto.UpdateSpaceMemberRequest;
import com.meetingmind.demo.dto.UpdateSpaceMemberResponse;
import com.meetingmind.demo.domain.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
        return new CreateMeetingResponse(
                result.meeting().id(),
                result.meeting().status().name(),
                result.meeting().joinCode(),
                "/meetings/" + result.meeting().id() + "?joinCode=" + result.meeting().joinCode()
        );
    }

    @GetMapping("/{spaceId}/members")
    public SpaceMembersResponse listMembers(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        List<SpaceMembersResponse.Member> members = workspaceDomainService.listSpaceMembers(user.id(), spaceId)
                .stream()
                .map(member -> {
                    User memberUser = member.user();
                    return new SpaceMembersResponse.Member(
                            member.member().id(),
                            member.member().userId(),
                            memberUser == null ? null : memberUser.displayName(),
                            memberUser == null ? null : memberUser.email(),
                            member.member().role().name(),
                            member.member().joinedAt().toString()
                    );
                })
                .toList();
        return new SpaceMembersResponse(members);
    }

    @PatchMapping("/{spaceId}/members/{memberId}")
    public UpdateSpaceMemberResponse updateMemberRole(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String memberId,
            @RequestBody UpdateSpaceMemberRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        var member = workspaceDomainService.updateSpaceMemberRole(user.id(), spaceId, memberId, request.role());
        return new UpdateSpaceMemberResponse(member.id(), member.role().name());
    }

    @DeleteMapping("/{spaceId}/members/{memberId}")
    public RemoveSpaceMemberResponse removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String memberId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new RemoveSpaceMemberResponse(workspaceDomainService.removeSpaceMember(user.id(), spaceId, memberId));
    }

    @PostMapping("/{spaceId}/owner-transfer")
    public TransferOwnerResponse transferOwner(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestBody TransferOwnerRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.OwnerTransferResult result = workspaceDomainService.transferOwner(
                user.id(),
                spaceId,
                request.targetMemberId(),
                request.confirmationText(),
                request.previousOwnerRole()
        );
        return new TransferOwnerResponse(true, result.newOwner().id(), result.previousOwner().role().name());
    }

    @GetMapping("/{spaceId}/project-ai/context-candidates")
    public ProjectAiContextCandidatesResponse projectAiContextCandidates(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.ProjectAiContextCandidates candidates = workspaceDomainService.projectAiContextCandidates(
                user.id(),
                spaceId
        );
        return new ProjectAiContextCandidatesResponse(
                candidates.projectKnowledge()
                        .stream()
                        .map(knowledge -> new ProjectAiContextCandidatesResponse.ProjectKnowledgeCandidate(
                                knowledge.id(),
                                knowledge.title(),
                                knowledge.content()
                        ))
                        .toList(),
                candidates.meetings()
                        .stream()
                        .map(meeting -> new ProjectAiContextCandidatesResponse.MeetingCandidate(
                                meeting.id(),
                                meeting.title(),
                                null
                        ))
                        .toList()
        );
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }
}
