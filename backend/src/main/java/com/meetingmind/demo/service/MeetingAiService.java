package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendMeetingAiChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MeetingAiService {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final MeetingAccessPolicy meetingAccessPolicy;
    private final MeetingAiGatewayClient aiGatewayClient;

    public MeetingAiService(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            MeetingAccessPolicy meetingAccessPolicy,
            MeetingAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.meetingAccessPolicy = meetingAccessPolicy;
        this.aiGatewayClient = aiGatewayClient;
    }

    public AiChatResponse chat(String authorizationHeader, String meetingId, BackendMeetingAiChatRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        meetingAccessPolicy.requireReadAccess(workspaceDomainService.meetingAccessContext(meetingId, user.id()));
        WorkspaceDomainService.MeetingAiContext context = workspaceDomainService.meetingAiContext(meetingId);

        MeetingAiGatewayChatRequest gatewayRequest = new MeetingAiGatewayChatRequest(
                context.meeting().spaceId(),
                context.meeting().id(),
                context.meeting().title(),
                request.question().trim(),
                transcriptRows(context.transcriptSegments()),
                decisionRows(context.reports()),
                actionRows(context.reports()),
                sourceRows(context)
        );

        try {
            return aiGatewayClient.chat(gatewayRequest);
        } catch (AiGatewayException exception) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }
    }

    private List<MeetingAiGatewayChatRequest.TranscriptRow> transcriptRows(List<TranscriptSegment> segments) {
        return segments.stream()
                .map(segment -> new MeetingAiGatewayChatRequest.TranscriptRow(
                        formatTime(segment.startMs()),
                        segment.speakerName() == null || segment.speakerName().isBlank()
                                ? segment.speakerLabel()
                                : segment.speakerName(),
                        segment.text()
                ))
                .toList();
    }

    private List<MeetingAiGatewayChatRequest.LabeledItem> decisionRows(List<MeetingReport> reports) {
        return reports.stream()
                .flatMap(report -> report.decisions().stream())
                .map(decision -> new MeetingAiGatewayChatRequest.LabeledItem(decision.title(), decision.content()))
                .toList();
    }

    private List<MeetingAiGatewayChatRequest.LabeledItem> actionRows(List<MeetingReport> reports) {
        return reports.stream()
                .flatMap(report -> report.actionItems().stream())
                .map(action -> new MeetingAiGatewayChatRequest.LabeledItem(action.title(), action.assigneeName()))
                .toList();
    }

    private List<MeetingAiGatewayChatRequest.SourceContext> sourceRows(WorkspaceDomainService.MeetingAiContext context) {
        List<MeetingAiGatewayChatRequest.SourceContext> transcriptSources = context.transcriptSegments().stream()
                .map(segment -> new MeetingAiGatewayChatRequest.SourceContext(
                        sourceId(segment.id(), "transcript-%03d".formatted(segment.sequence())),
                        "transcript",
                        context.meeting().id(),
                        context.meeting().title(),
                        segment.speakerName() == null || segment.speakerName().isBlank()
                                ? segment.speakerLabel()
                                : segment.speakerName(),
                        formatTimeRange(segment.startMs(), segment.endMs()),
                        segment.startMs(),
                        segment.endMs(),
                        segment.text()
                ))
                .toList();
        List<MeetingAiGatewayChatRequest.SourceContext> reportSources = context.reports().stream()
                .filter(report -> report.current() || report.status() == MeetingReportStatus.CONFIRMED)
                .flatMap(report -> reportSourceRows(context, report).stream())
                .toList();

        return java.util.stream.Stream.concat(transcriptSources.stream(), reportSources.stream()).toList();
    }

    private List<MeetingAiGatewayChatRequest.SourceContext> reportSourceRows(
            WorkspaceDomainService.MeetingAiContext context,
            MeetingReport report
    ) {
        return java.util.stream.Stream.of(
                        reportSummarySource(context, report),
                        report.decisions().stream()
                                .map(decision -> new MeetingAiGatewayChatRequest.SourceContext(
                                        sourceId(decision.id(), "decision-" + stableSuffix(decision.title())),
                                        "decision",
                                        context.meeting().id(),
                                        decision.title(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        decision.content()
                                )),
                        report.actionItems().stream()
                                .map(action -> new MeetingAiGatewayChatRequest.SourceContext(
                                        sourceId(action.id(), "action-" + stableSuffix(action.title())),
                                        "actionItem",
                                        context.meeting().id(),
                                        action.title(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        action.assigneeName() == null || action.assigneeName().isBlank()
                                                ? action.title()
                                                : action.title() + " / assignee=" + action.assigneeName()
                                ))
                )
                .flatMap(stream -> stream)
                .filter(source -> source.text() != null && !source.text().isBlank())
                .toList();
    }

    private java.util.stream.Stream<MeetingAiGatewayChatRequest.SourceContext> reportSummarySource(
            WorkspaceDomainService.MeetingAiContext context,
            MeetingReport report
    ) {
        if (report.summary() == null || report.summary().isBlank()) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.Stream.of(new MeetingAiGatewayChatRequest.SourceContext(
                sourceId(report.id(), "report-" + report.version()),
                "report",
                context.meeting().id(),
                report.title(),
                null,
                null,
                null,
                null,
                report.summary()
        ));
    }

    private String sourceId(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stableSuffix(String value) {
        return Integer.toUnsignedString((value == null ? "" : value).hashCode());
    }

    private String formatTime(int startMs) {
        int totalSeconds = Math.max(0, startMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%02d:%02d".formatted(minutes, seconds);
    }

    private String formatTimeRange(int startMs, int endMs) {
        return "%s-%s".formatted(formatTimestamp(startMs), formatTimestamp(endMs));
    }

    private String formatTimestamp(int valueMs) {
        int totalSeconds = Math.max(0, valueMs / 1000);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }
}
