package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.meetingmind.demo.observability.BackendOperationMetrics;
import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.dto.ConfirmMeetingReportResponse;
import com.meetingmind.demo.service.MeetingReportLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingReportLifecycleServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-13T03:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-14T10:00:00+09:00");

    @Test
    void confirmReplacesCurrentReportAndKeepsVersionHistory() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of()
        );
        MeetingReport first = context.candidate(meeting.meeting().id(), owner.id(), "첫 번째 요약");
        ConfirmMeetingReportResponse firstResponse = context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), first.id()
        );
        MeetingReport second = context.candidate(meeting.meeting().id(), owner.id(), "두 번째 요약");

        ConfirmMeetingReportResponse secondResponse = context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), second.id()
        );

        assertThat(firstResponse.status()).isEqualTo("CONFIRMED");
        assertThat(firstResponse.version()).isEqualTo(1);
        assertThat(firstResponse.isCurrent()).isTrue();
        assertThat(firstResponse.confirmedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(secondResponse.version()).isEqualTo(2);
        assertThat(secondResponse.isCurrent()).isTrue();
        assertThat(context.store.findMeetingReportById(first.id())).get()
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo(MeetingReportStatus.CONFIRMED);
                    assertThat(report.current()).isFalse();
                    assertThat(report.version()).isEqualTo(1);
                });
        assertThat(context.store.findMeetingReportById(second.id())).get()
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo(MeetingReportStatus.CONFIRMED);
                    assertThat(report.current()).isTrue();
                    assertThat(report.version()).isEqualTo(2);
                });
        assertThat(context.store.findMeetingReports(meeting.meeting().id()))
                .filteredOn(report -> report.status() == MeetingReportStatus.CONFIRMED && report.current())
                .singleElement()
                .extracting(MeetingReport::id)
                .isEqualTo(second.id());
    }

    @Test
    void confirmRejectsDuplicateAndReportFromAnotherMeeting() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult firstMeeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "첫 회의", SCHEDULED_AT, List.of()
        );
        WorkspaceDomainService.MeetingCreationResult secondMeeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "둘째 회의", SCHEDULED_AT.plusDays(1), List.of()
        );
        MeetingReport candidate = context.candidate(firstMeeting.meeting().id(), owner.id(), "요약");
        context.service.confirm("Bearer access-token", firstMeeting.meeting().id(), candidate.id());

        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", firstMeeting.meeting().id(), candidate.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", secondMeeting.meeting().id(), candidate.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND"));
    }

    @Test
    void confirmRejectsStaleCandidateAfterNewerVersionExists() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of()
        );
        MeetingReport stale = context.candidate(meeting.meeting().id(), owner.id(), "첫 번째 후보");
        MeetingReport latest = context.candidate(meeting.meeting().id(), owner.id(), "두 번째 후보");

        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), stale.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "REPORT_VERSION_CONFLICT"));
        assertThat(context.store.findMeetingReportById(stale.id())).get()
                .extracting(MeetingReport::status)
                .isEqualTo(MeetingReportStatus.CANDIDATE);
        assertThat(context.store.findMeetingReportById(latest.id())).get()
                .extracting(MeetingReport::version)
                .isEqualTo(2);
    }

    @Test
    void confirmRejectsViewerBeforeChangingCandidate() {
        TestContext context = newContext("user-viewer");
        User owner = context.user("user-owner");
        User viewer = context.user("user-viewer");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), viewer.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of(viewer.id())
        );
        MeetingReport candidate = context.candidate(meeting.meeting().id(), owner.id(), "요약");

        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), candidate.id()
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
        assertThat(context.store.findMeetingReportById(candidate.id())).get()
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo(MeetingReportStatus.CANDIDATE);
                    assertThat(report.current()).isFalse();
                    assertThat(report.confirmedAt()).isNull();
                });
    }

    private TestContext newContext(String authUserId) {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(
                        authUserId, authUserId + "@meetingmind.ai", authUserId, null, "active"
                ));
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        SpaceAccessPolicy spaceAccessPolicy = new SpaceAccessPolicy();
        WorkspaceDomainService workspace = new WorkspaceDomainService(store, spaceAccessPolicy, FIXED_CLOCK);
        MeetingReportLifecycleService service = new MeetingReportLifecycleService(
                authService,
                workspace,
                new MeetingAccessPolicy(spaceAccessPolicy),
                new BackendOperationMetrics(new SimpleMeterRegistry())
        );
        return new TestContext(store, workspace, service);
    }

    private static void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(
            InMemoryWorkspaceStore store,
            WorkspaceDomainService workspace,
            MeetingReportLifecycleService service
    ) {
        User user(String id) {
            return store.saveUser(new User(
                    id, id + "@meetingmind.ai", id, null, "active", FIXED_CLOCK.instant(), FIXED_CLOCK.instant()
            ));
        }

        MeetingReport candidate(String meetingId, String createdBy, String summary) {
            return workspace.saveReportCandidate(
                    meetingId,
                    createdBy,
                    "회의록",
                    summary,
                    "## 요약\n" + summary,
                    List.of(),
                    List.of(),
                    List.of("segment-1")
            );
        }
    }
}
