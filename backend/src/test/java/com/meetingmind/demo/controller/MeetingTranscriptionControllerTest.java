package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.MeetingTranscript;
import com.meetingmind.demo.domain.TranscriptSegment;
import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.gateway.InProcessTranscriptionGateway;
import com.meetingmind.demo.gateway.TranscriptionGateway;
import com.meetingmind.demo.gateway.TranscriptionHandle;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStatusGatewayResponse;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttProvider;
import com.meetingmind.demo.service.SttSessionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class MeetingTranscriptionControllerTest {

    @BeforeEach
    void configurePublicWebSocketUrl() {
        System.setProperty("PUBLIC_WS_BASE_URL", "https://stt-test.example");
    }

    @AfterEach
    void clearPublicWebSocketUrl() {
        System.clearProperty("PUBLIC_WS_BASE_URL");
    }

    @Test
    void startsInstantMeetingTranscriptionAgainstResolvedRoomName() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        SttProvider sttProvider = mock(SttProvider.class);
        TranscriptionGateway transcriptionGateway = new InProcessTranscriptionGateway(sessionRegistry, liveKitEgressService);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, transcriptionGateway, sttProvider
        );
        AuthUserResponse user = new AuthUserResponse("host-1", "host@meetingmind.test", "Host", null, "active");
        Instant now = Instant.parse("2026-07-23T03:00:00Z");

        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(sttProvider.providerId()).thenReturn("soniox-realtime");
        when(workspaceDomainService.startMeetingTranscript("host-1", "meeting-1", "soniox-realtime"))
                .thenReturn(new MeetingTranscript(
                        "meeting-1", TranscriptStatus.PROCESSING, "soniox-realtime", null,
                        now, null, null, null, false, null, now, now
                ));
        when(workspaceDomainService.meetingRoomName("meeting-1")).thenReturn("space-room-space-1");
        when(sessionRegistry.createMeetingSession("meeting-1", "space-room-space-1", "Host", "track-1"))
                .thenReturn("session-1");
        when(liveKitEgressService.startTrackEgress(eq("space-room-space-1"), eq("track-1"), anyString()))
                .thenReturn("egress-1");

        var response = controller.start(
                "Bearer token",
                "meeting-1",
                new com.meetingmind.demo.dto.StartMeetingTranscriptionRequest("realtime", "track-1")
        );

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.egressId()).isEqualTo("egress-1");
        verify(sessionRegistry).createMeetingSession("meeting-1", "space-room-space-1", "Host", "track-1");
        ArgumentCaptor<String> websocketUrl = ArgumentCaptor.forClass(String.class);
        verify(liveKitEgressService, times(1)).startTrackEgress(
                org.mockito.ArgumentMatchers.eq("space-room-space-1"),
                org.mockito.ArgumentMatchers.eq("track-1"),
                websocketUrl.capture()
        );
        assertThat(websocketUrl.getValue()).contains("/ws/egress-audio/session-1");
    }

    @Test
    void resumesACompletedRemoteTranscriptWithTheNewMicrophoneTrackAfterRejoin() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        TranscriptionGateway transcriptionGateway = mock(TranscriptionGateway.class);
        SttProvider sttProvider = mock(SttProvider.class);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, transcriptionGateway, sttProvider
        );
        AuthUserResponse user = new AuthUserResponse("host-1", "host@meetingmind.test", "Host", null, "active");
        Instant startedAt = Instant.parse("2026-07-27T03:00:00Z");
        MeetingTranscript resumed = new MeetingTranscript(
                "meeting-1", TranscriptStatus.PROCESSING, "soniox-realtime", "ko-KR",
                startedAt, null, null, null, false, null, startedAt, startedAt
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(sttProvider.providerId()).thenReturn("soniox-realtime");
        when(transcriptionGateway.status("meeting-1")).thenReturn(Optional.of(
                new TranscriptionStatusGatewayResponse("meeting-1", TranscriptStatus.COMPLETED)
        ));
        when(workspaceDomainService.resumeMeetingTranscript("host-1", "meeting-1", "soniox-realtime"))
                .thenReturn(resumed);
        when(workspaceDomainService.meetingRoomName("meeting-1")).thenReturn("space-room-space-1");
        when(transcriptionGateway.start(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TranscriptionHandle("session-2", "egress-2"));

        var response = controller.start(
                "Bearer token",
                "meeting-1",
                new com.meetingmind.demo.dto.StartMeetingTranscriptionRequest("realtime", "track-2")
        );

        assertThat(response.sessionId()).isEqualTo("session-2");
        verify(workspaceDomainService).requireTranscriptManagement("host-1", "meeting-1");
        verify(workspaceDomainService).resumeMeetingTranscript("host-1", "meeting-1", "soniox-realtime");
    }

    @Test
    void returnsPersistedDialogueWhileTranscriptionIsProcessing() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        TranscriptionGateway transcriptionGateway = new InProcessTranscriptionGateway(
                mock(SttSessionRegistry.class), mock(LiveKitEgressService.class)
        );
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService,
                workspaceDomainService,
                transcriptionGateway,
                mock(SttProvider.class)
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
    void returnsAuthoritativeRemoteDialogueWhenSttRunsAsASeparateService() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        TranscriptionGateway transcriptionGateway = mock(TranscriptionGateway.class);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService,
                workspaceDomainService,
                transcriptionGateway,
                mock(SttProvider.class)
        );
        AuthUserResponse user = new AuthUserResponse("viewer-1", "viewer@meetingmind.test", "Viewer", null, "active");
        Instant now = Instant.parse("2026-07-27T02:00:00Z");
        MeetingTranscript coreTranscript = new MeetingTranscript(
                "meeting-1", TranscriptStatus.PROCESSING, "soniox-realtime", null,
                now, null, null, null, false, null, now, now
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(workspaceDomainService.meetingTranscript(user.id(), "meeting-1"))
                .thenReturn(new WorkspaceDomainService.MeetingTranscriptView(coreTranscript, List.of()));
        when(transcriptionGateway.transcript("meeting-1")).thenReturn(Optional.of(
                new MeetingTranscriptGatewayResponse(
                        "meeting-1",
                        TranscriptStatus.PROCESSING,
                        List.of(new MeetingTranscriptGatewayResponse.Segment(
                                "segment-remote-1", "speaker-1", "stt-session-1", "Viewer",
                                100, 900, "STT 서비스에 저장된 자막입니다."
                        ))
                )
        ));
        when(transcriptionGateway.partials("meeting-1")).thenReturn(List.of());

        var response = controller.dialogue("Bearer token", "meeting-1");

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.rows()).extracting(row -> row.text())
                .containsExactly("STT 서비스에 저장된 자막입니다.");
        verify(workspaceDomainService).meetingTranscript(user.id(), "meeting-1");
        verify(transcriptionGateway).transcript("meeting-1");
    }

    @Test
    void marksTranscriptFailedWhenEgressStopFails() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        SttProvider sttProvider = mock(SttProvider.class);
        TranscriptionGateway transcriptionGateway = new InProcessTranscriptionGateway(sessionRegistry, liveKitEgressService);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, transcriptionGateway, sttProvider
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

    @Test
    void projectsAuthoritativeRemoteSnapshotAfterStop() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        TranscriptionGateway transcriptionGateway = mock(TranscriptionGateway.class);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, transcriptionGateway, mock(SttProvider.class)
        );
        AuthUserResponse user = new AuthUserResponse("host-1", "host@meetingmind.test", "Host", null, "active");
        Instant now = Instant.parse("2026-07-27T03:00:00Z");
        MeetingTranscript completed = new MeetingTranscript(
                "meeting-1", TranscriptStatus.COMPLETED, "soniox-realtime", "ko-KR",
                now, now, null, null, false, null, now, now
        );
        MeetingTranscriptGatewayResponse snapshot = new MeetingTranscriptGatewayResponse(
                "meeting-1",
                TranscriptStatus.COMPLETED,
                List.of(new MeetingTranscriptGatewayResponse.Segment(
                        "segment-stt-1", "speaker-stt-1", "화자 1", "Host",
                        100, 900, "STT 원본 전사"
                ))
        );
        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(transcriptionGateway.transcript("meeting-1")).thenReturn(Optional.of(snapshot));
        when(workspaceDomainService.projectRemoteMeetingTranscript(
                eq(user.id()), eq("meeting-1"), eq(TranscriptStatus.COMPLETED), org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(completed);

        var response = controller.stop("Bearer token", "meeting-1", "session-1");

        assertThat(response.transcriptStatus()).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WorkspaceDomainService.RemoteTranscriptSegment>> segments =
                ArgumentCaptor.forClass(List.class);
        verify(workspaceDomainService).projectRemoteMeetingTranscript(
                eq(user.id()), eq("meeting-1"), eq(TranscriptStatus.COMPLETED), segments.capture()
        );
        assertThat(segments.getValue())
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.id()).isEqualTo("segment-stt-1");
                    assertThat(segment.speakerId()).isEqualTo("speaker-stt-1");
                    assertThat(segment.text()).isEqualTo("STT 원본 전사");
                });
    }

    @Test
    void stopsActiveMeetingSessionWhenBrowserSessionIdIsUnavailable() {
        AuthService authService = mock(AuthService.class);
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        TranscriptionGateway transcriptionGateway = new InProcessTranscriptionGateway(sessionRegistry, liveKitEgressService);
        MeetingTranscriptionController controller = new MeetingTranscriptionController(
                authService, workspaceDomainService, transcriptionGateway, mock(SttProvider.class)
        );
        AuthUserResponse user = new AuthUserResponse("host-1", "host@meetingmind.test", "Host", null, "active");
        Instant now = Instant.parse("2026-07-23T03:00:00Z");
        MeetingTranscript completed = new MeetingTranscript(
                "meeting-1", TranscriptStatus.COMPLETED, "soniox-realtime", "ko",
                now, now, null, null, false, null, now, now
        );

        when(authService.currentUser("Bearer token")).thenReturn(user);
        when(sessionRegistry.findActiveMeetingSessionId("meeting-1")).thenReturn("session-1");
        when(sessionRegistry.belongsToMeeting("session-1", "meeting-1")).thenReturn(true);
        when(workspaceDomainService.meetingTranscript(user.id(), "meeting-1"))
                .thenReturn(new WorkspaceDomainService.MeetingTranscriptView(completed, List.of()));

        var response = controller.stopActive("Bearer token", "meeting-1");

        assertThat(response.transcriptStatus()).isEqualTo("COMPLETED");
        verify(workspaceDomainService).requireTranscriptManagement(user.id(), "meeting-1");
        verify(sessionRegistry).close("session-1");
    }
}
