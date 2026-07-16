package com.meetingmind.demo.persistence.entity;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.domain.Meeting;
import com.meetingmind.demo.domain.MeetingJoinRequest;
import com.meetingmind.demo.domain.MeetingJoinRequestStatus;
import com.meetingmind.demo.domain.MeetingParticipant;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.MeetingSpeaker;
import com.meetingmind.demo.domain.MeetingTranscript;
import com.meetingmind.demo.domain.ProjectKnowledge;
import com.meetingmind.demo.domain.Space;
import com.meetingmind.demo.domain.SpaceMember;
import com.meetingmind.demo.domain.TaskCandidate;
import com.meetingmind.demo.domain.TaskCandidateStatus;
import com.meetingmind.demo.domain.TaskCard;
import com.meetingmind.demo.domain.TaskCardStatus;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.EmbeddingStatus;
import com.meetingmind.demo.domain.AuditEvent;
import com.meetingmind.demo.domain.KnowledgeStatus;
import com.meetingmind.demo.domain.KnowledgeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "db"})
public class JpaWorkspacePersistence {

    @PersistenceContext
    private EntityManager entityManager;

    public Space saveSpace(Space space) {
        SpaceEntity entity = new SpaceEntity();
        entity.id = space.id();
        entity.name = space.name();
        entity.description = space.description();
        entity.createdBy = space.createdBy();
        entity.createdAt = space.createdAt();
        entityManager.persist(entity);
        return space;
    }

    public Optional<Space> findSpace(String spaceId) {
        return first(entityManager.createQuery(
                        "select s from SpaceEntity s where s.id = :spaceId and s.deletedAt is null", SpaceEntity.class)
                .setParameter("spaceId", spaceId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toSpace);
    }

    public void lockSpace(String spaceId) {
        entityManager.find(SpaceEntity.class, spaceId, LockModeType.PESSIMISTIC_WRITE);
    }

    public SpaceMember saveSpaceMember(SpaceMember member) {
        SpaceMemberEntity entity = new SpaceMemberEntity();
        entity.id = member.id();
        entity.spaceId = member.spaceId();
        entity.userId = member.userId();
        entity.role = member.role().name();
        entity.joinedAt = member.joinedAt();
        entityManager.persist(entity);
        return member;
    }

    public Optional<SpaceMember> findSpaceMember(String spaceId, String userId) {
        return first(entityManager.createQuery(
                        "select sm from SpaceMemberEntity sm where sm.spaceId = :spaceId and sm.userId = :userId and sm.removedAt is null",
                        SpaceMemberEntity.class)
                .setParameter("spaceId", spaceId)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toSpaceMember);
    }

    public Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId) {
        return first(entityManager.createQuery(
                        "select sm from SpaceMemberEntity sm where sm.spaceId = :spaceId and sm.id = :memberId and sm.removedAt is null",
                        SpaceMemberEntity.class)
                .setParameter("spaceId", spaceId)
                .setParameter("memberId", memberId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toSpaceMember);
    }

    public List<SpaceMember> findSpaceMembersBySpaceId(String spaceId) {
        return entityManager.createQuery(
                        "select sm from SpaceMemberEntity sm where sm.spaceId = :spaceId and sm.removedAt is null order by sm.joinedAt, sm.id",
                        SpaceMemberEntity.class)
                .setParameter("spaceId", spaceId)
                .getResultList().stream().map(JpaWorkspacePersistence::toSpaceMember).toList();
    }

    public List<SpaceMember> findSpaceMembersByUserId(String userId) {
        return entityManager.createQuery(
                        "select sm from SpaceMemberEntity sm where sm.userId = :userId and sm.removedAt is null order by sm.joinedAt, sm.id",
                        SpaceMemberEntity.class)
                .setParameter("userId", userId)
                .getResultList().stream().map(JpaWorkspacePersistence::toSpaceMember).toList();
    }

    public SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role) {
        SpaceMemberEntity entity = entityManager.find(SpaceMemberEntity.class, memberId);
        if (entity == null || entity.removedAt != null) {
            throw new IllegalStateException("활성 SpaceMember를 찾을 수 없습니다.");
        }
        entity.role = role.name();
        return toSpaceMember(entity);
    }

    public void removeSpaceMember(String memberId, Instant removedAt) {
        SpaceMemberEntity entity = entityManager.find(SpaceMemberEntity.class, memberId);
        if (entity != null && entity.removedAt == null) {
            entity.removedAt = removedAt;
        }
    }

    public SpaceMember updateOwner(String memberId, SpaceRole role) {
        return updateSpaceMemberRole(memberId, role);
    }

    public Meeting saveMeeting(Meeting meeting, String joinCodeHash) {
        MeetingEntity entity = new MeetingEntity();
        entity.id = meeting.id();
        entity.spaceId = meeting.spaceId();
        entity.title = meeting.title();
        entity.scheduledAt = meeting.scheduledAt();
        entity.startedAt = meeting.startedAt();
        entity.endedAt = meeting.endedAt();
        entity.status = meeting.status().name();
        entity.failureReason = meeting.failureReason();
        entity.retentionPolicy = meeting.retentionPolicy();
        entity.joinCodeHash = joinCodeHash;
        entity.deletedAt = meeting.deletedAt();
        entity.deletedBy = meeting.deletedBy();
        entityManager.persist(entity);
        return meeting;
    }

    public Optional<Meeting> findMeeting(String meetingId) {
        return first(entityManager.createQuery(
                        "select m from MeetingEntity m where m.id = :meetingId and m.deletedAt is null", MeetingEntity.class)
                .setParameter("meetingId", meetingId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toMeeting);
    }

    public Optional<Meeting> findMeetingByJoinCodeHash(String joinCodeHash) {
        return first(entityManager.createQuery(
                        "select m from MeetingEntity m where m.joinCodeHash = :joinCodeHash and m.deletedAt is null", MeetingEntity.class)
                .setParameter("joinCodeHash", joinCodeHash)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toMeeting);
    }

    public long countMeetings(String spaceId) {
        return entityManager.createQuery(
                        "select count(m) from MeetingEntity m where m.spaceId = :spaceId and m.deletedAt is null", Long.class)
                .setParameter("spaceId", spaceId)
                .getSingleResult();
    }

    public List<Meeting> findMeetings(String spaceId) {
        return entityManager.createQuery(
                        "select m from MeetingEntity m where m.spaceId = :spaceId and m.deletedAt is null order by m.scheduledAt nulls last, m.id",
                        MeetingEntity.class)
                .setParameter("spaceId", spaceId)
                .getResultList().stream().map(JpaWorkspacePersistence::toMeeting).toList();
    }

    public void lockMeeting(String meetingId) {
        entityManager.find(MeetingEntity.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
    }

    public Meeting updateMeeting(
            String meetingId,
            String title,
            OffsetDateTime scheduledAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            MeetingStatus status
    ) {
        MeetingEntity entity = entityManager.find(MeetingEntity.class, meetingId);
        if (entity == null || entity.deletedAt != null) {
            throw new IllegalStateException("회의를 찾을 수 없습니다.");
        }
        entity.title = title;
        entity.scheduledAt = scheduledAt;
        entity.startedAt = startedAt;
        entity.endedAt = endedAt;
        entity.status = status.name();
        return toMeeting(entity);
    }

    public Meeting softDeleteMeeting(String meetingId, MeetingStatus status, String deletedBy, Instant deletedAt) {
        MeetingEntity entity = entityManager.find(MeetingEntity.class, meetingId);
        if (entity == null || entity.deletedAt != null) {
            throw new IllegalStateException("회의를 찾을 수 없습니다.");
        }
        entity.status = status.name();
        entity.deletedBy = deletedBy;
        entity.deletedAt = deletedAt;
        return toMeeting(entity);
    }

    public MeetingJoinRequest saveJoinRequest(MeetingJoinRequest request) {
        MeetingJoinRequestEntity entity = new MeetingJoinRequestEntity();
        entity.id = request.id();
        entity.meetingId = request.meetingId();
        entity.userId = request.userId();
        entity.status = request.status().name();
        entity.requestedAt = request.requestedAt();
        entity.reviewedAt = request.reviewedAt();
        entity.reviewedBy = request.reviewedBy();
        entityManager.persist(entity);
        return request;
    }

    public Optional<MeetingJoinRequest> findJoinRequest(String meetingId, String requestId, boolean lock) {
        var query = entityManager.createQuery(
                        "select r from MeetingJoinRequestEntity r where r.meetingId = :meetingId and r.id = :requestId",
                        MeetingJoinRequestEntity.class)
                .setParameter("meetingId", meetingId)
                .setParameter("requestId", requestId)
                .setMaxResults(1);
        if (lock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return first(query.getResultList()).map(JpaWorkspacePersistence::toJoinRequest);
    }

    public List<MeetingJoinRequest> findJoinRequests(String meetingId) {
        return entityManager.createQuery(
                        "select r from MeetingJoinRequestEntity r where r.meetingId = :meetingId order by r.requestedAt, r.id",
                        MeetingJoinRequestEntity.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toJoinRequest).toList();
    }

    public MeetingJoinRequest updateJoinRequest(String requestId, MeetingJoinRequestStatus status, Instant reviewedAt, String reviewedBy) {
        MeetingJoinRequestEntity entity = entityManager.find(MeetingJoinRequestEntity.class, requestId);
        if (entity == null) {
            throw new IllegalStateException("참가 신청을 찾을 수 없습니다.");
        }
        entity.status = status.name();
        entity.reviewedAt = reviewedAt;
        entity.reviewedBy = reviewedBy;
        return toJoinRequest(entity);
    }

    public MeetingParticipant saveParticipant(MeetingParticipant participant) {
        MeetingParticipantEntity entity = new MeetingParticipantEntity();
        entity.id = participant.id();
        entity.meetingId = participant.meetingId();
        entity.userId = participant.userId();
        entity.role = participant.role().name();
        entity.participantType = participant.participantType().name().toLowerCase();
        entity.accessStatus = participant.accessStatus().name();
        entityManager.persist(entity);
        return participant;
    }

    public Optional<MeetingParticipant> findParticipant(String meetingId, String userId) {
        return first(entityManager.createQuery(
                        "select p from MeetingParticipantEntity p where p.meetingId = :meetingId and p.userId = :userId "
                                + "order by case when p.accessStatus = 'ACTIVE' then 0 else 1 end, p.id",
                        MeetingParticipantEntity.class)
                .setParameter("meetingId", meetingId)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toParticipant);
    }

    public Optional<MeetingParticipant> findParticipantById(String meetingId, String participantId) {
        return first(entityManager.createQuery(
                        "select p from MeetingParticipantEntity p where p.meetingId = :meetingId and p.id = :participantId",
                        MeetingParticipantEntity.class)
                .setParameter("meetingId", meetingId)
                .setParameter("participantId", participantId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toParticipant);
    }

    public List<MeetingParticipant> findParticipants(String meetingId) {
        return entityManager.createQuery(
                        "select p from MeetingParticipantEntity p where p.meetingId = :meetingId order by p.id",
                        MeetingParticipantEntity.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toParticipant).toList();
    }

    public MeetingParticipant updateParticipant(String participantId, MeetingRole role, ParticipantAccessStatus accessStatus) {
        MeetingParticipantEntity entity = entityManager.find(MeetingParticipantEntity.class, participantId);
        if (entity == null) {
            throw new IllegalStateException("회의 참여자를 찾을 수 없습니다.");
        }
        entity.role = role.name();
        entity.accessStatus = accessStatus.name();
        return toParticipant(entity);
    }

    public MeetingParticipant updateParticipantType(String participantId, ParticipantType type) {
        MeetingParticipantEntity entity = entityManager.find(MeetingParticipantEntity.class, participantId);
        if (entity == null) {
            throw new IllegalStateException("회의 참여자를 찾을 수 없습니다.");
        }
        entity.participantType = type.name().toLowerCase();
        return toParticipant(entity);
    }

    public MeetingSpeaker saveSpeaker(MeetingSpeaker speaker) {
        MeetingSpeakerEntity entity = new MeetingSpeakerEntity();
        entity.id = speaker.id();
        entity.meetingId = speaker.meetingId();
        entity.label = speaker.label();
        entity.displayName = speaker.displayName();
        entity.createdAt = speaker.createdAt();
        entityManager.persist(entity);
        return speaker;
    }

    public List<MeetingSpeaker> findSpeakers(String meetingId) {
        return entityManager.createQuery(
                        "select s from MeetingSpeakerEntity s where s.meetingId = :meetingId order by s.createdAt, s.id",
                        MeetingSpeakerEntity.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toSpeaker).toList();
    }

    public MeetingTranscript saveMeetingTranscript(MeetingTranscript transcript) {
        MeetingTranscriptEntity entity = entityManager.find(MeetingTranscriptEntity.class, transcript.meetingId());
        boolean created = entity == null;
        if (created) {
            entity = new MeetingTranscriptEntity();
            entity.meetingId = transcript.meetingId();
        }
        entity.status = transcript.status().name();
        entity.provider = transcript.provider();
        entity.language = transcript.language();
        entity.startedAt = transcript.startedAt();
        entity.completedAt = transcript.completedAt();
        entity.failureReason = transcript.failureReason();
        entity.retentionUntil = transcript.retentionUntil();
        entity.legalHold = transcript.legalHold();
        entity.purgedAt = transcript.purgedAt();
        entity.createdAt = transcript.createdAt();
        entity.updatedAt = transcript.updatedAt();
        if (created) {
            entityManager.persist(entity);
        }
        return transcript;
    }

    public Optional<MeetingTranscript> findMeetingTranscript(String meetingId) {
        MeetingTranscriptEntity entity = entityManager.find(MeetingTranscriptEntity.class, meetingId);
        return Optional.ofNullable(entity).map(JpaWorkspacePersistence::toMeetingTranscript);
    }

    public TranscriptSegment saveTranscriptSegment(TranscriptSegment segment) {
        TranscriptSegmentEntity entity = new TranscriptSegmentEntity();
        entity.id = segment.id();
        entity.meetingId = segment.meetingId();
        entity.speakerId = segment.speakerId();
        entity.speakerLabel = segment.speakerLabel();
        entity.speakerName = segment.speakerName();
        entity.sequence = segment.sequence();
        entity.startMs = segment.startMs();
        entity.endMs = segment.endMs();
        entity.text = segment.text();
        entity.source = segment.source();
        entityManager.persist(entity);
        return segment;
    }

    public List<TranscriptSegment> findTranscriptSegments(String meetingId) {
        return entityManager.createQuery(
                        "select s from TranscriptSegmentEntity s where s.meetingId = :meetingId order by s.sequence, s.id",
                        TranscriptSegmentEntity.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toTranscriptSegment).toList();
    }

    public MeetingReport saveMeetingReport(MeetingReport report) {
        MeetingReportEntity entity = entityManager.find(MeetingReportEntity.class, report.id());
        boolean created = entity == null;
        if (entity == null) {
            entity = new MeetingReportEntity();
            entity.id = report.id();
        }
        entity.meetingId = report.meetingId();
        entity.status = report.status().name();
        entity.title = report.title();
        entity.summary = report.summary();
        entity.markdown = report.markdown();
        entity.sourceIds = report.sourceIds();
        entity.createdBy = report.createdBy();
        entity.version = report.version();
        entity.current = report.current();
        entity.createdAt = report.createdAt();
        entity.confirmedAt = report.confirmedAt();
        if (created) {
            entityManager.persist(entity);
        }
        if (created) {
            for (int index = 0; index < report.decisions().size(); index++) {
                MeetingReport.ReportDecision decision = report.decisions().get(index);
                ReportDecisionEntity decisionEntity = new ReportDecisionEntity();
                decisionEntity.id = decision.id();
                decisionEntity.reportId = report.id();
                decisionEntity.decisionOrder = index;
                decisionEntity.title = decision.title();
                decisionEntity.rationale = decision.content();
                decisionEntity.sourceIds = decision.sourceIds();
                entityManager.persist(decisionEntity);
            }
            for (int index = 0; index < report.actionItems().size(); index++) {
                MeetingReport.ReportActionItem item = report.actionItems().get(index);
                ReportActionItemEntity itemEntity = new ReportActionItemEntity();
                itemEntity.id = item.id();
                itemEntity.reportId = report.id();
                itemEntity.itemOrder = index;
                itemEntity.title = item.title();
                itemEntity.assigneeName = item.assigneeName();
                itemEntity.dueDate = item.dueDate() == null || item.dueDate().isBlank() ? null : LocalDate.parse(item.dueDate());
                itemEntity.confirmationState = report.status() == MeetingReportStatus.CONFIRMED ? "confirmed" : "candidate";
                itemEntity.sourceIds = item.sourceIds();
                entityManager.persist(itemEntity);
            }
        }
        return report;
    }

    public Optional<MeetingReport> findMeetingReportById(String reportId) {
        return first(entityManager.createQuery("select r from MeetingReportEntity r where r.id = :reportId", MeetingReportEntity.class)
                .setParameter("reportId", reportId).setMaxResults(1).getResultList()).map(this::toMeetingReport);
    }

    public List<MeetingReport> findMeetingReports(String meetingId) {
        return entityManager.createQuery(
                        "select r from MeetingReportEntity r where r.meetingId = :meetingId order by r.version, r.id",
                        MeetingReportEntity.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(this::toMeetingReport).toList();
    }

    public TaskCandidate saveTaskCandidate(TaskCandidate candidate) {
        TaskCandidateEntity entity = entityManager.find(TaskCandidateEntity.class, candidate.id());
        boolean created = entity == null;
        if (entity == null) {
            entity = new TaskCandidateEntity();
            entity.id = candidate.id();
        }
        entity.meetingId = candidate.meetingId();
        entity.title = candidate.title();
        entity.assigneeName = candidate.assigneeName();
        entity.suggestedAssigneeId = candidate.suggestedAssigneeId();
        entity.dueDate = candidate.dueDate();
        entity.status = candidate.status().name();
        entity.sourceIds = candidate.sourceIds();
        entity.createdBy = candidate.createdBy();
        entity.createdAt = candidate.createdAt();
        entity.confirmedAt = candidate.confirmedAt();
        if (created) {
            entityManager.persist(entity);
        }
        return candidate;
    }

    public Optional<TaskCandidate> findTaskCandidate(String candidateId, boolean lock) {
        var query = entityManager.createQuery("select c from TaskCandidateEntity c where c.id = :candidateId", TaskCandidateEntity.class)
                .setParameter("candidateId", candidateId).setMaxResults(1);
        if (lock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return first(query.getResultList()).map(JpaWorkspacePersistence::toTaskCandidate);
    }

    public List<TaskCandidate> findTaskCandidates(String meetingId) {
        return entityManager.createQuery(
                        "select c from TaskCandidateEntity c where c.meetingId = :meetingId order by c.createdAt, c.id",
                        TaskCandidateEntity.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toTaskCandidate).toList();
    }

    public TaskCard saveTaskCard(TaskCard taskCard) {
        TaskCardEntity entity = entityManager.find(TaskCardEntity.class, taskCard.id());
        boolean created = entity == null;
        if (entity == null) {
            entity = new TaskCardEntity();
            entity.id = taskCard.id();
        }
        entity.spaceId = taskCard.spaceId();
        entity.meetingId = taskCard.meetingId();
        entity.sourceCandidateId = taskCard.sourceCandidateId();
        entity.title = taskCard.title();
        entity.description = taskCard.description();
        entity.status = taskCard.status().name();
        entity.assigneeId = taskCard.assigneeId();
        entity.dueDate = taskCard.dueDate();
        entity.createdAt = taskCard.createdAt();
        entity.updatedAt = taskCard.updatedAt();
        if (created) {
            entityManager.persist(entity);
        }
        return taskCard;
    }

    public Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId) {
        return first(entityManager.createQuery(
                        "select c from TaskCardEntity c where c.sourceCandidateId = :candidateId", TaskCardEntity.class)
                .setParameter("candidateId", candidateId).setMaxResults(1).getResultList()).map(JpaWorkspacePersistence::toTaskCard);
    }

    public ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge) {
        ProjectKnowledgeEntity entity = entityManager.find(ProjectKnowledgeEntity.class, knowledge.id());
        boolean created = entity == null;
        if (entity == null) {
            entity = new ProjectKnowledgeEntity();
            entity.id = knowledge.id();
        }
        entity.spaceId = knowledge.spaceId();
        entity.type = knowledge.type().name().toLowerCase();
        entity.title = knowledge.title();
        entity.content = knowledge.content();
        entity.sourceMeetingId = knowledge.sourceMeetingId();
        entity.approvedBy = knowledge.approvedBy();
        entity.status = knowledge.status().name();
        entity.embeddingStatus = knowledge.embeddingStatus().name();
        entity.embeddingJobId = knowledge.embeddingJobId();
        entity.createdAt = knowledge.createdAt();
        entity.updatedAt = knowledge.updatedAt();
        entity.deletedAt = knowledge.deletedAt();
        if (created) {
            entityManager.persist(entity);
        }
        return knowledge;
    }

    public List<ProjectKnowledge> findProjectKnowledge(String spaceId) {
        return entityManager.createQuery(
                        "select k from ProjectKnowledgeEntity k where k.spaceId = :spaceId order by k.updatedAt desc, k.id",
                        ProjectKnowledgeEntity.class)
                .setParameter("spaceId", spaceId)
                .getResultList().stream().map(JpaWorkspacePersistence::toProjectKnowledge).toList();
    }

    public AuditEvent addAuditEvent(
            String type,
            String actorUserId,
            String targetUserId,
            String resourceId,
            String beforeValue,
            String afterValue,
            Instant createdAt
    ) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.id = "audit-" + java.util.UUID.randomUUID();
        entity.spaceId = resolveAuditSpaceId(resourceId);
        entity.actorUserId = actorUserId;
        entity.action = type;
        entity.targetType = auditTargetType(type);
        entity.targetId = targetUserId == null || targetUserId.isBlank() ? resourceId : targetUserId;
        entity.beforeValue = auditValue(resourceId, beforeValue);
        entity.afterValue = auditValue(resourceId, afterValue);
        entity.occurredAt = createdAt;
        entityManager.persist(entity);
        return new AuditEvent(entity.id, type, actorUserId, targetUserId, resourceId, beforeValue, afterValue, createdAt);
    }

    private String resolveAuditSpaceId(String resourceId) {
        SpaceEntity space = entityManager.find(SpaceEntity.class, resourceId);
        if (space != null) {
            return space.id;
        }
        MeetingEntity meeting = entityManager.find(MeetingEntity.class, resourceId);
        if (meeting == null) {
            throw new IllegalStateException("감사 로그의 Space를 찾을 수 없습니다: " + resourceId);
        }
        return meeting.spaceId;
    }

    private static Map<String, String> auditValue(String resourceId, String value) {
        return value == null ? null : Map.of("resourceId", resourceId, "value", value);
    }

    private static String auditTargetType(String action) {
        if (action.startsWith("OWNER") || action.startsWith("SPACE_MEMBER")) {
            return "SPACE_MEMBER";
        }
        if (action.startsWith("MEETING_JOIN_REQUEST")) {
            return "MEETING_JOIN_REQUEST";
        }
        if (action.startsWith("MEETING_PARTICIPANT")) {
            return "MEETING_PARTICIPANT";
        }
        return action.startsWith("MEETING_") ? "MEETING" : "RESOURCE";
    }

    private MeetingReport toMeetingReport(MeetingReportEntity report) {
        List<MeetingReport.ReportDecision> decisions = entityManager.createQuery(
                        "select d from ReportDecisionEntity d where d.reportId = :reportId order by d.decisionOrder",
                        ReportDecisionEntity.class)
                .setParameter("reportId", report.id)
                .getResultList().stream()
                .map(value -> new MeetingReport.ReportDecision(value.id, value.title, value.rationale, value.sourceIds))
                .toList();
        List<MeetingReport.ReportActionItem> actionItems = entityManager.createQuery(
                        "select a from ReportActionItemEntity a where a.reportId = :reportId order by a.itemOrder",
                        ReportActionItemEntity.class)
                .setParameter("reportId", report.id)
                .getResultList().stream()
                .map(value -> new MeetingReport.ReportActionItem(value.id, value.title, value.assigneeName,
                        value.dueDate == null ? null : value.dueDate.toString(), value.sourceIds))
                .toList();
        return new MeetingReport(report.id, report.meetingId, MeetingReportStatus.valueOf(report.status), report.title,
                report.summary, report.markdown, decisions, actionItems, report.sourceIds, report.createdBy,
                report.version, report.current, report.createdAt, report.confirmedAt);
    }

    private static <T> Optional<T> first(List<T> rows) { return rows.stream().findFirst(); }
    private static Space toSpace(SpaceEntity value) { return new Space(value.id, value.name, value.description, value.createdBy, value.createdAt); }
    private static SpaceMember toSpaceMember(SpaceMemberEntity value) { return new SpaceMember(value.id, value.spaceId, value.userId, SpaceRole.valueOf(value.role), value.joinedAt); }
    private static Meeting toMeeting(MeetingEntity value) { return new Meeting(value.id, value.spaceId, value.title, value.scheduledAt, null, value.startedAt, value.endedAt, MeetingStatus.valueOf(value.status), value.failureReason, value.retentionPolicy, value.deletedAt, value.deletedBy); }
    private static MeetingJoinRequest toJoinRequest(MeetingJoinRequestEntity value) { return new MeetingJoinRequest(value.id, value.meetingId, value.userId, MeetingJoinRequestStatus.valueOf(value.status), value.requestedAt, value.reviewedAt, value.reviewedBy); }
    private static MeetingParticipant toParticipant(MeetingParticipantEntity value) { return new MeetingParticipant(value.id, value.meetingId, value.userId, MeetingRole.valueOf(value.role), ParticipantType.valueOf(value.participantType.toUpperCase()), ParticipantAccessStatus.valueOf(value.accessStatus)); }
    private static MeetingSpeaker toSpeaker(MeetingSpeakerEntity value) { return new MeetingSpeaker(value.id, value.meetingId, value.label, value.displayName, value.createdAt); }
    private static MeetingTranscript toMeetingTranscript(MeetingTranscriptEntity value) { return new MeetingTranscript(value.meetingId, TranscriptStatus.valueOf(value.status), value.provider, value.language, value.startedAt, value.completedAt, value.failureReason, value.retentionUntil, value.legalHold, value.purgedAt, value.createdAt, value.updatedAt); }
    private static TranscriptSegment toTranscriptSegment(TranscriptSegmentEntity value) { return new TranscriptSegment(value.id, value.meetingId, value.speakerId, value.speakerLabel, value.speakerName, value.startMs, value.endMs, value.text, value.source, value.sequence); }
    private static TaskCandidate toTaskCandidate(TaskCandidateEntity value) { return new TaskCandidate(value.id, value.meetingId, value.title, value.assigneeName, value.suggestedAssigneeId, value.dueDate, TaskCandidateStatus.valueOf(value.status), value.sourceIds, value.createdBy, value.createdAt, value.confirmedAt); }
    private static TaskCard toTaskCard(TaskCardEntity value) { return new TaskCard(value.id, value.spaceId, value.meetingId, value.sourceCandidateId, value.title, value.description, TaskCardStatus.valueOf(value.status), value.assigneeId, value.dueDate, value.createdAt, value.updatedAt); }
    private static ProjectKnowledge toProjectKnowledge(ProjectKnowledgeEntity value) { return new ProjectKnowledge(value.id, value.spaceId, KnowledgeType.valueOf(value.type.toUpperCase()), value.title, value.content, value.sourceMeetingId, value.approvedBy, KnowledgeStatus.valueOf(value.status), EmbeddingStatus.valueOf(value.embeddingStatus), value.embeddingJobId, value.createdAt, value.updatedAt, value.deletedAt); }
}
