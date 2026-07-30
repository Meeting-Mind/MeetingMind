package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record DomainTermListResponse(List<Term> terms) {
    public record Term(
            String id,
            String term,
            String definition,
            String status,
            Instant updatedAt,
            String source,
            String categoryId,
            String categoryName,
            boolean editable
    ) {
    }
}
