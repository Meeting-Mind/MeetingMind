package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditReportWithAiRequest(
        @NotBlank(message = "편집 지시는 필수입니다.")
        @Size(max = 1000, message = "편집 지시는 1,000자 이하여야 합니다.")
        String instruction
) {
}
