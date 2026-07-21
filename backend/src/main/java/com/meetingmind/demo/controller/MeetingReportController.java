package com.meetingmind.demo.controller;

import jakarta.validation.Valid;
import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ConfirmMeetingReportResponse;
import com.meetingmind.demo.dto.EditReportWithAiRequest;
import com.meetingmind.demo.dto.ReportListResponse;
import com.meetingmind.demo.dto.ReportDetailResponse;
import com.meetingmind.demo.dto.RestoreReportResponse;
import com.meetingmind.demo.dto.UpdateReportRequest;
import com.meetingmind.demo.dto.UpdateReportResponse;
import com.meetingmind.demo.dto.ai.ReportCandidateGenerationResponse;
import com.meetingmind.demo.service.MeetingReportLifecycleService;
import com.meetingmind.demo.service.ReportCandidateService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingReportController {

    private final ReportCandidateService reportCandidateService;
    private final MeetingReportLifecycleService meetingReportLifecycleService;
    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;

    public MeetingReportController(
            ReportCandidateService reportCandidateService,
            MeetingReportLifecycleService meetingReportLifecycleService,
            AuthService authService,
            WorkspaceDomainService workspaceDomainService
    ) {
        this.reportCandidateService = reportCandidateService;
        this.meetingReportLifecycleService = meetingReportLifecycleService;
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
    }

    @PostMapping("/{meetingId}/reports/generate")
    public ReportCandidateGenerationResponse generate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        return reportCandidateService.generate(authorizationHeader, meetingId);
    }

    @PostMapping("/{meetingId}/reports/{reportId}/ai-edits")
    public ReportCandidateGenerationResponse editWithAi(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId,
            @Valid @RequestBody EditReportWithAiRequest request
    ) {
        return reportCandidateService.edit(authorizationHeader, meetingId, reportId, request.instruction());
    }

    @PostMapping("/{meetingId}/reports/{reportId}/confirm")
    public ConfirmMeetingReportResponse confirm(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId
    ) {
        return meetingReportLifecycleService.confirm(authorizationHeader, meetingId, reportId);
    }

    @GetMapping("/{meetingId}/reports")
    public ReportListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @RequestParam(required = false) String status
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new ReportListResponse(workspaceDomainService.listMeetingReports(user.id(), meetingId, status)
                .stream().map(MeetingReportController::toSummary).toList());
    }

    @GetMapping("/{meetingId}/reports/{reportId}")
    public ReportDetailResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingReport report = workspaceDomainService.meetingReportDetail(user.id(), meetingId, reportId);
        return new ReportDetailResponse(
                report.id(), report.meetingId(), report.status().name(), report.title(), report.summary(), report.markdown(),
                report.version(), report.current(), report.createdAt(), report.confirmedAt(), report.sourceIds()
        );
    }

    @PatchMapping("/{meetingId}/reports/{reportId}")
    public UpdateReportResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId,
            @RequestBody UpdateReportRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingReport updated = workspaceDomainService.updateMeetingReport(user.id(), meetingId, reportId,
                new WorkspaceDomainService.ReportPatch(
                        request.title(), request.titlePresent(), request.summary(), request.summaryPresent(),
                        request.markdown(), request.markdownPresent()
                ));
        return new UpdateReportResponse(updated.id(), updated.status().name(), updated.version());
    }

    @PostMapping("/{meetingId}/reports/{reportId}/restore")
    public RestoreReportResponse restore(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingReport restored = workspaceDomainService.restoreMeetingReport(user.id(), meetingId, reportId);
        return new RestoreReportResponse(restored.id(), restored.status().name(), restored.version(), reportId);
    }

    @GetMapping("/{meetingId}/reports/{reportId}/download")
    public ResponseEntity<byte[]> download(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String reportId,
            @RequestParam String format
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        String normalizedFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (!"markdown".equals(normalizedFormat)
                && !"docx".equals(normalizedFormat)
                && !"pdf".equals(normalizedFormat)) {
            throw new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "지원하지 않는 회의록 다운로드 형식입니다.");
        }
        MeetingReport report = workspaceDomainService.downloadMeetingReport(user.id(), meetingId, reportId);
        if ("docx".equals(normalizedFormat)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("meeting-report-" + report.id() + ".docx", StandardCharsets.UTF_8).build().toString())
                    .body(renderDocx(report));
        }
        if ("pdf".equals(normalizedFormat)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("meeting-report-" + report.id() + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(renderPdf(report));
        }
        byte[] body = (report.markdown() == null ? report.summary() : report.markdown()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("meeting-report-" + report.id() + ".md", StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private byte[] renderDocx(MeetingReport report) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(report.title());

            String content = report.markdown() == null || report.markdown().isBlank() ? report.summary() : report.markdown();
            for (String line : content.split("\\R", -1)) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(line);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("회의록 DOCX를 생성하지 못했습니다.", exception);
        }
    }

    private byte[] renderPdf(MeetingReport report) {
        try (InputStream fontStream = MeetingReportController.class.getResourceAsStream(
                        "/fonts/NanumGothic-Regular.ttf");
                PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontStream == null) {
                throw new IllegalStateException("PDF 글꼴 리소스를 찾을 수 없습니다.");
            }
            PDType0Font font = PDType0Font.load(document, fontStream);
            PdfWriter writer = new PdfWriter(document, font);
            writer.write(report.title(), 16f, true);
            writer.blankLine();
            String content = report.markdown() == null || report.markdown().isBlank()
                    ? report.summary()
                    : report.markdown();
            for (String line : content.split("\\R", -1)) {
                writer.write(line, 11f, false);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("회의록 PDF를 생성하지 못했습니다.", exception);
        }
    }

    private static final class PdfWriter {

        private static final float MARGIN = 54f;
        private static final float BODY_LEADING = 17f;
        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private float cursorY;

        private PdfWriter(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        private void write(String source, float fontSize, boolean title) throws IOException {
            String value = source == null || source.isBlank() ? (title ? "회의록" : "") : source;
            float leading = title ? 22f : BODY_LEADING;
            for (String line : wrap(supportedText(value), fontSize)) {
                ensurePage(leading);
                try (PDPageContentStream stream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(MARGIN, cursorY);
                    stream.showText(line);
                    stream.endText();
                }
                cursorY -= leading;
            }
        }

        private void blankLine() throws IOException {
            ensurePage(BODY_LEADING);
            cursorY -= BODY_LEADING;
        }

        private void ensurePage(float leading) {
            if (page == null || cursorY - leading < MARGIN) {
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                cursorY = page.getMediaBox().getHeight() - MARGIN;
            }
        }

        private List<String> wrap(String text, float fontSize) throws IOException {
            if (text.isEmpty()) {
                return List.of("");
            }
            float availableWidth = PDRectangle.A4.getWidth() - (MARGIN * 2);
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                String candidate = current + glyph;
                if (current.length() > 0 && width(candidate, fontSize) > availableWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(glyph);
                offset += Character.charCount(codePoint);
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            return lines;
        }

        private float width(String text, float fontSize) throws IOException {
            return font.getStringWidth(text) / 1000f * fontSize;
        }

        private String supportedText(String source) {
            StringBuilder result = new StringBuilder(source.length());
            for (int offset = 0; offset < source.length();) {
                int codePoint = source.codePointAt(offset);
                if (Character.isISOControl(codePoint)) {
                    result.append(' ');
                } else {
                    String glyph = new String(Character.toChars(codePoint));
                    try {
                        font.getStringWidth(glyph);
                        result.append(glyph);
                    } catch (IOException | IllegalArgumentException exception) {
                        result.append('?');
                    }
                }
                offset += Character.charCount(codePoint);
            }
            return result.toString();
        }
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }

    private static ReportListResponse.Report toSummary(MeetingReport report) {
        return new ReportListResponse.Report(
                report.id(), report.meetingId(), report.status().name(), report.title(), report.summary(),
                report.version(), report.current(), report.createdAt()
        );
    }
}
