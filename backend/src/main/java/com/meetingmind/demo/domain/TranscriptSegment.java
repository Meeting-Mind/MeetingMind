package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "transcript_segments")
public class TranscriptSegment {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "speaker_id", nullable = false) String speakerId;
    @Column(name = "speaker_label", nullable = false) String speakerLabel;
    @Column(name = "speaker_name") String speakerName;
    @Column(nullable = false) int sequence;
    @Column(name = "start_ms", nullable = false) int startMs;
    @Column(name = "end_ms", nullable = false) int endMs;
    @Column(nullable = false) String text;
    @Column(nullable = false) String source;

    protected TranscriptSegment() {
    }

    public TranscriptSegment(String id, String meetingId, String speakerId, String speakerLabel, String speakerName,
                             int startMs, int endMs, String text, String source, int sequence) {
        this.id = id;
        this.meetingId = meetingId;
        this.speakerId = speakerId;
        this.speakerLabel = speakerLabel;
        this.speakerName = speakerName;
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
        this.source = source;
        this.sequence = sequence;
    }

    public String id() { return id; }
    public String meetingId() { return meetingId; }
    public String speakerId() { return speakerId; }
    public String speakerLabel() { return speakerLabel; }
    public String speakerName() { return speakerName; }
    public int startMs() { return startMs; }
    public int endMs() { return endMs; }
    public String text() { return text; }
    public String source() { return source; }
    public int sequence() { return sequence; }

    @Override public boolean equals(Object other) { return other instanceof TranscriptSegment value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
