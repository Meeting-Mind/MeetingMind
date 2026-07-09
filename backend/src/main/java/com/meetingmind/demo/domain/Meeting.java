package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingStatus;
import java.time.OffsetDateTime;

public record Meeting(
        String id,
        String spaceId,
        String title,
        OffsetDateTime scheduledAt,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        MeetingStatus status,
        String failureReason,
        String retentionPolicy
) {
    static Meeting scheduled(String id, String spaceId, String title, OffsetDateTime scheduledAt) {
        return new Meeting(
                id,
                spaceId,
                title,
                scheduledAt,
                null,
                null,
                MeetingStatus.SCHEDULED,
                null,
                null
        );
    }
}
