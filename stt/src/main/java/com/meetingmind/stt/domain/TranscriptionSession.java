package com.meetingmind.stt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Durable session state so a Pod restart or a stop request landing on a
 * different Pod can still find/close the session. The live CLOVA gRPC stream
 * object itself stays in-process (see SttSessionRegistry) — only the metadata
 * needed to look it up and to reconcile on restart lives here.
 */
@Entity
@Table(name = "transcription_sessions")
public class TranscriptionSession {
    @Id @Column(name = "session_id") String sessionId;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "room_name", nullable = false) String roomName;
    @Column(name = "track_id") String trackId;
    @Column(name = "egress_id") String egressId;
    @Column(nullable = false) String status;
    @Column(name = "request_id", unique = true) String requestId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected TranscriptionSession() {
    }

    public TranscriptionSession(
            String sessionId, String meetingId, String roomName, String trackId, String egressId,
            TranscriptionSessionStatus status, String requestId, Instant createdAt, Instant updatedAt) {
        this.sessionId = sessionId;
        this.meetingId = meetingId;
        this.roomName = roomName;
        this.trackId = trackId;
        this.egressId = egressId;
        this.status = status.name();
        this.requestId = requestId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String sessionId() { return sessionId; }
    public String meetingId() { return meetingId; }
    public String roomName() { return roomName; }
    public String trackId() { return trackId; }
    public String egressId() { return egressId; }
    public TranscriptionSessionStatus status() { return TranscriptionSessionStatus.valueOf(status); }
    public String requestId() { return requestId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void setTrackId(String trackId) { this.trackId = trackId; }
    public void setEgressId(String egressId) { this.egressId = egressId; }
    public void setStatus(TranscriptionSessionStatus status) { this.status = status.name(); }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override public boolean equals(Object other) { return other instanceof TranscriptionSession value && Objects.equals(sessionId, value.sessionId); }
    @Override public int hashCode() { return Objects.hashCode(sessionId); }
}
