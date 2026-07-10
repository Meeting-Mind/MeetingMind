package com.meetingmind.demo.dto;

import java.util.List;

public record SpaceListResponse(List<SpaceSummary> spaces) {

    public record SpaceSummary(
            String id,
            String name,
            String description,
            String role,
            long meetingCount,
            String updatedAt
    ) {
    }
}
