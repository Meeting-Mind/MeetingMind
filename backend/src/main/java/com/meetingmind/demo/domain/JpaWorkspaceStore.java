package com.meetingmind.demo.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
class JpaWorkspaceStore extends DelegatingWorkspaceStore {

    private final JpaWorkspacePersistence persistence;

    JpaWorkspaceStore(JdbcTemplate jdbc, ObjectMapper objectMapper, JpaWorkspacePersistence persistence) {
        super(new JdbcWorkspaceStore(jdbc, objectMapper));
        this.persistence = persistence;
    }

    @Override
    Space createSpace(String name, String description, String createdBy, Instant now) {
        return persistence.saveSpace(new Space("space-" + UUID.randomUUID(), name, description, createdBy, now));
    }

    @Override
    Optional<Space> findSpaceById(String spaceId) {
        return persistence.findSpace(spaceId);
    }

    @Override
    Space updateSpace(String spaceId, String name, String description, Instant updatedAt) {
        Space current = persistence.findSpace(spaceId).orElseThrow();
        return persistence.saveSpace(current.updated(name, description, updatedAt));
    }

    @Override
    void softDeleteSpace(String spaceId, Instant deletedAt) {
        Space current = persistence.findSpace(spaceId).orElseThrow();
        persistence.saveSpace(current.deleted(deletedAt));
    }

    @Override
    SpaceMember addSpaceMember(String spaceId, String userId, SpaceRole role, Instant joinedAt) {
        return persistence.saveSpaceMember(new SpaceMember("space-member-" + UUID.randomUUID(), spaceId, userId, role, joinedAt));
    }

    @Override
    Optional<SpaceMember> findSpaceMember(String spaceId, String userId) {
        return persistence.findSpaceMember(spaceId, userId);
    }

    @Override
    Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId) {
        return persistence.findSpaceMemberById(spaceId, memberId);
    }

    @Override
    List<SpaceMember> findSpaceMembersBySpaceId(String spaceId) {
        return persistence.findSpaceMembersBySpaceId(spaceId);
    }

    @Override
    List<SpaceMember> findSpaceMembersByUserId(String userId) {
        return persistence.findSpaceMembersByUserId(userId);
    }

    @Override
    List<SpaceMember> findSpaceMembers(String spaceId) {
        return persistence.findSpaceMembersBySpaceId(spaceId);
    }

    @Override
    void lockSpace(String spaceId) {
        persistence.lockSpace(spaceId);
    }

    @Override
    SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role) {
        return persistence.updateSpaceMemberRole(memberId, role);
    }

    @Override
    void removeSpaceMember(String memberId) {
        persistence.removeSpaceMember(memberId, Instant.now());
    }

    @Override
    OwnerTransferUpdate transferOwner(String currentOwnerMemberId, String targetMemberId, SpaceRole previousOwnerRole) {
        SpaceMember previousOwner = persistence.updateOwner(currentOwnerMemberId, previousOwnerRole);
        SpaceMember newOwner = persistence.updateOwner(targetMemberId, SpaceRole.OWNER);
        return new OwnerTransferUpdate(newOwner, previousOwner);
    }

    @Override
    SpaceInvitation saveSpaceInvitation(SpaceInvitation invitation) {
        return persistence.saveSpaceInvitation(invitation);
    }

    @Override
    Optional<SpaceInvitation> findSpaceInvitationById(String spaceId, String invitationId) {
        return persistence.findSpaceInvitation(spaceId, invitationId);
    }

    @Override
    Optional<SpaceInvitation> findPendingSpaceInvitation(String spaceId, String email) {
        return persistence.findPendingSpaceInvitation(spaceId, email);
    }

    @Override
    Meeting createMeeting(String spaceId, String title, String description, OffsetDateTime scheduledAt, OffsetDateTime scheduledEndAt) {
        Meeting meeting = Meeting.scheduled("meeting-" + UUID.randomUUID(), spaceId, title, description, scheduledAt, scheduledEndAt);
        return persistence.saveMeeting(meeting, hashJoinCode(meeting.joinCode()));
    }

    @Override
    Optional<Meeting> findMeetingById(String meetingId) {
        return persistence.findMeeting(meetingId);
    }

    @Override
    Optional<Meeting> findMeetingByJoinCode(String joinCode) {
        return persistence.findMeetingByJoinCodeHash(hashJoinCode(joinCode));
    }

    @Override
    long countMeetingsBySpaceId(String spaceId) {
        return persistence.countMeetings(spaceId);
    }

    @Override
    List<Meeting> findMeetingsBySpaceId(String spaceId) {
        return persistence.findMeetings(spaceId);
    }

    @Override
    List<Meeting> findProjectAiMeetings(String spaceId, String userId) {
        Optional<SpaceMember> membership = persistence.findSpaceMember(spaceId, userId);
        if (membership.isEmpty()) {
            return List.of();
        }
        List<Meeting> meetings = persistence.findMeetings(spaceId);
        if (membership.get().role() == SpaceRole.OWNER || membership.get().role() == SpaceRole.ADMIN) {
            return meetings;
        }
        return meetings.stream()
                .filter(meeting -> persistence.findParticipant(meeting.id(), userId)
                        .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                        .isPresent())
                .toList();
    }

    @Override
    void lockMeeting(String meetingId) {
        persistence.lockMeeting(meetingId);
    }

    @Override
    Meeting updateMeeting(String meetingId, String title, String description, OffsetDateTime scheduledAt, OffsetDateTime scheduledEndAt, OffsetDateTime startedAt, OffsetDateTime endedAt, MeetingStatus status) {
        return persistence.updateMeeting(meetingId, title, description, scheduledAt, scheduledEndAt, startedAt, endedAt, status);
    }

    @Override
    Meeting softDeleteMeeting(String meetingId, MeetingStatus status, String deletedBy, Instant deletedAt) {
        return persistence.softDeleteMeeting(meetingId, status, deletedBy, deletedAt);
    }

    @Override
    MeetingJoinRequest createMeetingJoinRequest(String meetingId, String userId, Instant requestedAt) {
        return persistence.saveJoinRequest(new MeetingJoinRequest(
                "join-request-" + UUID.randomUUID(), meetingId, userId,
                MeetingJoinRequestStatus.PENDING, requestedAt, null, null
        ));
    }

    @Override
    Optional<MeetingJoinRequest> findMeetingJoinRequestById(String meetingId, String requestId) {
        return persistence.findJoinRequest(meetingId, requestId, false);
    }

    @Override
    Optional<MeetingJoinRequest> findMeetingJoinRequestByIdForUpdate(String meetingId, String requestId) {
        return persistence.findJoinRequest(meetingId, requestId, true);
    }

    @Override
    List<MeetingJoinRequest> findMeetingJoinRequests(String meetingId) {
        return persistence.findJoinRequests(meetingId);
    }

    @Override
    MeetingJoinRequest updateMeetingJoinRequest(String requestId, MeetingJoinRequestStatus status, Instant reviewedAt, String reviewedBy) {
        return persistence.updateJoinRequest(requestId, status, reviewedAt, reviewedBy);
    }

    @Override
    MeetingParticipant addMeetingParticipant(String meetingId, String userId, MeetingRole role, ParticipantType participantType) {
        return persistence.saveParticipant(new MeetingParticipant(
                "meeting-participant-" + UUID.randomUUID(), meetingId, userId, role, participantType,
                ParticipantAccessStatus.ACTIVE
        ));
    }

    @Override
    Optional<MeetingParticipant> findMeetingParticipant(String meetingId, String userId) {
        return persistence.findParticipant(meetingId, userId);
    }

    @Override
    Optional<MeetingParticipant> findMeetingParticipantById(String meetingId, String participantId) {
        return persistence.findParticipantById(meetingId, participantId);
    }

    @Override
    List<MeetingParticipant> findMeetingParticipants(String meetingId) {
        return persistence.findParticipants(meetingId);
    }

    @Override
    MeetingParticipant updateMeetingParticipant(String participantId, MeetingRole role, ParticipantAccessStatus accessStatus) {
        return persistence.updateParticipant(participantId, role, accessStatus);
    }

    @Override
    MeetingParticipant updateMeetingParticipantType(String participantId, ParticipantType participantType) {
        return persistence.updateParticipantType(participantId, participantType);
    }

    @Override
    MeetingSpeaker addMeetingSpeaker(String meetingId, String label, String displayName, Instant createdAt) {
        return persistence.saveSpeaker(new MeetingSpeaker("meeting-speaker-" + UUID.randomUUID(), meetingId, label, displayName, createdAt));
    }

    @Override
    List<MeetingSpeaker> findMeetingSpeakers(String meetingId) {
        return persistence.findSpeakers(meetingId);
    }

    @Override
    MeetingTranscript saveMeetingTranscript(MeetingTranscript transcript) {
        return persistence.saveMeetingTranscript(transcript);
    }

    @Override
    Optional<MeetingTranscript> findMeetingTranscript(String meetingId) {
        return persistence.findMeetingTranscript(meetingId);
    }

    @Override
    TranscriptSegment addTranscriptSegment(
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
        return persistence.saveTranscriptSegment(new TranscriptSegment(
                "transcript-segment-" + UUID.randomUUID(), meetingId, speakerId, speakerLabel,
                speakerName, startMs, endMs, text, source, sequence
        ));
    }

    @Override
    List<TranscriptSegment> findTranscriptSegments(String meetingId) {
        return persistence.findTranscriptSegments(meetingId);
    }

    @Override
    MeetingReport saveMeetingReport(MeetingReport report) {
        return persistence.saveMeetingReport(report);
    }

    @Override
    Optional<MeetingReport> findMeetingReportById(String reportId) {
        return persistence.findMeetingReportById(reportId);
    }

    @Override
    List<MeetingReport> findMeetingReports(String meetingId) {
        return persistence.findMeetingReports(meetingId);
    }

    @Override
    TaskCandidate saveTaskCandidate(TaskCandidate candidate) {
        return persistence.saveTaskCandidate(candidate);
    }

    @Override
    Optional<TaskCandidate> findTaskCandidateById(String candidateId) {
        return persistence.findTaskCandidate(candidateId, false);
    }

    @Override
    Optional<TaskCandidate> findTaskCandidateByIdForUpdate(String candidateId) {
        return persistence.findTaskCandidate(candidateId, true);
    }

    @Override
    List<TaskCandidate> findTaskCandidates(String meetingId) {
        return persistence.findTaskCandidates(meetingId);
    }

    @Override
    TaskCard saveTaskCard(TaskCard taskCard) {
        return persistence.saveTaskCard(taskCard);
    }

    @Override
    Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId) {
        return persistence.findTaskCardBySourceCandidateId(candidateId);
    }

    @Override
    Optional<TaskCard> findTaskCardById(String spaceId, String taskId) {
        return persistence.findTaskCardById(spaceId, taskId);
    }

    @Override
    List<TaskCard> findTaskCards(String spaceId) {
        return persistence.findTaskCards(spaceId);
    }

    @Override
    void softDeleteTaskCard(String taskId, Instant deletedAt) {
        persistence.softDeleteTaskCard(taskId, deletedAt);
    }

    @Override
    ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge) {
        return persistence.saveProjectKnowledge(knowledge);
    }

    @Override
    List<ProjectKnowledge> findProjectKnowledge(String spaceId) {
        return persistence.findProjectKnowledge(spaceId);
    }

    @Override
    AuditEvent addAuditEvent(
            String type,
            String actorUserId,
            String targetUserId,
            String resourceId,
            String beforeValue,
            String afterValue,
            Instant createdAt
    ) {
        return persistence.addAuditEvent(type, actorUserId, targetUserId, resourceId, beforeValue, afterValue, createdAt);
    }

    private static String hashJoinCode(String joinCode) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(joinCode.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("회의 참가 코드 hash 생성에 실패했습니다.", exception);
        }
    }
}
