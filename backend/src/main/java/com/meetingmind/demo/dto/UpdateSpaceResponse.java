package com.meetingmind.demo.dto;

import java.time.Instant;

public record UpdateSpaceResponse(String id, String name, String description, Instant updatedAt) {
}
