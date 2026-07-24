package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantType;
import java.time.Instant;

public record MeetingInvitation(
        String id,
        String meetingId,
        String email,
        MeetingRole meetingRole,
        ParticipantType participantType,
        InvitationStatus status,
        String tokenHash,
        Instant expiresAt,
        Instant acceptedAt,
        Instant declinedAt
) {
    MeetingInvitation accepted(Instant at) {
        return new MeetingInvitation(id, meetingId, email, meetingRole, participantType, InvitationStatus.ACCEPTED, tokenHash, expiresAt, at, null);
    }

    MeetingInvitation declined(Instant at) {
        return new MeetingInvitation(id, meetingId, email, meetingRole, participantType, InvitationStatus.DECLINED, tokenHash, expiresAt, null, at);
    }

    MeetingInvitation expired() {
        return new MeetingInvitation(id, meetingId, email, meetingRole, participantType, InvitationStatus.EXPIRED, tokenHash, expiresAt, null, null);
    }
}
