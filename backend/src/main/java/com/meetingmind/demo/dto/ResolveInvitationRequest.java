package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveInvitationRequest(@NotBlank String token) {
}
