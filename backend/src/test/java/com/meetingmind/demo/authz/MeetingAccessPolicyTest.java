package com.meetingmind.demo.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingAccessPolicyTest {

    private final MeetingAccessPolicy policy = new MeetingAccessPolicy(new SpaceAccessPolicy());

    @Test
    void activeParticipantsCanReadMeetingContext() {
        policy.requireReadAccess(context(participant(MeetingRole.HOST, ParticipantType.MEMBER), null));
        policy.requireReadAccess(context(participant(MeetingRole.EDITOR, ParticipantType.MEMBER), null));
        policy.requireReadAccess(context(participant(MeetingRole.VIEWER, ParticipantType.MEMBER), null));
    }

    @Test
    void revokedOrMissingParticipantCannotReadMeeting() {
        assertThatThrownBy(() -> policy.requireReadAccess(context(
                participant(MeetingRole.VIEWER, ParticipantType.MEMBER, ParticipantAccessStatus.REVOKED),
                spaceMembership(SpaceRole.MEMBER)
        )))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));

        assertThatThrownBy(() -> policy.requireReadAccess(context(null, spaceMembership(SpaceRole.MEMBER))))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void ownerAndAdminCanReadWithoutMeetingParticipant() {
        policy.requireReadAccess(context(null, spaceMembership(SpaceRole.OWNER)));
        policy.requireReadAccess(context(null, spaceMembership(SpaceRole.ADMIN)));
    }

    @Test
    void nonMemberWithoutParticipantCannotReadMeeting() {
        assertThatThrownBy(() -> policy.requireReadAccess(context(null, null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void guestCanReadOnlyWhenParticipantForThisMeeting() {
        policy.requireReadAccess(context(participant(MeetingRole.VIEWER, ParticipantType.GUEST), null));

        assertThatThrownBy(() -> policy.requireReadAccess(context(null, null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void missingMeetingReturnsNotFound() {
        assertThatThrownBy(() -> policy.requireReadAccess(new MeetingAccessPolicy.MeetingAccessContext(
                false,
                MeetingStatus.SCHEDULED,
                spaceContext(null),
                null,
                List.of()
        )))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND"));
    }

    @Test
    void editRequiresEditorHostOrSpaceManager() {
        policy.requireEditAccess(context(participant(MeetingRole.EDITOR, ParticipantType.MEMBER), null));
        policy.requireEditAccess(context(participant(MeetingRole.HOST, ParticipantType.MEMBER), null));
        policy.requireEditAccess(context(null, spaceMembership(SpaceRole.OWNER)));
        policy.requireEditAccess(context(null, spaceMembership(SpaceRole.ADMIN)));

        assertThatThrownBy(() -> policy.requireEditAccess(context(participant(MeetingRole.VIEWER, ParticipantType.MEMBER), null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void deleteRequiresOwnerOrHostAndAdminIsDeniedByDefault() {
        policy.requireDeleteAccess(context(null, spaceMembership(SpaceRole.OWNER)));
        policy.requireDeleteAccess(context(participant(MeetingRole.HOST, ParticipantType.MEMBER), spaceMembership(SpaceRole.MEMBER)));

        assertThatThrownBy(() -> policy.requireDeleteAccess(context(null, spaceMembership(SpaceRole.ADMIN))))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));

        assertThatThrownBy(() -> policy.requireDeleteAccess(context(participant(MeetingRole.EDITOR, ParticipantType.MEMBER), null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void participantManagementRequiresOwnerAdminOrHost() {
        policy.requireParticipantManagement(context(null, spaceMembership(SpaceRole.OWNER)));
        policy.requireParticipantManagement(context(null, spaceMembership(SpaceRole.ADMIN)));
        policy.requireParticipantManagement(context(participant(MeetingRole.HOST, ParticipantType.MEMBER), null));

        assertThatThrownBy(() -> policy.requireParticipantManagement(context(participant(MeetingRole.VIEWER, ParticipantType.MEMBER), null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void invalidRoleAndAccessStatusAreRejected() {
        assertThatThrownBy(() -> MeetingRole.parse("participant"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));

        assertThatThrownBy(() -> ParticipantAccessStatus.parse("LEFT"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void lastActiveHostCannotBeDowngradedRevokedOrRemoved() {
        MeetingAccessPolicy.MeetingParticipant host = participant(MeetingRole.HOST, ParticipantType.MEMBER);
        MeetingAccessPolicy.MeetingAccessContext context = context(host, spaceMembership(SpaceRole.OWNER), List.of(host));

        assertThatThrownBy(() -> policy.requireParticipantMutationAllowed(
                context,
                host,
                MeetingRole.EDITOR,
                ParticipantAccessStatus.ACTIVE,
                false
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "LAST_ACTIVE_HOST_REQUIRED"));

        assertThatThrownBy(() -> policy.requireParticipantMutationAllowed(
                context,
                host,
                MeetingRole.HOST,
                ParticipantAccessStatus.REVOKED,
                false
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "LAST_ACTIVE_HOST_REQUIRED"));

        assertThatThrownBy(() -> policy.requireParticipantMutationAllowed(
                context,
                host,
                null,
                null,
                true
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "LAST_ACTIVE_HOST_REQUIRED"));
    }

    @Test
    void hostCanBeChangedWhenAnotherActiveHostRemains() {
        MeetingAccessPolicy.MeetingParticipant firstHost = participant("participant-1", "user-1", MeetingRole.HOST, ParticipantType.MEMBER);
        MeetingAccessPolicy.MeetingParticipant secondHost = participant("participant-2", "user-2", MeetingRole.HOST, ParticipantType.MEMBER);
        MeetingAccessPolicy.MeetingAccessContext context = context(
                firstHost,
                spaceMembership(SpaceRole.OWNER),
                List.of(firstHost, secondHost)
        );

        policy.requireParticipantMutationAllowed(
                context,
                firstHost,
                MeetingRole.EDITOR,
                ParticipantAccessStatus.ACTIVE,
                false
        );
    }

    @Test
    void liveKitAccessRequiresReadableActiveMeeting() {
        policy.requireLiveKitAccess(context(participant(MeetingRole.VIEWER, ParticipantType.MEMBER), null));

        assertThatThrownBy(() -> policy.requireLiveKitAccess(context(
                MeetingStatus.ENDED,
                participant(MeetingRole.HOST, ParticipantType.MEMBER),
                null
        )))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));

        assertThatThrownBy(() -> policy.requireLiveKitAccess(context(
                MeetingStatus.CANCELED,
                participant(MeetingRole.HOST, ParticipantType.MEMBER),
                null
        )))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    private MeetingAccessPolicy.MeetingAccessContext context(
            MeetingAccessPolicy.MeetingParticipant participant,
            SpaceAccessPolicy.SpaceMembership membership
    ) {
        return context(participant, membership, participant == null ? List.of() : List.of(participant));
    }

    private MeetingAccessPolicy.MeetingAccessContext context(
            MeetingAccessPolicy.MeetingParticipant participant,
            SpaceAccessPolicy.SpaceMembership membership,
            List<MeetingAccessPolicy.MeetingParticipant> participants
    ) {
        return context(MeetingStatus.SCHEDULED, participant, membership, participants);
    }

    private MeetingAccessPolicy.MeetingAccessContext context(
            MeetingStatus status,
            MeetingAccessPolicy.MeetingParticipant participant,
            SpaceAccessPolicy.SpaceMembership membership
    ) {
        return context(status, participant, membership, participant == null ? List.of() : List.of(participant));
    }

    private MeetingAccessPolicy.MeetingAccessContext context(
            MeetingStatus status,
            MeetingAccessPolicy.MeetingParticipant participant,
            SpaceAccessPolicy.SpaceMembership membership,
            List<MeetingAccessPolicy.MeetingParticipant> participants
    ) {
        return new MeetingAccessPolicy.MeetingAccessContext(
                true,
                status,
                spaceContext(membership),
                participant,
                participants
        );
    }

    private SpaceAccessPolicy.SpaceAccessContext spaceContext(SpaceAccessPolicy.SpaceMembership membership) {
        return new SpaceAccessPolicy.SpaceAccessContext(true, membership);
    }

    private SpaceAccessPolicy.SpaceMembership spaceMembership(SpaceRole role) {
        return new SpaceAccessPolicy.SpaceMembership("space-1", "user-actor", role, true);
    }

    private MeetingAccessPolicy.MeetingParticipant participant(MeetingRole role, ParticipantType participantType) {
        return participant(role, participantType, ParticipantAccessStatus.ACTIVE);
    }

    private MeetingAccessPolicy.MeetingParticipant participant(
            MeetingRole role,
            ParticipantType participantType,
            ParticipantAccessStatus accessStatus
    ) {
        return participant("participant-1", "user-actor", role, participantType, accessStatus);
    }

    private MeetingAccessPolicy.MeetingParticipant participant(
            String participantId,
            String userId,
            MeetingRole role,
            ParticipantType participantType
    ) {
        return participant(participantId, userId, role, participantType, ParticipantAccessStatus.ACTIVE);
    }

    private MeetingAccessPolicy.MeetingParticipant participant(
            String participantId,
            String userId,
            MeetingRole role,
            ParticipantType participantType,
            ParticipantAccessStatus accessStatus
    ) {
        return new MeetingAccessPolicy.MeetingParticipant(
                participantId,
                "meeting-1",
                userId,
                role,
                participantType,
                accessStatus
        );
    }

    private void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }
}
