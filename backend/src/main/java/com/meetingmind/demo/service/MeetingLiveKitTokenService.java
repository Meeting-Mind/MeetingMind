package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import com.meetingmind.demo.dto.MeetingLiveKitTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MeetingLiveKitTokenService {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final MeetingAccessPolicy meetingAccessPolicy;
    private final LiveKitTokenService liveKitTokenService;

    public MeetingLiveKitTokenService(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            MeetingAccessPolicy meetingAccessPolicy,
            LiveKitTokenService liveKitTokenService
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.meetingAccessPolicy = meetingAccessPolicy;
        this.liveKitTokenService = liveKitTokenService;
    }

    public MeetingLiveKitTokenResponse issueMeetingToken(String authorizationHeader, String meetingId) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        meetingAccessPolicy.requireLiveKitAccess(workspaceDomainService.meetingAccessContext(meetingId, user.id()));

        try {
            String roomName = workspaceDomainService.meetingRoomName(meetingId);
            LiveKitTokenResponse token = liveKitTokenService.issueToken(roomName, user.id(), user.displayName());
            return new MeetingLiveKitTokenResponse(
                    token.serverUrl(),
                    token.participantToken(),
                    token.roomName(),
                    token.identity(),
                    token.name(),
                    LiveKitTokenService.TOKEN_EXPIRES_IN_SECONDS
            );
        } catch (IllegalStateException exception) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LIVEKIT_NOT_CONFIGURED",
                    exception.getMessage()
            );
        }
    }
}
