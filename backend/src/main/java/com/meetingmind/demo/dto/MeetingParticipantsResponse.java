package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingParticipantsResponse(List<Participant> participants) {
    public record Participant(
            String id,
            String userId,
            String displayName,
            String role,
            String participantType,
            String accessStatus
    ) {
    }
}
