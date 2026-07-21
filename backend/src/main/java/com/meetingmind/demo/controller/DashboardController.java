package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.TaskCard;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;

    public DashboardController(AuthService authService, WorkspaceDomainService workspaceDomainService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
    }

    @GetMapping
    public DashboardSummaryResponse summary(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        WorkspaceDomainService.DashboardSummary summary = workspaceDomainService.dashboardSummary(user.id());
        return new DashboardSummaryResponse(
                summary.todayMeetings().stream().map(meeting -> new DashboardSummaryResponse.CalendarEvent(
                        meeting.id(), meeting.spaceId(), meeting.id(), meeting.title(), meeting.scheduledAt(),
                        meeting.scheduledEndAt(), meeting.status().name()
                )).toList(),
                summary.recentActivities().stream().map(activity -> new DashboardSummaryResponse.RecentActivity(
                        activity.id(), activity.spaceId(), activity.title(), activity.occurredAt(), activity.type()
                )).toList(),
                summary.spaces().stream().map(space -> new DashboardSummaryResponse.Space(
                        space.space().id(), space.space().name(), space.space().description(), space.role().name(),
                        space.meetingCount(), space.space().updatedAt()
                )).toList(),
                summary.actionItems().stream().map(DashboardController::taskResponse).toList(),
                summary.latestReports().stream().map(report -> new DashboardSummaryResponse.LatestReport(
                        report.report().id(), report.meeting().spaceId(), report.meeting().id(), report.meeting().title(),
                        report.report().title(), report.report().summary(), report.report().version(), report.occurredAt()
                )).toList()
        );
    }

    private static DashboardSummaryResponse.Task taskResponse(WorkspaceDomainService.TaskCardView view) {
        TaskCard task = view.task();
        return new DashboardSummaryResponse.Task(
                task.id(), task.spaceId(), view.meetingSourceVisible() ? task.meetingId() : null,
                task.title(), task.description(), task.status().name(), task.assigneeId(), task.dueDate(),
                view.meetingSourceVisible() ? task.sourceCandidateId() : null
        );
    }
}
