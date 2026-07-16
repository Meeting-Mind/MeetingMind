package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "meeting_participants")
public class MeetingParticipant {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String role;
    @Column(name = "participant_type", nullable = false) String participantType;
    @Column(name = "access_status", nullable = false) String accessStatus;

    protected MeetingParticipant() {
    }

    public MeetingParticipant(String id, String meetingId, String userId, MeetingRole role,
                              ParticipantType participantType, ParticipantAccessStatus accessStatus) {
        this.id = id;
        this.meetingId = meetingId;
        this.userId = userId;
        this.role = role.name();
        this.participantType = participantType.name().toLowerCase();
        this.accessStatus = accessStatus.name();
    }

    public String id() { return id; }
    public String meetingId() { return meetingId; }
    public String userId() { return userId; }
    public MeetingRole role() { return MeetingRole.valueOf(role); }
    public ParticipantType participantType() { return ParticipantType.valueOf(participantType.toUpperCase()); }
    public ParticipantAccessStatus accessStatus() { return ParticipantAccessStatus.valueOf(accessStatus); }

    @Override public boolean equals(Object other) { return other instanceof MeetingParticipant value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
