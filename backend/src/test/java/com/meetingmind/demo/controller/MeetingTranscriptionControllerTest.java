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
import com.meetingmind.demo.domain.MeetingTranscript;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttSessionRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingTranscriptionControllerTest {

    @Test
    void returnsPersistedDialogueWhileTranscriptionIsProcessing() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, mock(SttSessionRegistry.class), mock(LiveKitEgressService.class)
        );
        AuthUserResponse user = new AuthUserResponse("viewer-1", "viewer@meetingmind.test", "Viewer", null, "active");
        Instant now = Instant.parse("2026-07-16T05:00:00Z");
        MeetingTranscript transcript = new MeetingTranscript(
                "meeting-1", TranscriptStatus.PROCESSING, "clova-nest", null,
                now, null, null, null, false, null, now, now
        );
        TranscriptSegment segment = new TranscriptSegment(
                "segment-1", "meeting-1", "speaker-1", "화자 1", "Host",
                0, 1_000, "실시간 자막입니다.", "stt", 0
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(workspaceDomainService.meetingTranscript(user.id(), "meeting-1"))
                .thenReturn(new WorkspaceDomainService.MeetingTranscriptView(transcript, List.of(segment)));

        var response = controller.dialogue("Bearer token", "meeting-1");

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.rows()).extracting(row -> row.text()).containsExactly("실시간 자막입니다.");
    }

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
