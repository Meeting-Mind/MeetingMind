package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Meeting(
        String id,
        String spaceId,
        String title,
        OffsetDateTime scheduledAt,
        String joinCode,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        MeetingStatus status,
        String failureReason,
        String retentionPolicy,
        Instant deletedAt,
        String deletedBy
) {
    static Meeting scheduled(String id, String spaceId, String title, OffsetDateTime scheduledAt) {
        return new Meeting(
                id,
                spaceId,
                title,
                scheduledAt,
                UUID.randomUUID().toString().replace("-", ""),
                null,
                null,
                MeetingStatus.SCHEDULED,
                null,
                "DAYS_30",
                null,
                null
        );
    }

    boolean deleted() {
        return deletedAt != null;
    }
}
