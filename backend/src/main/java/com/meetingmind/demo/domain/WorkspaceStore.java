package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public abstract class WorkspaceStore {

    abstract User saveUser(User user);

    abstract Optional<User> findUserById(String userId);

    abstract Space createSpace(String name, String description, String createdBy, Instant now);

    abstract Optional<Space> findSpaceById(String spaceId);

    abstract Space updateSpace(String spaceId, String name, String description, Instant updatedAt);

    abstract void softDeleteSpace(String spaceId, Instant deletedAt);

    abstract SpaceMember addSpaceMember(String spaceId, String userId, SpaceRole role, Instant joinedAt);

    abstract Optional<SpaceMember> findSpaceMember(String spaceId, String userId);

    abstract Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId);

    abstract List<SpaceMember> findSpaceMembersBySpaceId(String spaceId);

    abstract List<SpaceMember> findSpaceMembersByUserId(String userId);

    abstract List<SpaceMember> findSpaceMembers(String spaceId);

    abstract void lockSpace(String spaceId);

    abstract SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role);

    abstract void removeSpaceMember(String memberId);

    abstract OwnerTransferUpdate transferOwner(
            String currentOwnerMemberId,
            String targetMemberId,
            SpaceRole previousOwnerRole
    );

    abstract SpaceInvitation saveSpaceInvitation(SpaceInvitation invitation);

    abstract Optional<SpaceInvitation> findSpaceInvitationById(String spaceId, String invitationId);

    abstract Optional<SpaceInvitation> findPendingSpaceInvitation(String spaceId, String email);

    abstract Meeting createMeeting(
            String spaceId, String title, String description, OffsetDateTime scheduledAt, OffsetDateTime scheduledEndAt
    );

    abstract Meeting createInstantMeeting(
            String spaceId,
            String roomCode,
            String title,
            String description,
            OffsetDateTime startedAt,
            OffsetDateTime scheduledEndAt
    );

    Meeting createMeeting(String spaceId, String title, OffsetDateTime scheduledAt) {
        return createMeeting(spaceId, title, null, scheduledAt, scheduledAt.plusHours(1));
    }

    abstract Optional<Meeting> findMeetingById(String meetingId);

    abstract Optional<Meeting> findMeetingByJoinCode(String joinCode);

    abstract long countMeetingsBySpaceId(String spaceId);

    abstract List<Meeting> findMeetingsBySpaceId(String spaceId);

    abstract List<Meeting> findProjectAiMeetings(String spaceId, String userId);

    abstract void lockMeeting(String meetingId);

    abstract Meeting updateMeeting(
            String meetingId,
            String title,
            String description,
            OffsetDateTime scheduledAt,
            OffsetDateTime scheduledEndAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            com.meetingmind.demo.authz.MeetingStatus status
    );

    abstract Meeting softDeleteMeeting(
            String meetingId,
            com.meetingmind.demo.authz.MeetingStatus status,
            String deletedBy,
            Instant deletedAt
    );

    abstract MeetingJoinRequest createMeetingJoinRequest(String meetingId, String userId, Instant requestedAt);

    abstract Optional<MeetingJoinRequest> findMeetingJoinRequestById(String meetingId, String requestId);

    abstract Optional<MeetingJoinRequest> findMeetingJoinRequestByIdForUpdate(String meetingId, String requestId);

    abstract List<MeetingJoinRequest> findMeetingJoinRequests(String meetingId);

    abstract MeetingJoinRequest updateMeetingJoinRequest(
            String requestId,
            MeetingJoinRequestStatus status,
            Instant reviewedAt,
            String reviewedBy
    );

    abstract MeetingParticipant addMeetingParticipant(
            String meetingId,
            String userId,
            MeetingRole role,
            ParticipantType participantType
    );

    abstract Optional<MeetingParticipant> findMeetingParticipant(String meetingId, String userId);

    abstract Optional<MeetingParticipant> findMeetingParticipantById(String meetingId, String participantId);

    abstract List<MeetingParticipant> findMeetingParticipants(String meetingId);

    abstract MeetingParticipant updateMeetingParticipant(
            String participantId,
            MeetingRole role,
            ParticipantAccessStatus accessStatus
    );

    abstract MeetingParticipant updateMeetingParticipantType(String participantId, ParticipantType participantType);

    abstract MeetingSpeaker addMeetingSpeaker(String meetingId, String label, String displayName, Instant createdAt);

    abstract List<MeetingSpeaker> findMeetingSpeakers(String meetingId);

    abstract MeetingTranscript saveMeetingTranscript(MeetingTranscript transcript);

    abstract Optional<MeetingTranscript> findMeetingTranscript(String meetingId);

    abstract TranscriptSegment addTranscriptSegment(
            String meetingId,
            String speakerId,
            String speakerLabel,
            String speakerName,
            int startMs,
            int endMs,
            String text,
            String source,
            int sequence
    );

    abstract List<TranscriptSegment> findTranscriptSegments(String meetingId);

    abstract MeetingReport saveMeetingReport(MeetingReport report);

    abstract Optional<MeetingReport> findMeetingReportById(String reportId);

    abstract List<MeetingReport> findMeetingReports(String meetingId);

    abstract TaskCandidate saveTaskCandidate(TaskCandidate candidate);

    abstract Optional<TaskCandidate> findTaskCandidateById(String candidateId);

    abstract Optional<TaskCandidate> findTaskCandidateByIdForUpdate(String candidateId);

    abstract List<TaskCandidate> findTaskCandidates(String meetingId);

    abstract TaskCard saveTaskCard(TaskCard taskCard);

    abstract Optional<TaskCard> findTaskCardById(String spaceId, String taskId);

    abstract List<TaskCard> findTaskCards(String spaceId);

    abstract void softDeleteTaskCard(String taskId, Instant deletedAt);

    abstract Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId);

    abstract ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge);

    abstract List<ProjectKnowledge> findProjectKnowledge(String spaceId);

    abstract AuditEvent addAuditEvent(
            String type,
            String actorUserId,
            String targetUserId,
            String resourceId,
            String beforeValue,
            String afterValue,
            Instant createdAt
    );

    public record OwnerTransferUpdate(SpaceMember newOwner, SpaceMember previousOwner) {
    }
}
