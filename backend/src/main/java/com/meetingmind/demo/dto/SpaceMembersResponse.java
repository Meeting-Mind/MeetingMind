package com.meetingmind.demo.dto;

import java.util.List;

public record SpaceMembersResponse(List<Member> members) {
    public record Member(
            String id,
            String userId,
            String displayName,
            String email,
            String pictureUrl,
            String role,
            String joinedAt
    ) {
    }
}
