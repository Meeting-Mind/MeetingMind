package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;
import com.meetingmind.demo.dto.ai.ReportCandidateGenerationResponse;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReportCandidateService {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final MeetingAccessPolicy meetingAccessPolicy;
    private final ReportAiGatewayClient aiGatewayClient;

    public ReportCandidateService(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            MeetingAccessPolicy meetingAccessPolicy,
            ReportAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.meetingAccessPolicy = meetingAccessPolicy;
        this.aiGatewayClient = aiGatewayClient;
    }

    public ReportCandidateGenerationResponse generate(String authorizationHeader, String meetingId) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        meetingAccessPolicy.requireEditAccess(workspaceDomainService.meetingAccessContext(meetingId, user.id()));
        WorkspaceDomainService.MeetingAiContext context = workspaceDomainService.meetingAiContext(meetingId);
        return generateCandidate(user, context, context.meeting().title() + " 회의록", null, null);
    }

    public ReportCandidateGenerationResponse edit(
            String authorizationHeader,
            String meetingId,
            String reportId,
            String instruction
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        meetingAccessPolicy.requireEditAccess(workspaceDomainService.meetingAccessContext(meetingId, user.id()));
        MeetingReport report = workspaceDomainService.meetingReportDetail(user.id(), meetingId, reportId);
        WorkspaceDomainService.MeetingAiContext context = workspaceDomainService.meetingAiContext(meetingId);
        String currentReportContent = hasText(report.markdown()) ? report.markdown() : report.summary();
        return generateCandidate(user, context, report.title(), instruction.trim(), currentReportContent);
    }

    private ReportCandidateGenerationResponse generateCandidate(
            AuthUserResponse user,
            WorkspaceDomainService.MeetingAiContext context,
            String title,
            String instruction,
            String currentReportMarkdown
    ) {
        List<ReportAiGatewayRequest.SourceContext> requestSources = sourceRows(context);
        if (requestSources.isEmpty()) {
            return new ReportCandidateGenerationResponse(null, List.of(), true, "context-only");
        }
        ReportAiGatewayResponse aiResponse;
        try {
            aiResponse = aiGatewayClient.generate(new ReportAiGatewayRequest(
                    context.meeting().spaceId(),
                    context.meeting().id(),
                    context.meeting().title(),
                    "markdown",
                    requestSources,
                    instruction,
                    currentReportMarkdown
            ));
        } catch (AiGatewayException exception) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }

        Set<String> allowedSourceIds = requestSources.stream()
                .map(ReportAiGatewayRequest.SourceContext::sourceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> returnedSourceIds = aiResponse.sources().stream()
                .map(ReportAiGatewayResponse.Source::sourceId)
                .filter(allowedSourceIds::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ReportAiGatewayResponse.Source> responseSources = requestSources.stream()
                .filter(source -> returnedSourceIds.contains(source.sourceId()))
                .map(this::toResponseSource)
                .toList();
        if (aiResponse.unsupported()) {
            return new ReportCandidateGenerationResponse(null, responseSources, true, aiResponse.model());
        }
        if (!hasText(aiResponse.summary()) || !hasText(aiResponse.markdown())) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }
        List<String> sourceIds = responseSources.stream()
                .map(ReportAiGatewayResponse.Source::sourceId)
                .toList();
        if (sourceIds.isEmpty()) {
            return new ReportCandidateGenerationResponse(null, List.of(), true, "context-only");
        }
        MeetingReport candidate = workspaceDomainService.saveReportCandidate(
                context.meeting().id(),
                user.id(),
                title,
                aiResponse.summary().trim(),
                aiResponse.markdown().trim(),
                decisionRows(aiResponse.decisions(), returnedSourceIds),
                actionRows(aiResponse.actionItems(), returnedSourceIds),
                sourceIds
        );
        return new ReportCandidateGenerationResponse(
                toCandidate(candidate),
                responseSources,
                false,
                aiResponse.model()
        );
    }

    private List<ReportAiGatewayRequest.SourceContext> sourceRows(WorkspaceDomainService.MeetingAiContext context) {
        Stream<ReportAiGatewayRequest.SourceContext> transcriptSources = context.transcriptSegments().stream()
                .filter(segment -> hasText(segment.text()))
                .map(segment -> transcriptSource(context, segment));
        Stream<ReportAiGatewayRequest.SourceContext> reportSources = currentConfirmedReport(context.reports()).stream()
                .flatMap(report -> Stream.concat(
                report.decisions().stream().map(decision -> new ReportAiGatewayRequest.SourceContext(
                        decision.id(), "decision", context.meeting().id(), decision.title(), null, null,
                        null, null, decision.content()
                )),
                report.actionItems().stream().map(action -> new ReportAiGatewayRequest.SourceContext(
                        action.id(), "actionItem", context.meeting().id(), action.title(), null, null,
                        null, null, actionText(action)
                ))
        ));
        return Stream.concat(transcriptSources, reportSources)
                .filter(source -> hasText(source.sourceId()) && hasText(source.text()))
                .toList();
    }

    private Optional<MeetingReport> currentConfirmedReport(List<MeetingReport> reports) {
        return reports.stream()
                .filter(report -> report.status() == MeetingReportStatus.CONFIRMED)
                .filter(MeetingReport::current)
                .findFirst()
                .or(() -> reports.stream()
                        .filter(report -> report.status() == MeetingReportStatus.CONFIRMED)
                        .max(Comparator.comparingInt(MeetingReport::version)));
    }

    private ReportAiGatewayResponse.Source toResponseSource(ReportAiGatewayRequest.SourceContext source) {
        return new ReportAiGatewayResponse.Source(
                source.sourceId(), source.type(), source.title(), source.speaker(), source.time(),
                source.startMs(), source.endMs(), source.text()
        );
    }

    private ReportAiGatewayRequest.SourceContext transcriptSource(
            WorkspaceDomainService.MeetingAiContext context,
            TranscriptSegment segment
    ) {
        return new ReportAiGatewayRequest.SourceContext(
                segment.id(),
                "transcript",
                context.meeting().id(),
                context.meeting().title(),
                hasText(segment.speakerName()) ? segment.speakerName() : segment.speakerLabel(),
                formatTimeRange(segment.startMs(), segment.endMs()),
                segment.startMs(),
                segment.endMs(),
                segment.text()
        );
    }

    private List<MeetingReport.ReportDecision> decisionRows(
            List<ReportAiGatewayResponse.Decision> decisions,
            Set<String> allowedSourceIds
    ) {
        return decisions.stream()
                .filter(decision -> hasText(decision.title()))
                .map(decision -> new MeetingReport.ReportDecision(
                        "report-decision-" + UUID.randomUUID(),
                        decision.title().trim(),
                        blankToNull(decision.rationale()),
                        allowedIds(decision.sourceIds(), allowedSourceIds)
                ))
                .toList();
    }

    private List<MeetingReport.ReportActionItem> actionRows(
            List<ReportAiGatewayResponse.ActionItem> actions,
            Set<String> allowedSourceIds
    ) {
        return actions.stream()
                .filter(action -> hasText(action.title()))
                .map(action -> new MeetingReport.ReportActionItem(
                        "report-action-" + UUID.randomUUID(),
                        action.title().trim(),
                        blankToNull(action.assignee()),
                        blankToNull(action.dueDate()),
                        allowedIds(action.sourceIds(), allowedSourceIds)
                ))
                .toList();
    }

    private List<String> allowedIds(List<String> values, Set<String> allowedSourceIds) {
        return values.stream().filter(allowedSourceIds::contains).distinct().toList();
    }

    private ReportCandidateGenerationResponse.Candidate toCandidate(MeetingReport report) {
        return new ReportCandidateGenerationResponse.Candidate(
                report.id(),
                report.meetingId(),
                report.status().name(),
                report.title(),
                report.summary(),
                report.markdown(),
                report.decisions().stream()
                        .map(decision -> new ReportCandidateGenerationResponse.Decision(
                                decision.id(), decision.title(), decision.content(), decision.sourceIds()
                        ))
                        .toList(),
                report.actionItems().stream()
                        .map(action -> new ReportCandidateGenerationResponse.ActionItem(
                                action.id(), action.title(), action.assigneeName(), action.dueDate(),
                                "candidate", action.sourceIds()
                        ))
                        .toList(),
                report.sourceIds(),
                report.createdBy(),
                report.version(),
                report.current(),
                report.createdAt()
        );
    }

    private String actionText(MeetingReport.ReportActionItem action) {
        return hasText(action.assigneeName())
                ? action.title() + " / assignee=" + action.assigneeName()
                : action.title();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
