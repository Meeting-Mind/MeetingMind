package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ConfirmMeetingReportResponse;
import org.springframework.stereotype.Service;

@Service
public class MeetingReportLifecycleService {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final MeetingAccessPolicy meetingAccessPolicy;

    public MeetingReportLifecycleService(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            MeetingAccessPolicy meetingAccessPolicy
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.meetingAccessPolicy = meetingAccessPolicy;
    }

    public ConfirmMeetingReportResponse confirm(String authorizationHeader, String meetingId, String reportId) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        meetingAccessPolicy.requireEditAccess(workspaceDomainService.meetingAccessContext(meetingId, user.id()));
        MeetingReport report = workspaceDomainService.confirmMeetingReport(meetingId, reportId);
        return new ConfirmMeetingReportResponse(
                report.id(),
                report.status().name(),
                report.version(),
                report.current(),
                report.confirmedAt()
        );
    }
}
