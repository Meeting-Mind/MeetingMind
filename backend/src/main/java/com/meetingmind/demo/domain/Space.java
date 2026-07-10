package com.meetingmind.demo.domain;

import java.time.Instant;

public record Space(
        String id,
        String name,
        String description,
        String createdBy,
        Instant createdAt
) {
}
