package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateSpaceRequest(
        @NotBlank String name,
        String description,
        List<String> glossaryCategoryIds,
        List<String> customGlossaryCategories
) {
    public CreateSpaceRequest(String name, String description) {
        this(name, description, null, null);
    }
}
