package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.MeetingParticipant;
import com.meetingmind.demo.domain.MeetingJoinRequest;
import com.meetingmind.demo.domain.User;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.domain.WorkspaceDomainService.MeetingParticipantWithUser;
import com.meetingmind.demo.dto.CreateMeetingJoinRequestRequest;
import com.meetingmind.demo.dto.CreateMeetingJoinRequestResponse;
import com.meetingmind.demo.dto.AddMeetingParticipantRequest;
import com.meetingmind.demo.dto.MeetingJoinRequestSummaryResponse;
import com.meetingmind.demo.dto.MeetingParticipantMutationResponse;
import com.meetingmind.demo.dto.MeetingParticipantsResponse;
import com.meetingmind.demo.dto.ReviewMeetingJoinRequestResponse;
import com.meetingmind.demo.dto.UpdateMeetingParticipantRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;

    public MeetingController(AuthService authService, WorkspaceDomainService workspaceDomainService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
    }

    @GetMapping("/{meetingId}/participants")
    public MeetingParticipantsResponse listParticipants(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        List<MeetingParticipantsResponse.Participant> participants = workspaceDomainService
                .listMeetingParticipants(user.id(), meetingId)
                .stream()
                .map(participant -> {
                    User participantUser = participant.user();
                    return new MeetingParticipantsResponse.Participant(
                            participant.participant().id(),
                            participant.participant().userId(),
                            participantUser == null ? null : participantUser.displayName(),
                            participant.participant().role().name(),
                            participant.participant().participantType().name().toLowerCase(),
                            participant.participant().accessStatus().name()
                    );
                })
                .toList();
        return new MeetingParticipantsResponse(participants);
    }

    @GetMapping("/{meetingId}/join-requests")
    public MeetingJoinRequestSummaryResponse listJoinRequests(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        List<MeetingJoinRequestSummaryResponse.Request> requests = workspaceDomainService
                .listMeetingJoinRequests(user.id(), meetingId)
                .stream()
                .map(request -> new MeetingJoinRequestSummaryResponse.Request(
                        request.id(),
                        request.userId(),
                        request.status().name(),
                        request.requestedAt().toString()
                ))
                .toList();
        return new MeetingJoinRequestSummaryResponse(requests);
    }

    @PostMapping("/join-requests")
    public CreateMeetingJoinRequestResponse createJoinRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody CreateMeetingJoinRequestRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingJoinRequest joinRequest = workspaceDomainService.createMeetingJoinRequest(
                user.id(),
                request.joinCodeOrUrl()
        );
        return new CreateMeetingJoinRequestResponse(
                joinRequest.id(),
                joinRequest.meetingId(),
                joinRequest.status().name()
        );
    }

    @PostMapping("/{meetingId}/join-requests/{requestId}/approve")
    public ReviewMeetingJoinRequestResponse approveJoinRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String requestId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingJoinRequest joinRequest = workspaceDomainService.approveMeetingJoinRequest(user.id(), meetingId, requestId);
        MeetingParticipant created = workspaceDomainService.listMeetingParticipants(user.id(), meetingId).stream()
                .filter(result -> result.participant().userId().equals(joinRequest.userId()))
                .map(MeetingParticipantWithUser::participant)
                .findFirst()
                .orElseThrow();
        return new ReviewMeetingJoinRequestResponse(
                joinRequest.id(),
                joinRequest.status().name(),
                created.id(),
                created.participantType().name().toLowerCase()
        );
    }

    @PostMapping("/{meetingId}/join-requests/{requestId}/reject")
    public ReviewMeetingJoinRequestResponse rejectJoinRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String requestId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingJoinRequest joinRequest = workspaceDomainService.rejectMeetingJoinRequest(user.id(), meetingId, requestId);
        return new ReviewMeetingJoinRequestResponse(
                joinRequest.id(),
                joinRequest.status().name(),
                null,
                null
        );
    }

    @PostMapping("/{meetingId}/participants")
    public MeetingParticipantMutationResponse addParticipant(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @RequestBody AddMeetingParticipantRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingParticipant participant = workspaceDomainService.addMeetingParticipant(
                user.id(),
                meetingId,
                request.userId(),
                request.role(),
                request.participantType()
        );
        return toMutationResponse(participant);
    }

    @PatchMapping("/{meetingId}/participants/{participantId}")
    public MeetingParticipantMutationResponse updateParticipant(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String participantId,
            @RequestBody UpdateMeetingParticipantRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingParticipant participant = workspaceDomainService.updateMeetingParticipant(
                user.id(),
                meetingId,
                participantId,
                request.role(),
                request.accessStatus()
        );
        return toMutationResponse(participant);
    }

    private MeetingParticipantMutationResponse toMutationResponse(MeetingParticipant participant) {
        return new MeetingParticipantMutationResponse(
                participant.id(),
                participant.role().name(),
                participant.accessStatus().name()
        );
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }
}
