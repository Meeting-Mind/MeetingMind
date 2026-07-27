package com.meetingmind.demo.dto.stt;

import com.meetingmind.demo.domain.TranscriptStatus;
import java.util.List;

public record MeetingTranscriptGatewayResponse(
        String meetingId,
        TranscriptStatus status,
        List<Segment> segments
) {

    public MeetingTranscriptGatewayResponse {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public record Segment(
            String id,
            String speakerId,
            String speakerLabel,
            String speakerName,
            long startMs,
            long endMs,
            String text
    ) {
    }
}
