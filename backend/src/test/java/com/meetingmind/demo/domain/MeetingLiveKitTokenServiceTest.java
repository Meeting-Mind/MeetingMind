package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import com.meetingmind.demo.dto.MeetingLiveKitTokenResponse;
import com.meetingmind.demo.service.LiveKitTokenService;
import com.meetingmind.demo.service.MeetingLiveKitTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingLiveKitTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-10T10:00:00+09:00");

    @Test
    void issueMeetingTokenUsesAuthenticatedUserAndMeetingRoom() {
        TestContext context = newContext("user-member", "팀원");
        User owner = context.user("user-owner");
        User member = context.user("user-member");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), member.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of(member.id())
        );

        MeetingLiveKitTokenResponse response = context.service.issueMeetingToken("Bearer access-token", meeting.meeting().id());

        assertThat(response.serverUrl()).isEqualTo("wss://livekit.example.test");
        assertThat(response.participantToken()).isEqualTo("signed-token");
        assertThat(response.roomName()).isEqualTo(meeting.meeting().id());
        assertThat(response.identity()).isEqualTo(member.id());
        assertThat(response.name()).isEqualTo("팀원");
        assertThat(response.expiresIn()).isEqualTo(LiveKitTokenService.TOKEN_EXPIRES_IN_SECONDS);
        verify(context.liveKit).issueToken(meeting.meeting().id(), member.id(), "팀원");
    }

    @Test
    void issueMeetingTokenRejectsUserWithoutMeetingParticipant() {
        TestContext context = newContext("user-outsider", "외부인");
        User owner = context.user("user-owner");
        User outsider = context.user("user-outsider");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(),
                space.space().id(),
                "API 구조 논의",
                SCHEDULED_AT,
                List.of()
        );

        assertThat(outsider.id()).isEqualTo("user-outsider");
        assertThatThrownBy(() -> context.service.issueMeetingToken("Bearer access-token", meeting.meeting().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void issueMeetingTokenReturnsNotFoundForUnknownMeeting() {
        TestContext context = newContext("user-member", "팀원");

        assertThatThrownBy(() -> context.service.issueMeetingToken("Bearer access-token", "meeting-missing"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND"));
    }

    @Test
    void issueMeetingTokenMapsLiveKitConfigFailureToContractError() {
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
        when(context.liveKit.issueToken(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("LIVEKIT_API_SECRET 환경변수가 설정되지 않았습니다."));

        assertThatThrownBy(() -> context.service.issueMeetingToken("Bearer access-token", meeting.meeting().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.SERVICE_UNAVAILABLE, "LIVEKIT_NOT_CONFIGURED"));
    }

    private TestContext newContext(String authUserId, String displayName) {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(authUserId, authUserId + "@meetingmind.ai", displayName, null, "active"));

        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        WorkspaceDomainService workspace = new WorkspaceDomainService(store, new SpaceAccessPolicy(), FIXED_CLOCK);
        LiveKitTokenService liveKit = mock(LiveKitTokenService.class);
        when(liveKit.issueToken(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> new LiveKitTokenResponse(
                        "wss://livekit.example.test",
                        "signed-token",
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));

        MeetingLiveKitTokenService service = new MeetingLiveKitTokenService(
                authService,
                workspace,
                new MeetingAccessPolicy(new SpaceAccessPolicy()),
                liveKit
        );
        return new TestContext(store, workspace, liveKit, service);
    }

    private void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(
            InMemoryWorkspaceStore store,
            WorkspaceDomainService workspace,
            LiveKitTokenService liveKit,
            MeetingLiveKitTokenService service
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
}
