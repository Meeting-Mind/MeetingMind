package com.meetingmind.demo.service;

public record SttTranscriptChunk(
        String text,
        boolean finalChunk,
        int position,
        int startTimestamp,
        int endTimestamp
) {
}
