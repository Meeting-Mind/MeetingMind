package com.meetingmind.demo.domain;

import java.util.Locale;

public enum AiUsageFeature {
    MEETING_AI("meeting-ai"),
    PROJECT_AI("project-ai"),
    REPORT_AI("report-ai");

    private final String apiValue;

    AiUsageFeature(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static AiUsageFeature parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI usage feature 값이 비어 있습니다.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AiUsageFeature feature : values()) {
            if (feature.apiValue.equals(normalized)) {
                return feature;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 AI usage feature 값입니다: " + value);
    }
}
