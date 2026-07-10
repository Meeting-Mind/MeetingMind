package com.meetingmind.demo.dto;

public record CreateSpaceResponse(
        String id,
        String name,
        String description,
        String role,
        String createdAt
) {
}
