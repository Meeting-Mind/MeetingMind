package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingDialogueResponse(String meetingId, String status, List<Row> rows) {

    public record Row(
            String segmentId,
            String speakerId,
            String speakerLabel,
            String speakerName,
            int startMs,
            int endMs,
            String text
    ) {
    }
}
