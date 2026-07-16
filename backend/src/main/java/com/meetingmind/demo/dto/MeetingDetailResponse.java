package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingDetailResponse(
        String id,
        String spaceId,
        String title,
        String status,
        String scheduledAt,
        String startedAt,
        String endedAt,
        String myRole,
        List<Participant> participants
) {

    public record Participant(
            String participantId,
            String userId,
            String displayName,
            String role,
            String participantType,
            String accessStatus
    ) {
    }
}
