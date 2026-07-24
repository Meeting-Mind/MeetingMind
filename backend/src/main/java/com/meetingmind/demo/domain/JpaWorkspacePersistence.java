package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
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
        return entityManager.merge(space);
    }

    public Optional<Space> findSpace(String spaceId) {
        return first(entityManager.createQuery(
                        "select s from Space s where s.id = :spaceId and s.deletedAt is null", Space.class)
                .setParameter("spaceId", spaceId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toSpace);
    }

    public void lockSpace(String spaceId) {
        entityManager.find(Space.class, spaceId, LockModeType.PESSIMISTIC_WRITE);
    }

    public SpaceInvitation saveSpaceInvitation(SpaceInvitation invitation) {
        return entityManager.merge(invitation);
    }

    public Optional<SpaceInvitation> findSpaceInvitation(String spaceId, String invitationId) {
        return first(entityManager.createQuery(
                        "select i from SpaceInvitation i where i.spaceId = :spaceId and i.id = :invitationId",
                        SpaceInvitation.class)
                .setParameter("spaceId", spaceId)
                .setParameter("invitationId", invitationId)
                .setMaxResults(1)
                .getResultList());
    }

    public Optional<SpaceInvitation> findPendingSpaceInvitation(String spaceId, String email) {
        return first(entityManager.createQuery(
                        "select i from SpaceInvitation i where i.spaceId = :spaceId and lower(i.email) = lower(:email) "
                                + "and i.status = 'PENDING' order by i.id",
                        SpaceInvitation.class)
                .setParameter("spaceId", spaceId)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList());
    }

    public List<SpaceInvitation> findPendingSpaceInvitations(String email) {
        return entityManager.createQuery(
                        "select i from SpaceInvitation i where lower(i.email) = lower(:email) "
                                + "and i.status = 'PENDING' order by i.expiresAt, i.id",
                        SpaceInvitation.class)
                .setParameter("email", email)
                .getResultList();
    }

    public List<SpaceInvitation> findSpaceInvitations(String spaceId) {
        return entityManager.createQuery("select i from SpaceInvitation i where i.spaceId = :spaceId order by i.expiresAt desc, i.id", SpaceInvitation.class)
                .setParameter("spaceId", spaceId).getResultList();
    }

    public SpaceMember saveSpaceMember(SpaceMember member) {
        return entityManager.merge(member);
    }

    public Optional<SpaceMember> findSpaceMember(String spaceId, String userId) {
        return first(entityManager.createQuery(
                        "select sm from SpaceMember sm where sm.spaceId = :spaceId and sm.userId = :userId and sm.removedAt is null",
                        SpaceMember.class)
                .setParameter("spaceId", spaceId)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toSpaceMember);
    }

    public Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId) {
        return first(entityManager.createQuery(
                        "select sm from SpaceMember sm where sm.spaceId = :spaceId and sm.id = :memberId and sm.removedAt is null",
                        SpaceMember.class)
                .setParameter("spaceId", spaceId)
                .setParameter("memberId", memberId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toSpaceMember);
    }

    public List<SpaceMember> findSpaceMembersBySpaceId(String spaceId) {
        return entityManager.createQuery(
                        "select sm from SpaceMember sm where sm.spaceId = :spaceId and sm.removedAt is null order by sm.joinedAt, sm.id",
                        SpaceMember.class)
                .setParameter("spaceId", spaceId)
                .getResultList().stream().map(JpaWorkspacePersistence::toSpaceMember).toList();
    }

    public List<SpaceMember> findSpaceMembersByUserId(String userId) {
        return entityManager.createQuery(
                        "select sm from SpaceMember sm where sm.userId = :userId and sm.removedAt is null order by sm.joinedAt, sm.id",
                        SpaceMember.class)
                .setParameter("userId", userId)
                .getResultList().stream().map(JpaWorkspacePersistence::toSpaceMember).toList();
    }

    public SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role) {
        SpaceMember entity = entityManager.find(SpaceMember.class, memberId);
        if (entity == null || entity.removedAt != null) {
            throw new IllegalStateException("활성 SpaceMember를 찾을 수 없습니다.");
        }
        entity.role = role.name();
        return toSpaceMember(entity);
    }

    public void removeSpaceMember(String memberId, Instant removedAt) {
        SpaceMember entity = entityManager.find(SpaceMember.class, memberId);
        if (entity != null && entity.removedAt == null) {
            entity.removedAt = removedAt;
        }
    }

    public SpaceMember updateOwner(String memberId, SpaceRole role) {
        return updateSpaceMemberRole(memberId, role);
    }

    public Meeting saveMeeting(Meeting meeting, String joinCodeHash) {
        meeting.joinCodeHash = joinCodeHash;
        entityManager.persist(meeting);
        return meeting;
    }

    public Optional<Meeting> findMeeting(String meetingId) {
        return first(entityManager.createQuery(
                        "select m from Meeting m where m.id = :meetingId and m.deletedAt is null", Meeting.class)
                .setParameter("meetingId", meetingId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toMeeting);
    }

    public Optional<Meeting> findMeetingByJoinCodeHash(String joinCodeHash) {
        return first(entityManager.createQuery(
                        "select m from Meeting m where m.joinCodeHash = :joinCodeHash and m.deletedAt is null", Meeting.class)
                .setParameter("joinCodeHash", joinCodeHash)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toMeeting);
    }

    public long countMeetings(String spaceId) {
        return entityManager.createQuery(
                        "select count(m) from Meeting m where m.spaceId = :spaceId and m.deletedAt is null", Long.class)
                .setParameter("spaceId", spaceId)
                .getSingleResult();
    }

    public List<Meeting> findMeetings(String spaceId) {
        return entityManager.createQuery(
                        "select m from Meeting m where m.spaceId = :spaceId and m.deletedAt is null order by m.scheduledAt nulls last, m.id",
                        Meeting.class)
                .setParameter("spaceId", spaceId)
                .getResultList().stream().map(JpaWorkspacePersistence::toMeeting).toList();
    }

    public void lockMeeting(String meetingId) {
        entityManager.find(Meeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
    }

    public Meeting updateMeeting(
            String meetingId,
            String title,
            String description,
            OffsetDateTime scheduledAt,
            OffsetDateTime scheduledEndAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            MeetingStatus status
    ) {
        Meeting entity = entityManager.find(Meeting.class, meetingId);
        if (entity == null || entity.deletedAt != null) {
            throw new IllegalStateException("회의를 찾을 수 없습니다.");
        }
        entity.title = title;
        entity.description = description;
        entity.scheduledAt = scheduledAt;
        entity.scheduledEndAt = scheduledEndAt;
        entity.startedAt = startedAt;
        entity.endedAt = endedAt;
        entity.status = status.name();
        return toMeeting(entity);
    }

    public Meeting softDeleteMeeting(String meetingId, MeetingStatus status, String deletedBy, Instant deletedAt) {
        Meeting entity = entityManager.find(Meeting.class, meetingId);
        if (entity == null || entity.deletedAt != null) {
            throw new IllegalStateException("회의를 찾을 수 없습니다.");
        }
        entity.status = status.name();
        entity.deletedBy = deletedBy;
        entity.deletedAt = deletedAt;
        return toMeeting(entity);
    }

    public MeetingJoinRequest saveJoinRequest(MeetingJoinRequest request) {
        return entityManager.merge(request);
    }

    public Optional<MeetingJoinRequest> findJoinRequest(String meetingId, String requestId, boolean lock) {
        var query = entityManager.createQuery(
                        "select r from MeetingJoinRequest r where r.meetingId = :meetingId and r.id = :requestId",
                        MeetingJoinRequest.class)
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
                        "select r from MeetingJoinRequest r where r.meetingId = :meetingId order by r.requestedAt, r.id",
                        MeetingJoinRequest.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toJoinRequest).toList();
    }

    public MeetingJoinRequest updateJoinRequest(String requestId, MeetingJoinRequestStatus status, Instant reviewedAt, String reviewedBy) {
        MeetingJoinRequest entity = entityManager.find(MeetingJoinRequest.class, requestId);
        if (entity == null) {
            throw new IllegalStateException("참가 신청을 찾을 수 없습니다.");
        }
        entity.status = status.name();
        entity.reviewedAt = reviewedAt;
        entity.reviewedBy = reviewedBy;
        return toJoinRequest(entity);
    }

    public MeetingParticipant saveParticipant(MeetingParticipant participant) {
        return entityManager.merge(participant);
    }

    public Optional<MeetingParticipant> findParticipant(String meetingId, String userId) {
        return first(entityManager.createQuery(
                        "select p from MeetingParticipant p where p.meetingId = :meetingId and p.userId = :userId "
                                + "order by case when p.accessStatus = 'ACTIVE' then 0 else 1 end, p.id",
                        MeetingParticipant.class)
                .setParameter("meetingId", meetingId)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toParticipant);
    }

    public Optional<MeetingParticipant> findParticipantById(String meetingId, String participantId) {
        return first(entityManager.createQuery(
                        "select p from MeetingParticipant p where p.meetingId = :meetingId and p.id = :participantId",
                        MeetingParticipant.class)
                .setParameter("meetingId", meetingId)
                .setParameter("participantId", participantId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toParticipant);
    }

    public List<MeetingParticipant> findParticipants(String meetingId) {
        return entityManager.createQuery(
                        "select p from MeetingParticipant p where p.meetingId = :meetingId order by p.id",
                        MeetingParticipant.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toParticipant).toList();
    }

    public MeetingParticipant updateParticipant(String participantId, MeetingRole role, ParticipantAccessStatus accessStatus) {
        MeetingParticipant entity = entityManager.find(MeetingParticipant.class, participantId);
        if (entity == null) {
            throw new IllegalStateException("회의 참여자를 찾을 수 없습니다.");
        }
        entity.role = role.name();
        entity.accessStatus = accessStatus.name();
        return toParticipant(entity);
    }

    public MeetingParticipant updateParticipantType(String participantId, ParticipantType type) {
        MeetingParticipant entity = entityManager.find(MeetingParticipant.class, participantId);
        if (entity == null) {
            throw new IllegalStateException("회의 참여자를 찾을 수 없습니다.");
        }
        entity.participantType = type.name().toLowerCase();
        return toParticipant(entity);
    }

    public MeetingSpeaker saveSpeaker(MeetingSpeaker speaker) {
        return entityManager.merge(speaker);
    }

    public List<MeetingSpeaker> findSpeakers(String meetingId) {
        return entityManager.createQuery(
                        "select s from MeetingSpeaker s where s.meetingId = :meetingId order by s.createdAt, s.id",
                        MeetingSpeaker.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toSpeaker).toList();
    }

    public MeetingTranscript saveMeetingTranscript(MeetingTranscript transcript) {
        return entityManager.merge(transcript);
    }

    public Optional<MeetingTranscript> findMeetingTranscript(String meetingId) {
        MeetingTranscript entity = entityManager.find(MeetingTranscript.class, meetingId);
        return Optional.ofNullable(entity).map(JpaWorkspacePersistence::toMeetingTranscript);
    }

    public TranscriptSegment saveTranscriptSegment(TranscriptSegment segment) {
        return entityManager.merge(segment);
    }

    public List<TranscriptSegment> findTranscriptSegments(String meetingId) {
        return entityManager.createQuery(
                        "select s from TranscriptSegment s where s.meetingId = :meetingId order by s.sequence, s.id",
                        TranscriptSegment.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toTranscriptSegment).toList();
    }

    public MeetingReport saveMeetingReport(MeetingReport report) {
        boolean created = entityManager.find(MeetingReport.class, report.id()) == null;
        MeetingReport entity = entityManager.merge(report);
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
        return entity;
    }

    public Optional<MeetingReport> findMeetingReportById(String reportId) {
        return first(entityManager.createQuery("select r from MeetingReport r where r.id = :reportId", MeetingReport.class)
                .setParameter("reportId", reportId).setMaxResults(1).getResultList()).map(this::toMeetingReport);
    }

    public List<MeetingReport> findMeetingReports(String meetingId) {
        return entityManager.createQuery(
                        "select r from MeetingReport r where r.meetingId = :meetingId order by r.version, r.id",
                        MeetingReport.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(this::toMeetingReport).toList();
    }

    public TaskCandidate saveTaskCandidate(TaskCandidate candidate) {
        return entityManager.merge(candidate);
    }

    public Optional<TaskCandidate> findTaskCandidate(String candidateId, boolean lock) {
        var query = entityManager.createQuery("select c from TaskCandidate c where c.id = :candidateId", TaskCandidate.class)
                .setParameter("candidateId", candidateId).setMaxResults(1);
        if (lock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return first(query.getResultList()).map(JpaWorkspacePersistence::toTaskCandidate);
    }

    public List<TaskCandidate> findTaskCandidates(String meetingId) {
        return entityManager.createQuery(
                        "select c from TaskCandidate c where c.meetingId = :meetingId order by c.createdAt, c.id",
                        TaskCandidate.class)
                .setParameter("meetingId", meetingId)
                .getResultList().stream().map(JpaWorkspacePersistence::toTaskCandidate).toList();
    }

    public TaskCard saveTaskCard(TaskCard taskCard) {
        return entityManager.merge(taskCard);
    }

    public Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId) {
        return first(entityManager.createQuery(
                        "select c from TaskCard c where c.sourceCandidateId = :candidateId", TaskCard.class)
                .setParameter("candidateId", candidateId).setMaxResults(1).getResultList()).map(JpaWorkspacePersistence::toTaskCard);
    }

    public Optional<TaskCard> findTaskCardById(String spaceId, String taskId) {
        return first(entityManager.createQuery(
                        "select c from TaskCard c where c.spaceId = :spaceId and c.id = :taskId and c.deletedAt is null",
                        TaskCard.class)
                .setParameter("spaceId", spaceId)
                .setParameter("taskId", taskId)
                .setMaxResults(1)
                .getResultList()).map(JpaWorkspacePersistence::toTaskCard);
    }

    public List<TaskCard> findTaskCards(String spaceId) {
        return entityManager.createQuery(
                        "select c from TaskCard c where c.spaceId = :spaceId and c.deletedAt is null order by c.updatedAt desc, c.id",
                        TaskCard.class)
                .setParameter("spaceId", spaceId)
                .getResultList().stream().map(JpaWorkspacePersistence::toTaskCard).toList();
    }

    public void softDeleteTaskCard(String taskId, Instant deletedAt) {
        TaskCard entity = entityManager.find(TaskCard.class, taskId);
        if (entity != null && entity.deletedAt == null) {
            entity.deletedAt = deletedAt;
        }
    }

    public ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge) {
        return entityManager.merge(knowledge);
    }

    public List<ProjectKnowledge> findProjectKnowledge(String spaceId) {
        return entityManager.createQuery(
                        "select k from ProjectKnowledge k where k.spaceId = :spaceId order by k.updatedAt desc, k.id",
                        ProjectKnowledge.class)
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
        AuditEvent entity = new AuditEvent();
        entity.id = "audit-" + java.util.UUID.randomUUID();
        entity.spaceId = resolveAuditSpaceId(resourceId);
        entity.actorUserId = actorUserId;
        entity.action = type;
        entity.targetType = auditTargetType(type);
        entity.targetId = targetUserId == null || targetUserId.isBlank() ? resourceId : targetUserId;
        entity.beforeValue = auditValue(resourceId, beforeValue);
        entity.afterValue = auditValue(resourceId, afterValue);
        entity.createdAt = createdAt;
        entityManager.persist(entity);
        return entity;
    }

    private String resolveAuditSpaceId(String resourceId) {
        Space space = entityManager.find(Space.class, resourceId);
        if (space != null) {
            return space.id;
        }
        Meeting meeting = entityManager.find(Meeting.class, resourceId);
        if (meeting != null) {
            return meeting.spaceId;
        }
        TaskCard taskCard = entityManager.find(TaskCard.class, resourceId);
        if (taskCard != null) {
            return taskCard.spaceId;
        }
        MeetingReport report = entityManager.find(MeetingReport.class, resourceId);
        if (report != null) {
            Meeting reportMeeting = entityManager.find(Meeting.class, report.meetingId);
            if (reportMeeting != null) {
                return reportMeeting.spaceId;
            }
        }
        throw new IllegalStateException("감사 로그의 Space를 찾을 수 없습니다: " + resourceId);
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

    private MeetingReport toMeetingReport(MeetingReport report) {
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
        report.decisions = decisions;
        report.actionItems = actionItems;
        return report;
    }

    private static <T> Optional<T> first(List<T> rows) { return rows.stream().findFirst(); }
    private static Space toSpace(Space value) { return value; }
    private static SpaceMember toSpaceMember(SpaceMember value) { return value; }
    private static Meeting toMeeting(Meeting value) { return value; }
    private static MeetingJoinRequest toJoinRequest(MeetingJoinRequest value) { return value; }
    private static MeetingParticipant toParticipant(MeetingParticipant value) { return value; }
    private static MeetingSpeaker toSpeaker(MeetingSpeaker value) { return value; }
    private static MeetingTranscript toMeetingTranscript(MeetingTranscript value) { return value; }
    private static TranscriptSegment toTranscriptSegment(TranscriptSegment value) { return value; }
    private static TaskCandidate toTaskCandidate(TaskCandidate value) { return value; }
    private static TaskCard toTaskCard(TaskCard value) { return value; }
    private static ProjectKnowledge toProjectKnowledge(ProjectKnowledge value) { return value; }
}
