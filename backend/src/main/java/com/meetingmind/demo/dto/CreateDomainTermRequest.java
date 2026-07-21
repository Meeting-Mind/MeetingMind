package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDomainTermRequest(@NotBlank String term, @NotBlank String definition) {
}
