package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingTranscriptionControllerTest {

    @Test
    void marksTranscriptFailedWhenEgressStopFails() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, sessionRegistry, liveKitEgressService
        );
        AuthUserResponse user = new AuthUserResponse("host-1", "host@meetingmind.test", "Host", null, "active");

        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(sessionRegistry.belongsToMeeting("session-1", "meeting-1")).thenReturn(true);
        when(sessionRegistry.getEgressId("session-1")).thenReturn("egress-1");
        doThrow(new IllegalStateException("egress unavailable")).when(liveKitEgressService).stopEgress("egress-1");

        assertThatThrownBy(() -> controller.stop("Bearer token", "meeting-1", "session-1"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(exception -> {
                    AuthorizationException authorizationException = (AuthorizationException) exception;
                    assertThat(authorizationException.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(authorizationException.code()).isEqualTo("STT_PROVIDER_UNAVAILABLE");
                });

        verify(sessionRegistry).failAndClose("session-1");
    }
}
