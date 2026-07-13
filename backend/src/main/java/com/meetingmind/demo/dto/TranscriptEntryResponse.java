package com.meetingmind.demo.dto;

public record TranscriptEntryResponse(
        String time,
        String displayName,
        String text
) {
}
