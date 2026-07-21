package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.MeetingStatus;
import com.meetingmind.demo.domain.Meeting;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardControllerTest {

    @Test
    void returnsScheduledEndAtInsteadOfAssumingOneHourDuration() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspace = mock(WorkspaceDomainService.class);
        AuthUserResponse user = new AuthUserResponse("user-1", "user@meetingmind.test", "User", null, "active");
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-07-20T10:00:00+09:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-07-20T11:30:00+09:00");
        Meeting meeting = new Meeting(
                "meeting-1", "space-1", "대시보드 회의", null, startsAt, endsAt, null,
                null, null, MeetingStatus.SCHEDULED, null, "DAYS_30", null, null
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        MeetingReport report = new MeetingReport(
                "report-1", meeting.id(), MeetingReportStatus.CONFIRMED, "최신 회의록", "요약", "본문",
                List.of(), List.of(), List.of(), "user-1", 3, true, Instant.parse("2026-07-20T02:00:00Z"),
                Instant.parse("2026-07-20T03:00:00Z")
        );
        when(workspace.dashboardSummary("user-1")).thenReturn(new WorkspaceDomainService.DashboardSummary(
                List.of(meeting), List.of(), List.of(), List.of(), List.of(new WorkspaceDomainService.DashboardReport(meeting, report))
        ));
        DashboardController controller = new DashboardController(authService, workspace);

        var response = controller.summary("Bearer token");

        assertThat(response.todayMeetings()).singleElement().satisfies(event -> {
            assertThat(event.startsAt()).isEqualTo(startsAt);
            assertThat(event.endsAt()).isEqualTo(endsAt);
        });
        assertThat(response.latestReports()).singleElement().satisfies(latestReport -> {
            assertThat(latestReport.id()).isEqualTo("report-1");
            assertThat(latestReport.meetingId()).isEqualTo("meeting-1");
            assertThat(latestReport.confirmedAt()).isEqualTo(Instant.parse("2026-07-20T03:00:00Z"));
        });
    }
}
