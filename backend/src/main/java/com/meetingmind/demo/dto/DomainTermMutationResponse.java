package com.meetingmind.demo.dto;

import java.time.Instant;

public record DomainTermMutationResponse(String id, String status, Instant updatedAt) {
}
