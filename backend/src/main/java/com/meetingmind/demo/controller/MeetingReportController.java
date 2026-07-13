package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.ConfirmMeetingReportResponse;
import com.meetingmind.demo.dto.ai.ReportCandidateGenerationResponse;
import com.meetingmind.demo.service.MeetingReportLifecycleService;
import com.meetingmind.demo.service.ReportCandidateService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingReportController {

    private final ReportCandidateService reportCandidateService;
    private final MeetingReportLifecycleService meetingReportLifecycleService;

    public MeetingReportController(
            ReportCandidateService reportCandidateService,
            MeetingReportLifecycleService meetingReportLifecycleService
    ) {
        this.reportCandidateService = reportCandidateService;
        this.meetingReportLifecycleService = meetingReportLifecycleService;
    }

    @PostMapping("/{meetingId}/reports/generate")
    public ReportCandidateGenerationResponse generate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        return reportCandidateService.generate(authorizationHeader, meetingId);
    }

    @PostMapping("/{meetingId}/reports/{reportId}/confirm")
    public ConfirmMeetingReportResponse confirm(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId
    ) {
        return meetingReportLifecycleService.confirm(authorizationHeader, meetingId, reportId);
    }
}
