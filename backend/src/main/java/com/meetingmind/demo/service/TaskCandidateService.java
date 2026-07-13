package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantAccessStatus;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.TaskCandidate;
import com.meetingmind.demo.domain.TaskCardStatus;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ConfirmTaskCandidateRequest;
import com.meetingmind.demo.dto.ConfirmTaskCandidateResponse;
import com.meetingmind.demo.dto.TaskCandidateGenerationResponse;
import com.meetingmind.demo.dto.TaskCandidateResponse;
import com.meetingmind.demo.dto.TaskCandidatesResponse;
import com.meetingmind.demo.dto.TaskAssigneeResponse;
import com.meetingmind.demo.dto.ai.TaskAiGatewayRequest;
import com.meetingmind.demo.dto.ai.TaskAiGatewayResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskCandidateService {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final MeetingAccessPolicy meetingAccessPolicy;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final TaskAiGatewayClient aiGatewayClient;

    public TaskCandidateService(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            MeetingAccessPolicy meetingAccessPolicy,
            SpaceAccessPolicy spaceAccessPolicy,
            TaskAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.meetingAccessPolicy = meetingAccessPolicy;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.aiGatewayClient = aiGatewayClient;
    }

    public TaskCandidateGenerationResponse generate(String authorizationHeader, String meetingId) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        MeetingAccessPolicy.MeetingAccessContext accessContext =
                workspaceDomainService.meetingAccessContext(meetingId, user.id());
        meetingAccessPolicy.requireEditAccess(accessContext);
        WorkspaceDomainService.TaskCandidateContext context = workspaceDomainService.taskCandidateContext(meetingId);
        List<TaskAiGatewayRequest.SourceContext> requestSources = sourceRows(context);
        if (requestSources.isEmpty()) {
            return new TaskCandidateGenerationResponse(
                    List.of(), assigneeRows(context, accessContext), canConfirm(accessContext),
                    List.of(), true, "context-only"
            );
        }

        TaskAiGatewayResponse aiResponse;
        try {
            aiResponse = aiGatewayClient.extract(new TaskAiGatewayRequest(
                    context.meeting().spaceId(),
                    context.meeting().id(),
                    context.meeting().title(),
                    participantRows(context),
                    requestSources
            ));
        } catch (AiGatewayException exception) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }

        Set<String> allowedSourceIds = requestSources.stream()
                .map(TaskAiGatewayRequest.SourceContext::sourceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> returnedSourceIds = aiResponse.sources().stream()
                .map(TaskAiGatewayResponse.Source::sourceId)
                .filter(allowedSourceIds::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<TaskCandidateGenerationResponse.Source> returnedSources = requestSources.stream()
                .filter(source -> returnedSourceIds.contains(source.sourceId()))
                .map(this::toResponseSource)
                .toList();
        if (aiResponse.unsupported()) {
            return new TaskCandidateGenerationResponse(
                    List.of(), assigneeRows(context, accessContext), canConfirm(accessContext),
                    returnedSources, true, aiResponse.model()
            );
        }

        List<TaskCandidate> candidates = aiResponse.tasks().stream()
                .filter(task -> hasText(task.title()))
                .map(task -> saveCandidate(context, user.id(), task, allowedSourceIds))
                .flatMap(Optional::stream)
                .toList();
        if (candidates.isEmpty()) {
            return new TaskCandidateGenerationResponse(
                    List.of(), assigneeRows(context, accessContext), canConfirm(accessContext),
                    returnedSources, true, aiResponse.model()
            );
        }
        Set<String> usedSourceIds = candidates.stream()
                .flatMap(candidate -> candidate.sourceIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<TaskCandidateGenerationResponse.Source> responseSources = requestSources.stream()
                .filter(source -> usedSourceIds.contains(source.sourceId()))
                .map(this::toResponseSource)
                .toList();
        return new TaskCandidateGenerationResponse(
                candidates.stream().map(TaskCandidateResponse::from).toList(),
                assigneeRows(context, accessContext),
                canConfirm(accessContext),
                responseSources,
                false,
                aiResponse.model()
        );
    }

    public TaskCandidatesResponse list(String authorizationHeader, String meetingId) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        MeetingAccessPolicy.MeetingAccessContext accessContext =
                workspaceDomainService.meetingAccessContext(meetingId, user.id());
        meetingAccessPolicy.requireReadAccess(accessContext);
        WorkspaceDomainService.TaskCandidateContext context = workspaceDomainService.taskCandidateContext(meetingId);
        return new TaskCandidatesResponse(
                workspaceDomainService.taskCandidates(meetingId).stream()
                        .map(TaskCandidateResponse::from)
                        .toList(),
                assigneeRows(context, accessContext),
                canConfirm(accessContext)
        );
    }

    public ConfirmTaskCandidateResponse confirm(
            String authorizationHeader,
            String meetingId,
            String candidateId,
            ConfirmTaskCandidateRequest request
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        MeetingAccessPolicy.MeetingAccessContext accessContext =
                workspaceDomainService.meetingAccessContext(meetingId, user.id());
        meetingAccessPolicy.requireEditAccess(accessContext);
        spaceAccessPolicy.requireSpaceAccess(accessContext.spaceContext());
        if (request == null) {
            throw invalidRequest("요청 본문은 필수입니다.");
        }
        WorkspaceDomainService.TaskConfirmationResult result = workspaceDomainService.confirmTaskCandidate(
                meetingId,
                candidateId,
                request.title(),
                request.description(),
                blankToNull(request.assigneeId()),
                parseRequiredDate(request.dueDate()),
                parseStatus(request.status())
        );
        return new ConfirmTaskCandidateResponse(result.taskCard().id(), result.candidate().id());
    }

    private Optional<TaskCandidate> saveCandidate(
            WorkspaceDomainService.TaskCandidateContext context,
            String createdBy,
            TaskAiGatewayResponse.Task task,
            Set<String> allowedSourceIds
    ) {
        List<String> sourceIds = task.sourceIds().stream()
                .filter(allowedSourceIds::contains)
                .distinct()
                .toList();
        if (sourceIds.isEmpty()) {
            return Optional.empty();
        }
        String assigneeName = blankToNull(task.assignee());
        String suggestedAssigneeId = assigneeName == null ? null : context.participants().stream()
                .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                .filter(WorkspaceDomainService.TaskParticipant::spaceMember)
                .filter(participant -> assigneeName.equals(participant.displayName()))
                .map(WorkspaceDomainService.TaskParticipant::userId)
                .findFirst()
                .orElse(null);
        return Optional.of(workspaceDomainService.saveTaskCandidate(
                context.meeting().id(),
                createdBy,
                task.title().trim(),
                assigneeName,
                suggestedAssigneeId,
                parseOptionalDate(task.dueDate()),
                sourceIds
        ));
    }

    private List<TaskAiGatewayRequest.Participant> participantRows(
            WorkspaceDomainService.TaskCandidateContext context
    ) {
        return context.participants().stream()
                .filter(participant -> participant.accessStatus() == ParticipantAccessStatus.ACTIVE)
                .filter(participant -> hasText(participant.displayName()))
                .map(participant -> new TaskAiGatewayRequest.Participant(
                        participant.displayName(), participant.role().name()
                ))
                .toList();
    }

    private List<TaskAssigneeResponse> assigneeRows(
            WorkspaceDomainService.TaskCandidateContext context,
            MeetingAccessPolicy.MeetingAccessContext accessContext
    ) {
        if (!canConfirm(accessContext)) {
            return List.of();
        }
        return context.assignees().stream()
                .filter(assignee -> hasText(assignee.displayName()))
                .map(assignee -> new TaskAssigneeResponse(assignee.userId(), assignee.displayName()))
                .toList();
    }

    private boolean canConfirm(MeetingAccessPolicy.MeetingAccessContext context) {
        if (context == null || context.spaceContext() == null || context.spaceContext().membership() == null
                || !context.spaceContext().membership().active()) {
            return false;
        }
        SpaceRole spaceRole = context.spaceContext().membership().role();
        if (spaceRole == SpaceRole.OWNER || spaceRole == SpaceRole.ADMIN) {
            return true;
        }
        return context.participant() != null
                && context.participant().accessStatus() == ParticipantAccessStatus.ACTIVE
                && (context.participant().role() == MeetingRole.HOST
                || context.participant().role() == MeetingRole.EDITOR);
    }

    private List<TaskAiGatewayRequest.SourceContext> sourceRows(
            WorkspaceDomainService.TaskCandidateContext context
    ) {
        Stream<TaskAiGatewayRequest.SourceContext> transcriptSources = context.transcriptSegments().stream()
                .filter(segment -> hasText(segment.text()))
                .map(segment -> transcriptSource(context, segment));
        Stream<TaskAiGatewayRequest.SourceContext> reportSources = currentConfirmedReport(context.reports()).stream()
                .flatMap(report -> Stream.concat(
                        Stream.of(new TaskAiGatewayRequest.SourceContext(
                                report.id(), "report", context.meeting().spaceId(), context.meeting().id(),
                                report.title(), null, null, null, null, report.summary()
                        )),
                        Stream.concat(
                                report.decisions().stream().map(decision -> new TaskAiGatewayRequest.SourceContext(
                                        decision.id(), "decision", context.meeting().spaceId(), context.meeting().id(),
                                        decision.title(), null, null, null, null, decision.content()
                                )),
                                report.actionItems().stream().map(action -> new TaskAiGatewayRequest.SourceContext(
                                        action.id(), "actionItem", context.meeting().spaceId(), context.meeting().id(),
                                        action.title(), null, null, null, null, actionText(action)
                                ))
                        )
                ));
        return Stream.concat(transcriptSources, reportSources)
                .filter(source -> hasText(source.sourceId()) && hasText(source.text()))
                .toList();
    }

    private TaskAiGatewayRequest.SourceContext transcriptSource(
            WorkspaceDomainService.TaskCandidateContext context,
            TranscriptSegment segment
    ) {
        return new TaskAiGatewayRequest.SourceContext(
                segment.id(),
                "transcript",
                context.meeting().spaceId(),
                context.meeting().id(),
                context.meeting().title(),
                hasText(segment.speakerName()) ? segment.speakerName() : segment.speakerLabel(),
                formatTimeRange(segment.startMs(), segment.endMs()),
                segment.startMs(),
                segment.endMs(),
                segment.text()
        );
    }

    private TaskCandidateGenerationResponse.Source toResponseSource(TaskAiGatewayRequest.SourceContext source) {
        return new TaskCandidateGenerationResponse.Source(
                source.sourceId(), source.type(), source.title(), source.speaker(), source.time(),
                source.startMs(), source.endMs(), source.text()
        );
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

    private TaskCardStatus parseStatus(String value) {
        if (!hasText(value)) {
            return TaskCardStatus.TODO;
        }
        try {
            return TaskCardStatus.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("지원하지 않는 태스크 상태입니다.");
        }
    }

    private LocalDate parseRequiredDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw invalidRequest("마감일은 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private LocalDate parseOptionalDate(String value) {
        try {
            return hasText(value) ? LocalDate.parse(value.trim()) : null;
        } catch (DateTimeParseException ignored) {
            return null;
        }
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

    private AuthorizationException invalidRequest(String message) {
        return new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
