package com.meetingmind.stt.dto;

import java.util.List;

public record TranscriptResponse(String meetingId, String status, List<Segment> segments) {

    public record Segment(
            String id, String speakerId, String speakerLabel, String speakerName,
            int startMs, int endMs, String text) {
    }
}
