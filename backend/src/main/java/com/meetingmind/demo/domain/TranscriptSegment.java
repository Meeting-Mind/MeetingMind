package com.meetingmind.demo.domain;

public record TranscriptSegment(
        String id,
        String meetingId,
        String speakerId,
        String speakerLabel,
        String speakerName,
        int startMs,
        int endMs,
        String text,
        String source,
        int sequence
) {
}
