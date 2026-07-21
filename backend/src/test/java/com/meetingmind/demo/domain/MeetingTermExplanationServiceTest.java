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
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayTermRequest;
import com.meetingmind.demo.dto.ai.TermExplanationResponse;
import com.meetingmind.demo.service.AiSearchScopeResolver;
import com.meetingmind.demo.service.MeetingAiGatewayClient;
import com.meetingmind.demo.service.MeetingTermExplanationService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingTermExplanationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-21T10:00:00+09:00");

    @Test
    void registeredTermReturnsDictionaryDefinitionWithoutCallingAi() {
        TestContext context = newContext("user-member");
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.workspaceStore.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "RAG 설계", SCHEDULED_AT, List.of(member.id())
        );
        context.termStore.save(new DomainTerm(
                "term-1", space.space().id(), "pgvector", "PostgreSQL 벡터 검색 확장입니다.",
                DomainTermStatus.ACTIVE, FIXED_CLOCK.instant(), FIXED_CLOCK.instant(), null
        ));

        TermExplanationResponse response = context.service.explain(
                "Bearer access-token", meeting.meeting().id(), " PGVECTOR "
        );

        assertThat(response.term()).isEqualTo("PGVECTOR");
        assertThat(response.explanation()).isEqualTo("PostgreSQL 벡터 검색 확장입니다.");
        assertThat(response.sourceType()).isEqualTo("glossary");
        assertThat(response.model()).isEqualTo("local-glossary");
        assertThat(context.gateway.termRequest).isNull();
    }

    @Test
    void unregisteredTermSendsOnlyAuthorizedMeetingScopeToAi() {
        TestContext context = newContext("user-member");
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.workspaceStore.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "RAG 설계", SCHEDULED_AT, List.of(member.id())
        );

        TermExplanationResponse response = context.service.explain(
                "Bearer access-token", meeting.meeting().id(), " RAG "
        );

        assertThat(response.explanation()).isEqualTo("회의 검색 방식입니다.");
        assertThat(context.gateway.termRequest).isEqualTo(new MeetingAiGatewayTermRequest(
                space.space().id(), meeting.meeting().id(), "RAG"
        ));
    }

    @Test
    void deniedMeetingAccessDoesNotReachAiGateway() {
        TestContext context = newContext("user-outsider");
        User owner = context.user("user-owner");
        context.user("user-outsider");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "RAG 설계", SCHEDULED_AT, List.of()
        );

        assertThatThrownBy(() -> context.service.explain("Bearer access-token", meeting.meeting().id(), "RAG"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> {
                    AuthorizationException exception = (AuthorizationException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("MEETING_ACCESS_DENIED");
                });
        assertThat(context.gateway.termRequest).isNull();
    }

    private TestContext newContext(String authUserId) {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(authUserId, authUserId + "@meetingmind.ai", authUserId, null, "active"));
        InMemoryWorkspaceStore workspaceStore = new InMemoryWorkspaceStore();
        WorkspaceDomainService workspace = new WorkspaceDomainService(workspaceStore, new SpaceAccessPolicy(), FIXED_CLOCK);
        InMemoryDomainTermStore termStore = new InMemoryDomainTermStore();
        FakeGateway gateway = new FakeGateway();
        MeetingTermExplanationService service = new MeetingTermExplanationService(
                authService,
                new AiSearchScopeResolver(workspace, new MeetingAccessPolicy(new SpaceAccessPolicy())),
                termStore,
                gateway
        );
        return new TestContext(workspaceStore, workspace, termStore, gateway, service);
    }

    private record TestContext(
            InMemoryWorkspaceStore workspaceStore,
            WorkspaceDomainService workspace,
            InMemoryDomainTermStore termStore,
            FakeGateway gateway,
            MeetingTermExplanationService service
    ) {
        User user(String id) {
            return workspaceStore.saveUser(new User(
                    id, id + "@meetingmind.ai", id, null, "active", FIXED_CLOCK.instant(), FIXED_CLOCK.instant()
            ));
        }
    }

    private static class FakeGateway implements MeetingAiGatewayClient {
        private MeetingAiGatewayTermRequest termRequest;

        @Override
        public AiChatResponse chat(MeetingAiGatewayChatRequest request) {
            throw new UnsupportedOperationException("Chat is not used by this test.");
        }

        @Override
        public TermExplanationResponse explainTerm(MeetingAiGatewayTermRequest request) {
            termRequest = request;
            return new TermExplanationResponse(
                    request.term(),
                    "회의 검색 방식입니다.",
                    "transcript",
                    List.of(new AiSource("segment-1", "transcript", "RAG 설계", "RAG 검색을 구성합니다.")),
                    false,
                    null,
                    "test-model"
            );
        }
    }
}
