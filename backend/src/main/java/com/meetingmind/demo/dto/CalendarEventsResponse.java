package com.meetingmind.demo.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CalendarEventsResponse(List<Event> events) {
    public record Event(
            String id,
            String spaceId,
            String meetingId,
            String title,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String status
    ) {
    }
}
