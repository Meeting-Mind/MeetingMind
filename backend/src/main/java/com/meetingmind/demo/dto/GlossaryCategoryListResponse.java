package com.meetingmind.demo.dto;

import java.util.List;

public record GlossaryCategoryListResponse(List<Category> categories) {
    public record Category(String id, String slug, String name, String description) {
    }
}
