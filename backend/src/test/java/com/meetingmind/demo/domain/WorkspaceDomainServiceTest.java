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
    void meetingParticipantMustBeSpaceMember() {
        TestContext context = newContext();
        User owner = context.user("user-owner");
        User outsider = context.user("user-outsider");
        WorkspaceDomainService.SpaceCreationResult space = context.service.createSpace(owner.id(), "MeetingMind", null);

        assertThatThrownBy(() -> context.service.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of(outsider.id())
        ))
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
