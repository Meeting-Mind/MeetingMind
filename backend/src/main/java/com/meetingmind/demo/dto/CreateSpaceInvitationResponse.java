package com.meetingmind.demo.dto;

import java.time.Instant;

public record CreateSpaceInvitationResponse(String invitationId, String status, Instant expiresAt, String inviteToken) {
}
