package com.meetingmind.demo.dto;

import java.time.LocalDate;
import java.util.List;

public record TaskListResponse(List<Task> tasks) {
    public record Task(
            String id,
            String spaceId,
            String meetingId,
            String title,
            String description,
            String status,
            String priority,
            List<String> labels,
            String assigneeId,
            LocalDate dueDate,
            String sourceCandidateId
    ) {
    }
}
