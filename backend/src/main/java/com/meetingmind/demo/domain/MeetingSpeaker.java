package com.meetingmind.demo.domain;

import java.time.Instant;

public record MeetingSpeaker(
        String id,
        String meetingId,
        String label,
        String displayName,
        Instant createdAt
) {
}
