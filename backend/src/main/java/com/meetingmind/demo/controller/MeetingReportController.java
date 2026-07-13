package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.ai.ReportCandidateGenerationResponse;
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

    public MeetingReportController(ReportCandidateService reportCandidateService) {
        this.reportCandidateService = reportCandidateService;
    }

    @PostMapping("/{meetingId}/reports/generate")
    public ReportCandidateGenerationResponse generate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        return reportCandidateService.generate(authorizationHeader, meetingId);
    }
}
