package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.EmbeddingStatus;
import com.meetingmind.demo.domain.KnowledgeStatus;
import com.meetingmind.demo.domain.Meeting;
import com.meetingmind.demo.domain.MeetingReport;
import com.meetingmind.demo.domain.MeetingReportStatus;
import com.meetingmind.demo.domain.ProjectKnowledge;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendProjectAiChatRequest;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAiService {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final ProjectAiGatewayClient aiGatewayClient;

    public ProjectAiService(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            ProjectAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.aiGatewayClient = aiGatewayClient;
    }

    public AiChatResponse chat(String authorizationHeader, String spaceId, BackendProjectAiChatRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        WorkspaceDomainService.ProjectAiContextCandidates context =
                workspaceDomainService.projectAiContextCandidates(user.id(), spaceId);

        List<Meeting> allowedMeetingRecords = context.meetings();
        List<String> allowedMeetingIds = allowedMeetingRecords.stream()
                .map(Meeting::id)
                .toList();
        List<WorkspaceDomainService.ProjectMeetingContext> allowedMeetings = allowedMeetingRecords.stream()
                .map(meeting -> workspaceDomainService.projectMeetingContext(meeting.id()))
                .toList();
        List<ProjectAiGatewayChatRequest.SourceContext> sources = Stream.concat(
                        projectKnowledgeSources(spaceId, context.projectKnowledge()),
                        meetingSummarySources(spaceId, allowedMeetings)
                )
                .toList();

        try {
            return aiGatewayClient.chat(new ProjectAiGatewayChatRequest(
                    spaceId,
                    request.question().trim(),
                    allowedMeetingIds,
                    sources
            ));
        } catch (AiGatewayException exception) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }
    }

    private Stream<ProjectAiGatewayChatRequest.SourceContext> projectKnowledgeSources(
            String spaceId,
            List<ProjectKnowledge> knowledgeItems
    ) {
        return knowledgeItems.stream()
                .filter(knowledge -> knowledge.status() == KnowledgeStatus.PUBLISHED)
                .filter(knowledge -> knowledge.embeddingStatus() == EmbeddingStatus.COMPLETED)
                .filter(knowledge -> knowledge.deletedAt() == null)
                .filter(knowledge -> hasText(knowledge.content()))
                .map(knowledge -> new ProjectAiGatewayChatRequest.SourceContext(
                        knowledge.id(),
                        "projectKnowledge",
                        spaceId,
                        null,
                        knowledge.title(),
                        knowledge.content()
                ));
    }

    private Stream<ProjectAiGatewayChatRequest.SourceContext> meetingSummarySources(
            String spaceId,
            List<WorkspaceDomainService.ProjectMeetingContext> meetings
    ) {
        return meetings.stream()
                .map(meeting -> currentConfirmedReport(meeting.reports())
                        .map(report -> new ProjectAiGatewayChatRequest.SourceContext(
                                report.id(),
                                "meetingSummary",
                                spaceId,
                                meeting.meeting().id(),
                                report.title(),
                                report.summary()
                        )))
                .flatMap(Optional::stream);
    }

    private Optional<MeetingReport> currentConfirmedReport(List<MeetingReport> reports) {
        Optional<MeetingReport> current = reports.stream()
                .filter(report -> report.status() == MeetingReportStatus.CONFIRMED)
                .filter(MeetingReport::current)
                .filter(report -> hasText(report.summary()))
                .findFirst();
        return current.or(() -> reports.stream()
                .filter(report -> report.status() == MeetingReportStatus.CONFIRMED)
                .filter(report -> hasText(report.summary()))
                .max(Comparator.comparingInt(MeetingReport::version)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
