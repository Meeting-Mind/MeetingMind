package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;
import com.meetingmind.demo.dto.ai.ReportCandidateGenerationResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        MeetingAccessPolicy.MeetingAccessContext accessContext = workspaceDomainService.meetingAccessContext(
                meetingId, user.id()
        );
        meetingAccessPolicy.requireEditAccess(accessContext);
        WorkspaceDomainService.MeetingAiContext context = workspaceDomainService.meetingAiContext(meetingId);
        return generateCandidate(
                user, context, activeParticipantCount(accessContext), context.meeting().title(), null, null
        );
    }

    public ReportCandidateGenerationResponse edit(
            String authorizationHeader,
            String meetingId,
            String reportId,
            String instruction
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        MeetingAccessPolicy.MeetingAccessContext accessContext = workspaceDomainService.meetingAccessContext(
                meetingId, user.id()
        );
        meetingAccessPolicy.requireEditAccess(accessContext);
        MeetingReport report = workspaceDomainService.meetingReportDetail(user.id(), meetingId, reportId);
        WorkspaceDomainService.MeetingAiContext context = workspaceDomainService.meetingAiContext(meetingId);
        String currentReportContent = hasText(report.markdown()) ? report.markdown() : report.summary();
        return generateCandidate(
                user,
                context,
                activeParticipantCount(accessContext),
                report.title(),
                instruction.trim(),
                currentReportContent
        );
    }

    private ReportCandidateGenerationResponse generateCandidate(
            AuthUserResponse user,
            WorkspaceDomainService.MeetingAiContext context,
            int participantCount,
            String title,
            String instruction,
            String currentReportMarkdown
    ) {
        List<ReportAiGatewayRequest.SourceContext> requestSources = sourceRows(context);
        if (requestSources.isEmpty()) {
            return unsupportedResponse("NO_EVIDENCE", 0, "context-only");
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
        recordUsage(user.id(), context, aiResponse);

        Set<String> allowedSourceIds = requestSources.stream()
                .map(ReportAiGatewayRequest.SourceContext::sourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (aiResponse.unsupported()) {
            return unsupportedResponse(
                    normalizeUnsupportedReason(aiResponse.unsupportedReason(), "MODEL_UNSUPPORTED"),
                    aiResponse.droppedCount(),
                    aiResponse.model()
            );
        }

        List<ReportAiGatewayResponse.SummarySentence> summaryRows = validSummaryRows(
                aiResponse.summary(), allowedSourceIds
        );
        List<ReportAiGatewayResponse.Decision> decisions = validDecisionRows(
                aiResponse.decisions(), allowedSourceIds
        );
        List<ReportAiGatewayResponse.ActionItem> actions = validActionRows(
                aiResponse.actionItems(), allowedSourceIds
        );
        int coreDroppedCount = aiResponse.summary().size() - summaryRows.size()
                + aiResponse.decisions().size() - decisions.size()
                + aiResponse.actionItems().size() - actions.size();
        int droppedCount = (int) Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(0, aiResponse.droppedCount()) + coreDroppedCount
        );
        if (summaryRows.isEmpty()) {
            return unsupportedResponse("UNVERIFIED_OUTPUT", droppedCount, aiResponse.model());
        }

        List<String> citedIds = citedSourceIds(summaryRows, decisions, actions);
        Map<String, ReportAiGatewayRequest.SourceContext> sourcesById = new LinkedHashMap<>();
        requestSources.forEach(source -> sourcesById.putIfAbsent(source.sourceId(), source));
        List<ReportAiGatewayResponse.Source> responseSources = citedIds.stream()
                .map(sourcesById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toResponseSource)
                .toList();
        List<String> sourceIds = responseSources.stream()
                .map(ReportAiGatewayResponse.Source::sourceId)
                .toList();
        if (sourceIds.isEmpty()) {
            return unsupportedResponse("UNVERIFIED_OUTPUT", droppedCount, aiResponse.model());
        }
        String summary = summaryRows.stream()
                .map(ReportAiGatewayResponse.SummarySentence::text)
                .collect(Collectors.joining("\n"));
        String markdown = renderMarkdown(
                context, participantCount, title, summaryRows, decisions, actions, responseSources
        );
        MeetingReport candidate = workspaceDomainService.saveReportCandidate(
                context.meeting().id(),
                user.id(),
                title,
                summary,
                markdown,
                decisionRows(decisions),
                actionRows(actions),
                sourceIds
        );
        return new ReportCandidateGenerationResponse(
                toCandidate(candidate),
                responseSources,
                false,
                null,
                droppedCount,
                aiResponse.model()
        );
    }

    private String normalizeUnsupportedReason(String value, String fallback) {
        return switch (value == null ? "" : value) {
            case "NO_EVIDENCE", "LOW_RELEVANCE", "MODEL_UNSUPPORTED", "UNVERIFIED_OUTPUT" -> value;
            default -> fallback;
        };
    }

    private ReportCandidateGenerationResponse unsupportedResponse(String reason, int droppedCount, String model) {
        return new ReportCandidateGenerationResponse(
                null,
                List.of(),
                true,
                reason,
                Math.max(0, droppedCount),
                hasText(model) ? model : "context-only"
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

    private List<ReportAiGatewayResponse.SummarySentence> validSummaryRows(
            List<ReportAiGatewayResponse.SummarySentence> summary,
            Set<String> allowedSourceIds
    ) {
        return summary.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> new ReportAiGatewayResponse.SummarySentence(
                        item.text() == null ? null : item.text().trim(),
                        allowedIds(item.sourceIds(), allowedSourceIds)
                ))
                .filter(item -> hasText(item.text()) && !item.sourceIds().isEmpty())
                .toList();
    }

    private List<ReportAiGatewayResponse.Decision> validDecisionRows(
            List<ReportAiGatewayResponse.Decision> decisions,
            Set<String> allowedSourceIds
    ) {
        return decisions.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> new ReportAiGatewayResponse.Decision(
                        item.title() == null ? null : item.title().trim(),
                        blankToNull(item.rationale()),
                        allowedIds(item.sourceIds(), allowedSourceIds)
                ))
                .filter(item -> hasText(item.title()) && !item.sourceIds().isEmpty())
                .toList();
    }

    private List<ReportAiGatewayResponse.ActionItem> validActionRows(
            List<ReportAiGatewayResponse.ActionItem> actions,
            Set<String> allowedSourceIds
    ) {
        return actions.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> new ReportAiGatewayResponse.ActionItem(
                        item.title() == null ? null : item.title().trim(),
                        blankToNull(item.assignee()),
                        blankToNull(item.dueDate()),
                        allowedIds(item.sourceIds(), allowedSourceIds),
                        "candidate"
                ))
                .filter(item -> hasText(item.title()) && !item.sourceIds().isEmpty())
                .toList();
    }

    private List<String> citedSourceIds(
            List<ReportAiGatewayResponse.SummarySentence> summary,
            List<ReportAiGatewayResponse.Decision> decisions,
            List<ReportAiGatewayResponse.ActionItem> actions
    ) {
        return Stream.of(
                        summary.stream().flatMap(item -> item.sourceIds().stream()),
                        decisions.stream().flatMap(item -> item.sourceIds().stream()),
                        actions.stream().flatMap(item -> item.sourceIds().stream())
                )
                .flatMap(stream -> stream)
                .distinct()
                .toList();
    }

    private List<MeetingReport.ReportDecision> decisionRows(List<ReportAiGatewayResponse.Decision> decisions) {
        return decisions.stream()
                .map(decision -> new MeetingReport.ReportDecision(
                        "report-decision-" + UUID.randomUUID(),
                        decision.title(),
                        decision.rationale(),
                        decision.sourceIds()
                ))
                .toList();
    }

    private List<MeetingReport.ReportActionItem> actionRows(List<ReportAiGatewayResponse.ActionItem> actions) {
        return actions.stream()
                .map(action -> new MeetingReport.ReportActionItem(
                        "report-action-" + UUID.randomUUID(),
                        action.title(),
                        action.assignee(),
                        action.dueDate(),
                        action.sourceIds()
                ))
                .toList();
    }

    private List<String> allowedIds(List<String> values, Set<String> allowedSourceIds) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .filter(allowedSourceIds::contains)
                .distinct()
                .toList();
    }

    private String renderMarkdown(
            WorkspaceDomainService.MeetingAiContext context,
            int participantCount,
            String title,
            List<ReportAiGatewayResponse.SummarySentence> summary,
            List<ReportAiGatewayResponse.Decision> decisions,
            List<ReportAiGatewayResponse.ActionItem> actions,
            List<ReportAiGatewayResponse.Source> sources
    ) {
        Map<String, Integer> citationNumbers = new LinkedHashMap<>();
        for (ReportAiGatewayResponse.Source source : sources) {
            citationNumbers.putIfAbsent(source.sourceId(), citationNumbers.size() + 1);
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(markdownText(title)).append("\n\n");
        markdown.append(meetingMetadata(context, participantCount)).append("\n\n");
        markdown.append("## 요약\n\n");
        summary.forEach(item -> markdown
                .append(markdownText(item.text()))
                .append(citations(item.sourceIds(), citationNumbers))
                .append('\n'));

        if (!decisions.isEmpty()) {
            markdown.append("\n## 결정\n\n");
            for (int index = 0; index < decisions.size(); index++) {
                ReportAiGatewayResponse.Decision decision = decisions.get(index);
                markdown.append(index + 1)
                        .append(". ")
                        .append(markdownText(decision.title()))
                        .append(' ')
                        .append(citations(decision.sourceIds(), citationNumbers))
                        .append('\n');
                if (hasText(decision.rationale())) {
                    markdown.append("   ").append(markdownText(decision.rationale())).append('\n');
                }
            }
        }

        if (!actions.isEmpty()) {
            markdown.append("\n## 다음 할 일\n\n");
            for (ReportAiGatewayResponse.ActionItem action : actions) {
                markdown.append("- [ ] ")
                        .append(markdownText(action.title()))
                        .append(' ')
                        .append(citations(action.sourceIds(), citationNumbers))
                        .append('\n')
                        .append("  담당 ")
                        .append(hasText(action.assignee()) ? markdownText(action.assignee()) : "미정")
                        .append(" · 기한 ")
                        .append(hasText(action.dueDate()) ? markdownText(action.dueDate()) : "미정")
                        .append('\n');
            }
        }

        markdown.append("\n## 근거\n\n");
        for (ReportAiGatewayResponse.Source source : sources) {
            Integer number = citationNumbers.get(source.sourceId());
            markdown.append('[').append(number).append("] ")
                    .append(sourceLabel(source));
            String time = sourceTime(source);
            if (hasText(time)) {
                markdown.append(' ').append(markdownText(time));
            }
            markdown.append(" — ").append(markdownText(source.text())).append('\n');
        }
        markdown.append("\n---\nMeetingMind가 이 회의의 검증된 근거만 사용해 생성했습니다.");
        return markdown.toString();
    }

    private String meetingMetadata(WorkspaceDomainService.MeetingAiContext context, int participantCount) {
        OffsetDateTime start = context.meeting().startedAt() == null
                ? context.meeting().scheduledAt()
                : context.meeting().startedAt();
        OffsetDateTime end = context.meeting().endedAt() == null
                ? context.meeting().scheduledEndAt()
                : context.meeting().endedAt();
        String participantText = "참석 %d명".formatted(Math.max(0, participantCount));
        if (start == null) {
            return participantText;
        }
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String range = start.format(dateTime);
        if (end != null) {
            range += start.toLocalDate().equals(end.toLocalDate())
                    ? "–" + end.format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "–" + end.format(dateTime);
        }
        return range + " · " + participantText;
    }

    private String citations(List<String> sourceIds, Map<String, Integer> citationNumbers) {
        return sourceIds.stream()
                .map(citationNumbers::get)
                .filter(java.util.Objects::nonNull)
                .map(number -> "[" + number + "]")
                .collect(Collectors.joining());
    }

    private String sourceLabel(ReportAiGatewayResponse.Source source) {
        if (hasText(source.speaker())) {
            return markdownText(source.speaker());
        }
        return switch (source.type() == null ? "" : source.type()) {
            case "decision" -> "기존 결정사항";
            case "actionItem" -> "기존 실행 항목";
            default -> hasText(source.title()) ? markdownText(source.title()) : "회의 근거";
        };
    }

    private String sourceTime(ReportAiGatewayResponse.Source source) {
        if (hasText(source.time())) {
            return source.time();
        }
        if (source.startMs() == null) {
            return null;
        }
        return source.endMs() == null
                ? formatTimestamp(source.startMs())
                : formatTimeRange(source.startMs(), source.endMs());
    }

    private String markdownText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("\\s+", " ")
                .replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`");
    }

    private int activeParticipantCount(MeetingAccessPolicy.MeetingAccessContext accessContext) {
        return (int) accessContext.participants().stream()
                .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                .count();
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

    private void recordUsage(
            String actorUserId,
            WorkspaceDomainService.MeetingAiContext context,
            ReportAiGatewayResponse response
    ) {
        AiChatResponse.AiUsageMetrics usage = response.usage();
        if (usage == null) {
            return;
        }
        workspaceDomainService.recordAiUsageEvent(
                actorUserId,
                context.meeting().spaceId(),
                context.meeting().id(),
                "report-ai",
                usage.provider(),
                usage.apiStyle(),
                usage.stream(),
                usage.inputTokens(),
                usage.outputTokens(),
                totalTokens(usage),
                (long) usage.totalMs()
        );
    }

    private Integer totalTokens(AiChatResponse.AiUsageMetrics usage) {
        Integer inputTokens = usage.inputTokens();
        Integer outputTokens = usage.outputTokens();
        if (inputTokens != null && outputTokens != null) {
            return inputTokens + outputTokens;
        }
        if (inputTokens != null && usage.outputTokenEstimate() != null) {
            return inputTokens + usage.outputTokenEstimate();
        }
        if (outputTokens != null) {
            return outputTokens;
        }
        return inputTokens;
    }
}
