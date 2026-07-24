package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record SpaceInvitationListResponse(List<Invitation> invitations) {
    public record Invitation(String invitationId, String spaceId, String spaceName, String role, Instant expiresAt) {}
}
