package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.SpaceRole;
import java.time.Instant;

public record SpaceMember(
        String id,
        String spaceId,
        String userId,
        SpaceRole role,
        Instant joinedAt
) {
}
