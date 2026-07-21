package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public record CreateTaskCardRequest(
        @NotBlank String title,
        String description,
        String assigneeId,
        LocalDate dueDate,
        String meetingId,
        String priority,
        List<String> labels
) {
}
