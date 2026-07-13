package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMeetingJoinRequestRequest(
        @NotBlank @Size(max = 2048) String joinCodeOrUrl
) {
}
