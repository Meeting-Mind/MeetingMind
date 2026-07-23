package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingDialogueResponse(String meetingId, String status, List<Row> rows, List<Partial> partials) {

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

    // STT가 아직 확정하지 않은, 현재 화자가 말하고 있는 중인 문장. DB에 저장되지 않으며 final이 오면 사라진다.
    public record Partial(
            String speakerLabel,
            String speakerName,
            String text
    ) {
    }
}
