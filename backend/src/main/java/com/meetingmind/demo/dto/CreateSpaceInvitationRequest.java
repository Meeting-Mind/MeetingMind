package com.meetingmind.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSpaceInvitationRequest(@NotBlank @Email String email, @NotNull String role) {
}
