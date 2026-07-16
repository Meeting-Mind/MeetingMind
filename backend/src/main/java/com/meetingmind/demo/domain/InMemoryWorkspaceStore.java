package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class InMemoryWorkspaceStore extends WorkspaceStore {

    private final Map<String, User> usersById = new LinkedHashMap<>();
    private final Map<String, Space> spacesById = new LinkedHashMap<>();
    private final Map<String, SpaceMember> spaceMembersById = new LinkedHashMap<>();
    private final Map<String, Meeting> meetingsById = new LinkedHashMap<>();
    private final Map<String, MeetingParticipant> meetingParticipantsById = new LinkedHashMap<>();
    private final Map<String, MeetingSpeaker> meetingSpeakersById = new LinkedHashMap<>();
    private final Map<String, MeetingTranscript> meetingTranscriptsByMeetingId = new LinkedHashMap<>();
    private final Map<String, TranscriptSegment> transcriptSegmentsById = new LinkedHashMap<>();
    private final Map<String, MeetingReport> meetingReportsById = new LinkedHashMap<>();
    private final Map<String, TaskCandidate> taskCandidatesById = new LinkedHashMap<>();
    private final Map<String, TaskCard> taskCardsById = new LinkedHashMap<>();
    private final Map<String, ProjectKnowledge> projectKnowledgeById = new LinkedHashMap<>();
    private final Map<String, EmbeddingChunk> embeddingChunksById = new LinkedHashMap<>();
    private final Map<String, AuditEvent> auditEventsById = new LinkedHashMap<>();
    private final Map<String, MeetingJoinRequest> meetingJoinRequestsById = new LinkedHashMap<>();

    synchronized User saveUser(User user) {
        usersById.put(user.id(), user);
        return user;
    }

    synchronized Optional<User> findUserById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    synchronized Space createSpace(String name, String description, String createdBy, Instant now) {
        Space space = new Space(
                "space-" + UUID.randomUUID(),
                name,
                description,
                createdBy,
                now
        );
        spacesById.put(space.id(), space);
        return space;
    }

    synchronized Optional<Space> findSpaceById(String spaceId) {
        return Optional.ofNullable(spacesById.get(spaceId));
    }

    synchronized SpaceMember addSpaceMember(String spaceId, String userId, SpaceRole role, Instant joinedAt) {
        SpaceMember member = new SpaceMember(
                "space-member-" + UUID.randomUUID(),
                spaceId,
                userId,
                role,
                joinedAt
        );
        spaceMembersById.put(member.id(), member);
        return member;
    }

    synchronized Optional<SpaceMember> findSpaceMember(String spaceId, String userId) {
        return spaceMembersById.values()
                .stream()
                .filter(member -> member.spaceId().equals(spaceId) && member.userId().equals(userId))
                .findFirst();
    }

    synchronized Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId) {
        SpaceMember member = spaceMembersById.get(memberId);
        if (member == null || !member.spaceId().equals(spaceId)) {
            return Optional.empty();
        }
        return Optional.of(member);
    }

    synchronized List<SpaceMember> findSpaceMembersBySpaceId(String spaceId) {
        return spaceMembersById.values()
                .stream()
                .filter(member -> member.spaceId().equals(spaceId))
                .toList();
    }

    synchronized List<SpaceMember> findSpaceMembersByUserId(String userId) {
        return spaceMembersById.values()
                .stream()
                .filter(member -> member.userId().equals(userId))
                .toList();
    }

    synchronized List<SpaceMember> findSpaceMembers(String spaceId) {
        return spaceMembersById.values()
                .stream()
                .filter(member -> member.spaceId().equals(spaceId))
                .toList();
    }

    @Override
    synchronized void lockSpace(String spaceId) {
        // synchronized store methods already serialize in-memory mutations.
    }

    synchronized SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role) {
        SpaceMember current = spaceMembersById.get(memberId);
        SpaceMember updated = new SpaceMember(
                current.id(),
                current.spaceId(),
                current.userId(),
                role,
                current.joinedAt()
        );
        spaceMembersById.put(memberId, updated);
        return updated;
    }

    synchronized void removeSpaceMember(String memberId) {
        spaceMembersById.remove(memberId);
    }

    synchronized OwnerTransferUpdate transferOwner(
            String currentOwnerMemberId,
            String targetMemberId,
            SpaceRole previousOwnerRole
    ) {
        SpaceMember currentOwner = spaceMembersById.get(currentOwnerMemberId);
        SpaceMember target = spaceMembersById.get(targetMemberId);
        SpaceMember downgradedOwner = new SpaceMember(
                currentOwner.id(),
                currentOwner.spaceId(),
                currentOwner.userId(),
                previousOwnerRole,
                currentOwner.joinedAt()
        );
        SpaceMember newOwner = new SpaceMember(
                target.id(),
                target.spaceId(),
                target.userId(),
                SpaceRole.OWNER,
                target.joinedAt()
        );
        spaceMembersById.put(downgradedOwner.id(), downgradedOwner);
        spaceMembersById.put(newOwner.id(), newOwner);
        return new OwnerTransferUpdate(newOwner, downgradedOwner);
    }

    synchronized Meeting createMeeting(String spaceId, String title, OffsetDateTime scheduledAt) {
        Meeting meeting = Meeting.scheduled(
                "meeting-" + UUID.randomUUID(),
                spaceId,
                title,
                scheduledAt
        );
        meetingsById.put(meeting.id(), meeting);
        return meeting;
    }

    synchronized Optional<Meeting> findMeetingById(String meetingId) {
        return Optional.ofNullable(meetingsById.get(meetingId)).filter(meeting -> !meeting.deleted());
    }

    synchronized Optional<Meeting> findMeetingByJoinCode(String joinCode) {
        return meetingsById.values()
                .stream()
                .filter(meeting -> meeting.joinCode().equals(joinCode))
                .filter(meeting -> !meeting.deleted())
                .findFirst();
    }

    synchronized long countMeetingsBySpaceId(String spaceId) {
        return meetingsById.values()
                .stream()
                .filter(meeting -> meeting.spaceId().equals(spaceId))
                .filter(meeting -> !meeting.deleted())
                .count();
    }

    synchronized List<Meeting> findMeetingsBySpaceId(String spaceId) {
        return meetingsById.values()
                .stream()
                .filter(meeting -> meeting.spaceId().equals(spaceId))
                .filter(meeting -> !meeting.deleted())
                .toList();
    }

    @Override
    synchronized List<Meeting> findProjectAiMeetings(String spaceId, String userId) {
        Optional<SpaceMember> membership = findSpaceMember(spaceId, userId);
        if (membership.isEmpty()) {
            return List.of();
        }
        if (membership.get().role() == SpaceRole.OWNER || membership.get().role() == SpaceRole.ADMIN) {
            return findMeetingsBySpaceId(spaceId);
        }
        return findMeetingsBySpaceId(spaceId).stream()
                .filter(meeting -> findMeetingParticipant(meeting.id(), userId)
                        .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                        .isPresent())
                .toList();
    }

    @Override
    synchronized void lockMeeting(String meetingId) {
        // synchronized store methods already serialize in-memory mutations.
    }

    @Override
    synchronized Meeting updateMeeting(
            String meetingId,
            String title,
            OffsetDateTime scheduledAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            com.meetingmind.demo.authz.MeetingStatus status
    ) {
        Meeting current = findMeetingById(meetingId).orElseThrow();
        Meeting updated = new Meeting(
                current.id(), current.spaceId(), title, scheduledAt, current.joinCode(), startedAt, endedAt,
                status, current.failureReason(), current.retentionPolicy(), current.deletedAt(), current.deletedBy()
        );
        meetingsById.put(meetingId, updated);
        return updated;
    }

    @Override
    synchronized Meeting softDeleteMeeting(
            String meetingId,
            com.meetingmind.demo.authz.MeetingStatus status,
            String deletedBy,
            Instant deletedAt
    ) {
        Meeting current = meetingsById.get(meetingId);
        Meeting deleted = new Meeting(
                current.id(), current.spaceId(), current.title(), current.scheduledAt(), current.joinCode(),
                current.startedAt(), current.endedAt(), status, current.failureReason(), current.retentionPolicy(),
                deletedAt, deletedBy
        );
        meetingsById.put(meetingId, deleted);
        return deleted;
    }

    synchronized MeetingJoinRequest createMeetingJoinRequest(
            String meetingId,
            String userId,
            Instant requestedAt
    ) {
        MeetingJoinRequest request = new MeetingJoinRequest(
                "join-request-" + UUID.randomUUID(),
                meetingId,
                userId,
                MeetingJoinRequestStatus.PENDING,
                requestedAt,
                null,
                null
        );
        meetingJoinRequestsById.put(request.id(), request);
        return request;
    }

    synchronized Optional<MeetingJoinRequest> findMeetingJoinRequestById(String meetingId, String requestId) {
        MeetingJoinRequest request = meetingJoinRequestsById.get(requestId);
        if (request == null || !request.meetingId().equals(meetingId)) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    @Override
    synchronized Optional<MeetingJoinRequest> findMeetingJoinRequestByIdForUpdate(String meetingId, String requestId) {
        return findMeetingJoinRequestById(meetingId, requestId);
    }

    synchronized List<MeetingJoinRequest> findMeetingJoinRequests(String meetingId) {
        return meetingJoinRequestsById.values()
                .stream()
                .filter(request -> request.meetingId().equals(meetingId))
                .toList();
    }

    synchronized MeetingJoinRequest updateMeetingJoinRequest(
            String requestId,
            MeetingJoinRequestStatus status,
            Instant reviewedAt,
            String reviewedBy
    ) {
        MeetingJoinRequest current = meetingJoinRequestsById.get(requestId);
        MeetingJoinRequest updated = new MeetingJoinRequest(
                current.id(),
                current.meetingId(),
                current.userId(),
                status,
                current.requestedAt(),
                reviewedAt,
                reviewedBy
        );
        meetingJoinRequestsById.put(requestId, updated);
        return updated;
    }

    synchronized MeetingParticipant addMeetingParticipant(
            String meetingId,
            String userId,
            MeetingRole role,
            ParticipantType participantType
    ) {
        MeetingParticipant participant = new MeetingParticipant(
                "meeting-participant-" + UUID.randomUUID(),
                meetingId,
                userId,
                role,
                participantType,
                ParticipantAccessStatus.ACTIVE
        );
        meetingParticipantsById.put(participant.id(), participant);
        return participant;
    }

    synchronized Optional<MeetingParticipant> findMeetingParticipant(String meetingId, String userId) {
        return meetingParticipantsById.values()
                .stream()
                .filter(participant -> participant.meetingId().equals(meetingId) && participant.userId().equals(userId))
                .findFirst();
    }

    synchronized Optional<MeetingParticipant> findMeetingParticipantById(String meetingId, String participantId) {
        MeetingParticipant participant = meetingParticipantsById.get(participantId);
        if (participant == null || !participant.meetingId().equals(meetingId)) {
            return Optional.empty();
        }
        return Optional.of(participant);
    }

    synchronized List<MeetingParticipant> findMeetingParticipants(String meetingId) {
        return meetingParticipantsById.values()
                .stream()
                .filter(participant -> participant.meetingId().equals(meetingId))
                .toList();
    }

    synchronized MeetingParticipant updateMeetingParticipant(
            String participantId,
            MeetingRole role,
            ParticipantAccessStatus accessStatus
    ) {
        MeetingParticipant current = meetingParticipantsById.get(participantId);
        MeetingParticipant updated = new MeetingParticipant(
                current.id(),
                current.meetingId(),
                current.userId(),
                role,
                current.participantType(),
                accessStatus
        );
        meetingParticipantsById.put(participantId, updated);
        return updated;
    }

    synchronized MeetingParticipant updateMeetingParticipantType(
            String participantId,
            ParticipantType participantType
    ) {
        MeetingParticipant current = meetingParticipantsById.get(participantId);
        MeetingParticipant updated = new MeetingParticipant(
                current.id(),
                current.meetingId(),
                current.userId(),
                current.role(),
                participantType,
                current.accessStatus()
        );
        meetingParticipantsById.put(participantId, updated);
        return updated;
    }

    synchronized MeetingSpeaker addMeetingSpeaker(String meetingId, String label, String displayName, Instant createdAt) {
        MeetingSpeaker speaker = new MeetingSpeaker(
                "meeting-speaker-" + UUID.randomUUID(),
                meetingId,
                label,
                displayName,
                createdAt
        );
        meetingSpeakersById.put(speaker.id(), speaker);
        return speaker;
    }

    synchronized List<MeetingSpeaker> findMeetingSpeakers(String meetingId) {
        return new ArrayList<>(meetingSpeakersById.values())
                .stream()
                .filter(speaker -> speaker.meetingId().equals(meetingId))
                .toList();
    }

    @Override
    synchronized MeetingTranscript saveMeetingTranscript(MeetingTranscript transcript) {
        meetingTranscriptsByMeetingId.put(transcript.meetingId(), transcript);
        return transcript;
    }

    @Override
    synchronized Optional<MeetingTranscript> findMeetingTranscript(String meetingId) {
        return Optional.ofNullable(meetingTranscriptsByMeetingId.get(meetingId));
    }

    synchronized TranscriptSegment addTranscriptSegment(
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
        TranscriptSegment segment = new TranscriptSegment(
                "transcript-segment-" + UUID.randomUUID(),
                meetingId,
                speakerId,
                speakerLabel,
                speakerName,
                startMs,
                endMs,
                text,
                source,
                sequence
        );
        transcriptSegmentsById.put(segment.id(), segment);
        return segment;
    }

    synchronized List<TranscriptSegment> findTranscriptSegments(String meetingId) {
        return transcriptSegmentsById.values()
                .stream()
                .filter(segment -> segment.meetingId().equals(meetingId))
                .sorted(java.util.Comparator.comparingInt(TranscriptSegment::sequence))
                .toList();
    }

    synchronized MeetingReport saveMeetingReport(MeetingReport report) {
        meetingReportsById.put(report.id(), report);
        return report;
    }

    synchronized Optional<MeetingReport> findMeetingReportById(String reportId) {
        return Optional.ofNullable(meetingReportsById.get(reportId));
    }

    synchronized List<MeetingReport> findMeetingReports(String meetingId) {
        return meetingReportsById.values()
                .stream()
                .filter(report -> report.meetingId().equals(meetingId))
                .toList();
    }

    synchronized TaskCandidate saveTaskCandidate(TaskCandidate candidate) {
        taskCandidatesById.put(candidate.id(), candidate);
        return candidate;
    }

    synchronized Optional<TaskCandidate> findTaskCandidateById(String candidateId) {
        return Optional.ofNullable(taskCandidatesById.get(candidateId));
    }

    @Override
    synchronized Optional<TaskCandidate> findTaskCandidateByIdForUpdate(String candidateId) {
        return findTaskCandidateById(candidateId);
    }

    synchronized List<TaskCandidate> findTaskCandidates(String meetingId) {
        return taskCandidatesById.values()
                .stream()
                .filter(candidate -> candidate.meetingId().equals(meetingId))
                .toList();
    }

    synchronized TaskCard saveTaskCard(TaskCard taskCard) {
        taskCardsById.put(taskCard.id(), taskCard);
        return taskCard;
    }

    synchronized Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId) {
        return taskCardsById.values()
                .stream()
                .filter(taskCard -> candidateId.equals(taskCard.sourceCandidateId()))
                .findFirst();
    }

    synchronized ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge) {
        projectKnowledgeById.put(knowledge.id(), knowledge);
        return knowledge;
    }

    synchronized List<ProjectKnowledge> findProjectKnowledge(String spaceId) {
        return projectKnowledgeById.values()
                .stream()
                .filter(knowledge -> knowledge.spaceId().equals(spaceId))
                .toList();
    }

    synchronized EmbeddingChunk saveEmbeddingChunk(EmbeddingChunk chunk) {
        embeddingChunksById.put(chunk.id(), chunk);
        return chunk;
    }

    synchronized List<EmbeddingChunk> findEmbeddingChunksBySource(SourceType sourceType, String sourceId) {
        return embeddingChunksById.values()
                .stream()
                .filter(chunk -> chunk.sourceType() == sourceType && chunk.sourceId().equals(sourceId))
                .toList();
    }

    synchronized AuditEvent addAuditEvent(
            String type,
            String actorUserId,
            String targetUserId,
            String resourceId,
            String beforeValue,
            String afterValue,
            Instant createdAt
    ) {
        AuditEvent event = new AuditEvent(
                "audit-" + UUID.randomUUID(),
                type,
                actorUserId,
                targetUserId,
                resourceId,
                beforeValue,
                afterValue,
                createdAt
        );
        auditEventsById.put(event.id(), event);
        return event;
    }

    synchronized List<AuditEvent> findAuditEvents() {
        return List.copyOf(auditEventsById.values());
    }
}
