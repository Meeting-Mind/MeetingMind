package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class WorkspaceDomainServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-10T10:00:00+09:00");

    @Test
    void createSpaceCreatesOwnerMembership() {
        TestContext context = newContext();
        User owner = context.user("user-owner");

        WorkspaceDomainService.SpaceCreationResult result = context.service.createSpace(
                owner.id(),
                " MeetingMind ",
                " AI 회의 지식화 프로젝트 "
        );

        assertThat(result.space().name()).isEqualTo("MeetingMind");
        assertThat(result.space().description()).isEqualTo("AI 회의 지식화 프로젝트");
        assertThat(result.space().createdBy()).isEqualTo(owner.id());
        assertThat(result.owner().role()).isEqualTo(SpaceRole.OWNER);
        assertThat(result.owner().userId()).isEqualTo(owner.id());
        assertThat(result.owner().spaceId()).isEqualTo(result.space().id());
        assertThat(result.owner().joinedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void ownerCreatesMeetingAndBecomesActiveHost() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);

        WorkspaceDomainService.MeetingCreationResult result = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                " Sprint Planning #12 ",
                SCHEDULED_AT,
                List.of()
        );

        assertThat(result.meeting().spaceId()).isEqualTo(space.space().id());
        assertThat(result.meeting().title()).isEqualTo("Sprint Planning #12");
        assertThat(result.meeting().scheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(result.meeting().status()).isEqualTo(MeetingStatus.SCHEDULED);
        assertThat(result.meeting().joinCode()).matches("[0-9a-f]{32}");
        assertThat(result.meeting().joinCode()).doesNotContain(result.meeting().id());
        assertThat(result.host().userId()).isEqualTo(owner.id());
        assertThat(result.host().role()).isEqualTo(MeetingRole.HOST);
        assertThat(result.host().participantType()).isEqualTo(ParticipantType.MEMBER);
        assertThat(result.host().accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
    }

    @Test
    void adminCanCreateMeetingAndSpaceMemberParticipantsBecomeViewers() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User admin = context.user("user-admin");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), admin.id(), SpaceRole.ADMIN, FIXED_CLOCK.instant());
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());

        WorkspaceDomainService.MeetingCreationResult result = context.service.createMeeting(
                admin.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of(member.id())
        );

        assertThat(result.participants()).hasSize(2);
        assertThat(result.participants())
                .filteredOn(participant -> participant.userId().equals(admin.id()))
                .singleElement()
                .extracting(MeetingParticipant::role)
                .isEqualTo(MeetingRole.HOST);
        assertThat(result.participants())
                .filteredOn(participant -> participant.userId().equals(member.id()))
                .singleElement()
                .extracting(MeetingParticipant::role)
                .isEqualTo(MeetingRole.VIEWER);
    }

    @Test
    void memberCannotCreateMeeting() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());

        assertThatThrownBy(() -> context.service.createMeeting(
                member.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void meetingParticipantCanBeMeetingOnlyWithoutProjectAccess() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User outsider = context.user("user-outsider");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);

        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of(outsider.id())
        );

        assertThat(context.store.findSpaceMember(space.space().id(), outsider.id())).isEmpty();
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), outsider.id()))
                .get()
                .satisfies(participant -> {
                    assertThat(participant.role()).isEqualTo(MeetingRole.VIEWER);
                    assertThat(participant.participantType()).isEqualTo(ParticipantType.GUEST);
                    assertThat(participant.accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
                });
        MeetingAccessPolicy.MeetingAccessContext accessContext = context.service.meetingAccessContext(
                meeting.meeting().id(),
                outsider.id()
        );
        assertThat(accessContext.participant().accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
        assertThat(accessContext.spaceContext().membership()).isNull();
        assertThatThrownBy(() -> context.service.projectAiContextCandidates(outsider.id(), space.space().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void meetingAccessContextUsesCreatedDomainData() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of(member.id())
        );

        MeetingAccessPolicy.MeetingAccessContext accessContext = context.service.meetingAccessContext(
                meeting.meeting().id(),
                member.id()
        );

        assertThat(accessContext.meetingExists()).isTrue();
        assertThat(accessContext.meetingStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        assertThat(accessContext.spaceContext().membership().role()).isEqualTo(SpaceRole.MEMBER);
        assertThat(accessContext.participant().role()).isEqualTo(MeetingRole.VIEWER);
        assertThat(accessContext.participants()).hasSize(2);
    }

    @Test
    void ownerChangesSpaceMemberRoleAndAuditEventIsRecorded() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        SpaceMember target = context.store.addSpaceMember(
                space.space().id(),
                member.id(),
                SpaceRole.MEMBER,
                FIXED_CLOCK.instant()
        );

        SpaceMember updated = context.service.updateSpaceMemberRole(
                owner.id(),
                space.space().id(),
                target.id(),
                "ADMIN"
        );

        assertThat(updated.role()).isEqualTo(SpaceRole.ADMIN);
        assertThat(context.store.findAuditEvents())
                .filteredOn(event -> event.type().equals("SPACE_MEMBER_ROLE_CHANGED"))
                .singleElement()
                .extracting(AuditEvent::targetUserId)
                .isEqualTo(member.id());
    }

    @Test
    void adminCannotChangeSpaceMemberRole() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User admin = context.user("user-admin");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), admin.id(), SpaceRole.ADMIN, FIXED_CLOCK.instant());
        SpaceMember target = context.store.addSpaceMember(
                space.space().id(),
                member.id(),
                SpaceRole.MEMBER,
                FIXED_CLOCK.instant()
        );

        assertThatThrownBy(() -> context.service.updateSpaceMemberRole(
                admin.id(),
                space.space().id(),
                target.id(),
                "ADMIN"
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void removingSpaceMemberKeepsMeetingAccessAsGuestAndBlocksProjectAiContext() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        SpaceMember target = context.store.addSpaceMember(
                space.space().id(),
                member.id(),
                SpaceRole.MEMBER,
                FIXED_CLOCK.instant()
        );
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of(member.id())
        );

        boolean removed = context.service.removeSpaceMember(owner.id(), space.space().id(), target.id());

        assertThat(removed).isTrue();
        assertThat(context.store.findSpaceMember(space.space().id(), member.id())).isEmpty();
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), member.id()))
                .get()
                .satisfies(participant -> {
                    assertThat(participant.participantType()).isEqualTo(ParticipantType.GUEST);
                    assertThat(participant.accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
                });
        assertThat(context.service.meetingAccessContext(meeting.meeting().id(), member.id()).participant())
                .isNotNull();
        assertThatThrownBy(() -> context.service.projectAiContextCandidates(member.id(), space.space().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void removingSpaceMemberKeepsLastActiveHostAsMeetingOnlyGuest() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User admin = context.user("user-admin");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        SpaceMember target = context.store.addSpaceMember(
                space.space().id(),
                admin.id(),
                SpaceRole.ADMIN,
                FIXED_CLOCK.instant()
        );
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                admin.id(),
                space.space().id(),
                "HOST 단독 회의",
                SCHEDULED_AT,
                List.of()
        );

        boolean removed = context.service.removeSpaceMember(owner.id(), space.space().id(), target.id());

        assertThat(removed).isTrue();
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), admin.id()))
                .get()
                .satisfies(participant -> {
                    assertThat(participant.role()).isEqualTo(MeetingRole.HOST);
                    assertThat(participant.participantType()).isEqualTo(ParticipantType.GUEST);
                    assertThat(participant.accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
                });
    }

    @Test
    void hostCanAddParticipantButViewerCannot() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User viewer = context.user("user-viewer");
        User next = context.user("user-next");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), viewer.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "ACL 회의",
                SCHEDULED_AT,
                List.of(viewer.id())
        );

        assertThatThrownBy(() -> context.service.addMeetingParticipant(
                viewer.id(),
                meeting.meeting().id(),
                next.id(),
                "VIEWER",
                null
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));

        MeetingParticipant added = context.service.addMeetingParticipant(
                owner.id(),
                meeting.meeting().id(),
                next.id(),
                "EDITOR",
                null
        );

        assertThat(added.role()).isEqualTo(MeetingRole.EDITOR);
        assertThat(added.participantType()).isEqualTo(ParticipantType.GUEST);
        assertThat(added.accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
    }

    @Test
    void joinRequestUsesMeetingUrlAndHostApprovalCreatesGuestParticipant() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "초대 회의",
                SCHEDULED_AT,
                List.of()
        );

        MeetingJoinRequest request = context.service.createMeetingJoinRequest(
                guest.id(),
                "/meetings/" + meeting.meeting().id() + "?joinCode=" + meeting.meeting().joinCode()
        );

        assertThat(request.status()).isEqualTo(MeetingJoinRequestStatus.PENDING);
        assertThat(request.meetingId()).isEqualTo(meeting.meeting().id());
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), guest.id())).isEmpty();

        MeetingJoinRequest approved = context.service.approveMeetingJoinRequest(
                owner.id(),
                meeting.meeting().id(),
                request.id()
        );

        assertThat(approved.status()).isEqualTo(MeetingJoinRequestStatus.APPROVED);
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), guest.id()))
                .get()
                .satisfies(participant -> {
                    assertThat(participant.role()).isEqualTo(MeetingRole.VIEWER);
                    assertThat(participant.participantType()).isEqualTo(ParticipantType.GUEST);
                    assertThat(participant.accessStatus()).isEqualTo(ParticipantAccessStatus.ACTIVE);
                });
    }

    @Test
    void rawJoinCodeCreatesMemberRequestAndApprovalKeepsMemberType() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(), space.space().id(), "멤버 참가 회의", SCHEDULED_AT, List.of()
        );

        MeetingJoinRequest request = context.service.createMeetingJoinRequest(member.id(), meeting.meeting().joinCode());
        context.service.approveMeetingJoinRequest(owner.id(), meeting.meeting().id(), request.id());

        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), member.id()))
                .get()
                .extracting(MeetingParticipant::participantType)
                .isEqualTo(ParticipantType.MEMBER);
    }

    @Test
    void invalidJoinCodeAndUrlWithoutJoinCodeAreDeniedWithoutMeetingLookupDetails() {
        TestContext context = newContext();
        User guest = context.user("user-guest");

        assertThatThrownBy(() -> context.service.createMeetingJoinRequest(guest.id(), "invalid-code"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
        assertThatThrownBy(() -> context.service.createMeetingJoinRequest(
                guest.id(),
                "https://meetingmind.local/meetings/unknown"
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void duplicatePendingRequestAndExistingActiveParticipantAreDenied() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(), space.space().id(), "중복 신청 회의", SCHEDULED_AT, List.of()
        );
        context.service.createMeetingJoinRequest(guest.id(), meeting.meeting().joinCode());

        assertThatThrownBy(() -> context.service.createMeetingJoinRequest(guest.id(), meeting.meeting().joinCode()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        assertThatThrownBy(() -> context.service.createMeetingJoinRequest(owner.id(), meeting.meeting().joinCode()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void viewerCannotReviewJoinRequestAndApprovedRequestCannotBeProcessedAgain() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User viewer = context.user("user-viewer");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), viewer.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(), space.space().id(), "승인 권한 회의", SCHEDULED_AT, List.of(viewer.id())
        );
        MeetingJoinRequest request = context.service.createMeetingJoinRequest(guest.id(), meeting.meeting().joinCode());

        assertThatThrownBy(() -> context.service.approveMeetingJoinRequest(
                viewer.id(), meeting.meeting().id(), request.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));

        context.service.approveMeetingJoinRequest(owner.id(), meeting.meeting().id(), request.id());
        assertThatThrownBy(() -> context.service.rejectMeetingJoinRequest(
                owner.id(), meeting.meeting().id(), request.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void activeHostWithoutSpaceOverrideCanApproveJoinRequest() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User host = context.user("user-host");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(), space.space().id(), "HOST 승인 회의", SCHEDULED_AT, List.of()
        );
        context.service.addMeetingParticipant(
                owner.id(), meeting.meeting().id(), host.id(), "HOST", "guest"
        );
        MeetingJoinRequest request = context.service.createMeetingJoinRequest(guest.id(), meeting.meeting().joinCode());

        MeetingJoinRequest approved = context.service.approveMeetingJoinRequest(
                host.id(), meeting.meeting().id(), request.id()
        );

        assertThat(approved.status()).isEqualTo(MeetingJoinRequestStatus.APPROVED);
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), guest.id())).isPresent();
    }

    @Test
    void spaceAdminCanApproveJoinRequestWithoutParticipantAcl() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User admin = context.user("user-admin");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), admin.id(), SpaceRole.ADMIN, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(), space.space().id(), "ADMIN 승인 회의", SCHEDULED_AT, List.of()
        );
        MeetingJoinRequest request = context.service.createMeetingJoinRequest(guest.id(), meeting.meeting().joinCode());

        MeetingJoinRequest approved = context.service.approveMeetingJoinRequest(
                admin.id(), meeting.meeting().id(), request.id()
        );

        assertThat(approved.status()).isEqualTo(MeetingJoinRequestStatus.APPROVED);
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), admin.id())).isEmpty();
        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), guest.id())).isPresent();
    }

    @Test
    void rejectedRequestCannotBeReviewedAgain() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(), space.space().id(), "거절 회의", SCHEDULED_AT, List.of()
        );
        MeetingJoinRequest request = context.service.createMeetingJoinRequest(guest.id(), meeting.meeting().joinCode());

        context.service.rejectMeetingJoinRequest(owner.id(), meeting.meeting().id(), request.id());

        assertThat(context.store.findMeetingParticipant(meeting.meeting().id(), guest.id())).isEmpty();
        assertThatThrownBy(() -> context.service.approveMeetingJoinRequest(
                owner.id(), meeting.meeting().id(), request.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void revokingLastActiveHostIsDenied() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "마지막 HOST 회의",
                SCHEDULED_AT,
                List.of()
        );

        assertThatThrownBy(() -> context.service.updateMeetingParticipant(
                owner.id(),
                meeting.meeting().id(),
                meeting.host().id(),
                "VIEWER",
                "REVOKED"
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "LAST_ACTIVE_HOST_REQUIRED"));
    }

    @Test
    void ownerTransferRequiresConfirmationAndDemotesPreviousOwner() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        SpaceMember target = context.store.addSpaceMember(
                space.space().id(),
                member.id(),
                SpaceRole.MEMBER,
                FIXED_CLOCK.instant()
        );

        assertThatThrownBy(() -> context.service.transferOwner(
                owner.id(),
                space.space().id(),
                target.id(),
                "",
                "MEMBER"
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));

        WorkspaceDomainService.OwnerTransferResult result = context.service.transferOwner(
                owner.id(),
                space.space().id(),
                target.id(),
                "TRANSFER OWNER",
                "MEMBER"
        );

        assertThat(result.newOwner().userId()).isEqualTo(member.id());
        assertThat(result.newOwner().role()).isEqualTo(SpaceRole.OWNER);
        assertThat(result.previousOwner().userId()).isEqualTo(owner.id());
        assertThat(result.previousOwner().role()).isEqualTo(SpaceRole.MEMBER);
    }

    @Test
    void projectAiContextCandidatesOnlyIncludeAccessibleMeetings() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult accessible = context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "공개된 회의",
                SCHEDULED_AT,
                List.of(member.id())
        );
        context.service.createMeeting(owner.id(), space.space().id(), "비공개 회의", SCHEDULED_AT, List.of());
        context.store.saveProjectKnowledge(new ProjectKnowledge(
                "knowledge-001",
                space.space().id(),
                KnowledgeType.MANUAL,
                "권한 설계 메모",
                "Project AI는 접근 가능한 회의만 사용한다.",
                null,
                owner.id(),
                KnowledgeStatus.PUBLISHED,
                EmbeddingStatus.PENDING,
                null,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant(),
                null
        ));

        WorkspaceDomainService.ProjectAiContextCandidates candidates = context.service.projectAiContextCandidates(
                member.id(),
                space.space().id()
        );

        assertThat(candidates.projectKnowledge()).hasSize(1);
        assertThat(candidates.meetings())
                .extracting(Meeting::id)
                .containsExactly(accessible.meeting().id());
    }

    private TestContext newContext() {
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        WorkspaceDomainService service = new WorkspaceDomainService(
                store,
                new SpaceAccessPolicy(),
                FIXED_CLOCK
        );
        return new TestContext(store, service);
    }

    private void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(InMemoryWorkspaceStore store, WorkspaceDomainService service) {
        User user(String id) {
            User user = new User(
                    id,
                    id + "@meetingmind.ai",
                    id,
                    null,
                    "active",
                    FIXED_CLOCK.instant(),
                    FIXED_CLOCK.instant()
            );
            return store.saveUser(user);
        }
    }
}
