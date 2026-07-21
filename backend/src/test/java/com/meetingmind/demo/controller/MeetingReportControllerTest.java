package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.service.MeetingReportLifecycleService;
import com.meetingmind.demo.service.ReportCandidateService;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class MeetingReportControllerTest {

    @Test
    void downloadsAclCheckedReportAsDocx() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspace = mock(WorkspaceDomainService.class);
        AuthUserResponse user = new AuthUserResponse("user-1", "user@meetingmind.test", "User", null, "active");
        MeetingReport report = new MeetingReport(
                "report-1", "meeting-1", MeetingReportStatus.CONFIRMED, "회의록 제목", "요약", "# 회의록\n한글 본문",
                List.of(), List.of(), List.of(), "user-1", 1, true, Instant.parse("2026-07-20T00:00:00Z"), Instant.now()
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(workspace.downloadMeetingReport("user-1", "meeting-1", "report-1")).thenReturn(report);
        MeetingReportController controller = new MeetingReportController(
                mock(ReportCandidateService.class), mock(MeetingReportLifecycleService.class), authService, workspace
        );

        var response = controller.download("Bearer token", "meeting-1", "report-1", "docx");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("meeting-report-report-1.docx");
        assertThat(response.getBody()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void downloadsAclCheckedReportAsPdfWithKoreanText() throws Exception {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspace = mock(WorkspaceDomainService.class);
        AuthUserResponse user = new AuthUserResponse("user-1", "user@meetingmind.test", "User", null, "active");
        MeetingReport report = new MeetingReport(
                "report-1", "meeting-1", MeetingReportStatus.CONFIRMED, "회의록 제목", "요약", "# 회의록\n한글 본문",
                List.of(), List.of(), List.of(), "user-1", 1, true, Instant.parse("2026-07-20T00:00:00Z"), Instant.now()
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(workspace.downloadMeetingReport("user-1", "meeting-1", "report-1")).thenReturn(report);
        MeetingReportController controller = new MeetingReportController(
                mock(ReportCandidateService.class), mock(MeetingReportLifecycleService.class), authService, workspace
        );

        var response = controller.download("Bearer token", "meeting-1", "report-1", "pdf");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("meeting-report-report-1.pdf");
        assertThat(response.getBody()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var document = Loader.loadPDF(response.getBody())) {
            assertThat(new PDFTextStripper().getText(document)).contains("회의록 제목", "한글 본문");
        }
    }
}
