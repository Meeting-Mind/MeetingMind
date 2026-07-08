package com.meetingmind.demo.dto;

public record TranscriptEntryResponse(
        String time,
        String speaker,
        String text
) {
}
