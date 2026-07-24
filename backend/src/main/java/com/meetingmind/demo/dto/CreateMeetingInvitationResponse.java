package com.meetingmind.demo.dto;

import java.time.Instant;

public record CreateMeetingInvitationResponse(String invitationId, String status, Instant expiresAt, String inviteToken) {
}
