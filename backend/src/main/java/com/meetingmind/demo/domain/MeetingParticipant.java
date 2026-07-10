package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;

public record MeetingParticipant(
        String id,
        String meetingId,
        String userId,
        MeetingRole role,
        ParticipantType participantType,
        ParticipantAccessStatus accessStatus
) {
}
