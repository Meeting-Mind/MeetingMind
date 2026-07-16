package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

abstract class DelegatingWorkspaceStore extends WorkspaceStore {

    protected final WorkspaceStore delegate;

    DelegatingWorkspaceStore(WorkspaceStore delegate) {
        this.delegate = delegate;
    }

    @Override User saveUser(User user) { return delegate.saveUser(user); }
    @Override Optional<User> findUserById(String userId) { return delegate.findUserById(userId); }
    @Override Space createSpace(String name, String description, String createdBy, Instant now) { return delegate.createSpace(name, description, createdBy, now); }
    @Override Optional<Space> findSpaceById(String spaceId) { return delegate.findSpaceById(spaceId); }
    @Override SpaceMember addSpaceMember(String spaceId, String userId, SpaceRole role, Instant joinedAt) { return delegate.addSpaceMember(spaceId, userId, role, joinedAt); }
    @Override Optional<SpaceMember> findSpaceMember(String spaceId, String userId) { return delegate.findSpaceMember(spaceId, userId); }
    @Override Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId) { return delegate.findSpaceMemberById(spaceId, memberId); }
    @Override List<SpaceMember> findSpaceMembersBySpaceId(String spaceId) { return delegate.findSpaceMembersBySpaceId(spaceId); }
    @Override List<SpaceMember> findSpaceMembersByUserId(String userId) { return delegate.findSpaceMembersByUserId(userId); }
    @Override List<SpaceMember> findSpaceMembers(String spaceId) { return delegate.findSpaceMembers(spaceId); }
    @Override void lockSpace(String spaceId) { delegate.lockSpace(spaceId); }
    @Override SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role) { return delegate.updateSpaceMemberRole(memberId, role); }
    @Override void removeSpaceMember(String memberId) { delegate.removeSpaceMember(memberId); }
    @Override OwnerTransferUpdate transferOwner(String currentOwnerMemberId, String targetMemberId, SpaceRole previousOwnerRole) { return delegate.transferOwner(currentOwnerMemberId, targetMemberId, previousOwnerRole); }
    @Override Meeting createMeeting(String spaceId, String title, OffsetDateTime scheduledAt) { return delegate.createMeeting(spaceId, title, scheduledAt); }
    @Override Optional<Meeting> findMeetingById(String meetingId) { return delegate.findMeetingById(meetingId); }
    @Override Optional<Meeting> findMeetingByJoinCode(String joinCode) { return delegate.findMeetingByJoinCode(joinCode); }
    @Override long countMeetingsBySpaceId(String spaceId) { return delegate.countMeetingsBySpaceId(spaceId); }
    @Override List<Meeting> findMeetingsBySpaceId(String spaceId) { return delegate.findMeetingsBySpaceId(spaceId); }
    @Override List<Meeting> findProjectAiMeetings(String spaceId, String userId) { return delegate.findProjectAiMeetings(spaceId, userId); }
    @Override void lockMeeting(String meetingId) { delegate.lockMeeting(meetingId); }
    @Override Meeting updateMeeting(String meetingId, String title, OffsetDateTime scheduledAt, OffsetDateTime startedAt, OffsetDateTime endedAt, MeetingStatus status) { return delegate.updateMeeting(meetingId, title, scheduledAt, startedAt, endedAt, status); }
    @Override Meeting softDeleteMeeting(String meetingId, MeetingStatus status, String deletedBy, Instant deletedAt) { return delegate.softDeleteMeeting(meetingId, status, deletedBy, deletedAt); }
    @Override MeetingJoinRequest createMeetingJoinRequest(String meetingId, String userId, Instant requestedAt) { return delegate.createMeetingJoinRequest(meetingId, userId, requestedAt); }
    @Override Optional<MeetingJoinRequest> findMeetingJoinRequestById(String meetingId, String requestId) { return delegate.findMeetingJoinRequestById(meetingId, requestId); }
    @Override Optional<MeetingJoinRequest> findMeetingJoinRequestByIdForUpdate(String meetingId, String requestId) { return delegate.findMeetingJoinRequestByIdForUpdate(meetingId, requestId); }
    @Override List<MeetingJoinRequest> findMeetingJoinRequests(String meetingId) { return delegate.findMeetingJoinRequests(meetingId); }
    @Override MeetingJoinRequest updateMeetingJoinRequest(String requestId, MeetingJoinRequestStatus status, Instant reviewedAt, String reviewedBy) { return delegate.updateMeetingJoinRequest(requestId, status, reviewedAt, reviewedBy); }
    @Override MeetingParticipant addMeetingParticipant(String meetingId, String userId, MeetingRole role, ParticipantType participantType) { return delegate.addMeetingParticipant(meetingId, userId, role, participantType); }
    @Override Optional<MeetingParticipant> findMeetingParticipant(String meetingId, String userId) { return delegate.findMeetingParticipant(meetingId, userId); }
    @Override Optional<MeetingParticipant> findMeetingParticipantById(String meetingId, String participantId) { return delegate.findMeetingParticipantById(meetingId, participantId); }
    @Override List<MeetingParticipant> findMeetingParticipants(String meetingId) { return delegate.findMeetingParticipants(meetingId); }
    @Override MeetingParticipant updateMeetingParticipant(String participantId, MeetingRole role, ParticipantAccessStatus accessStatus) { return delegate.updateMeetingParticipant(participantId, role, accessStatus); }
    @Override MeetingParticipant updateMeetingParticipantType(String participantId, ParticipantType participantType) { return delegate.updateMeetingParticipantType(participantId, participantType); }
    @Override MeetingSpeaker addMeetingSpeaker(String meetingId, String label, String displayName, Instant createdAt) { return delegate.addMeetingSpeaker(meetingId, label, displayName, createdAt); }
    @Override List<MeetingSpeaker> findMeetingSpeakers(String meetingId) { return delegate.findMeetingSpeakers(meetingId); }
    @Override MeetingTranscript saveMeetingTranscript(MeetingTranscript transcript) { return delegate.saveMeetingTranscript(transcript); }
    @Override Optional<MeetingTranscript> findMeetingTranscript(String meetingId) { return delegate.findMeetingTranscript(meetingId); }
    @Override TranscriptSegment addTranscriptSegment(String meetingId, String speakerId, String speakerLabel, String speakerName, int startMs, int endMs, String text, String source, int sequence) { return delegate.addTranscriptSegment(meetingId, speakerId, speakerLabel, speakerName, startMs, endMs, text, source, sequence); }
    @Override List<TranscriptSegment> findTranscriptSegments(String meetingId) { return delegate.findTranscriptSegments(meetingId); }
    @Override MeetingReport saveMeetingReport(MeetingReport report) { return delegate.saveMeetingReport(report); }
    @Override Optional<MeetingReport> findMeetingReportById(String reportId) { return delegate.findMeetingReportById(reportId); }
    @Override List<MeetingReport> findMeetingReports(String meetingId) { return delegate.findMeetingReports(meetingId); }
    @Override TaskCandidate saveTaskCandidate(TaskCandidate candidate) { return delegate.saveTaskCandidate(candidate); }
    @Override Optional<TaskCandidate> findTaskCandidateById(String candidateId) { return delegate.findTaskCandidateById(candidateId); }
    @Override Optional<TaskCandidate> findTaskCandidateByIdForUpdate(String candidateId) { return delegate.findTaskCandidateByIdForUpdate(candidateId); }
    @Override List<TaskCandidate> findTaskCandidates(String meetingId) { return delegate.findTaskCandidates(meetingId); }
    @Override TaskCard saveTaskCard(TaskCard taskCard) { return delegate.saveTaskCard(taskCard); }
    @Override Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId) { return delegate.findTaskCardBySourceCandidateId(candidateId); }
    @Override ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge) { return delegate.saveProjectKnowledge(knowledge); }
    @Override List<ProjectKnowledge> findProjectKnowledge(String spaceId) { return delegate.findProjectKnowledge(spaceId); }
    @Override AuditEvent addAuditEvent(String type, String actorUserId, String targetUserId, String resourceId, String beforeValue, String afterValue, Instant createdAt) { return delegate.addAuditEvent(type, actorUserId, targetUserId, resourceId, beforeValue, afterValue, createdAt); }
}
