package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateMeetingRequest;
import com.meetingmind.demo.dto.CreateMeetingResponse;
import com.meetingmind.demo.dto.CreateInstantMeetingResponse;
import com.meetingmind.demo.dto.CreateProjectKnowledgeRequest;
import com.meetingmind.demo.dto.CreateSpaceRequest;
import com.meetingmind.demo.dto.CreateSpaceResponse;
import com.meetingmind.demo.dto.CreateSpaceInvitationRequest;
import com.meetingmind.demo.dto.CreateSpaceInvitationResponse;
import com.meetingmind.demo.dto.DeleteSpaceResponse;
import com.meetingmind.demo.dto.DeleteProjectKnowledgeResponse;
import com.meetingmind.demo.dto.MeetingListResponse;
import com.meetingmind.demo.dto.ProjectAiContextCandidatesResponse;
import com.meetingmind.demo.dto.ProjectKnowledgeListResponse;
import com.meetingmind.demo.dto.ProjectKnowledgeDetailResponse;
import com.meetingmind.demo.dto.ProjectKnowledgeMutationResponse;
import com.meetingmind.demo.dto.RemoveSpaceMemberResponse;
import com.meetingmind.demo.dto.SpaceListResponse;
import com.meetingmind.demo.dto.SpaceDetailResponse;
import com.meetingmind.demo.dto.SpaceMembersResponse;
import com.meetingmind.demo.dto.TransferOwnerRequest;
import com.meetingmind.demo.dto.TransferOwnerResponse;
import com.meetingmind.demo.dto.UpdateSpaceMemberRequest;
import com.meetingmind.demo.dto.UpdateSpaceMemberResponse;
import com.meetingmind.demo.dto.UpdateSpaceRequest;
import com.meetingmind.demo.dto.UpdateSpaceResponse;
import com.meetingmind.demo.dto.UpdateProjectKnowledgeRequest;
import com.meetingmind.demo.dto.ResolveInvitationRequest;
import com.meetingmind.demo.dto.ResolveSpaceInvitationResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/{spaceId}")
    public SpaceDetailResponse spaceDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.SpaceDetail detail = workspaceDomainService.spaceDetail(user.id(), spaceId);
        return new SpaceDetailResponse(
                detail.space().id(),
                detail.space().name(),
                detail.space().description(),
                detail.role().name(),
                detail.upcomingMeetings().stream().map(summary -> new SpaceDetailResponse.MeetingSummary(
                        summary.meeting().id(), summary.meeting().spaceId(), summary.meeting().title(),
                        summary.meeting().description(), summary.meeting().scheduledAt(), summary.meeting().scheduledEndAt(),
                        summary.meeting().status().name(), summary.myRole() == null ? null : summary.myRole().name()
                )).toList(),
                detail.recentReports().stream().map(report -> new SpaceDetailResponse.ReportSummary(
                        report.id(), report.meetingId(), report.status().name(), report.title(), report.summary(),
                        report.version(), report.current(), report.createdAt()
                )).toList(),
                detail.actionItems().stream().map(view -> taskResponse(view)).toList(),
                List.of("project-ai", "meeting-ai")
        );
    }

    @PatchMapping("/{spaceId}")
    public UpdateSpaceResponse updateSpace(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestBody UpdateSpaceRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        var updated = workspaceDomainService.updateSpace(
                user.id(), spaceId, request.name(), request.namePresent(), request.description(), request.descriptionPresent()
        );
        return new UpdateSpaceResponse(updated.id(), updated.name(), updated.description(), updated.updatedAt());
    }

    @DeleteMapping("/{spaceId}")
    public DeleteSpaceResponse deleteSpace(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new DeleteSpaceResponse(workspaceDomainService.deleteSpace(user.id(), spaceId));
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
                request.description(),
                request.scheduledAt(),
                request.scheduledEndAt(),
                request.participantUserIds()
        );
        return new CreateMeetingResponse(
                result.meeting().id(),
                result.meeting().status().name(),
                result.meeting().joinCode(),
                "/meetings/" + result.meeting().id() + "?joinCode=" + result.meeting().joinCode()
        );
    }

    @PostMapping("/{spaceId}/instant-meetings")
    public CreateInstantMeetingResponse createInstantMeeting(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.MeetingCreationResult result = workspaceDomainService.createInstantMeeting(user.id(), spaceId);
        return new CreateInstantMeetingResponse(
                result.meeting().id(),
                result.meeting().status().name(),
                result.meeting().roomCode(),
                result.meeting().joinCode(),
                "/meetings/" + result.meeting().id() + "?joinCode=" + result.meeting().joinCode()
        );
    }

    @GetMapping("/{spaceId}/meetings")
    public MeetingListResponse listMeetings(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new MeetingListResponse(workspaceDomainService.listMeetings(user.id(), spaceId, status, from, to).stream()
                .map(view -> new MeetingListResponse.MeetingSummary(
                        view.meeting().id(),
                        view.meeting().spaceId(),
                        view.meeting().title(),
                        view.meeting().description(),
                        view.meeting().scheduledAt().toString(),
                        view.meeting().scheduledEndAt().toString(),
                        view.meeting().status().name(),
                        view.myRole() == null ? null : view.myRole().name()
                ))
                .toList());
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

    @PostMapping("/{spaceId}/invitations")
    public CreateSpaceInvitationResponse createInvitation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @Valid @RequestBody CreateSpaceInvitationRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.SpaceInvitationCreation result = workspaceDomainService.createSpaceInvitation(
                user.id(), spaceId, request.email(), request.role()
        );
        return new CreateSpaceInvitationResponse(
                result.invitation().id(), result.invitation().status().name(), result.invitation().expiresAt(), result.token()
        );
    }

    @PostMapping("/{spaceId}/invitations/{invitationId}/accept")
    public ResolveSpaceInvitationResponse acceptInvitation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String invitationId,
            @Valid @RequestBody ResolveInvitationRequest request
    ) {
        return resolveInvitation(authorizationHeader, spaceId, invitationId, request, true);
    }

    @PostMapping("/{spaceId}/invitations/{invitationId}/decline")
    public ResolveSpaceInvitationResponse declineInvitation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String invitationId,
            @Valid @RequestBody ResolveInvitationRequest request
    ) {
        return resolveInvitation(authorizationHeader, spaceId, invitationId, request, false);
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

    @GetMapping("/{spaceId}/knowledge")
    public ProjectKnowledgeListResponse listProjectKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new ProjectKnowledgeListResponse(workspaceDomainService.listProjectKnowledge(user.id(), spaceId, type, keyword)
                .stream()
                .map(view -> {
                    var knowledge = view.knowledge();
                    return new ProjectKnowledgeListResponse.Item(
                            knowledge.id(), knowledge.spaceId(), knowledge.type().name().toLowerCase(), knowledge.title(),
                            contentPreview(knowledge.content()),
                            view.sourceMeetingAccessible() ? knowledge.sourceMeetingId() : null,
                            knowledge.embeddingStatus().name(), knowledge.updatedAt()
                    );
                })
                .toList());
    }

    @GetMapping("/{spaceId}/knowledge/{knowledgeId}")
    public ProjectKnowledgeDetailResponse projectKnowledgeDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String knowledgeId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        var view = workspaceDomainService.projectKnowledgeDetail(user.id(), spaceId, knowledgeId);
        var knowledge = view.knowledge();
        return new ProjectKnowledgeDetailResponse(
                knowledge.id(), knowledge.spaceId(), knowledge.type().name().toLowerCase(), knowledge.title(), knowledge.content(),
                view.sourceMeetingAccessible() ? knowledge.sourceMeetingId() : null,
                knowledge.embeddingStatus().name(), knowledge.updatedAt()
        );
    }

    @PostMapping("/{spaceId}/knowledge")
    public ProjectKnowledgeMutationResponse createProjectKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @Valid @RequestBody CreateProjectKnowledgeRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return projectKnowledgeResponse(workspaceDomainService.createProjectKnowledge(
                user.id(), spaceId, request.type(), request.title(), request.content(), request.sourceMeetingId()
        ));
    }

    @PatchMapping("/{spaceId}/knowledge/{knowledgeId}")
    public ProjectKnowledgeMutationResponse updateProjectKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String knowledgeId,
            @RequestBody UpdateProjectKnowledgeRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return projectKnowledgeResponse(workspaceDomainService.updateProjectKnowledge(
                user.id(), spaceId, knowledgeId,
                new WorkspaceDomainService.ProjectKnowledgePatch(
                        request.title(), request.titlePresent(), request.content(), request.contentPresent()
                )
        ));
    }

    @DeleteMapping("/{spaceId}/knowledge/{knowledgeId}")
    public DeleteProjectKnowledgeResponse deleteProjectKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String knowledgeId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new DeleteProjectKnowledgeResponse(workspaceDomainService.archiveProjectKnowledge(user.id(), spaceId, knowledgeId));
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }

    private ProjectKnowledgeMutationResponse projectKnowledgeResponse(com.meetingmind.demo.domain.ProjectKnowledge knowledge) {
        return new ProjectKnowledgeMutationResponse(
                knowledge.id(), knowledge.status().name(), knowledge.embeddingStatus().name(), knowledge.embeddingJobId(), knowledge.updatedAt()
        );
    }

    private static SpaceDetailResponse.Task taskResponse(WorkspaceDomainService.TaskCardView view) {
        var task = view.task();
        return new SpaceDetailResponse.Task(
                task.id(), task.spaceId(), view.meetingSourceVisible() ? task.meetingId() : null,
                task.title(), task.description(), task.status().name(), task.priority().name(), task.labels(),
                task.assigneeId(), task.dueDate(), view.meetingSourceVisible() ? task.sourceCandidateId() : null
        );
    }

    private String contentPreview(String content) {
        return content.length() <= 240 ? content : content.substring(0, 240);
    }

    private ResolveSpaceInvitationResponse resolveInvitation(
            String authorizationHeader,
            String spaceId,
            String invitationId,
            ResolveInvitationRequest request,
            boolean accept
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.SpaceInvitationResolution result = workspaceDomainService.resolveSpaceInvitation(
                user.id(), user.email(), spaceId, invitationId, request.token(), accept
        );
        return new ResolveSpaceInvitationResponse(
                result.member() == null ? null : result.member().id(),
                result.invitation().id(),
                result.member() == null ? null : result.member().role().name(),
                result.invitation().status().name()
        );
    }
}
