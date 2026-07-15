package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.AiSource;
import com.meetingmind.demo.dto.ai.BackendMeetingAiChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import com.meetingmind.demo.service.AiGatewayException;
import com.meetingmind.demo.service.AiSearchScopeResolver;
import com.meetingmind.demo.service.MeetingAiGatewayClient;
import com.meetingmind.demo.service.MeetingAiService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingAiServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-10T10:00:00+09:00");

    @Test
    void chatChecksMeetingAccessAndSendsScopeToAiGateway() {
        TestContext context = newContext("user-member", "팀원");
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                " Sprint Planning #12 ",
                SCHEDULED_AT,
                List.of(member.id())
        );
        MeetingSpeaker speaker = context.store.addMeetingSpeaker(meeting.meeting().id(), "S1", "김진수", FIXED_CLOCK.instant());
        context.store.addTranscriptSegment(
                meeting.meeting().id(),
                speaker.id(),
                speaker.label(),
                speaker.displayName(),
                65000,
                70000,
                "ERD 수정안 문서화가 필요합니다.",
                "STT",
                1
        );
        context.store.saveMeetingReport(new MeetingReport(
                "report-1",
                meeting.meeting().id(),
                MeetingReportStatus.CONFIRMED,
                "Sprint Planning #12 회의록",
                "회의 요약",
                "## 회의 요약",
                List.of(new MeetingReport.ReportDecision("decision-1", "ERD 수정", "회의별 ACL을 분리한다.", List.of("segment-1"))),
                List.of(new MeetingReport.ReportActionItem("action-1", "ERD 수정안 문서화", member.id(), null, List.of("segment-1"))),
                List.of("segment-1"),
                owner.id(),
                1,
                true,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant()
        ));

        AiChatResponse response = context.service.chat(
                "Bearer access-token",
                meeting.meeting().id(),
                new BackendMeetingAiChatRequest("  후속 작업이 뭐야?  ")
        );

        assertThat(response.answer()).isEqualTo("응답");
        assertThat(context.gateway.captured.projectId()).isEqualTo(space.space().id());
        assertThat(context.gateway.captured.meetingId()).isEqualTo(meeting.meeting().id());
        assertThat(context.gateway.captured.question()).isEqualTo("후속 작업이 뭐야?");
    }

    @Test
    void chatRejectsUserWithoutMeetingAccessBeforeAiGatewayCall() {
        TestContext context = newContext("user-outsider", "외부인");
        User owner = context.user("user-owner");
        context.user("user-outsider");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of()
        );

        assertThatThrownBy(() -> context.service.chat(
                "Bearer access-token",
                meeting.meeting().id(),
                new BackendMeetingAiChatRequest("질문")
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
        assertThat(context.gateway.captured).isNull();
    }

    @Test
    void chatMapsAiGatewayFailureToContractError() {
        TestContext context = newContext("user-owner", "오너");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of()
        );
        context.gateway.failure = new AiGatewayException("boom");

        assertThatThrownBy(() -> context.service.chat(
                "Bearer access-token",
                meeting.meeting().id(),
                new BackendMeetingAiChatRequest("질문")
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE"));
    }

    private TestContext newContext(String authUserId, String displayName) {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(authUserId, authUserId + "@meetingmind.ai", displayName, null, "active"));

        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        WorkspaceDomainService workspace = new WorkspaceDomainService(store, new SpaceAccessPolicy(), FIXED_CLOCK);
        FakeMeetingAiGateway gateway = new FakeMeetingAiGateway();
        MeetingAiService service = new MeetingAiService(
                authService,
                new AiSearchScopeResolver(
                        workspace,
                        new MeetingAccessPolicy(new SpaceAccessPolicy())
                ),
                gateway
        );
        return new TestContext(store, workspace, gateway, service);
    }

    private static void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(
            InMemoryWorkspaceStore store,
            WorkspaceDomainService workspace,
            FakeMeetingAiGateway gateway,
            MeetingAiService service
    ) {
        User user(String id) {
            User user = new User(
                    id,
                    id + "@meetingmind.ai",
                    id,
                    null,
                    "active",
                    FIXED_CLOCK.instant(),
                    FIXED_CLOCK.instant()
            );
            return store.saveUser(user);
        }
    }

    private static class FakeMeetingAiGateway implements MeetingAiGatewayClient {
        private MeetingAiGatewayChatRequest captured;
        private RuntimeException failure;

        @Override
        public AiChatResponse chat(MeetingAiGatewayChatRequest request) {
            captured = request;
            if (failure != null) {
                throw failure;
            }
            return new AiChatResponse(
                    "응답",
                    List.of(new AiSource("source-1", "transcript", "Sprint Planning #12", "근거")),
                    false,
                    "test-model"
            );
        }
    }
}
