package com.meetingmind.demo.domain;

import java.time.Instant;

public record AuditEvent(
        String id,
        String type,
        String actorUserId,
        String targetUserId,
        String resourceId,
        String beforeValue,
        String afterValue,
        Instant createdAt
) {
}
