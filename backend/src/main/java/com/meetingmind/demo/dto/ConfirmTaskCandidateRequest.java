package com.meetingmind.demo.dto;

public record ConfirmTaskCandidateRequest(
        String title,
        String description,
        String assigneeId,
        String dueDate,
        String status
) {
}
