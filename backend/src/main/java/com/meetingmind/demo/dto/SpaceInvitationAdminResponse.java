package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record SpaceInvitationAdminResponse(List<Invitation> invitations) {
    public record Invitation(String invitationId, String email, String role, String status, Instant expiresAt) {}
}
