package com.meetingmind.demo.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExplainMeetingTermRequest(
        @NotBlank(message = "용어는 필수입니다.")
        @Size(max = 120, message = "용어는 120자 이하여야 합니다.")
        String term
) {
}
