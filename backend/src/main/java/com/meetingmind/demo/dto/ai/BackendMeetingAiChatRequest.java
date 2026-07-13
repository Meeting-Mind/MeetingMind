package com.meetingmind.demo.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record BackendMeetingAiChatRequest(
        @NotBlank(message = "질문은 필수입니다.")
        String question
) {
}
