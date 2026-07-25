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

    @Test
    void detailExposesStoredDecisionsAndActionItems() {
        // 이 값들은 report_decisions / report_action_items에 저장되고 도메인도 읽고 있었으나
        // 응답에서 빠져 있었다. 그래서 화면이 마크다운을 정규식으로 훑어 결정을 만들고,
        // 할 일은 아예 보여주지 못했다.
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspace = mock(WorkspaceDomainService.class);
        AuthUserResponse user = new AuthUserResponse("user-1", "user@meetingmind.test", "User", null, "active");
        MeetingReport report = new MeetingReport(
                "report-1", "meeting-1", MeetingReportStatus.DRAFT, "회의록 제목", "요약", "# 회의록",
                List.of(new MeetingReport.ReportDecision(
                        "report-decision-1", "베타는 다음 달 시작", "일부 사용자 대상", List.of("segment-1"))),
                List.of(new MeetingReport.ReportActionItem(
                        "report-action-1", "오답 대응 방안 마련", "서동준", "2026-08-10", List.of("segment-4"))),
                List.of("segment-1", "segment-4"), "user-1", 2, false,
                Instant.parse("2026-07-26T00:00:00Z"), null
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(workspace.meetingReportDetail("user-1", "meeting-1", "report-1")).thenReturn(report);
        MeetingReportController controller = new MeetingReportController(
                mock(ReportCandidateService.class), mock(MeetingReportLifecycleService.class), authService, workspace
        );

        var detail = controller.detail("Bearer token", "meeting-1", "report-1");

        assertThat(detail.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.title()).isEqualTo("베타는 다음 달 시작");
            assertThat(decision.rationale()).isEqualTo("일부 사용자 대상");
            // 각주 번호를 붙이려면 항목별 근거가 있어야 한다.
            assertThat(decision.sourceIds()).containsExactly("segment-1");
        });
        assertThat(detail.actionItems()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("오답 대응 방안 마련");
            assertThat(item.assignee()).isEqualTo("서동준");
            assertThat(item.dueDate()).isEqualTo("2026-08-10");
            assertThat(item.sourceIds()).containsExactly("segment-4");
        });
    }
}
