package com.meetingmind.demo.domain;

import java.time.Instant;

public record MeetingJoinRequest(
        String id,
        String meetingId,
        String userId,
        MeetingJoinRequestStatus status,
        Instant requestedAt,
        Instant reviewedAt,
        String reviewedBy
) {
}
