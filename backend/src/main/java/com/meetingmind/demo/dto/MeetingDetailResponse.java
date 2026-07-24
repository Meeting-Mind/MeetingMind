package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingDetailResponse(
        String id,
        String spaceId,
        String title,
        String description,
        String status,
        String scheduledAt,
        String scheduledEndAt,
        String startedAt,
        String endedAt,
        String myRole,
        List<Participant> participants
) {

    public record Participant(
            String participantId,
            String userId,
            String displayName,
            String pictureUrl,
            String role,
            String participantType,
            String accessStatus
    ) {
    }
}
