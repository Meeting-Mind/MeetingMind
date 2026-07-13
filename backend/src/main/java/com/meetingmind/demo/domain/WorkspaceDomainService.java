package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceDomainService {

    private final InMemoryWorkspaceStore store;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final MeetingAccessPolicy meetingAccessPolicy;
    private final Clock clock;

    @Autowired
    public WorkspaceDomainService(
            InMemoryWorkspaceStore store,
            SpaceAccessPolicy spaceAccessPolicy,
            MeetingAccessPolicy meetingAccessPolicy
    ) {
        this(store, spaceAccessPolicy, meetingAccessPolicy, Clock.systemUTC());
    }

    public WorkspaceDomainService(InMemoryWorkspaceStore store, SpaceAccessPolicy spaceAccessPolicy) {
        this(store, spaceAccessPolicy, new MeetingAccessPolicy(spaceAccessPolicy), Clock.systemUTC());
    }

    WorkspaceDomainService(InMemoryWorkspaceStore store, SpaceAccessPolicy spaceAccessPolicy, Clock clock) {
        this(store, spaceAccessPolicy, new MeetingAccessPolicy(spaceAccessPolicy), clock);
    }

    WorkspaceDomainService(
            InMemoryWorkspaceStore store,
            SpaceAccessPolicy spaceAccessPolicy,
            MeetingAccessPolicy meetingAccessPolicy,
            Clock clock
    ) {
        this.store = store;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.meetingAccessPolicy = meetingAccessPolicy;
        this.clock = clock;
    }

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
                .map(member -> {
                    Space space = store.findSpaceById(member.spaceId())
                            .orElseThrow(() -> new AuthorizationException(
                                    HttpStatus.NOT_FOUND,
                                    "SPACE_NOT_FOUND",
                                    "Space를 찾을 수 없습니다."
                            ));
                    return new SpaceSummary(space, member.role(), store.countMeetingsBySpaceId(space.id()));
                })
                .toList();
    }

    public SpaceCreationResult createSpace(String actorUserId, String name, String description) {
        requireUser(actorUserId);
        validateRequired(name, "Space 이름은 필수입니다.");
        Instant now = Instant.now(clock);
        Space space = store.createSpace(name.trim(), blankToNull(description), actorUserId, now);
        SpaceMember owner = store.addSpaceMember(space.id(), actorUserId, SpaceRole.OWNER, now);
        return new SpaceCreationResult(space, owner);
    }

    public MeetingCreationResult createMeeting(
            String actorUserId,
            String spaceId,
            String title,
            OffsetDateTime scheduledAt,
            List<String> participantUserIds
    ) {
        validateRequired(title, "회의 제목은 필수입니다.");
        if (scheduledAt == null) {
            throw invalidRequest("회의 예정 일시는 필수입니다.");
        }

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

        Meeting meeting = store.createMeeting(spaceId, title.trim(), scheduledAt);
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

    public List<SpaceMemberWithUser> listSpaceMembers(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        return store.findSpaceMembersBySpaceId(spaceId)
                .stream()
                .map(member -> new SpaceMemberWithUser(member, store.findUserById(member.userId()).orElse(null)))
                .toList();
    }

    public SpaceMember updateSpaceMemberRole(String actorUserId, String spaceId, String memberId, String role) {
        requireUser(actorUserId);
        SpaceRole nextRole = SpaceRole.parse(role);
        if (nextRole == SpaceRole.OWNER) {
            throw invalidRequest("OWNER 이양은 owner-transfer API를 사용해야 합니다.");
        }
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

    public boolean removeSpaceMember(String actorUserId, String spaceId, String memberId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireOwnerManagement(spaceAccessContext(spaceId, actorUserId));
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

        InMemoryWorkspaceStore.OwnerTransferUpdate update = store.transferOwner(
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

    public List<MeetingJoinRequest> listMeetingJoinRequests(String actorUserId, String meetingId) {
        requireUser(actorUserId);
        meetingAccessPolicy.requireParticipantManagement(meetingAccessContext(meetingId, actorUserId));
        return store.findMeetingJoinRequests(meetingId);
    }

    public MeetingJoinRequest approveMeetingJoinRequest(
            String actorUserId,
            String meetingId,
            String requestId
    ) {
        requireUser(actorUserId);
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

    public MeetingJoinRequest rejectMeetingJoinRequest(
            String actorUserId,
            String meetingId,
            String requestId
    ) {
        requireUser(actorUserId);
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

    private MeetingJoinRequest requireMeetingJoinRequestById(String meetingId, String requestId) {
        requireMeeting(meetingId);
        return store.findMeetingJoinRequestById(meetingId, requestId)
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

    public MeetingParticipant updateMeetingParticipant(
            String actorUserId,
            String meetingId,
            String participantId,
            String role,
            String accessStatus
    ) {
        requireUser(actorUserId);
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

    public ProjectAiContextCandidates projectAiContextCandidates(String actorUserId, String spaceId) {
        requireUser(actorUserId);
        spaceAccessPolicy.requireSpaceAccess(spaceAccessContext(spaceId, actorUserId));
        List<ProjectKnowledge> knowledge = store.findProjectKnowledge(spaceId)
                .stream()
                .filter(item -> item.status() == KnowledgeStatus.PUBLISHED)
                .filter(item -> item.deletedAt() == null)
                .toList();
        List<Meeting> meetings = store.findMeetingsBySpaceId(spaceId)
                .stream()
                .filter(meeting -> canReadMeeting(actorUserId, meeting.id()))
                .toList();
        return new ProjectAiContextCandidates(knowledge, meetings);
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

    private boolean canReadMeeting(String actorUserId, String meetingId) {
        try {
            meetingAccessPolicy.requireReadAccess(meetingAccessContext(meetingId, actorUserId));
            return true;
        } catch (AuthorizationException exception) {
            return false;
        }
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

    private AuthorizationException invalidRequest(String message) {
        return new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
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

    public record MeetingCreationResult(
            Meeting meeting,
            MeetingParticipant host,
            List<MeetingParticipant> participants
    ) {
    }

    public record SpaceSummary(Space space, SpaceRole role, long meetingCount) {
    }

    public record SpaceMemberWithUser(SpaceMember member, User user) {
    }

    public record MeetingParticipantWithUser(MeetingParticipant participant, User user) {
    }

    public record OwnerTransferResult(SpaceMember newOwner, SpaceMember previousOwner) {
    }

    public record ProjectAiContextCandidates(List<ProjectKnowledge> projectKnowledge, List<Meeting> meetings) {
    }
}
