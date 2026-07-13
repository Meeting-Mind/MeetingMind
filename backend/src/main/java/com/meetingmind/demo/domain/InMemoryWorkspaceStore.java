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
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryWorkspaceStore {

    private final Map<String, User> usersById = new LinkedHashMap<>();
    private final Map<String, Space> spacesById = new LinkedHashMap<>();
    private final Map<String, SpaceMember> spaceMembersById = new LinkedHashMap<>();
    private final Map<String, Meeting> meetingsById = new LinkedHashMap<>();
    private final Map<String, MeetingParticipant> meetingParticipantsById = new LinkedHashMap<>();
    private final Map<String, MeetingSpeaker> meetingSpeakersById = new LinkedHashMap<>();
    private final Map<String, TranscriptSegment> transcriptSegmentsById = new LinkedHashMap<>();
    private final Map<String, MeetingReport> meetingReportsById = new LinkedHashMap<>();
    private final Map<String, TaskCandidate> taskCandidatesById = new LinkedHashMap<>();
    private final Map<String, TaskCard> taskCardsById = new LinkedHashMap<>();
    private final Map<String, ProjectKnowledge> projectKnowledgeById = new LinkedHashMap<>();
    private final Map<String, EmbeddingChunk> embeddingChunksById = new LinkedHashMap<>();

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
        return Optional.ofNullable(meetingsById.get(meetingId));
    }

    synchronized long countMeetingsBySpaceId(String spaceId) {
        return meetingsById.values()
                .stream()
                .filter(meeting -> meeting.spaceId().equals(spaceId))
                .count();
    }

    synchronized List<Meeting> findMeetingsBySpaceId(String spaceId) {
        return meetingsById.values()
                .stream()
                .filter(meeting -> meeting.spaceId().equals(spaceId))
                .toList();
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

    synchronized List<MeetingParticipant> findMeetingParticipants(String meetingId) {
        return meetingParticipantsById.values()
                .stream()
                .filter(participant -> participant.meetingId().equals(meetingId))
                .toList();
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
}
