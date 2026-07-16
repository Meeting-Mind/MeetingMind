package com.meetingmind.demo.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
public class JdbcWorkspaceStore extends WorkspaceStore {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcWorkspaceStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    User saveUser(User user) {
        jdbc.update(
                """
                insert into users (id, email, display_name, picture_url, status, created_at, last_login_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    email = excluded.email,
                    display_name = excluded.display_name,
                    picture_url = excluded.picture_url,
                    status = excluded.status,
                    last_login_at = excluded.last_login_at
                """,
                user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status(),
                timestamp(user.createdAt()), timestamp(user.lastLoginAt())
        );
        return user;
    }

    @Override
    Optional<User> findUserById(String userId) {
        return first(jdbc.query(
                """
                select id, email, display_name, picture_url, status, created_at, last_login_at
                from users where id = ?
                """,
                JdbcWorkspaceStore::mapUser,
                userId
        ));
    }

    @Override
    Space createSpace(String name, String description, String createdBy, Instant now) {
        Space space = new Space("space-" + UUID.randomUUID(), name, description, createdBy, now);
        jdbc.update(
                "insert into spaces (id, name, description, created_by, created_at) values (?, ?, ?, ?, ?)",
                space.id(), space.name(), space.description(), space.createdBy(), timestamp(space.createdAt())
        );
        return space;
    }

    @Override
    Optional<Space> findSpaceById(String spaceId) {
        return first(jdbc.query(
                """
                select id, name, description, created_by, created_at
                from spaces where id = ? and deleted_at is null
                """,
                JdbcWorkspaceStore::mapSpace,
                spaceId
        ));
    }

    @Override
    SpaceMember addSpaceMember(String spaceId, String userId, SpaceRole role, Instant joinedAt) {
        SpaceMember member = new SpaceMember(
                "space-member-" + UUID.randomUUID(), spaceId, userId, role, joinedAt
        );
        jdbc.update(
                """
                insert into space_members (id, space_id, user_id, role, joined_at)
                values (?, ?, ?, ?, ?)
                """,
                member.id(), member.spaceId(), member.userId(), member.role().name(), timestamp(member.joinedAt())
        );
        return member;
    }

    @Override
    Optional<SpaceMember> findSpaceMember(String spaceId, String userId) {
        return first(jdbc.query(
                """
                select id, space_id, user_id, role, joined_at
                from space_members
                where space_id = ? and user_id = ? and removed_at is null
                """,
                JdbcWorkspaceStore::mapSpaceMember,
                spaceId,
                userId
        ));
    }

    @Override
    Optional<SpaceMember> findSpaceMemberById(String spaceId, String memberId) {
        return first(jdbc.query(
                """
                select id, space_id, user_id, role, joined_at
                from space_members
                where space_id = ? and id = ? and removed_at is null
                """,
                JdbcWorkspaceStore::mapSpaceMember,
                spaceId,
                memberId
        ));
    }

    @Override
    List<SpaceMember> findSpaceMembersBySpaceId(String spaceId) {
        return jdbc.query(
                """
                select id, space_id, user_id, role, joined_at
                from space_members
                where space_id = ? and removed_at is null
                order by joined_at, id
                """,
                JdbcWorkspaceStore::mapSpaceMember,
                spaceId
        );
    }

    @Override
    List<SpaceMember> findSpaceMembersByUserId(String userId) {
        return jdbc.query(
                """
                select id, space_id, user_id, role, joined_at
                from space_members
                where user_id = ? and removed_at is null
                order by joined_at, id
                """,
                JdbcWorkspaceStore::mapSpaceMember,
                userId
        );
    }

    @Override
    List<SpaceMember> findSpaceMembers(String spaceId) {
        return findSpaceMembersBySpaceId(spaceId);
    }

    @Override
    void lockSpace(String spaceId) {
        jdbc.queryForObject("select id from spaces where id = ? for update", String.class, spaceId);
    }

    @Override
    SpaceMember updateSpaceMemberRole(String memberId, SpaceRole role) {
        jdbc.update("update space_members set role = ? where id = ? and removed_at is null", role.name(), memberId);
        return findActiveSpaceMemberById(memberId).orElseThrow();
    }

    @Override
    void removeSpaceMember(String memberId) {
        jdbc.update(
                "update space_members set removed_at = ? where id = ? and removed_at is null",
                timestamp(Instant.now()),
                memberId
        );
    }

    @Override
    OwnerTransferUpdate transferOwner(
            String currentOwnerMemberId,
            String targetMemberId,
            SpaceRole previousOwnerRole
    ) {
        jdbc.update(
                "update space_members set role = ? where id = ? and removed_at is null",
                previousOwnerRole.name(),
                currentOwnerMemberId
        );
        jdbc.update(
                "update space_members set role = 'OWNER' where id = ? and removed_at is null",
                targetMemberId
        );
        SpaceMember previousOwner = findActiveSpaceMemberById(currentOwnerMemberId).orElseThrow();
        SpaceMember newOwner = findActiveSpaceMemberById(targetMemberId).orElseThrow();
        return new OwnerTransferUpdate(newOwner, previousOwner);
    }

    @Override
    Meeting createMeeting(String spaceId, String title, OffsetDateTime scheduledAt) {
        Meeting meeting = Meeting.scheduled("meeting-" + UUID.randomUUID(), spaceId, title, scheduledAt);
        jdbc.update(
                """
                insert into meetings (
                    id, space_id, title, scheduled_at, started_at, ended_at, status,
                    failure_reason, retention_policy, join_code_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                meeting.id(), meeting.spaceId(), meeting.title(), meeting.scheduledAt(), meeting.startedAt(),
                meeting.endedAt(), meeting.status().name(), meeting.failureReason(), meeting.retentionPolicy(),
                hashJoinCode(meeting.joinCode())
        );
        return meeting;
    }

    @Override
    Optional<Meeting> findMeetingById(String meetingId) {
        return first(jdbc.query(
                meetingSelect() + " where id = ? and deleted_at is null",
                JdbcWorkspaceStore::mapMeeting,
                meetingId
        ));
    }

    @Override
    Optional<Meeting> findMeetingByJoinCode(String joinCode) {
        return first(jdbc.query(
                meetingSelect() + " where join_code_hash = ? and deleted_at is null",
                JdbcWorkspaceStore::mapMeeting,
                hashJoinCode(joinCode)
        ));
    }

    @Override
    long countMeetingsBySpaceId(String spaceId) {
        Long count = jdbc.queryForObject(
                "select count(*) from meetings where space_id = ? and deleted_at is null",
                Long.class,
                spaceId
        );
        return count == null ? 0 : count;
    }

    @Override
    List<Meeting> findMeetingsBySpaceId(String spaceId) {
        return jdbc.query(
                meetingSelect() + " where space_id = ? and deleted_at is null order by scheduled_at nulls last, id",
                JdbcWorkspaceStore::mapMeeting,
                spaceId
        );
    }

    @Override
    List<Meeting> findProjectAiMeetings(String spaceId, String userId) {
        return jdbc.query(
                meetingSelect() + """
                 where space_id = ?
                   and deleted_at is null
                   and exists (
                       select 1
                       from space_members sm
                       where sm.space_id = meetings.space_id
                         and sm.user_id = ?
                         and sm.removed_at is null
                         and (
                             sm.role in ('OWNER', 'ADMIN')
                             or exists (
                                 select 1
                                 from meeting_participants mp
                                 where mp.meeting_id = meetings.id
                                   and mp.user_id = sm.user_id
                                   and mp.access_status = 'ACTIVE'
                             )
                         )
                   )
                 order by scheduled_at nulls last, id
                """,
                JdbcWorkspaceStore::mapMeeting,
                spaceId,
                userId
        );
    }

    @Override
    void lockMeeting(String meetingId) {
        jdbc.queryForObject(
                "select id from meetings where id = ? and deleted_at is null for update",
                String.class,
                meetingId
        );
    }

    @Override
    Meeting updateMeeting(
            String meetingId,
            String title,
            OffsetDateTime scheduledAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            MeetingStatus status
    ) {
        jdbc.update(
                """
                update meetings
                set title = ?, scheduled_at = ?, started_at = ?, ended_at = ?, status = ?
                where id = ? and deleted_at is null
                """,
                title, scheduledAt, startedAt, endedAt, status.name(), meetingId
        );
        return findMeetingById(meetingId).orElseThrow();
    }

    @Override
    Meeting softDeleteMeeting(String meetingId, MeetingStatus status, String deletedBy, Instant deletedAt) {
        jdbc.update(
                """
                update meetings
                set status = ?, deleted_by = ?, deleted_at = ?
                where id = ? and deleted_at is null
                """,
                status.name(), deletedBy, timestamp(deletedAt), meetingId
        );
        return first(jdbc.query(
                meetingSelect() + " where id = ?",
                JdbcWorkspaceStore::mapMeeting,
                meetingId
        )).orElseThrow();
    }

    @Override
    MeetingJoinRequest createMeetingJoinRequest(String meetingId, String userId, Instant requestedAt) {
        MeetingJoinRequest request = new MeetingJoinRequest(
                "join-request-" + UUID.randomUUID(), meetingId, userId,
                MeetingJoinRequestStatus.PENDING, requestedAt, null, null
        );
        jdbc.update(
                """
                insert into meeting_join_requests (
                    id, meeting_id, user_id, status, requested_at, reviewed_at, reviewed_by
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                request.id(), request.meetingId(), request.userId(), request.status().name(),
                timestamp(request.requestedAt()), null, null
        );
        return request;
    }

    @Override
    Optional<MeetingJoinRequest> findMeetingJoinRequestById(String meetingId, String requestId) {
        return findMeetingJoinRequest(meetingId, requestId, false);
    }

    @Override
    Optional<MeetingJoinRequest> findMeetingJoinRequestByIdForUpdate(String meetingId, String requestId) {
        return findMeetingJoinRequest(meetingId, requestId, true);
    }

    @Override
    List<MeetingJoinRequest> findMeetingJoinRequests(String meetingId) {
        return jdbc.query(
                """
                select id, meeting_id, user_id, status, requested_at, reviewed_at, reviewed_by
                from meeting_join_requests
                where meeting_id = ?
                order by requested_at, id
                """,
                JdbcWorkspaceStore::mapMeetingJoinRequest,
                meetingId
        );
    }

    @Override
    MeetingJoinRequest updateMeetingJoinRequest(
            String requestId,
            MeetingJoinRequestStatus status,
            Instant reviewedAt,
            String reviewedBy
    ) {
        jdbc.update(
                """
                update meeting_join_requests
                set status = ?, reviewed_at = ?, reviewed_by = ?
                where id = ?
                """,
                status.name(), timestamp(reviewedAt), reviewedBy, requestId
        );
        return findMeetingJoinRequestByRequestId(requestId).orElseThrow();
    }

    @Override
    MeetingParticipant addMeetingParticipant(
            String meetingId,
            String userId,
            MeetingRole role,
            ParticipantType participantType
    ) {
        MeetingParticipant participant = new MeetingParticipant(
                "meeting-participant-" + UUID.randomUUID(), meetingId, userId, role,
                participantType, ParticipantAccessStatus.ACTIVE
        );
        jdbc.update(
                """
                insert into meeting_participants (
                    id, meeting_id, user_id, role, participant_type, access_status
                ) values (?, ?, ?, ?, ?, ?)
                """,
                participant.id(), participant.meetingId(), participant.userId(), participant.role().name(),
                participant.participantType().name().toLowerCase(), participant.accessStatus().name()
        );
        return participant;
    }

    @Override
    Optional<MeetingParticipant> findMeetingParticipant(String meetingId, String userId) {
        return first(jdbc.query(
                """
                select id, meeting_id, user_id, role, participant_type, access_status
                from meeting_participants
                where meeting_id = ? and user_id = ?
                order by case when access_status = 'ACTIVE' then 0 else 1 end, id
                limit 1
                """,
                JdbcWorkspaceStore::mapMeetingParticipant,
                meetingId,
                userId
        ));
    }

    @Override
    Optional<MeetingParticipant> findMeetingParticipantById(String meetingId, String participantId) {
        return first(jdbc.query(
                """
                select id, meeting_id, user_id, role, participant_type, access_status
                from meeting_participants
                where meeting_id = ? and id = ?
                """,
                JdbcWorkspaceStore::mapMeetingParticipant,
                meetingId,
                participantId
        ));
    }

    @Override
    List<MeetingParticipant> findMeetingParticipants(String meetingId) {
        return jdbc.query(
                """
                select id, meeting_id, user_id, role, participant_type, access_status
                from meeting_participants
                where meeting_id = ?
                order by id
                """,
                JdbcWorkspaceStore::mapMeetingParticipant,
                meetingId
        );
    }

    @Override
    MeetingParticipant updateMeetingParticipant(
            String participantId,
            MeetingRole role,
            ParticipantAccessStatus accessStatus
    ) {
        jdbc.update(
                "update meeting_participants set role = ?, access_status = ? where id = ?",
                role.name(), accessStatus.name(), participantId
        );
        return findMeetingParticipantByParticipantId(participantId).orElseThrow();
    }

    @Override
    MeetingParticipant updateMeetingParticipantType(String participantId, ParticipantType participantType) {
        jdbc.update(
                "update meeting_participants set participant_type = ? where id = ?",
                participantType.name().toLowerCase(), participantId
        );
        return findMeetingParticipantByParticipantId(participantId).orElseThrow();
    }

    @Override
    MeetingSpeaker addMeetingSpeaker(String meetingId, String label, String displayName, Instant createdAt) {
        MeetingSpeaker speaker = new MeetingSpeaker(
                "meeting-speaker-" + UUID.randomUUID(), meetingId, label, displayName, createdAt
        );
        jdbc.update(
                """
                insert into meeting_speakers (id, meeting_id, label, display_name, created_at)
                values (?, ?, ?, ?, ?)
                """,
                speaker.id(), speaker.meetingId(), speaker.label(), speaker.displayName(), timestamp(speaker.createdAt())
        );
        return speaker;
    }

    @Override
    List<MeetingSpeaker> findMeetingSpeakers(String meetingId) {
        return jdbc.query(
                """
                select id, meeting_id, label, display_name, created_at
                from meeting_speakers where meeting_id = ? order by created_at, id
                """,
                JdbcWorkspaceStore::mapMeetingSpeaker,
                meetingId
        );
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
        TranscriptSegment segment = new TranscriptSegment(
                "transcript-segment-" + UUID.randomUUID(), meetingId, speakerId, speakerLabel,
                speakerName, startMs, endMs, text, source, sequence
        );
        jdbc.update(
                """
                insert into transcript_segments (
                    id, meeting_id, speaker_id, speaker_label, speaker_name,
                    sequence, start_ms, end_ms, text, source
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                segment.id(), segment.meetingId(), segment.speakerId(), segment.speakerLabel(),
                segment.speakerName(), segment.sequence(), segment.startMs(), segment.endMs(),
                segment.text(), segment.source()
        );
        return segment;
    }

    @Override
    List<TranscriptSegment> findTranscriptSegments(String meetingId) {
        return jdbc.query(
                """
                select id, meeting_id, speaker_id, speaker_label, speaker_name,
                       sequence, start_ms, end_ms, text, source
                from transcript_segments
                where meeting_id = ?
                order by sequence
                """,
                JdbcWorkspaceStore::mapTranscriptSegment,
                meetingId
        );
    }

    @Override
    MeetingReport saveMeetingReport(MeetingReport report) {
        jdbc.update(
                """
                insert into meeting_reports (
                    id, meeting_id, status, title, summary, markdown, version, is_current,
                    created_at, confirmed_at, created_by, source_ids
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (id) do update set
                    status = excluded.status,
                    title = excluded.title,
                    summary = excluded.summary,
                    markdown = excluded.markdown,
                    version = excluded.version,
                    is_current = excluded.is_current,
                    confirmed_at = excluded.confirmed_at,
                    created_by = excluded.created_by,
                    source_ids = excluded.source_ids
                """,
                report.id(), report.meetingId(), report.status().name(), report.title(), report.summary(),
                report.markdown(), report.version(), report.current(), timestamp(report.createdAt()),
                timestamp(report.confirmedAt()), report.createdBy(), writeJson(report.sourceIds())
        );
        jdbc.update("delete from report_decisions where report_id = ?", report.id());
        jdbc.update("delete from report_action_items where report_id = ?", report.id());
        for (int index = 0; index < report.decisions().size(); index++) {
            MeetingReport.ReportDecision decision = report.decisions().get(index);
            jdbc.update(
                    """
                    insert into report_decisions (
                        id, report_id, decision_order, title, rationale, source_ids
                    ) values (?, ?, ?, ?, ?, cast(? as jsonb))
                    """,
                    decision.id(), report.id(), index, decision.title(), decision.content(),
                    writeJson(decision.sourceIds())
            );
        }
        for (int index = 0; index < report.actionItems().size(); index++) {
            MeetingReport.ReportActionItem item = report.actionItems().get(index);
            jdbc.update(
                    """
                    insert into report_action_items (
                        id, report_id, item_order, title, assignee_name, due_date,
                        confirmation_state, source_ids
                    ) values (?, ?, ?, ?, ?, ?, 'candidate', cast(? as jsonb))
                    """,
                    item.id(), report.id(), index, item.title(), item.assigneeName(), sqlDate(item.dueDate()),
                    writeJson(item.sourceIds())
            );
        }
        return report;
    }

    @Override
    Optional<MeetingReport> findMeetingReportById(String reportId) {
        return first(jdbc.query(reportSelect() + " where id = ?", this::mapReportRow, reportId))
                .map(this::hydrateReport);
    }

    @Override
    List<MeetingReport> findMeetingReports(String meetingId) {
        return jdbc.query(
                        reportSelect() + " where meeting_id = ? order by version, id",
                        this::mapReportRow,
                        meetingId
                ).stream()
                .map(this::hydrateReport)
                .toList();
    }

    @Override
    TaskCandidate saveTaskCandidate(TaskCandidate candidate) {
        jdbc.update(
                """
                insert into task_candidates (
                    id, meeting_id, title, assignee_name, suggested_assignee_id, due_date,
                    status, source_ids, created_by, created_at, confirmed_at
                ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                on conflict (id) do update set
                    title = excluded.title,
                    assignee_name = excluded.assignee_name,
                    suggested_assignee_id = excluded.suggested_assignee_id,
                    due_date = excluded.due_date,
                    status = excluded.status,
                    source_ids = excluded.source_ids,
                    confirmed_at = excluded.confirmed_at
                """,
                candidate.id(), candidate.meetingId(), candidate.title(), candidate.assigneeName(),
                candidate.suggestedAssigneeId(), sqlDate(candidate.dueDate()), candidate.status().name(),
                writeJson(candidate.sourceIds()), candidate.createdBy(), timestamp(candidate.createdAt()),
                timestamp(candidate.confirmedAt())
        );
        return candidate;
    }

    @Override
    Optional<TaskCandidate> findTaskCandidateById(String candidateId) {
        return findTaskCandidate(candidateId, false);
    }

    @Override
    Optional<TaskCandidate> findTaskCandidateByIdForUpdate(String candidateId) {
        return findTaskCandidate(candidateId, true);
    }

    @Override
    List<TaskCandidate> findTaskCandidates(String meetingId) {
        return jdbc.query(
                taskCandidateSelect() + " where meeting_id = ? order by created_at, id",
                this::mapTaskCandidate,
                meetingId
        );
    }

    @Override
    TaskCard saveTaskCard(TaskCard taskCard) {
        jdbc.update(
                """
                insert into task_cards (
                    id, space_id, meeting_id, source_candidate_id, title, description,
                    status, assignee_id, due_date, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    title = excluded.title,
                    description = excluded.description,
                    status = excluded.status,
                    assignee_id = excluded.assignee_id,
                    due_date = excluded.due_date,
                    updated_at = excluded.updated_at
                """,
                taskCard.id(), taskCard.spaceId(), taskCard.meetingId(), taskCard.sourceCandidateId(),
                taskCard.title(), taskCard.description(), taskCard.status().name(), taskCard.assigneeId(),
                sqlDate(taskCard.dueDate()), timestamp(taskCard.createdAt()), timestamp(taskCard.updatedAt())
        );
        return taskCard;
    }

    @Override
    Optional<TaskCard> findTaskCardBySourceCandidateId(String candidateId) {
        return first(jdbc.query(
                """
                select id, space_id, meeting_id, source_candidate_id, title, description,
                       status, assignee_id, due_date, created_at, updated_at
                from task_cards where source_candidate_id = ?
                """,
                JdbcWorkspaceStore::mapTaskCard,
                candidateId
        ));
    }

    @Override
    ProjectKnowledge saveProjectKnowledge(ProjectKnowledge knowledge) {
        jdbc.update(
                """
                insert into project_knowledge (
                    id, space_id, type, title, content, source_meeting_id, approved_by,
                    status, embedding_status, embedding_job_id, created_at, updated_at, deleted_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    type = excluded.type,
                    title = excluded.title,
                    content = excluded.content,
                    source_meeting_id = excluded.source_meeting_id,
                    approved_by = excluded.approved_by,
                    status = excluded.status,
                    embedding_status = excluded.embedding_status,
                    embedding_job_id = excluded.embedding_job_id,
                    updated_at = excluded.updated_at,
                    deleted_at = excluded.deleted_at
                """,
                knowledge.id(), knowledge.spaceId(), knowledge.type().name().toLowerCase(), knowledge.title(),
                knowledge.content(), knowledge.sourceMeetingId(), knowledge.approvedBy(), knowledge.status().name(),
                knowledge.embeddingStatus().name(), knowledge.embeddingJobId(), timestamp(knowledge.createdAt()),
                timestamp(knowledge.updatedAt()), timestamp(knowledge.deletedAt())
        );
        return knowledge;
    }

    @Override
    List<ProjectKnowledge> findProjectKnowledge(String spaceId) {
        return jdbc.query(
                """
                select id, space_id, type, title, content, source_meeting_id, approved_by,
                       status, embedding_status, embedding_job_id, created_at, updated_at, deleted_at
                from project_knowledge
                where space_id = ?
                order by updated_at desc, id
                """,
                JdbcWorkspaceStore::mapProjectKnowledge,
                spaceId
        );
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
        String id = "audit-" + UUID.randomUUID();
        String spaceId = resolveAuditSpaceId(resourceId);
        String targetType = auditTargetType(type);
        String targetId = targetUserId == null || targetUserId.isBlank() ? resourceId : targetUserId;
        jdbc.update(
                """
                insert into audit_logs (
                    id, space_id, actor_user_id, action, target_type, target_id,
                    before_value, after_value, occurred_at
                ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
                """,
                id, spaceId, actorUserId, type, targetType, targetId,
                auditValue(resourceId, beforeValue), auditValue(resourceId, afterValue), timestamp(createdAt)
        );
        return new AuditEvent(
                id, type, actorUserId, targetUserId, resourceId, beforeValue, afterValue, createdAt
        );
    }

    private Optional<SpaceMember> findActiveSpaceMemberById(String memberId) {
        return first(jdbc.query(
                """
                select id, space_id, user_id, role, joined_at
                from space_members where id = ? and removed_at is null
                """,
                JdbcWorkspaceStore::mapSpaceMember,
                memberId
        ));
    }

    private Optional<MeetingJoinRequest> findMeetingJoinRequest(
            String meetingId,
            String requestId,
            boolean forUpdate
    ) {
        String suffix = forUpdate ? " for update" : "";
        return first(jdbc.query(
                """
                select id, meeting_id, user_id, status, requested_at, reviewed_at, reviewed_by
                from meeting_join_requests
                where meeting_id = ? and id = ?
                """ + suffix,
                JdbcWorkspaceStore::mapMeetingJoinRequest,
                meetingId,
                requestId
        ));
    }

    private Optional<MeetingJoinRequest> findMeetingJoinRequestByRequestId(String requestId) {
        return first(jdbc.query(
                """
                select id, meeting_id, user_id, status, requested_at, reviewed_at, reviewed_by
                from meeting_join_requests where id = ?
                """,
                JdbcWorkspaceStore::mapMeetingJoinRequest,
                requestId
        ));
    }

    private Optional<MeetingParticipant> findMeetingParticipantByParticipantId(String participantId) {
        return first(jdbc.query(
                """
                select id, meeting_id, user_id, role, participant_type, access_status
                from meeting_participants where id = ?
                """,
                JdbcWorkspaceStore::mapMeetingParticipant,
                participantId
        ));
    }

    private Optional<TaskCandidate> findTaskCandidate(String candidateId, boolean forUpdate) {
        String suffix = forUpdate ? " for update" : "";
        return first(jdbc.query(
                taskCandidateSelect() + " where id = ?" + suffix,
                this::mapTaskCandidate,
                candidateId
        ));
    }

    private MeetingReport hydrateReport(ReportRow row) {
        List<MeetingReport.ReportDecision> decisions = jdbc.query(
                """
                select id, title, rationale, source_ids
                from report_decisions where report_id = ? order by decision_order
                """,
                (rs, rowNum) -> new MeetingReport.ReportDecision(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("rationale"),
                        readStringList(rs.getString("source_ids"))
                ),
                row.id()
        );
        List<MeetingReport.ReportActionItem> actionItems = jdbc.query(
                """
                select id, title, assignee_name, due_date, source_ids
                from report_action_items where report_id = ? order by item_order
                """,
                (rs, rowNum) -> new MeetingReport.ReportActionItem(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("assignee_name"),
                        dateString(rs.getDate("due_date")),
                        readStringList(rs.getString("source_ids"))
                ),
                row.id()
        );
        return new MeetingReport(
                row.id(), row.meetingId(), row.status(), row.title(), row.summary(), row.markdown(),
                decisions, actionItems, row.sourceIds(), row.createdBy(), row.version(), row.current(),
                row.createdAt(), row.confirmedAt()
        );
    }

    private ReportRow mapReportRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRow(
                rs.getString("id"),
                rs.getString("meeting_id"),
                MeetingReportStatus.valueOf(rs.getString("status")),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("markdown"),
                readStringList(rs.getString("source_ids")),
                rs.getString("created_by"),
                rs.getInt("version"),
                rs.getBoolean("is_current"),
                instant(rs, "created_at"),
                nullableInstant(rs, "confirmed_at")
        );
    }

    private TaskCandidate mapTaskCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new TaskCandidate(
                rs.getString("id"),
                rs.getString("meeting_id"),
                rs.getString("title"),
                rs.getString("assignee_name"),
                rs.getString("suggested_assignee_id"),
                nullableLocalDate(rs, "due_date"),
                TaskCandidateStatus.valueOf(rs.getString("status")),
                readStringList(rs.getString("source_ids")),
                rs.getString("created_by"),
                instant(rs, "created_at"),
                nullableInstant(rs, "confirmed_at")
        );
    }

    private String resolveAuditSpaceId(String resourceId) {
        Optional<String> direct = first(jdbc.query(
                "select id from spaces where id = ?",
                (rs, rowNum) -> rs.getString(1),
                resourceId
        ));
        if (direct.isPresent()) {
            return direct.get();
        }
        try {
            return jdbc.queryForObject("select space_id from meetings where id = ?", String.class, resourceId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("감사 로그의 Space를 찾을 수 없습니다: " + resourceId, exception);
        }
    }

    private String auditValue(String resourceId, String value) {
        if (value == null) {
            return null;
        }
        return writeJson(Map.of("resourceId", resourceId, "value", value));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON 저장 값 생성에 실패했습니다.", exception);
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON sourceIds를 읽을 수 없습니다.", exception);
        }
    }

    private static String meetingSelect() {
        return """
                select id, space_id, title, scheduled_at, started_at, ended_at,
                       status, failure_reason, retention_policy, deleted_at, deleted_by
                from meetings
                """;
    }

    private static String reportSelect() {
        return """
                select id, meeting_id, status, title, summary, markdown, source_ids,
                       created_by, version, is_current, created_at, confirmed_at
                from meeting_reports
                """;
    }

    private static String taskCandidateSelect() {
        return """
                select id, meeting_id, title, assignee_name, suggested_assignee_id, due_date,
                       status, source_ids, created_by, created_at, confirmed_at
                from task_candidates
                """;
    }

    private static User mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getString("id"), rs.getString("email"), rs.getString("display_name"),
                rs.getString("picture_url"), rs.getString("status"), instant(rs, "created_at"),
                nullableInstant(rs, "last_login_at")
        );
    }

    private static Space mapSpace(ResultSet rs, int rowNum) throws SQLException {
        return new Space(
                rs.getString("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("created_by"), instant(rs, "created_at")
        );
    }

    private static SpaceMember mapSpaceMember(ResultSet rs, int rowNum) throws SQLException {
        return new SpaceMember(
                rs.getString("id"), rs.getString("space_id"), rs.getString("user_id"),
                SpaceRole.valueOf(rs.getString("role")), instant(rs, "joined_at")
        );
    }

    private static Meeting mapMeeting(ResultSet rs, int rowNum) throws SQLException {
        return new Meeting(
                rs.getString("id"), rs.getString("space_id"), rs.getString("title"),
                nullableOffsetDateTime(rs, "scheduled_at"), null,
                nullableOffsetDateTime(rs, "started_at"), nullableOffsetDateTime(rs, "ended_at"),
                MeetingStatus.valueOf(rs.getString("status")), rs.getString("failure_reason"),
                rs.getString("retention_policy"), nullableInstant(rs, "deleted_at"), rs.getString("deleted_by")
        );
    }

    private static MeetingJoinRequest mapMeetingJoinRequest(ResultSet rs, int rowNum) throws SQLException {
        return new MeetingJoinRequest(
                rs.getString("id"), rs.getString("meeting_id"), rs.getString("user_id"),
                MeetingJoinRequestStatus.valueOf(rs.getString("status")), instant(rs, "requested_at"),
                nullableInstant(rs, "reviewed_at"), rs.getString("reviewed_by")
        );
    }

    private static MeetingParticipant mapMeetingParticipant(ResultSet rs, int rowNum) throws SQLException {
        return new MeetingParticipant(
                rs.getString("id"), rs.getString("meeting_id"), rs.getString("user_id"),
                MeetingRole.valueOf(rs.getString("role")),
                ParticipantType.valueOf(rs.getString("participant_type").toUpperCase()),
                ParticipantAccessStatus.valueOf(rs.getString("access_status"))
        );
    }

    private static MeetingSpeaker mapMeetingSpeaker(ResultSet rs, int rowNum) throws SQLException {
        return new MeetingSpeaker(
                rs.getString("id"), rs.getString("meeting_id"), rs.getString("label"),
                rs.getString("display_name"), instant(rs, "created_at")
        );
    }

    private static TranscriptSegment mapTranscriptSegment(ResultSet rs, int rowNum) throws SQLException {
        return new TranscriptSegment(
                rs.getString("id"), rs.getString("meeting_id"), rs.getString("speaker_id"),
                rs.getString("speaker_label"), rs.getString("speaker_name"), rs.getInt("start_ms"),
                rs.getInt("end_ms"), rs.getString("text"), rs.getString("source"), rs.getInt("sequence")
        );
    }

    private static TaskCard mapTaskCard(ResultSet rs, int rowNum) throws SQLException {
        return new TaskCard(
                rs.getString("id"), rs.getString("space_id"), rs.getString("meeting_id"),
                rs.getString("source_candidate_id"), rs.getString("title"), rs.getString("description"),
                TaskCardStatus.valueOf(rs.getString("status")), rs.getString("assignee_id"),
                nullableLocalDate(rs, "due_date"), instant(rs, "created_at"), instant(rs, "updated_at")
        );
    }

    private static ProjectKnowledge mapProjectKnowledge(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectKnowledge(
                rs.getString("id"), rs.getString("space_id"),
                KnowledgeType.valueOf(rs.getString("type").toUpperCase()), rs.getString("title"),
                rs.getString("content"), rs.getString("source_meeting_id"), rs.getString("approved_by"),
                KnowledgeStatus.valueOf(rs.getString("status")),
                EmbeddingStatus.valueOf(rs.getString("embedding_status")), rs.getString("embedding_job_id"),
                instant(rs, "created_at"), instant(rs, "updated_at"), nullableInstant(rs, "deleted_at")
        );
    }

    private static String hashJoinCode(String joinCode) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(joinCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("회의 참가 코드 hash 생성에 실패했습니다.", exception);
        }
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
        if (action.startsWith("MEETING_")) {
            return "MEETING";
        }
        return "RESOURCE";
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime nullableOffsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static LocalDate nullableLocalDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static Date sqlDate(String value) {
        return value == null || value.isBlank() ? null : Date.valueOf(value);
    }

    private static String dateString(Date value) {
        return value == null ? null : value.toLocalDate().toString();
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }

    private record ReportRow(
            String id,
            String meetingId,
            MeetingReportStatus status,
            String title,
            String summary,
            String markdown,
            List<String> sourceIds,
            String createdBy,
            int version,
            boolean current,
            Instant createdAt,
            Instant confirmedAt
    ) {
    }
}
