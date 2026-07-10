package com.meetingmind.demo.domain;

import java.time.Instant;

public record User(
        String id,
        String email,
        String displayName,
        String pictureUrl,
        String status,
        Instant createdAt,
        Instant lastLoginAt
) {
}
