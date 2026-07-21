package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CalendarServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-21T10:00:00+09:00");

    @Test
    void returnsOnlyMeetingsTheUserCanReadWithinTheRequestedRange() {
        TestContext context = newContext();
        var space = context.workspace.createSpace("owner", "MeetingMind", null);
        OffsetDateTime scheduledEndAt = SCHEDULED_AT.plusMinutes(90);
        context.workspace.createMeeting(
                "owner", space.space().id(), "Sprint planning", "일정 범위를 검증합니다.", SCHEDULED_AT, scheduledEndAt, List.of()
        );
        context.store.addSpaceMember(space.space().id(), "member", SpaceRole.MEMBER, CLOCK.instant());

        assertThat(context.workspace.listCalendarEvents(
                "owner", null, "2026-07-21T00:00:00+09:00", "2026-07-21T23:59:59+09:00"
        )).singleElement().satisfies(event -> {
            assertThat(event.meeting().spaceId()).isEqualTo(space.space().id());
            assertThat(event.meeting().scheduledAt()).isEqualTo(SCHEDULED_AT);
            assertThat(event.meeting().scheduledEndAt()).isEqualTo(scheduledEndAt);
            assertThat(event.meeting().description()).isEqualTo("일정 범위를 검증합니다.");
        });
        assertThat(context.workspace.listCalendarEvents(
                "member", space.space().id(), "2026-07-21T00:00:00+09:00", "2026-07-21T23:59:59+09:00"
        )).isEmpty();
    }

    @Test
    void rejectsMissingOrReversedCalendarRanges() {
        TestContext context = newContext();
        context.workspace.createSpace("owner", "MeetingMind", null);

        assertThatThrownBy(() -> context.workspace.listCalendarEvents("owner", null, null, "2026-07-21T23:59:59+09:00"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertThat(((AuthorizationException) error).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> context.workspace.listCalendarEvents(
                "owner", null, "2026-07-22T00:00:00+09:00", "2026-07-21T23:59:59+09:00"
        )).isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertThat(((AuthorizationException) error).status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private TestContext newContext() {
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        store.saveUser(user("owner", "owner@meetingmind.test"));
        store.saveUser(user("member", "member@meetingmind.test"));
        SpaceAccessPolicy policy = new SpaceAccessPolicy();
        return new TestContext(store, new WorkspaceDomainService(store, policy, CLOCK));
    }

    private static User user(String id, String email) {
        return new User(id, email, id, null, "active", CLOCK.instant(), CLOCK.instant());
    }

    private record TestContext(InMemoryWorkspaceStore store, WorkspaceDomainService workspace) {
    }
}
