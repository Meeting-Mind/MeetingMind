package com.meetingmind.demo.authz;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MeetingAccessPolicy {

    private final SpaceAccessPolicy spaceAccessPolicy;

    public MeetingAccessPolicy(SpaceAccessPolicy spaceAccessPolicy) {
        this.spaceAccessPolicy = spaceAccessPolicy;
    }

    public void requireReadAccess(MeetingAccessContext context) {
        ensureMeetingExists(context);
        if (canReadAccess(context)) {
            return;
        }
        throw meetingAccessDenied();
    }

    public boolean canReadAccess(MeetingAccessContext context) {
        return context != null
                && context.meetingExists()
                && (spaceAccessPolicy.hasManagerOverride(context.spaceContext()) || hasActiveParticipant(context));
    }

    public void requireEditAccess(MeetingAccessContext context) {
        ensureMeetingExists(context);
        if (spaceAccessPolicy.hasManagerOverride(context.spaceContext()) || hasActiveRole(context, MeetingRole.EDITOR)) {
            return;
        }
        throw meetingAccessDenied();
    }

    public void requireDeleteAccess(MeetingAccessContext context) {
        ensureMeetingExists(context);
        if (spaceAccessPolicy.hasOwnerOverride(context.spaceContext()) || hasActiveRole(context, MeetingRole.HOST)) {
            return;
        }
        throw meetingAccessDenied();
    }

    public void requireParticipantManagement(MeetingAccessContext context) {
        ensureMeetingExists(context);
        if (spaceAccessPolicy.hasManagerOverride(context.spaceContext()) || hasActiveRole(context, MeetingRole.HOST)) {
            return;
        }
        throw meetingAccessDenied();
    }

    public void requireLiveKitAccess(MeetingAccessContext context) {
        ensureMeetingExists(context);
        if (context.meetingStatus() == MeetingStatus.ENDED || context.meetingStatus() == MeetingStatus.CANCELED) {
            throw meetingAccessDenied();
        }
        requireReadAccess(context);
    }

    public void requireParticipantMutationAllowed(
            MeetingAccessContext context,
            MeetingParticipant target,
            MeetingRole nextRole,
            ParticipantAccessStatus nextAccessStatus,
            boolean removing
    ) {
        requireParticipantManagement(context);
        if (target == null) {
            throw meetingAccessDenied();
        }

        MeetingRole resolvedRole = nextRole == null ? target.role() : nextRole;
        ParticipantAccessStatus resolvedStatus = nextAccessStatus == null ? target.accessStatus() : nextAccessStatus;
        boolean targetStopsBeingActiveHost = target.isActiveHost()
                && (removing || resolvedRole != MeetingRole.HOST || resolvedStatus != ParticipantAccessStatus.ACTIVE);

        if (targetStopsBeingActiveHost && activeHostCount(context.participants()) <= 1) {
            throw new AuthorizationException(
                    HttpStatus.CONFLICT,
                    "LAST_ACTIVE_HOST_REQUIRED",
                    "마지막 active HOST는 강등, 회수, 제거할 수 없습니다."
            );
        }
    }

    public List<MeetingParticipant> revokeMemberParticipantsForRemovedSpaceMember(
            String removedUserId,
            List<MeetingParticipant> participants
    ) {
        return participants.stream()
                .map(participant -> {
                    if (participant.userId().equals(removedUserId)
                            && participant.participantType() == ParticipantType.MEMBER
                            && participant.accessStatus() == ParticipantAccessStatus.ACTIVE) {
                        return participant.withRoleAndAccessStatus(participant.role(), ParticipantAccessStatus.REVOKED);
                    }
                    return participant;
                })
                .toList();
    }

    private boolean hasActiveParticipant(MeetingAccessContext context) {
        return context.participant() != null && context.participant().accessStatus() == ParticipantAccessStatus.ACTIVE;
    }

    private boolean hasActiveRole(MeetingAccessContext context, MeetingRole requiredRole) {
        return hasActiveParticipant(context) && context.participant().role().includes(requiredRole);
    }

    private long activeHostCount(List<MeetingParticipant> participants) {
        return participants.stream()
                .filter(MeetingParticipant::isActiveHost)
                .count();
    }

    private void ensureMeetingExists(MeetingAccessContext context) {
        if (context == null || !context.meetingExists()) {
            throw new AuthorizationException(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", "회의를 찾을 수 없습니다.");
        }
    }

    private AuthorizationException meetingAccessDenied() {
        return new AuthorizationException(HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED", "회의 접근 권한이 없습니다.");
    }

    public record MeetingAccessContext(
            boolean meetingExists,
            MeetingStatus meetingStatus,
            SpaceAccessPolicy.SpaceAccessContext spaceContext,
            MeetingParticipant participant,
            List<MeetingParticipant> participants
    ) {
        public MeetingAccessContext {
            participants = participants == null ? List.of() : List.copyOf(participants);
        }
    }

    public record MeetingParticipant(
            String id,
            String meetingId,
            String userId,
            MeetingRole role,
            ParticipantType participantType,
            ParticipantAccessStatus accessStatus
    ) {
        boolean isActiveHost() {
            return role == MeetingRole.HOST && accessStatus == ParticipantAccessStatus.ACTIVE;
        }

        MeetingParticipant withRoleAndAccessStatus(MeetingRole nextRole, ParticipantAccessStatus nextAccessStatus) {
            return new MeetingParticipant(id, meetingId, userId, nextRole, participantType, nextAccessStatus);
        }
    }
}
