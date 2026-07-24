package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceDomainService {

    private static final SecureRandom INVITATION_RANDOM = new SecureRandom();
    private static final Duration REPORT_CANDIDATE_TTL = Duration.ofDays(7);
    private static final Duration TASK_CANDIDATE_TTL = Duration.ofDays(7);
    private static final ZoneId PRODUCT_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DASHBOARD_ACTIVITY_LIMIT = 10;
    private static final int DASHBOARD_ACTION_ITEM_LIMIT = 10;
    private static final int DASHBOARD_LATEST_REPORT_LIMIT = 5;

    private final WorkspaceStore store;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final MeetingAccessPolicy meetingAccessPolicy;
    private final Clock clock;

    @Autowired
    public WorkspaceDomainService(
            WorkspaceStore store,
            SpaceAccessPolicy spaceAccessPolicy,
            MeetingAccessPolicy meetingAccessPolicy
    ) {
        this(store, spaceAccessPolicy, meetingAccessPolicy, Clock.systemUTC());
    }

    public WorkspaceDomainService(WorkspaceStore store, SpaceAccessPolicy spaceAccessPolicy) {
        this(store, spaceAccessPolicy, new MeetingAccessPolicy(spaceAccessPolicy), Clock.systemUTC());
    }

    WorkspaceDomainService(WorkspaceStore store, SpaceAccessPolicy spaceAccessPolicy, Clock clock) {
        this(store, spaceAccessPolicy, new MeetingAccessPolicy(spaceAccessPolicy), clock);
    }

    WorkspaceDomainService(
            WorkspaceStore store,
            SpaceAccessPolicy spaceAccessPolicy,
            MeetingAccessPolicy meetingAccessPolicy,
            Clock clock
    ) {
        this.store = store;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.meetingAccessPolicy = meetingAccessPolicy;
        this.clock = clock;
    }

    @Transactional
    public User ensureUser(String id, String email, String displayName, String pictureUrl, String status) {
        return store.findUserById(id)
                .orElseGet(() -> store.saveUser(new User(
                        id,
                        email,
                        displayName,
                        pictureUrl,
                        status,
                        Instant.now(clock),
                        Instant.now(clock)
                )));
    }

    public List<SpaceSummary> listSpaces(String userId) {
        requireUser(userId);
        return store.findSpaceMembersByUserId(userId)
                .stream()
                .flatMap(member -> store.findSpaceById(member.spaceId())
                        .map(space -> new SpaceSummary(space, member.role(), store.countMeetingsBySpaceId(space.id())))
                        .stream())
                .toList();
    }

    public SpaceDetail spaceDetail(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        SpaceAccessPolicy.SpaceAccessContext accessContext = spaceAccessContext(spaceId, actorUserId);
        spaceAccessPolicy.requireSpaceAccess(accessContext);
        Space space = store.findSpaceById(spaceId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "Space를 찾을 수 없습니다."
        ));
        List<MeetingSummary> meetings = listMeetings(actorUserId, spaceId);
        Instant now = Instant.now(clock);
        List<MeetingSummary> upcomingMeetings = meetings.stream()
                .filter(summary -> summary.meeting().scheduledAt().toInstant().isAfter(now))
                .sorted(Comparator.comparing(summary -> summary.meeting().scheduledAt()))
                .toList();
        List<MeetingReport> recentReports = meetings.stream()
                .flatMap(summary -> listMeetingReports(actorUserId, summary.meeting().id(), "CONFIRMED").stream())
                .sorted(Comparator.comparing(
                        report -> report.confirmedAt() == null ? report.createdAt() : report.confirmedAt(),
                        Comparator.reverseOrder()))
                .limit(DASHBOARD_LATEST_REPORT_LIMIT)
                .toList();
        // Keep completed cards in the space summary so the UI can report
        // completion as completed/total. Open-task lists filter DONE locally.
        List<TaskCardView> actionItems = listTaskCards(actorUserId, spaceId, null, null, null);
        return new SpaceDetail(
                space,
                accessContext.membership().role(),
                upcomingMeetings,
                recentReports,
                actionItems
        );
    }

    public DashboardSummary dashboardSummary(String actorUserId) {
        requireUser(actorUserId);
        List<SpaceSummary> spaces = listSpaces(actorUserId);
        LocalDate today = Instant.now(clock).atZone(PRODUCT_ZONE).toLocalDate();
        List<Meeting> accessibleMeetings = spaces.stream()
                .flatMap(space -> listMeetings(actorUserId, space.space().id()).stream())
                .map(MeetingSummary::meeting)
                .toList();
        List<Meeting> todayMeetings = accessibleMeetings.stream()
                .filter(meeting -> meeting.scheduledAt().atZoneSameInstant(PRODUCT_ZONE).toLocalDate().equals(today))
                .sorted(Comparator.comparing(Meeting::scheduledAt))
                .toList();
        List<TaskCardView> actionItems = spaces.stream()
                .flatMap(space -> listTaskCards(actorUserId, space.space().id(), null, null, null).stream())
                .filter(view -> view.task().status() != TaskCardStatus.DONE)
                .sorted(Comparator.comparing((TaskCardView view) -> view.task().dueDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(view -> view.task().updatedAt(), Comparator.reverseOrder()))
                .limit(DASHBOARD_ACTION_ITEM_LIMIT)
                .toList();
        List<DashboardActivity> activities = new java.util.ArrayList<>();
        spaces.forEach(space -> activities.add(new DashboardActivity(
                space.space().id(), space.space().id(), space.space().name() + " 프로젝트 활동", space.space().updatedAt(), "space"
        )));
        spaces.forEach(space -> listTaskCards(actorUserId, space.space().id(), null, null, null).forEach(view -> activities.add(
                new DashboardActivity(view.task().id(), space.space().id(), view.task().title() + " 태스크 업데이트", view.task().updatedAt(), "task")
        )));
        accessibleMeetings.forEach(meeting -> listMeetingReports(actorUserId, meeting.id(), null).forEach(report -> activities.add(
                new DashboardActivity(report.id(), meeting.spaceId(), report.title() + " 회의록 생성", report.createdAt(), "report")
        )));
        List<DashboardActivity> recentActivities = activities.stream()
                .sorted(Comparator.comparing(DashboardActivity::occurredAt).reversed())
                .limit(DASHBOARD_ACTIVITY_LIMIT)
                .toList();
        List<DashboardReport> latestReports = accessibleMeetings.stream()
                .flatMap(meeting -> listMeetingReports(actorUserId, meeting.id(), null).stream()
                        .filter(report -> report.status() == MeetingReportStatus.CONFIRMED)
                        .filter(MeetingReport::current)
                        .map(report -> new DashboardReport(meeting, report)))
                .sorted(Comparator.comparing(DashboardReport::occurredAt).reversed())
                .limit(DASHBOARD_LATEST_REPORT_LIMIT)
                .toList();
        return new DashboardSummary(todayMeetings, recentActivities, spaces, actionItems, latestReports);
    }

    public List<MeetingSummary> listMeetings(String actorUserId, String spaceId) {
        return listMeetings(actorUserId, spaceId, (MeetingStatus) null, (OffsetDateTime) null, (OffsetDateTime) null);
    }

    public List<MeetingSummary> listMeetings(
            String actorUserId,
            String spaceId,
            MeetingStatus status,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        if (from != null && to != null && from.isAfter(to)) {
            throw invalidRequest("회의 조회 시작 일시는 종료 일시보다 늦을 수 없습니다.");
        }
        return store.findProjectAiMeetings(spaceId, actorUserId)
                .stream()
                .filter(meeting -> status == null || meeting.status() == status)
                .filter(meeting -> from == null || !meeting.scheduledAt().isBefore(from))
                .filter(meeting -> to == null || !meeting.scheduledAt().isAfter(to))
                .map(meeting -> new MeetingSummary(
                        meeting,
                        store.findMeetingParticipant(meeting.id(), actorUserId)
                                .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                                .map(MeetingParticipant::role)
                                .orElse(null)
                ))
                .toList();
    }

    @Transactional
    public SpaceCreationResult createSpace(String actorUserId, String name, String description) {
        requireUser(actorUserId);
        validateRequired(name, "Space 이름은 필수입니다.");
        Instant now = Instant.now(clock);
        Space space = store.createSpace(name.trim(), blankToNull(description), null, actorUserId, now);
        SpaceMember owner = store.addSpaceMember(space.id(), actorUserId, SpaceRole.OWNER, now);
        return new SpaceCreationResult(space, owner);
    }

    @Transactional
    public Space updateSpace(String actorUserId, String spaceId, String name, boolean namePresent, String description, boolean descriptionPresent, String imageUrl, boolean imageUrlPresent) {
        requireUser(actorUserId);
        if (!namePresent && !descriptionPresent && !imageUrlPresent) {
            throw invalidRequest("수정할 Space 필드가 필요합니다.");
        }
        store.lockSpace(spaceId);
        SpaceAccessPolicy.SpaceAccessContext context = spaceAccessContext(spaceId, actorUserId);
        spaceAccessPolicy.requireMemberManagement(context);
        Space current = store.findSpaceById(spaceId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "Space를 찾을 수 없습니다."
        ));
        if (namePresent && (name == null || name.isBlank())) {
            throw invalidRequest("Space 이름은 blank일 수 없습니다.");
        }
        if (imageUrlPresent && !isHttpUrl(imageUrl)) {
            throw invalidRequest("대표 이미지는 http 또는 https URL이어야 합니다.");
        }
        Space updated = store.updateSpace(
                spaceId,
                namePresent ? name.trim() : current.name(),
                descriptionPresent ? blankToNull(description) : current.description(),
                imageUrlPresent ? blankToNull(imageUrl) : current.imageUrl(),
                Instant.now(clock)
        );
        addAudit("SPACE_UPDATED", actorUserId, null, spaceId, current.name(), updated.name());
        return updated;
    }

    @Transactional
    public Space updateSpace(
            String actorUserId,
            String spaceId,
            String name,
            boolean namePresent,
            String description,
            boolean descriptionPresent
    ) {
        return updateSpace(actorUserId, spaceId, name, namePresent, description, descriptionPresent, null, false);
    }

    public void requireSpaceMemberManagement(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Transactional
    public boolean deleteSpace(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireOwnerManagement(spaceAccessContext(spaceId, actorUserId));
        if (store.findMeetingsBySpaceId(spaceId).stream().anyMatch(meeting -> meeting.status() == MeetingStatus.IN_PROGRESS)) {
            throw new AuthorizationException(
                    HttpStatus.CONFLICT,
                    "MEETING_ALREADY_PROCESSING",
                    "진행 중인 회의가 있어 Space를 삭제할 수 없습니다."
            );
        }
        store.softDeleteSpace(spaceId, Instant.now(clock));
        addAudit("SPACE_DELETED", actorUserId, null, spaceId, "ACTIVE", "SOFT_DELETED");
        return true;
    }

    @Transactional
    public SpaceInvitationCreation createSpaceInvitation(String actorUserId, String spaceId, String email, String role) {
        requireUser(actorUserId);
        String normalizedEmail = normalizeEmail(email);
        SpaceRole invitationRole = SpaceRole.parse(role);
        if (invitationRole == SpaceRole.OWNER) {
            throw invalidRequest("OWNER는 초대할 수 없습니다.");
        }
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        Instant now = Instant.now(clock);
        store.findPendingSpaceInvitation(spaceId, normalizedEmail).ifPresent(invitation -> {
            if (invitation.expiresAt().isAfter(now)) {
                throw invalidRequest("같은 이메일의 대기 중 초대가 있습니다.");
            }
            store.saveSpaceInvitation(invitation.expired());
        });
        String token = invitationToken();
        SpaceInvitation invitation = store.saveSpaceInvitation(new SpaceInvitation(
                "space-invitation-" + UUID.randomUUID(),
                spaceId,
                normalizedEmail,
                invitationRole,
                InvitationStatus.PENDING,
                sha256(token),
                now.plus(java.time.Duration.ofDays(7)),
                null,
                null
        ));
        addAudit("SPACE_MEMBER_INVITED", actorUserId, null, spaceId, null, normalizedEmail + "/" + invitationRole.name());
        return new SpaceInvitationCreation(invitation, token);
    }

    // Member-management checks use the same space lock as invitation mutations.
    // Keep this transaction writable so PostgreSQL permits the FOR NO KEY UPDATE lock.
    @Transactional
    public List<SpaceInvitation> listSpaceInvitations(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        return store.findSpaceInvitations(spaceId);
    }

    @Transactional
    public SpaceInvitationCreation resendSpaceInvitation(String actorUserId, String spaceId, String invitationId) {
        SpaceInvitation current = store.findSpaceInvitationById(spaceId, invitationId).orElseThrow(() -> new AuthorizationException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "초대를 찾을 수 없습니다."));
        if (current.status() == InvitationStatus.PENDING) store.saveSpaceInvitation(current.expired());
        return createSpaceInvitation(actorUserId, spaceId, current.email(), current.role().name());
    }

    @Transactional
    public void cancelSpaceInvitation(String actorUserId, String spaceId, String invitationId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        SpaceInvitation current = store.findSpaceInvitationById(spaceId, invitationId).orElseThrow(() -> new AuthorizationException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "초대를 찾을 수 없습니다."));
        if (current.status() == InvitationStatus.PENDING) store.saveSpaceInvitation(current.declined(Instant.now(clock)));
    }

    @Transactional
    public SpaceInvitationResolution resolveSpaceInvitation(
            String actorUserId,
            String actorEmail,
            String spaceId,
            String invitationId,
            String token,
            boolean accept
    ) {
        requireUser(actorUserId);
        Space space = store.findSpaceById(spaceId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "Space를 찾을 수 없습니다."
        ));
        store.lockSpace(space.id());
        SpaceInvitation invitation = store.findSpaceInvitationById(spaceId, invitationId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "초대를 찾을 수 없습니다."
        ));
        if (!normalizeEmail(actorEmail).equals(invitation.email()) || (token != null && !MessageDigest.isEqual(
                sha256(token).getBytes(StandardCharsets.UTF_8), invitation.tokenHash().getBytes(StandardCharsets.UTF_8)
        ))) {
            throw new AuthorizationException(HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED", "초대 대상이 일치하지 않습니다.");
        }
        if (invitation.status() != InvitationStatus.PENDING) {
            if (accept && invitation.status() == InvitationStatus.ACCEPTED) {
                SpaceMember member = store.findSpaceMember(spaceId, actorUserId).orElseThrow(() -> invalidRequest("수락된 초대의 멤버를 찾을 수 없습니다."));
                return new SpaceInvitationResolution(invitation, member);
            }
            throw new AuthorizationException(HttpStatus.CONFLICT, "INVALID_REQUEST", "처리할 수 없는 초대 상태입니다.");
        }
        Instant now = Instant.now(clock);
        if (!invitation.expiresAt().isAfter(now)) {
            store.saveSpaceInvitation(invitation.expired());
            throw new AuthorizationException(HttpStatus.CONFLICT, "INVALID_REQUEST", "만료된 초대입니다.");
        }
        if (!accept) {
            SpaceInvitation declined = store.saveSpaceInvitation(invitation.declined(now));
            addAudit("SPACE_INVITATION_RESOLVED", actorUserId, null, spaceId, "PENDING", "DECLINED");
            return new SpaceInvitationResolution(declined, null);
        }
        SpaceMember member = store.findSpaceMember(spaceId, actorUserId)
                .orElseGet(() -> store.addSpaceMember(spaceId, actorUserId, invitation.role(), now));
        SpaceInvitation accepted = store.saveSpaceInvitation(invitation.accepted(now));
        addAudit("SPACE_INVITATION_RESOLVED", actorUserId, actorUserId, spaceId, "PENDING", "ACCEPTED");
        return new SpaceInvitationResolution(accepted, member);
    }

    @Transactional(readOnly = true)
    public List<PendingSpaceInvitation> listPendingSpaceInvitations(String actorUserId) {
        requireUser(actorUserId);
        User user = store.findUserById(actorUserId).orElseThrow();
        return store.findPendingSpaceInvitations(normalizeEmail(user.email())).stream()
                .filter(invitation -> store.findSpaceById(invitation.spaceId()).isPresent())
                .map(invitation -> new PendingSpaceInvitation(
                        invitation.id(),
                        invitation.spaceId(),
                        store.findSpaceById(invitation.spaceId()).orElseThrow().name(),
                        invitation.role(),
                        invitation.expiresAt()
                ))
                .toList();
    }

    @Transactional
    public SpaceInvitationResolution resolveAuthenticatedSpaceInvitation(
            String actorUserId,
            String actorEmail,
            String spaceId,
            String invitationId,
            boolean accept
    ) {
        return resolveSpaceInvitation(actorUserId, actorEmail, spaceId, invitationId, null, accept);
    }

    @Transactional
    public MeetingCreationResult createMeeting(
            String actorUserId,
            String spaceId,
            String title,
            OffsetDateTime scheduledAt,
            List<String> participantUserIds
    ) {
        return createMeeting(actorUserId, spaceId, title, null, scheduledAt, scheduledAt == null ? null : scheduledAt.plusHours(1), participantUserIds);
    }

    @Transactional
    public MeetingCreationResult createMeeting(
            String actorUserId,
            String spaceId,
            String title,
            String description,
            OffsetDateTime scheduledAt,
            OffsetDateTime scheduledEndAt,
            List<String> participantUserIds
    ) {
        validateRequired(title, "회의 제목은 필수입니다.");
        if (scheduledAt == null) {
            throw invalidRequest("회의 예정 일시는 필수입니다.");
        }
        validateMeetingSchedule(scheduledAt, scheduledEndAt);

        store.lockSpace(spaceId);
        SpaceAccessPolicy.SpaceAccessContext spaceContext = spaceAccessContext(spaceId, actorUserId);
        spaceAccessPolicy.requireMemberManagement(spaceContext);
        requireUser(actorUserId);

        LinkedHashSet<String> participantIds = new LinkedHashSet<>();
        participantIds.add(actorUserId);
        if (participantUserIds != null) {
            participantIds.addAll(participantUserIds);
        }
        for (String participantUserId : participantIds) {
            requireUser(participantUserId);
        }

        Meeting meeting = store.createMeeting(spaceId, title.trim(), normalizeDescription(description), scheduledAt, scheduledEndAt);
        MeetingParticipant host = store.addMeetingParticipant(
                meeting.id(),
                actorUserId,
                MeetingRole.HOST,
                ParticipantType.MEMBER
        );
        for (String participantUserId : participantIds) {
            if (!participantUserId.equals(actorUserId)) {
                store.addMeetingParticipant(
                        meeting.id(),
                        participantUserId,
                        MeetingRole.VIEWER,
                        participantTypeForMeetingParticipant(spaceId, participantUserId)
                );
            }
        }

        return new MeetingCreationResult(meeting, host, store.findMeetingParticipants(meeting.id()));
    }

    @Transactional
    public MeetingCreationResult createInstantMeeting(String actorUserId, String spaceId) {
        store.lockSpace(spaceId);
        SpaceAccessPolicy.SpaceAccessContext spaceContext = spaceAccessContext(spaceId, actorUserId);
        spaceAccessPolicy.requireMemberManagement(spaceContext);
        requireUser(actorUserId);

        Space space = store.findSpaceById(spaceId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND,
                "SPACE_NOT_FOUND",
                "Space를 찾을 수 없습니다."
        ));
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        OffsetDateTime scheduledEndAt = startedAt.plusHours(1);
        String roomCode = defaultRoomCode(space.id());
        String title = (space.name() == null || space.name().isBlank() ? "Instant meeting" : space.name().trim() + " Live Room");
        Meeting meeting = store.createInstantMeeting(
                space.id(),
                roomCode,
                title,
                null,
                startedAt,
                scheduledEndAt
        );
        MeetingParticipant host = store.addMeetingParticipant(
                meeting.id(),
                actorUserId,
                MeetingRole.HOST,
                ParticipantType.MEMBER
        );
        return new MeetingCreationResult(meeting, host, store.findMeetingParticipants(meeting.id()));
    }

    public List<MeetingView> listMeetings(
            String actorUserId,
            String spaceId,
            String status,
            String from,
            String to
    ) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        MeetingStatus statusFilter = status == null ? null : MeetingStatus.parse(status);
        OffsetDateTime fromFilter = parseDateTime(from, "from");
        OffsetDateTime toFilter = parseDateTime(to, "to");
        if (fromFilter != null && toFilter != null && fromFilter.isAfter(toFilter)) {
            throw invalidRequest("from은 to보다 이후일 수 없습니다.");
        }

        return store.findMeetingsBySpaceId(spaceId).stream()
                .filter(meeting -> meetingAccessPolicy.canReadAccess(meetingAccessContext(meeting.id(), actorUserId)))
                .filter(meeting -> statusFilter == null || meeting.status() == statusFilter)
                .filter(meeting -> fromFilter == null || !meeting.scheduledAt().isBefore(fromFilter))
                .filter(meeting -> toFilter == null || !meeting.scheduledAt().isAfter(toFilter))
                .map(meeting -> new MeetingView(meeting, activeMeetingRole(meeting.id(), actorUserId), List.of()))
                .toList();
    }

    public List<MeetingView> listAccessibleMeetings(String actorUserId) {
        requireUser(actorUserId);
        return store.findAccessibleMeetings(actorUserId).stream()
                .map(meeting -> new MeetingView(meeting, activeMeetingRole(meeting.id(), actorUserId), List.of()))
                .toList();
    }

    public List<CalendarEvent> listCalendarEvents(String actorUserId, String spaceId, String from, String to) {
        requireUser(actorUserId);
        OffsetDateTime fromFilter = requireDateTime(from, "from");
        OffsetDateTime toFilter = requireDateTime(to, "to");
        if (fromFilter.isAfter(toFilter)) {
            throw invalidRequest("from은 to보다 이후일 수 없습니다.");
        }

        List<Space> spaces;
        if (spaceId == null || spaceId.isBlank()) {
            spaces = listSpaces(actorUserId).stream().map(SpaceSummary::space).toList();
        } else {
            spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
            spaces = List.of(store.findSpaceById(spaceId).orElseThrow());
        }

        return spaces.stream()
                .flatMap(space -> store.findMeetingsBySpaceId(space.id()).stream())
                .filter(meeting -> meetingAccessPolicy.canReadAccess(meetingAccessContext(meeting.id(), actorUserId)))
                .filter(meeting -> !meeting.scheduledAt().isBefore(fromFilter))
                .filter(meeting -> !meeting.scheduledAt().isAfter(toFilter))
                .sorted(java.util.Comparator.comparing(Meeting::scheduledAt))
                .map(CalendarEvent::new)
                .toList();
    }

    public MeetingView meetingDetail(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        Meeting meeting = requireMeeting(meetingId);
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
        List<MeetingParticipantWithUser> participants = store.findMeetingParticipants(meetingId).stream()
                .map(participant -> new MeetingParticipantWithUser(
                        participant,
                        store.findUserById(participant.userId()).orElse(null)
                ))
                .toList();
        return new MeetingView(meeting, activeMeetingRole(meetingId, actorUserId), participants);
    }

    @Transactional
    public Meeting updateMeeting(
            String actorUserId,
            String meetingId,
            String title,
            OffsetDateTime scheduledAt,
            String status
    ) {
        return updateMeeting(
                actorUserId, meetingId, title, null, scheduledAt,
                scheduledAt == null ? null : scheduledAt.plusHours(1), status
        );
    }

    @Transactional
    public Meeting updateMeeting(
            String actorUserId,
            String meetingId,
            String title,
            String description,
            OffsetDateTime scheduledAt,
            OffsetDateTime scheduledEndAt,
            String status
    ) {
        requireUser(actorUserId);
        Meeting current = requireMeeting(meetingId);
        store.lockMeeting(meetingId);
        current = requireMeeting(meetingId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));

        if (title == null && description == null && scheduledAt == null && scheduledEndAt == null && status == null) {
            throw invalidRequest("수정할 회의 필드가 필요합니다.");
        }
        if (title != null && title.isBlank()) {
            throw invalidRequest("회의 제목은 blank일 수 없습니다.");
        }
        if ((title != null || description != null || scheduledAt != null || scheduledEndAt != null) && current.status() != MeetingStatus.SCHEDULED) {
            throw invalidRequest("회의 정보는 SCHEDULED 상태에서만 수정할 수 있습니다.");
        }

        OffsetDateTime nextScheduledAt = scheduledAt == null ? current.scheduledAt() : scheduledAt;
        OffsetDateTime nextScheduledEndAt = scheduledEndAt == null ? current.scheduledEndAt() : scheduledEndAt;
        validateMeetingSchedule(nextScheduledAt, nextScheduledEndAt);

        MeetingStatus nextStatus = status == null ? current.status() : MeetingStatus.parse(status);
        validateMeetingTransition(current.status(), nextStatus);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime startedAt = current.startedAt();
        OffsetDateTime endedAt = current.endedAt();
        if (current.status() != nextStatus && nextStatus == MeetingStatus.IN_PROGRESS) {
            startedAt = now;
        }
        if (current.status() != nextStatus && nextStatus == MeetingStatus.ENDED) {
            endedAt = now;
        }

        Meeting updated = store.updateMeeting(
                meetingId,
                title == null ? current.title() : title.trim(),
                description == null ? current.description() : normalizeDescription(description),
                nextScheduledAt,
                nextScheduledEndAt,
                startedAt,
                endedAt,
                nextStatus
        );
        addAudit(
                "MEETING_UPDATED",
                actorUserId,
                null,
                meetingId,
                current.title() + "/" + current.scheduledAt() + "/" + current.scheduledEndAt() + "/" + current.status(),
                updated.title() + "/" + updated.scheduledAt() + "/" + updated.scheduledEndAt() + "/" + updated.status()
        );
        return updated;
    }

    @Transactional
    public boolean deleteMeeting(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        Meeting current = requireMeeting(meetingId);
        store.lockMeeting(meetingId);
        current = requireMeeting(meetingId);
        meetingAccessPolicy.requireDeleteAccess(meetingAccessContext(meetingId, actorUserId));
        if (current.status() == MeetingStatus.IN_PROGRESS) {
            throw new AuthorizationException(
                    HttpStatus.CONFLICT,
                    "MEETING_ALREADY_PROCESSING",
                    "진행 중인 회의는 삭제할 수 없습니다."
            );
        }

        MeetingStatus deletedStatus = current.status() == MeetingStatus.SCHEDULED
                ? MeetingStatus.CANCELED
                : current.status();
        store.softDeleteMeeting(meetingId, deletedStatus, actorUserId, Instant.now(clock));
        addAudit("MEETING_DELETED", actorUserId, null, meetingId, current.status().name(), deletedStatus.name());
        return true;
    }

    public List<TaskCardView> listTaskCards(String actorUserId, String spaceId, String status, String assigneeId, String keyword) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        TaskCardStatus statusFilter = status == null || status.isBlank() ? null : parseTaskStatus(status);
        if (assigneeId != null && !assigneeId.isBlank() && store.findSpaceMember(spaceId, assigneeId).isEmpty()) {
            throw invalidRequest("담당자는 active SpaceMember여야 합니다.");
        }
        String keywordFilter = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        return store.findTaskCards(spaceId).stream()
                .filter(task -> statusFilter == null || task.status() == statusFilter)
                .filter(task -> assigneeId == null || assigneeId.isBlank() || assigneeId.equals(task.assigneeId()))
                .filter(task -> keywordFilter == null || keywordFilter.isEmpty()
                        || task.title().toLowerCase(Locale.ROOT).contains(keywordFilter)
                        || (task.description() != null && task.description().toLowerCase(Locale.ROOT).contains(keywordFilter)))
                .map(task -> new TaskCardView(
                        task,
                        task.meetingId() == null || meetingAccessPolicy.canReadAccess(meetingAccessContext(task.meetingId(), actorUserId))
                ))
                .toList();
    }

    @Transactional
    public TaskCard createTaskCard(
            String actorUserId,
            String spaceId,
            String title,
            String description,
            String assigneeId,
            LocalDate dueDate,
            String meetingId
    ) {
        return createTaskCard(actorUserId, spaceId, title, description, assigneeId, dueDate, meetingId, null, null);
    }

    @Transactional
    public TaskCard createTaskCard(
            String actorUserId,
            String spaceId,
            String title,
            String description,
            String assigneeId,
            LocalDate dueDate,
            String meetingId,
            String priority,
            List<String> labels
    ) {
        requireUser(actorUserId);
        validateRequired(title, "태스크 제목은 필수입니다.");
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        String normalizedAssigneeId = blankToNull(assigneeId);
        if (normalizedAssigneeId != null && store.findSpaceMember(spaceId, normalizedAssigneeId).isEmpty()) {
            throw invalidRequest("담당자는 active SpaceMember여야 합니다.");
        }
        String normalizedMeetingId = blankToNull(meetingId);
        if (normalizedMeetingId != null) {
            Meeting meeting = requireMeeting(normalizedMeetingId);
            if (!meeting.spaceId().equals(spaceId)) {
                throw invalidRequest("태스크의 meetingId는 같은 Space에 속해야 합니다.");
            }
            meetingAccessPolicy.requireReadAccess(meetingAccessContext(normalizedMeetingId, actorUserId));
        }
        TaskCardPriority normalizedPriority = priority == null || priority.isBlank()
                ? TaskCardPriority.MEDIUM : parseTaskPriority(priority);
        List<String> normalizedLabels = normalizeTaskLabels(labels);
        Instant now = Instant.now(clock);
        TaskCard task = store.saveTaskCard(new TaskCard(
                "task-" + UUID.randomUUID(), spaceId, normalizedMeetingId, null, title.trim(), blankToNull(description),
                TaskCardStatus.TODO, normalizedPriority, normalizedLabels, normalizedAssigneeId, dueDate, now, now, null
        ));
        addAudit("TASK_CARD_CHANGED", actorUserId, normalizedAssigneeId, task.id(), null, "CREATED");
        return task;
    }

    @Transactional
    public TaskCard updateTaskCard(String actorUserId, String spaceId, String taskId, TaskCardPatch patch) {
        requireUser(actorUserId);
        if (!patch.hasUpdates()) {
            throw invalidRequest("수정할 태스크 필드가 필요합니다.");
        }
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        TaskCard current = store.findTaskCardById(spaceId, taskId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "태스크를 찾을 수 없습니다."
        ));
        if (patch.titlePresent() && (patch.title() == null || patch.title().isBlank())) {
            throw invalidRequest("태스크 제목은 blank일 수 없습니다.");
        }
        String nextAssigneeId = patch.assigneePresent() ? blankToNull(patch.assigneeId()) : current.assigneeId();
        if (nextAssigneeId != null && store.findSpaceMember(spaceId, nextAssigneeId).isEmpty()) {
            throw invalidRequest("담당자는 active SpaceMember여야 합니다.");
        }
        TaskCard updated = store.saveTaskCard(current.updated(
                patch.titlePresent() ? patch.title().trim() : current.title(),
                patch.descriptionPresent() ? blankToNull(patch.description()) : current.description(),
                patch.statusPresent() ? parseTaskStatus(patch.status()) : current.status(),
                patch.priorityPresent() ? parseTaskPriority(patch.priority()) : current.priority(),
                patch.labelsPresent() ? normalizeTaskLabels(patch.labels()) : current.labels(),
                nextAssigneeId,
                patch.dueDatePresent() ? patch.dueDate() : current.dueDate(),
                Instant.now(clock)
        ));
        addAudit("TASK_CARD_CHANGED", actorUserId, nextAssigneeId, taskId, current.status().name(), updated.status().name());
        return updated;
    }

    @Transactional
    public boolean deleteTaskCard(String actorUserId, String spaceId, String taskId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        store.findTaskCardById(spaceId, taskId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "태스크를 찾을 수 없습니다."
        ));
        store.softDeleteTaskCard(taskId, Instant.now(clock));
        addAudit("TASK_CARD_CHANGED", actorUserId, null, taskId, "ACTIVE", "SOFT_DELETED");
        return true;
    }

    public List<SpaceMemberWithUser> listSpaceMembers(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        return store.findSpaceMembersBySpaceId(spaceId)
                .stream()
                .map(member -> new SpaceMemberWithUser(member, store.findUserById(member.userId()).orElse(null)))
                .toList();
    }

    @Transactional
    public SpaceMember updateSpaceMemberRole(String actorUserId, String spaceId, String memberId, String role) {
        requireUser(actorUserId);
        SpaceRole nextRole = SpaceRole.parse(role);
        if (nextRole == SpaceRole.OWNER) {
            throw invalidRequest("OWNER 이양은 owner-transfer API를 사용해야 합니다.");
        }
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireOwnerManagement(spaceAccessContext(spaceId, actorUserId));
        SpaceMember target = requireSpaceMemberById(spaceId, memberId);
        if (target.role() == SpaceRole.OWNER) {
            throw invalidRequest("기존 OWNER role 변경은 owner-transfer API를 사용해야 합니다.");
        }

        SpaceMember updated = store.updateSpaceMemberRole(memberId, nextRole);
        addAudit(
                "SPACE_MEMBER_ROLE_CHANGED",
                actorUserId,
                target.userId(),
                spaceId,
                target.role().name(),
                updated.role().name()
        );
        return updated;
    }

    @Transactional
    public boolean removeSpaceMember(String actorUserId, String spaceId, String memberId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        SpaceMember target = requireSpaceMemberById(spaceId, memberId);
        if (target.role() == SpaceRole.OWNER) {
            throw new AuthorizationException(HttpStatus.CONFLICT, "INVALID_REQUEST", "OWNER는 제거할 수 없습니다.");
        }

        for (Meeting meeting : store.findMeetingsBySpaceId(spaceId)) {
            store.findMeetingParticipant(meeting.id(), target.userId())
                    .filter(participant -> participant.participantType() == ParticipantType.MEMBER)
                    .ifPresent(participant -> store.updateMeetingParticipantType(
                            participant.id(),
                            ParticipantType.GUEST
                    ));
        }

        store.removeSpaceMember(memberId);
        addAudit("SPACE_MEMBER_REMOVED", actorUserId, target.userId(), spaceId, target.role().name(), "REMOVED_PROJECT_ACCESS");
        return true;
    }

    @Transactional
    public boolean leaveSpace(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        SpaceMember target = store.findSpaceMember(spaceId, actorUserId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "SPACE_MEMBER_NOT_FOUND", "Space 멤버가 아닙니다."
        ));
        if (target.role() == SpaceRole.OWNER) {
            throw new AuthorizationException(HttpStatus.CONFLICT, "OWNER_TRANSFER_REQUIRED", "OWNER는 소유권을 이양한 뒤 Space를 나갈 수 있습니다.");
        }
        for (Meeting meeting : store.findMeetingsBySpaceId(spaceId)) {
            store.findMeetingParticipant(meeting.id(), actorUserId)
                    .filter(participant -> participant.participantType() == ParticipantType.MEMBER)
                    .ifPresent(participant -> store.updateMeetingParticipantType(participant.id(), ParticipantType.GUEST));
        }
        store.removeSpaceMember(target.id());
        addAudit("SPACE_MEMBER_LEFT", actorUserId, actorUserId, spaceId, target.role().name(), "REMOVED_PROJECT_ACCESS");
        return true;
    }

    @Transactional
    public OwnerTransferResult transferOwner(
            String actorUserId,
            String spaceId,
            String targetMemberId,
            String confirmationText,
            String previousOwnerRole
    ) {
        requireUser(actorUserId);
        validateRequired(targetMemberId, "새 OWNER 대상은 필수입니다.");
        if (!"TRANSFER OWNER".equals(confirmationText)) {
            throw invalidRequest("OWNER 이양 확인 문자열이 일치하지 않습니다.");
        }
        SpaceRole previousRole = previousOwnerRole == null || previousOwnerRole.isBlank()
                ? SpaceRole.ADMIN
                : SpaceRole.parse(previousOwnerRole);
        if (previousRole == SpaceRole.OWNER) {
            throw invalidRequest("기존 OWNER 강등 role은 ADMIN 또는 MEMBER여야 합니다.");
        }
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireOwnerManagement(spaceAccessContext(spaceId, actorUserId));
        SpaceMember currentOwner = store.findSpaceMember(spaceId, actorUserId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.FORBIDDEN,
                        "SPACE_ACCESS_DENIED",
                        "Space 접근 권한이 없습니다."
                ));
        SpaceMember target = requireSpaceMemberById(spaceId, targetMemberId);
        if (target.userId().equals(actorUserId)) {
            throw invalidRequest("자기 자신에게 OWNER를 이양할 수 없습니다.");
        }

        WorkspaceStore.OwnerTransferUpdate update = store.transferOwner(
                currentOwner.id(),
                target.id(),
                previousRole
        );
        addAudit(
                "OWNER_TRANSFERRED",
                actorUserId,
                target.userId(),
                spaceId,
                currentOwner.userId() + ":OWNER," + target.userId() + ":" + target.role().name(),
                currentOwner.userId() + ":" + update.previousOwner().role().name() + "," + target.userId() + ":OWNER"
        );
        return new OwnerTransferResult(update.newOwner(), update.previousOwner());
    }

    public List<MeetingParticipantWithUser> listMeetingParticipants(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
        return store.findMeetingParticipants(meetingId)
                .stream()
                .map(participant -> new MeetingParticipantWithUser(
                        participant,
                        store.findUserById(participant.userId()).orElse(null)
                ))
                .toList();
    }

    @Transactional
    public MeetingParticipant addMeetingParticipant(
            String actorUserId,
            String meetingId,
            String userId,
            String role,
            String participantType
    ) {
        requireUser(actorUserId);
        validateRequired(userId, "참여자 userId는 필수입니다.");
        MeetingRole parsedRole = MeetingRole.parse(role);
        ParticipantType parsedType = participantType == null || participantType.isBlank()
                ? ParticipantType.GUEST
                : ParticipantType.parse(participantType);
        Meeting meeting = requireMeeting(meetingId);
        store.lockMeeting(meetingId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));
        requireUser(userId);
        if (parsedType == ParticipantType.MEMBER && store.findSpaceMember(meeting.spaceId(), userId).isEmpty()) {
            throw new AuthorizationException(
                    HttpStatus.FORBIDDEN,
                    "SPACE_ACCESS_DENIED",
                    "member participant는 SpaceMember여야 합니다."
            );
        }
        if (store.findMeetingParticipant(meetingId, userId).isPresent()) {
            throw invalidRequest("이미 회의 참여자로 등록된 사용자입니다.");
        }

        MeetingParticipant participant = store.addMeetingParticipant(meetingId, userId, parsedRole, parsedType);
        addAudit("MEETING_PARTICIPANT_CHANGED", actorUserId, userId, meetingId, "NONE", participant.role().name());
        return participant;
    }


    @Transactional
    public MeetingJoinRequest createMeetingJoinRequest(String actorUserId, String joinCodeOrUrl) {
        requireUser(actorUserId);
        validateRequired(joinCodeOrUrl, "회의 코드 또는 URL은 필수입니다.");
        if (joinCodeOrUrl.length() > 2048) {
            throw invalidRequest("회의 코드 또는 URL은 2048자 이하여야 합니다.");
        }
        String normalized = normalizeJoinCode(joinCodeOrUrl);
        Meeting meeting = store.findMeetingByJoinCode(normalized)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.FORBIDDEN,
                        "MEETING_ACCESS_DENIED",
                        "유효하지 않은 회의 코드 또는 URL입니다."
                ));
        store.lockMeeting(meeting.id());
        if (store.findMeetingParticipant(meeting.id(), actorUserId)
                .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                .isPresent()) {
            throw invalidRequest("이미 회의 접근 권한이 있습니다.");
        }
        if (store.findMeetingJoinRequests(meeting.id())
                .stream()
                .anyMatch(request -> request.userId().equals(actorUserId) && request.status() == MeetingJoinRequestStatus.PENDING)) {
            throw invalidRequest("이미 대기 중인 참가 신청이 있습니다.");
        }

        MeetingJoinRequest request = store.createMeetingJoinRequest(meeting.id(), actorUserId, Instant.now(clock));
        addAudit("MEETING_JOIN_REQUEST_CREATED", actorUserId, actorUserId, meeting.id(), "NONE", request.status().name());
        return request;
    }

    @Transactional
    public MeetingInvitationCreation createMeetingInvitation(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        store.lockMeeting(meetingId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));
        Instant now = Instant.now(clock);
        String token = invitationToken();
        MeetingInvitation invitation = store.saveMeetingInvitation(new MeetingInvitation(
                "meeting-invitation-" + UUID.randomUUID(), meetingId, null, MeetingRole.VIEWER,
                ParticipantType.GUEST, InvitationStatus.PENDING, sha256(token), now.plus(Duration.ofDays(7)), null, null
        ));
        addAudit("MEETING_GUEST_INVITED", actorUserId, null, meetingId, null, invitation.id());
        return new MeetingInvitationCreation(invitation, token);
    }

    @Transactional
    public MeetingInvitationResolution resolveMeetingInvitation(String actorUserId, String meetingId, String invitationId, String token, boolean accept) {
        requireUser(actorUserId);
        store.lockMeeting(meetingId);
        MeetingInvitation invitation = store.findMeetingInvitationById(meetingId, invitationId)
                .orElseThrow(() -> new AuthorizationException(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", "초대를 찾을 수 없습니다."));
        if (!MessageDigest.isEqual(sha256(token).getBytes(StandardCharsets.UTF_8), invitation.tokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new AuthorizationException(HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED", "유효하지 않은 초대 링크입니다.");
        }
        if (invitation.status() == InvitationStatus.ACCEPTED) {
            MeetingParticipant participant = store.findMeetingParticipant(meetingId, actorUserId).orElseThrow(() -> invalidRequest("수락된 초대의 참가자를 찾을 수 없습니다."));
            return new MeetingInvitationResolution(invitation, participant);
        }
        if (invitation.status() != InvitationStatus.PENDING) throw invalidRequest("처리할 수 없는 초대 상태입니다.");
        Instant now = Instant.now(clock);
        if (!invitation.expiresAt().isAfter(now)) { store.saveMeetingInvitation(invitation.expired()); throw invalidRequest("만료된 초대입니다."); }
        if (!accept) return new MeetingInvitationResolution(store.saveMeetingInvitation(invitation.declined(now)), null);
        MeetingParticipant participant = store.findMeetingParticipant(meetingId, actorUserId)
                .orElseGet(() -> store.addMeetingParticipant(meetingId, actorUserId, invitation.meetingRole(), invitation.participantType()));
        MeetingInvitation accepted = store.saveMeetingInvitation(invitation.accepted(now));
        addAudit("MEETING_INVITATION_ACCEPTED", actorUserId, actorUserId, meetingId, "PENDING", "ACCEPTED");
        return new MeetingInvitationResolution(accepted, participant);
    }

    public List<MeetingJoinRequest> listMeetingJoinRequests(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));
        return store.findMeetingJoinRequests(meetingId);
    }

    @Transactional
    public MeetingJoinRequest approveMeetingJoinRequest(
            String actorUserId,
            String meetingId,
            String requestId
    ) {
        requireUser(actorUserId);
        store.lockMeeting(meetingId);
        MeetingJoinRequest request = requireMeetingJoinRequestById(meetingId, requestId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));
        if (request.status() != MeetingJoinRequestStatus.PENDING) {
            throw invalidRequest("대기 중인 신청만 승인할 수 있습니다.");
        }

        Meeting meeting = requireMeeting(meetingId);
        ParticipantType participantType = store.findSpaceMember(meeting.spaceId(), request.userId()).isPresent()
                ? ParticipantType.MEMBER
                : ParticipantType.GUEST;
        if (store.findMeetingParticipant(meetingId, request.userId()).isPresent()) {
            throw invalidRequest("이미 회의 참여자로 등록된 사용자입니다.");
        }

        store.addMeetingParticipant(meetingId, request.userId(), MeetingRole.VIEWER, participantType);
        MeetingJoinRequest updated = store.updateMeetingJoinRequest(
                requestId,
                MeetingJoinRequestStatus.APPROVED,
                Instant.now(clock),
                actorUserId
        );
        addAudit("MEETING_JOIN_REQUEST_RESOLVED", actorUserId, request.userId(), meetingId, "PENDING", "APPROVED");
        return updated;
    }

    @Transactional
    public MeetingJoinRequest rejectMeetingJoinRequest(
            String actorUserId,
            String meetingId,
            String requestId
    ) {
        requireUser(actorUserId);
        store.lockMeeting(meetingId);
        MeetingJoinRequest request = requireMeetingJoinRequestById(meetingId, requestId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));
        if (request.status() != MeetingJoinRequestStatus.PENDING) {
            throw invalidRequest("대기 중인 신청만 거절할 수 있습니다.");
        }

        MeetingJoinRequest updated = store.updateMeetingJoinRequest(
                requestId,
                MeetingJoinRequestStatus.REJECTED,
                Instant.now(clock),
                actorUserId
        );
        addAudit("MEETING_JOIN_REQUEST_RESOLVED", actorUserId, request.userId(), meetingId, "PENDING", "REJECTED");
        return updated;
    }

    private ParticipantType participantTypeForMeetingParticipant(String spaceId, String userId) {
        return store.findSpaceMember(spaceId, userId).isPresent() ? ParticipantType.MEMBER : ParticipantType.GUEST;
    }

    private MeetingRole activeMeetingRole(String meetingId, String userId) {
        return store.findMeetingParticipant(meetingId, userId)
                .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                .map(MeetingParticipant::role)
                .orElse(null);
    }

    private OffsetDateTime parseDateTime(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidRequest(field + "은 ISO-8601 일시여야 합니다.");
        }
    }

    private OffsetDateTime requireDateTime(String value, String field) {
        OffsetDateTime parsed = parseDateTime(value, field);
        if (parsed == null) {
            throw invalidRequest(field + "은 필수입니다.");
        }
        return parsed;
    }

    private KnowledgeType parseKnowledgeType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return KnowledgeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("Project Knowledge type 값이 올바르지 않습니다.");
        }
    }

    private KnowledgeType parseRequiredKnowledgeType(String value) {
        KnowledgeType parsed = parseKnowledgeType(value);
        if (parsed == null) {
            throw invalidRequest("Project Knowledge type은 필수입니다.");
        }
        return parsed;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private void requireKnowledgeSourceMeetingAccess(String actorUserId, String spaceId, String sourceMeetingId) {
        if (sourceMeetingId == null) {
            return;
        }
        Meeting sourceMeeting = store.findMeetingById(sourceMeetingId).orElseThrow(() -> new AuthorizationException(
                HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", "원본 회의를 찾을 수 없습니다."
        ));
        if (!sourceMeeting.spaceId().equals(spaceId)) {
            throw invalidRequest("원본 회의는 같은 Space에 속해야 합니다.");
        }
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(sourceMeetingId, actorUserId));
    }

    private ProjectKnowledge findActiveProjectKnowledge(String spaceId, String knowledgeId) {
        return store.findProjectKnowledge(spaceId).stream()
                .filter(knowledge -> knowledge.id().equals(knowledgeId))
                .filter(knowledge -> knowledge.deletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND, "PROJECT_KNOWLEDGE_NOT_FOUND", "Project Knowledge를 찾을 수 없습니다."
                ));
    }

    private void validateMeetingTransition(MeetingStatus current, MeetingStatus next) {
        if (current == next) {
            return;
        }
        boolean allowed = (current == MeetingStatus.SCHEDULED
                && (next == MeetingStatus.IN_PROGRESS || next == MeetingStatus.CANCELED))
                || (current == MeetingStatus.IN_PROGRESS && next == MeetingStatus.ENDED);
        if (!allowed) {
            throw invalidRequest("허용되지 않은 회의 상태 전이입니다: " + current + " -> " + next);
        }
    }

    private void validateMeetingSchedule(OffsetDateTime scheduledAt, OffsetDateTime scheduledEndAt) {
        if (scheduledEndAt == null) {
            throw invalidRequest("회의 예정 종료 일시는 필수입니다.");
        }
        if (!scheduledEndAt.isAfter(scheduledAt)) {
            throw invalidRequest("회의 예정 종료 일시는 시작 일시보다 이후여야 합니다.");
        }
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private MeetingJoinRequest requireMeetingJoinRequestById(String meetingId, String requestId) {
        requireMeeting(meetingId);
        return store.findMeetingJoinRequestByIdForUpdate(meetingId, requestId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의 참가 신청을 찾을 수 없습니다."
                ));
    }

    private String normalizeJoinCode(String value) {
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed);
            String query = uri.getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] tokens = part.split("=", 2);
                    if (tokens.length == 2 && "joinCode".equals(tokens[0])) {
                        return URLDecoder.decode(tokens[1], StandardCharsets.UTF_8);
                    }
                }
            }
            if (uri.getScheme() != null || uri.getHost() != null || trimmed.contains("/") || trimmed.contains("?")) {
                return "";
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        return trimmed;
    }

    @Transactional
    public MeetingParticipant updateMeetingParticipant(
            String actorUserId,
            String meetingId,
            String participantId,
            String role,
            String accessStatus
    ) {
        requireUser(actorUserId);
        store.lockMeeting(meetingId);
        MeetingParticipant target = requireMeetingParticipantById(meetingId, participantId);
        MeetingRole nextRole = role == null || role.isBlank() ? target.role() : MeetingRole.parse(role);
        ParticipantAccessStatus nextStatus = accessStatus == null || accessStatus.isBlank()
                ? target.accessStatus()
                : ParticipantAccessStatus.parse(accessStatus);
        meetingAccessPolicy.requireParticipantMutationAllowed(
                meetingAccessContext(meetingId, actorUserId),
                toPolicyParticipant(target),
                nextRole,
                nextStatus,
                nextStatus == ParticipantAccessStatus.REVOKED
        );

        MeetingParticipant updated = store.updateMeetingParticipant(participantId, nextRole, nextStatus);
        addAudit(
                "MEETING_PARTICIPANT_CHANGED",
                actorUserId,
                target.userId(),
                meetingId,
                target.role().name() + "/" + target.accessStatus().name(),
                updated.role().name() + "/" + updated.accessStatus().name()
        );
        return updated;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectAiContextCandidates projectAiContextCandidates(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        List<ProjectKnowledge> knowledge = store.findProjectKnowledge(spaceId)
                .stream()
                .filter(item -> item.status() == KnowledgeStatus.PUBLISHED)
                .filter(item -> item.deletedAt() == null)
                .toList();
        List<Meeting> meetings = store.findProjectAiMeetings(spaceId, actorUserId);
        return new ProjectAiContextCandidates(knowledge, meetings);
    }

    @Transactional(readOnly = true)
    public List<ProjectKnowledgeView> listProjectKnowledge(
            String actorUserId,
            String spaceId,
            String type,
            String keyword
    ) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        KnowledgeType typeFilter = parseKnowledgeType(type);
        String keywordFilter = blankToNull(keyword);
        return store.findProjectKnowledge(spaceId).stream()
                .filter(knowledge -> knowledge.status() == KnowledgeStatus.PUBLISHED)
                .filter(knowledge -> knowledge.deletedAt() == null)
                .filter(knowledge -> typeFilter == null || knowledge.type() == typeFilter)
                .filter(knowledge -> keywordFilter == null || containsIgnoreCase(knowledge.title(), keywordFilter)
                        || containsIgnoreCase(knowledge.content(), keywordFilter))
                .map(knowledge -> new ProjectKnowledgeView(
                        knowledge,
                        knowledge.sourceMeetingId() != null
                                && meetingAccessPolicy.canReadAccess(meetingAccessContext(knowledge.sourceMeetingId(), actorUserId))
                ))
                .toList();
    }

    @Transactional
    public ProjectKnowledge createProjectKnowledge(
            String actorUserId,
            String spaceId,
            String type,
            String title,
            String content,
            String sourceMeetingId
    ) {
        requireUser(actorUserId);
        KnowledgeType knowledgeType = parseRequiredKnowledgeType(type);
        validateRequired(title, "Project Knowledge 제목은 필수입니다.");
        validateRequired(content, "Project Knowledge 내용은 필수입니다.");
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        String normalizedSourceMeetingId = blankToNull(sourceMeetingId);
        requireKnowledgeSourceMeetingAccess(actorUserId, spaceId, normalizedSourceMeetingId);
        Instant now = Instant.now(clock);
        ProjectKnowledge created = store.saveProjectKnowledge(new ProjectKnowledge(
                "knowledge-" + UUID.randomUUID(), spaceId, knowledgeType, title.trim(), content.trim(),
                normalizedSourceMeetingId, actorUserId, KnowledgeStatus.PUBLISHED, EmbeddingStatus.PENDING,
                null, now, now, null
        ));
        addAudit("PROJECT_KNOWLEDGE_CREATED", actorUserId, null, created.id(), null, created.type().name());
        return created;
    }

    @Transactional(readOnly = true)
    public ProjectKnowledgeView projectKnowledgeDetail(String actorUserId, String spaceId, String knowledgeId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        ProjectKnowledge knowledge = findActiveProjectKnowledge(spaceId, knowledgeId);
        return new ProjectKnowledgeView(
                knowledge,
                knowledge.sourceMeetingId() != null
                        && meetingAccessPolicy.canReadAccess(meetingAccessContext(knowledge.sourceMeetingId(), actorUserId))
        );
    }

    @Transactional
    public ProjectKnowledge updateProjectKnowledge(
            String actorUserId,
            String spaceId,
            String knowledgeId,
            ProjectKnowledgePatch patch
    ) {
        requireUser(actorUserId);
        if (!patch.hasUpdates()) {
            throw invalidRequest("수정할 Project Knowledge 필드가 필요합니다.");
        }
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        ProjectKnowledge current = findActiveProjectKnowledge(spaceId, knowledgeId);
        if (patch.titlePresent() && (patch.title() == null || patch.title().isBlank())) {
            throw invalidRequest("Project Knowledge 제목은 blank일 수 없습니다.");
        }
        if (patch.contentPresent() && (patch.content() == null || patch.content().isBlank())) {
            throw invalidRequest("Project Knowledge 내용은 blank일 수 없습니다.");
        }
        ProjectKnowledge updated = store.saveProjectKnowledge(current.updated(
                patch.titlePresent() ? patch.title().trim() : current.title(),
                patch.contentPresent() ? patch.content().trim() : current.content(),
                Instant.now(clock)
        ));
        addAudit("PROJECT_KNOWLEDGE_UPDATED", actorUserId, null, knowledgeId, current.title(), updated.title());
        return updated;
    }

    @Transactional
    public boolean archiveProjectKnowledge(String actorUserId, String spaceId, String knowledgeId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        ProjectKnowledge current = findActiveProjectKnowledge(spaceId, knowledgeId);
        store.saveProjectKnowledge(current.archived(Instant.now(clock)));
        addAudit("PROJECT_KNOWLEDGE_DELETED", actorUserId, null, knowledgeId, "PUBLISHED", "ARCHIVED");
        return true;
    }

    @Transactional
    public boolean restoreProjectKnowledge(String actorUserId, String spaceId, String knowledgeId) {
        requireUser(actorUserId);
        store.lockSpace(spaceId);
        spaceAccessPolicy.requireMemberManagement(spaceAccessContext(spaceId, actorUserId));
        ProjectKnowledge current = store.findProjectKnowledge(spaceId).stream()
                .filter(knowledge -> knowledge.id().equals(knowledgeId))
                .filter(knowledge -> knowledge.status() == KnowledgeStatus.ARCHIVED)
                .filter(knowledge -> knowledge.deletedAt() != null)
                .findFirst()
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND, "PROJECT_KNOWLEDGE_NOT_FOUND", "보관된 Project Knowledge를 찾을 수 없습니다."
                ));
        ProjectKnowledge restored = store.saveProjectKnowledge(current.restored(Instant.now(clock)));
        addAudit("PROJECT_KNOWLEDGE_RESTORED", actorUserId, null, knowledgeId, "ARCHIVED", "PUBLISHED");
        return restored.deletedAt() == null;
    }

    public SpaceAccessPolicy.SpaceAccessContext spaceAccessContext(String spaceId, String userId) {
        return new SpaceAccessPolicy.SpaceAccessContext(
                store.findSpaceById(spaceId).isPresent(),
                store.findSpaceMember(spaceId, userId)
                        .map(member -> new SpaceAccessPolicy.SpaceMembership(
                                member.spaceId(),
                                member.userId(),
                                member.role(),
                                true
                        ))
                        .orElse(null)
        );
    }

    public MeetingAccessPolicy.MeetingAccessContext meetingAccessContext(String meetingId, String userId) {
        Meeting meeting = store.findMeetingById(meetingId).orElse(null);
        if (meeting == null) {
            return new MeetingAccessPolicy.MeetingAccessContext(false, null, null, null, List.of());
        }

        MeetingParticipant participant = store.findMeetingParticipant(meetingId, userId).orElse(null);
        return new MeetingAccessPolicy.MeetingAccessContext(
                true,
                meeting.status(),
                spaceAccessContext(meeting.spaceId(), userId),
                participant == null ? null : new MeetingAccessPolicy.MeetingParticipant(
                        participant.id(),
                        participant.meetingId(),
                        participant.userId(),
                        participant.role(),
                        participant.participantType(),
                        participant.accessStatus()
                ),
                store.findMeetingParticipants(meetingId)
                        .stream()
                        .map(found -> new MeetingAccessPolicy.MeetingParticipant(
                                found.id(),
                                found.meetingId(),
                                found.userId(),
                                found.role(),
                                found.participantType(),
                                found.accessStatus()
                        ))
                        .toList()
        );
    }

    public String meetingRoomName(String meetingId) {
        Meeting meeting = requireMeeting(meetingId);
        if (meeting.roomCode() != null && !meeting.roomCode().isBlank()) {
            return meeting.roomCode();
        }
        return meeting.id();
    }

    public MeetingAiContext meetingAiContext(String meetingId) {
        Meeting meeting = store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        return new MeetingAiContext(
                meeting,
                store.findTranscriptSegments(meetingId),
                store.findMeetingReports(meetingId)
        );
    }

    @Transactional
    public MeetingTranscript startMeetingTranscript(String actorUserId, String meetingId, String provider) {
        requireTranscriptManagement(actorUserId, meetingId);
        store.lockMeeting(meetingId);
        Meeting meeting = requireMeeting(meetingId);

        MeetingTranscript existing = store.findMeetingTranscript(meetingId).orElse(null);
        if (existing != null && existing.status() == TranscriptStatus.PROCESSING) {
            throw new AuthorizationException(HttpStatus.CONFLICT, "TRANSCRIPTION_ALREADY_PROCESSING", "이미 진행 중인 전사가 있습니다.");
        }
        if (existing != null && existing.status() == TranscriptStatus.COMPLETED) {
            throw new AuthorizationException(HttpStatus.CONFLICT, "TRANSCRIPTION_ALREADY_COMPLETED", "완료된 전사가 있습니다.");
        }

        Instant now = Instant.now(clock);
        MeetingTranscript transcript = store.saveMeetingTranscript(new MeetingTranscript(
                meetingId,
                TranscriptStatus.PROCESSING,
                provider,
                "ko-KR",
                now,
                null,
                null,
                retentionUntil(meeting, now),
                existing != null && existing.legalHold(),
                existing == null ? null : existing.purgedAt(),
                existing == null ? now : existing.createdAt(),
                now
        ));
        addAudit("MEETING_TRANSCRIPTION_STARTED", actorUserId, null, meetingId, null, provider);
        return transcript;
    }

    @Transactional
    public TranscriptSegment appendTranscriptSegment(
            String meetingId,
            String speakerLabel,
            String speakerName,
            int startMs,
            int endMs,
            String text
    ) {
        store.lockMeeting(meetingId);
        MeetingTranscript transcript = requireProcessingTranscript(meetingId);
        if (text == null || text.isBlank()) {
            throw invalidRequest("전사 내용은 비어 있을 수 없습니다.");
        }
        if (startMs < 0 || endMs < startMs) {
            throw invalidRequest("전사 시간 범위가 올바르지 않습니다.");
        }
        MeetingSpeaker speaker = store.findMeetingSpeakers(meetingId).stream()
                .filter(candidate -> candidate.label().equals(speakerLabel))
                .findFirst()
                .orElseGet(() -> store.addMeetingSpeaker(meetingId, speakerLabel, speakerName, Instant.now(clock)));
        int sequence = store.findTranscriptSegments(meetingId).size();
        return store.addTranscriptSegment(
                transcript.meetingId(),
                speaker.id(),
                speaker.label(),
                speaker.displayName(),
                startMs,
                endMs,
                text.trim(),
                "stt",
                sequence
        );
    }

    @Transactional
    public MeetingTranscript completeMeetingTranscript(String meetingId) {
        store.lockMeeting(meetingId);
        MeetingTranscript transcript = requireProcessingTranscript(meetingId);
        Instant now = Instant.now(clock);
        return store.saveMeetingTranscript(new MeetingTranscript(
                transcript.meetingId(),
                TranscriptStatus.COMPLETED,
                transcript.provider(),
                transcript.language(),
                transcript.startedAt(),
                now,
                null,
                transcript.retentionUntil(),
                transcript.legalHold(),
                transcript.purgedAt(),
                transcript.createdAt(),
                now
        ));
    }

    @Transactional
    public MeetingTranscript failMeetingTranscript(String meetingId) {
        store.lockMeeting(meetingId);
        MeetingTranscript transcript = requireProcessingTranscript(meetingId);
        Instant now = Instant.now(clock);
        return store.saveMeetingTranscript(new MeetingTranscript(
                transcript.meetingId(),
                TranscriptStatus.FAILED,
                transcript.provider(),
                transcript.language(),
                transcript.startedAt(),
                now,
                "STT provider 오류",
                transcript.retentionUntil(),
                transcript.legalHold(),
                transcript.purgedAt(),
                transcript.createdAt(),
                now
        ));
    }

    public MeetingTranscriptView meetingTranscript(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
        MeetingTranscript transcript = store.findMeetingTranscript(meetingId)
                .orElseThrow(() -> new AuthorizationException(HttpStatus.NOT_FOUND, "TRANSCRIPT_NOT_FOUND", "전사를 찾을 수 없습니다."));
        return new MeetingTranscriptView(transcript, store.findTranscriptSegments(meetingId));
    }

    public void requireTranscriptManagement(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        // Any active meeting participant may contribute to the shared transcript.
        // Host-only checks remain for participant and meeting lifecycle management.
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
    }

    public TaskCandidateContext taskCandidateContext(String meetingId) {
        MeetingAiContext context = meetingAiContext(meetingId);
        List<TaskParticipant> participants = store.findMeetingParticipants(meetingId).stream()
                .map(participant -> store.findUserById(participant.userId())
                        .map(user -> new TaskParticipant(
                                user.id(),
                                user.displayName(),
                                participant.role(),
                                participant.accessStatus(),
                                store.findSpaceMember(context.meeting().spaceId(), user.id()).isPresent()
                        ))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<TaskAssignee> assignees = store.findSpaceMembers(context.meeting().spaceId()).stream()
                .map(member -> store.findUserById(member.userId())
                        .map(user -> new TaskAssignee(user.id(), user.displayName()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new TaskCandidateContext(
                context.meeting(),
                context.transcriptSegments(),
                context.reports(),
                participants,
                assignees
        );
    }

    @Transactional
    public MeetingReport saveReportCandidate(
            String meetingId,
            String createdBy,
            String title,
            String summary,
            String markdown,
            List<MeetingReport.ReportDecision> decisions,
            List<MeetingReport.ReportActionItem> actionItems,
            List<String> sourceIds
    ) {
        requireUser(createdBy);
        store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        store.lockMeeting(meetingId);
        int nextVersion = store.findMeetingReports(meetingId).stream()
                .mapToInt(MeetingReport::version)
                .max()
                .orElse(0) + 1;
        return store.saveMeetingReport(new MeetingReport(
                "report-" + UUID.randomUUID(),
                meetingId,
                MeetingReportStatus.CANDIDATE,
                title,
                summary,
                markdown,
                decisions,
                actionItems,
                sourceIds,
                createdBy,
                nextVersion,
                false,
                Instant.now(clock),
                null
        ));
    }

    @Transactional
    public synchronized MeetingReport confirmMeetingReport(String meetingId, String reportId) {
        store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        store.lockMeeting(meetingId);
        MeetingReport target = store.findMeetingReportById(reportId)
                .filter(report -> report.meetingId().equals(meetingId))
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_NOT_FOUND",
                        "회의록을 찾을 수 없습니다."
                ));
        if (target.status() != MeetingReportStatus.CANDIDATE && target.status() != MeetingReportStatus.DRAFT) {
            throw invalidRequest("CANDIDATE 또는 DRAFT 회의록만 확정할 수 있습니다.");
        }
        if (target.status() == MeetingReportStatus.CANDIDATE && candidateExpired(target.createdAt(), REPORT_CANDIDATE_TTL)) {
            throw candidateExpiredError("AI 회의록 후보가 만료되었습니다. 새 후보를 생성해 주세요.");
        }

        int latestVersion = store.findMeetingReports(meetingId).stream()
                .mapToInt(MeetingReport::version)
                .max()
                .orElse(target.version());
        if (target.version() != latestVersion) {
            throw new AuthorizationException(
                    HttpStatus.CONFLICT,
                    "REPORT_VERSION_CONFLICT",
                    "최신 회의록 version만 확정할 수 있습니다."
            );
        }

        store.findMeetingReports(meetingId).stream()
                .filter(report -> report.status() == MeetingReportStatus.CONFIRMED)
                .filter(MeetingReport::current)
                .filter(report -> !report.id().equals(reportId))
                .map(MeetingReport::withoutCurrent)
                .forEach(store::saveMeetingReport);
        return store.saveMeetingReport(target.confirmed(Instant.now(clock)));
    }

    public List<MeetingReport> listMeetingReports(String actorUserId, String meetingId, String status) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
        MeetingReportStatus statusFilter = status == null || status.isBlank() ? null : parseReportStatus(status);
        return store.findMeetingReports(meetingId).stream()
                .filter(report -> statusFilter == null
                        ? report.status() == MeetingReportStatus.CANDIDATE
                                || report.status() == MeetingReportStatus.DRAFT
                                || report.status() == MeetingReportStatus.CONFIRMED
                        : report.status() == statusFilter)
                .sorted(java.util.Comparator.comparingInt(MeetingReport::version).reversed())
                .toList();
    }

    public MeetingReport meetingReportDetail(String actorUserId, String meetingId, String reportId) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
        return store.findMeetingReportById(reportId)
                .filter(report -> report.meetingId().equals(meetingId))
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "회의록을 찾을 수 없습니다."
                ));
    }

    @Transactional
    public MeetingReport updateMeetingReport(String actorUserId, String meetingId, String reportId, ReportPatch patch) {
        requireUser(actorUserId);
        if (!patch.hasUpdates()) {
            throw invalidRequest("수정할 회의록 필드가 필요합니다.");
        }
        store.lockMeeting(meetingId);
        meetingAccessPolicy.requireEditAccess(meetingAccessContext(meetingId, actorUserId));
        MeetingReport source = store.findMeetingReportById(reportId)
                .filter(report -> report.meetingId().equals(meetingId))
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "회의록을 찾을 수 없습니다."
                ));
        if (source.status() == MeetingReportStatus.CANDIDATE && candidateExpired(source.createdAt(), REPORT_CANDIDATE_TTL)) {
            throw candidateExpiredError("AI 회의록 후보가 만료되었습니다. 새 후보를 생성해 주세요.");
        }
        if (patch.titlePresent() && (patch.title() == null || patch.title().isBlank())) {
            throw invalidRequest("회의록 제목은 blank일 수 없습니다.");
        }
        if (patch.summaryPresent() && (patch.summary() == null || patch.summary().isBlank())) {
            throw invalidRequest("회의록 요약은 blank일 수 없습니다.");
        }
        int nextVersion = store.findMeetingReports(meetingId).stream().mapToInt(MeetingReport::version).max().orElse(0) + 1;
        MeetingReport draft = new MeetingReport(
                "report-" + UUID.randomUUID(),
                meetingId,
                MeetingReportStatus.DRAFT,
                patch.titlePresent() ? patch.title().trim() : source.title(),
                patch.summaryPresent() ? patch.summary().trim() : source.summary(),
                patch.markdownPresent() ? blankToNull(patch.markdown()) : source.markdown(),
                source.decisions().stream().map(decision -> new MeetingReport.ReportDecision(
                        "report-decision-" + UUID.randomUUID(), decision.title(), decision.content(), decision.sourceIds()
                )).toList(),
                source.actionItems().stream().map(action -> new MeetingReport.ReportActionItem(
                        "report-action-" + UUID.randomUUID(), action.title(), action.assigneeName(), action.dueDate(), action.sourceIds()
                )).toList(),
                source.sourceIds(),
                actorUserId,
                nextVersion,
                false,
                Instant.now(clock),
                null
        );
        MeetingReport saved = store.saveMeetingReport(draft);
        addAudit("REPORT_UPDATED", actorUserId, null, saved.id(), source.id() + "/v" + source.version(), "v" + saved.version());
        return saved;
    }

    @Transactional
    public MeetingReport restoreMeetingReport(String actorUserId, String meetingId, String reportId) {
        MeetingReport source = meetingReportDetail(actorUserId, meetingId, reportId);
        MeetingReport restored = updateMeetingReport(
                actorUserId,
                meetingId,
                reportId,
                new ReportPatch(source.title(), true, source.summary(), true, source.markdown(), true)
        );
        addAudit("REPORT_RESTORED", actorUserId, null, restored.id(), source.id() + "/v" + source.version(), "v" + restored.version());
        return restored;
    }

    public MeetingReport downloadMeetingReport(String actorUserId, String meetingId, String reportId) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
        return store.findMeetingReportById(reportId)
                .filter(report -> report.meetingId().equals(meetingId))
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "회의록을 찾을 수 없습니다."
                ));
    }

    @Transactional
    public TaskCandidate saveTaskCandidate(
            String meetingId,
            String createdBy,
            String title,
            String assigneeName,
            String suggestedAssigneeId,
            LocalDate dueDate,
            List<String> sourceIds
    ) {
        requireUser(createdBy);
        Meeting meeting = store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        validateRequired(title, "태스크 제목은 필수입니다.");
        if (suggestedAssigneeId != null && store.findSpaceMember(meeting.spaceId(), suggestedAssigneeId).isEmpty()) {
            suggestedAssigneeId = null;
        }
        return store.saveTaskCandidate(new TaskCandidate(
                "task-candidate-" + UUID.randomUUID(),
                meetingId,
                title.trim(),
                blankToNull(assigneeName),
                suggestedAssigneeId,
                dueDate,
                TaskCandidateStatus.CANDIDATE,
                sourceIds,
                createdBy,
                Instant.now(clock),
                null
        ));
    }

    public List<TaskCandidate> taskCandidates(String meetingId) {
        store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        return store.findTaskCandidates(meetingId);
    }

    @Transactional
    public synchronized TaskConfirmationResult confirmTaskCandidate(
            String meetingId,
            String candidateId,
            String title,
            String description,
            String assigneeId,
            LocalDate dueDate,
            TaskCardStatus status
    ) {
        validateRequired(title, "태스크 제목은 필수입니다.");
        Meeting meeting = store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        store.lockSpace(meeting.spaceId());
        TaskCandidate candidate = store.findTaskCandidateByIdForUpdate(candidateId)
                .filter(found -> found.meetingId().equals(meetingId))
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "TASK_CANDIDATE_NOT_FOUND",
                        "태스크 후보를 찾을 수 없습니다."
                ));
        if (candidate.status() != TaskCandidateStatus.CANDIDATE
                || store.findTaskCardBySourceCandidateId(candidateId).isPresent()) {
            throw invalidRequest("확정 가능한 태스크 후보가 아닙니다.");
        }
        if (candidateExpired(candidate.createdAt(), TASK_CANDIDATE_TTL)) {
            throw candidateExpiredError("AI 태스크 후보가 만료되었습니다. 새 후보를 생성해 주세요.");
        }
        if (assigneeId != null && store.findSpaceMember(meeting.spaceId(), assigneeId).isEmpty()) {
            throw invalidRequest("담당자는 active SpaceMember여야 합니다.");
        }
        Instant now = Instant.now(clock);
        TaskCard taskCard = store.saveTaskCard(new TaskCard(
                "task-" + UUID.randomUUID(),
                meeting.spaceId(),
                meetingId,
                candidateId,
                title.trim(),
                blankToNull(description),
                status == null ? TaskCardStatus.TODO : status,
                assigneeId,
                dueDate,
                now,
                now
        ));
        TaskCandidate confirmed = store.saveTaskCandidate(candidate.confirmed(now));
        return new TaskConfirmationResult(confirmed, taskCard);
    }

    @Transactional
    public synchronized TaskCandidate dismissTaskCandidate(String actorUserId, String meetingId, String candidateId) {
        Meeting meeting = store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        store.lockSpace(meeting.spaceId());
        TaskCandidate candidate = store.findTaskCandidateByIdForUpdate(candidateId)
                .filter(found -> found.meetingId().equals(meetingId))
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "TASK_CANDIDATE_NOT_FOUND",
                        "태스크 후보를 찾을 수 없습니다."
                ));
        if (candidate.status() != TaskCandidateStatus.CANDIDATE) {
            throw invalidRequest("제외 가능한 태스크 후보가 아닙니다.");
        }
        TaskCandidate dismissed = store.saveTaskCandidate(candidate.dismissed());
        addAudit("TASK_CANDIDATE_DISMISSED", actorUserId, null, candidateId, "CANDIDATE", "DISMISSED");
        return dismissed;
    }

    public ProjectAiContext projectAiContext(String spaceId) {
        Space space = store.findSpaceById(spaceId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "SPACE_NOT_FOUND",
                        "Space를 찾을 수 없습니다."
                ));
        return new ProjectAiContext(space, store.findProjectKnowledge(spaceId), store.findMeetingsBySpaceId(spaceId));
    }

    public ProjectMeetingContext projectMeetingContext(String meetingId) {
        Meeting meeting = store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
        return new ProjectMeetingContext(meeting, store.findMeetingReports(meetingId));
    }

    private SpaceMember requireSpaceMemberById(String spaceId, String memberId) {
        return store.findSpaceMemberById(spaceId, memberId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "SPACE_NOT_FOUND",
                        "SpaceMember를 찾을 수 없습니다."
                ));
    }

    private Meeting requireMeeting(String meetingId) {
        return store.findMeetingById(meetingId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "회의를 찾을 수 없습니다."
                ));
    }

    private String defaultRoomCode(String spaceId) {
        return "space-room-" + spaceId;
    }

    private MeetingTranscript requireProcessingTranscript(String meetingId) {
        MeetingTranscript transcript = store.findMeetingTranscript(meetingId)
                .orElseThrow(() -> new AuthorizationException(HttpStatus.NOT_FOUND, "TRANSCRIPT_NOT_FOUND", "전사를 찾을 수 없습니다."));
        if (transcript.status() != TranscriptStatus.PROCESSING) {
            throw new AuthorizationException(HttpStatus.CONFLICT, "TRANSCRIPTION_NOT_PROCESSING", "진행 중인 전사가 아닙니다.");
        }
        return transcript;
    }

    private Instant retentionUntil(Meeting meeting, Instant now) {
        return switch (meeting.retentionPolicy()) {
            case "DAYS_7" -> now.plus(java.time.Duration.ofDays(7));
            case "PERMANENT" -> null;
            default -> now.plus(java.time.Duration.ofDays(30));
        };
    }

    private MeetingParticipant requireMeetingParticipantById(String meetingId, String participantId) {
        requireMeeting(meetingId);
        return store.findMeetingParticipantById(meetingId, participantId)
                .orElseThrow(() -> new AuthorizationException(
                        HttpStatus.NOT_FOUND,
                        "MEETING_NOT_FOUND",
                        "MeetingParticipant를 찾을 수 없습니다."
                ));
    }

    private MeetingAccessPolicy.MeetingParticipant toPolicyParticipant(MeetingParticipant participant) {
        return new MeetingAccessPolicy.MeetingParticipant(
                participant.id(),
                participant.meetingId(),
                participant.userId(),
                participant.role(),
                participant.participantType(),
                participant.accessStatus()
        );
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank() || store.findUserById(userId).isEmpty()) {
            throw new AuthorizationException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "사용자를 찾을 수 없습니다.");
        }
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalidRequest(message);
        }
    }

    private TaskCardStatus parseTaskStatus(String value) {
        try {
            return TaskCardStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidRequest("TaskCard status 값이 올바르지 않습니다.");
        }
    }

    private TaskCardPriority parseTaskPriority(String value) {
        try {
            return TaskCardPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidRequest("TaskCard priority 값이 올바르지 않습니다.");
        }
    }

    private List<String> normalizeTaskLabels(List<String> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 10) {
            throw invalidRequest("태스크 라벨은 최대 10개까지 지정할 수 있습니다.");
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (String value : values) {
            String label = blankToNull(value);
            if (label == null || label.length() > 40) {
                throw invalidRequest("태스크 라벨은 1자 이상 40자 이하여야 합니다.");
            }
            if (!labels.add(label.toLowerCase(Locale.ROOT))) {
                throw invalidRequest("중복된 태스크 라벨은 지정할 수 없습니다.");
            }
        }
        return values.stream().map(this::blankToNull).toList();
    }

    private MeetingReportStatus parseReportStatus(String value) {
        try {
            return MeetingReportStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidRequest("MeetingReport status 값이 올바르지 않습니다.");
        }
    }

    private String normalizeEmail(String value) {
        if (value == null || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw invalidRequest("유효한 이메일이 필요합니다.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String invitationToken() {
        byte[] value = new byte[32];
        INVITATION_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256(String value) {
        if (value == null || value.isBlank()) {
            throw invalidRequest("초대 token은 필수입니다.");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("초대 token hash를 생성할 수 없습니다.", exception);
        }
    }

    private AuthorizationException invalidRequest(String message) {
        return new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private boolean candidateExpired(Instant createdAt, Duration ttl) {
        return !createdAt.plus(ttl).isAfter(Instant.now(clock));
    }

    private AuthorizationException candidateExpiredError(String message) {
        return new AuthorizationException(HttpStatus.CONFLICT, "CANDIDATE_EXPIRED", message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void addAudit(
            String type,
            String actorUserId,
            String targetUserId,
            String resourceId,
            String beforeValue,
            String afterValue
    ) {
        store.addAuditEvent(type, actorUserId, targetUserId, resourceId, beforeValue, afterValue, Instant.now(clock));
    }

    public record SpaceCreationResult(Space space, SpaceMember owner) {
    }

    public record SpaceInvitationCreation(SpaceInvitation invitation, String token) {
    }

    public record SpaceInvitationResolution(SpaceInvitation invitation, SpaceMember member) {
    }

    public record PendingSpaceInvitation(String invitationId, String spaceId, String spaceName, SpaceRole role, Instant expiresAt) {
    }

    public record MeetingInvitationCreation(MeetingInvitation invitation, String token) {
    }

    public record MeetingInvitationResolution(MeetingInvitation invitation, MeetingParticipant participant) {
    }

    public record MeetingCreationResult(
            Meeting meeting,
            MeetingParticipant host,
            List<MeetingParticipant> participants
    ) {
    }

    public record MeetingView(
            Meeting meeting,
            MeetingRole myRole,
            List<MeetingParticipantWithUser> participants
    ) {
        public MeetingView {
            participants = participants == null ? List.of() : List.copyOf(participants);
        }
    }

    public record SpaceSummary(Space space, SpaceRole role, long meetingCount) {
    }

    public record SpaceDetail(
            Space space,
            SpaceRole role,
            List<MeetingSummary> upcomingMeetings,
            List<MeetingReport> recentReports,
            List<TaskCardView> actionItems
    ) {
    }

    public record MeetingSummary(Meeting meeting, MeetingRole myRole) {
    }

    public record CalendarEvent(Meeting meeting) {
    }

    public record DashboardSummary(
            List<Meeting> todayMeetings,
            List<DashboardActivity> recentActivities,
            List<SpaceSummary> spaces,
            List<TaskCardView> actionItems,
            List<DashboardReport> latestReports
    ) {
    }

    public record DashboardActivity(String id, String spaceId, String title, Instant occurredAt, String type) {
    }

    public record DashboardReport(Meeting meeting, MeetingReport report) {
        public Instant occurredAt() {
            return report.confirmedAt() == null ? report.createdAt() : report.confirmedAt();
        }
    }

    public record MeetingAiContext(
            Meeting meeting,
            List<TranscriptSegment> transcriptSegments,
            List<MeetingReport> reports
    ) {
        public MeetingAiContext {
            transcriptSegments = transcriptSegments == null ? List.of() : List.copyOf(transcriptSegments);
            reports = reports == null ? List.of() : List.copyOf(reports);
        }
    }

    public record MeetingTranscriptView(MeetingTranscript transcript, List<TranscriptSegment> segments) {
        public MeetingTranscriptView {
            segments = segments == null ? List.of() : List.copyOf(segments);
        }
    }

    public record TaskCandidateContext(
            Meeting meeting,
            List<TranscriptSegment> transcriptSegments,
            List<MeetingReport> reports,
            List<TaskParticipant> participants,
            List<TaskAssignee> assignees
    ) {
        public TaskCandidateContext {
            transcriptSegments = transcriptSegments == null ? List.of() : List.copyOf(transcriptSegments);
            reports = reports == null ? List.of() : List.copyOf(reports);
            participants = participants == null ? List.of() : List.copyOf(participants);
            assignees = assignees == null ? List.of() : List.copyOf(assignees);
        }
    }

    public record TaskParticipant(
            String userId,
            String displayName,
            MeetingRole role,
            com.meetingmind.demo.authz.ParticipantAccessStatus accessStatus,
            boolean spaceMember
    ) {
    }

    public record TaskAssignee(String userId, String displayName) {
    }

    public record TaskConfirmationResult(TaskCandidate candidate, TaskCard taskCard) {
    }

    public record ProjectKnowledgeView(ProjectKnowledge knowledge, boolean sourceMeetingAccessible) {
    }

    public record ProjectKnowledgePatch(
            String title,
            boolean titlePresent,
            String content,
            boolean contentPresent
    ) {
        public boolean hasUpdates() {
            return titlePresent || contentPresent;
        }
    }

    public record TaskCardPatch(
            String title,
            boolean titlePresent,
            String description,
            boolean descriptionPresent,
            String assigneeId,
            boolean assigneePresent,
            LocalDate dueDate,
            boolean dueDatePresent,
            String status,
            boolean statusPresent,
            String priority,
            boolean priorityPresent,
            List<String> labels,
            boolean labelsPresent
    ) {
        public TaskCardPatch(
                String title,
                boolean titlePresent,
                String description,
                boolean descriptionPresent,
                String assigneeId,
                boolean assigneePresent,
                LocalDate dueDate,
                boolean dueDatePresent,
                String status,
                boolean statusPresent
        ) {
            this(title, titlePresent, description, descriptionPresent, assigneeId, assigneePresent,
                    dueDate, dueDatePresent, status, statusPresent, null, false, null, false);
        }

        public boolean hasUpdates() {
            return titlePresent || descriptionPresent || assigneePresent || dueDatePresent || statusPresent || priorityPresent || labelsPresent;
        }
    }

    public record ReportPatch(
            String title,
            boolean titlePresent,
            String summary,
            boolean summaryPresent,
            String markdown,
            boolean markdownPresent
    ) {
        public boolean hasUpdates() {
            return titlePresent || summaryPresent || markdownPresent;
        }
    }

    public record ProjectAiContext(
            Space space,
            List<ProjectKnowledge> projectKnowledge,
            List<Meeting> meetings
    ) {
        public ProjectAiContext {
            projectKnowledge = projectKnowledge == null ? List.of() : List.copyOf(projectKnowledge);
            meetings = meetings == null ? List.of() : List.copyOf(meetings);
        }
    }

    public record ProjectMeetingContext(Meeting meeting, List<MeetingReport> reports) {
        public ProjectMeetingContext {
            reports = reports == null ? List.of() : List.copyOf(reports);
        }
    }

    public record SpaceMemberWithUser(SpaceMember member, User user) {
    }

    public record TaskCardView(TaskCard task, boolean meetingSourceVisible) {
    }

    public record MeetingParticipantWithUser(MeetingParticipant participant, User user) {
    }

    public record OwnerTransferResult(SpaceMember newOwner, SpaceMember previousOwner) {
    }

    public record ProjectAiContextCandidates(List<ProjectKnowledge> projectKnowledge, List<Meeting> meetings) {
    }
}
