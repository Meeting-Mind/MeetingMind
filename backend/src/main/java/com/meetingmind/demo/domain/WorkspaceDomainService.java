package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceDomainService {

    private final InMemoryWorkspaceStore store;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final Clock clock;

    @Autowired
    public WorkspaceDomainService(InMemoryWorkspaceStore store, SpaceAccessPolicy spaceAccessPolicy) {
        this(store, spaceAccessPolicy, Clock.systemUTC());
    }

    WorkspaceDomainService(InMemoryWorkspaceStore store, SpaceAccessPolicy spaceAccessPolicy, Clock clock) {
        this.store = store;
        this.spaceAccessPolicy = spaceAccessPolicy;
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
            if (store.findSpaceMember(spaceId, participantUserId).isEmpty()) {
                throw new AuthorizationException(
                        HttpStatus.FORBIDDEN,
                        "SPACE_ACCESS_DENIED",
                        "회의 참여자는 SpaceMember여야 합니다."
                );
            }
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
                store.addMeetingParticipant(meeting.id(), participantUserId, MeetingRole.VIEWER, ParticipantType.MEMBER);
            }
        }

        return new MeetingCreationResult(meeting, host, store.findMeetingParticipants(meeting.id()));
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
}
