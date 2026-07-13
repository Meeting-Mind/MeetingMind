package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.AiSource;
import com.meetingmind.demo.dto.ai.BackendProjectAiChatRequest;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;
import com.meetingmind.demo.service.AiGatewayException;
import com.meetingmind.demo.service.ProjectAiGatewayClient;
import com.meetingmind.demo.service.ProjectAiService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProjectAiServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-14T10:00:00+09:00");

    @Test
    void chatIncludesOfficialKnowledgeAndOnlyReadableMeetingSummaries() {
        TestContext context = newContext("user-member");
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult allowedMeeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "접근 가능 회의",
                SCHEDULED_AT,
                List.of(member.id())
        );
        WorkspaceDomainService.MeetingCreationResult deniedMeeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "접근 불가 회의",
                SCHEDULED_AT.plusDays(1),
                List.of()
        );
        context.store.saveMeetingReport(report("report-allowed", allowedMeeting.meeting().id(), "허용된 회의 요약"));
        context.store.saveMeetingReport(report("report-denied", deniedMeeting.meeting().id(), "권한 밖 회의 요약"));
        context.store.saveProjectKnowledge(knowledge("knowledge-1", space.space().id(), KnowledgeStatus.PUBLISHED));
        context.store.saveProjectKnowledge(knowledge("knowledge-archived", space.space().id(), KnowledgeStatus.ARCHIVED));

        AiChatResponse response = context.service.chat(
                "Bearer access-token",
                space.space().id(),
                new BackendProjectAiChatRequest("  권한 정책은?  ")
        );

        assertThat(response.answer()).isEqualTo("응답");
        assertThat(context.gateway.captured.projectId()).isEqualTo(space.space().id());
        assertThat(context.gateway.captured.question()).isEqualTo("권한 정책은?");
        assertThat(context.gateway.captured.allowedMeetingIds()).containsExactly(allowedMeeting.meeting().id());
        assertThat(context.gateway.captured.sources())
                .extracting(ProjectAiGatewayChatRequest.SourceContext::sourceId)
                .containsExactlyInAnyOrder("knowledge-1", "report-allowed");
        assertThat(context.gateway.captured.sources())
                .extracting(ProjectAiGatewayChatRequest.SourceContext::sourceId)
                .doesNotContain("knowledge-archived", "report-denied");
        assertThat(context.gateway.captured.sources())
                .extracting(ProjectAiGatewayChatRequest.SourceContext::type)
                .containsExactlyInAnyOrder("projectKnowledge", "meetingSummary");
    }

    @Test
    void meetingGuestCannotUseProjectAiWithoutSpaceMembership() {
        TestContext context = newContext("user-guest");
        User owner = context.user("user-owner");
        User guest = context.user("user-guest");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "게스트 회의",
                SCHEDULED_AT,
                List.of()
        );
        context.store.addMeetingParticipant(meeting.meeting().id(), guest.id(), MeetingRole.VIEWER, ParticipantType.GUEST);

        assertThatThrownBy(() -> context.service.chat(
                "Bearer access-token",
                space.space().id(),
                new BackendProjectAiChatRequest("질문")
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
        assertThat(context.gateway.captured).isNull();
    }

    @Test
    void chatMapsAiGatewayFailureToContractError() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.gateway.failure = new AiGatewayException("boom");

        assertThatThrownBy(() -> context.service.chat(
                "Bearer access-token",
                space.space().id(),
                new BackendProjectAiChatRequest("질문")
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE"));
    }

    private TestContext newContext(String authUserId) {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(authUserId, authUserId + "@meetingmind.ai", authUserId, null, "active"));

        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        SpaceAccessPolicy spaceAccessPolicy = new SpaceAccessPolicy();
        WorkspaceDomainService workspace = new WorkspaceDomainService(store, spaceAccessPolicy, FIXED_CLOCK);
        FakeProjectAiGateway gateway = new FakeProjectAiGateway();
        ProjectAiService service = new ProjectAiService(
                authService,
                workspace,
                spaceAccessPolicy,
                new MeetingAccessPolicy(spaceAccessPolicy),
                gateway
        );
        return new TestContext(store, workspace, gateway, service);
    }

    private MeetingReport report(String id, String meetingId, String summary) {
        return new MeetingReport(
                id,
                meetingId,
                MeetingReportStatus.CONFIRMED,
                "회의록",
                summary,
                "## 요약\n" + summary,
                List.of(),
                List.of(),
                List.of(),
                "user-owner",
                1,
                true,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant()
        );
    }

    private ProjectKnowledge knowledge(String id, String spaceId, KnowledgeStatus status) {
        return new ProjectKnowledge(
                id,
                spaceId,
                KnowledgeType.MANUAL,
                "권한 정책",
                "Project AI는 권한 필터를 먼저 적용합니다.",
                null,
                "user-owner",
                status,
                EmbeddingStatus.COMPLETED,
                null,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant(),
                null
        );
    }

    private static void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(
            InMemoryWorkspaceStore store,
            WorkspaceDomainService workspace,
            FakeProjectAiGateway gateway,
            ProjectAiService service
    ) {
        User user(String id) {
            return store.saveUser(new User(
                    id,
                    id + "@meetingmind.ai",
                    id,
                    null,
                    "active",
                    FIXED_CLOCK.instant(),
                    FIXED_CLOCK.instant()
            ));
        }
    }

    private static class FakeProjectAiGateway implements ProjectAiGatewayClient {
        private ProjectAiGatewayChatRequest captured;
        private RuntimeException failure;

        @Override
        public AiChatResponse chat(ProjectAiGatewayChatRequest request) {
            captured = request;
            if (failure != null) {
                throw failure;
            }
            return new AiChatResponse(
                    "응답",
                    List.of(new AiSource("knowledge-1", "projectKnowledge", "권한 정책", "근거")),
                    false,
                    "test-model"
            );
        }
    }
}
