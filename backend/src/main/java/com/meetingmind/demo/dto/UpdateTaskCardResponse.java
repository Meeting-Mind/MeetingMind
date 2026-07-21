package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record UpdateTaskCardResponse(String id, String status, String priority, List<String> labels, Instant updatedAt) {
}
