package com.meetingmind.stt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meetingmind.stt.domain.TranscriptionSession;
import com.meetingmind.stt.domain.TranscriptionSessionStatus;
import com.meetingmind.stt.dto.ActiveSessionResponse;
import com.meetingmind.stt.dto.PartialResponse;
import com.meetingmind.stt.dto.StartTranscriptionRequest;
import com.meetingmind.stt.dto.TranscriptionStartResponse;
import com.meetingmind.stt.repository.TranscriptionSessionRepository;
import com.meetingmind.stt.service.LiveKitEgressService;
import com.meetingmind.stt.service.SttProvider;
import com.meetingmind.stt.service.SttSessionRegistry;
import com.meetingmind.stt.service.TranscriptionCoordinator;
import com.meetingmind.stt.websocket.EgressTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalTranscriptionControllerTest {

    private final SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
    private final TranscriptionCoordinator transcriptionCoordinator = mock(TranscriptionCoordinator.class);
    private final TranscriptionSessionRepository sessionRepository = mock(TranscriptionSessionRepository.class);
    private final LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
    private final EgressTokenService egressTokenService = mock(EgressTokenService.class);
    private final SttProvider sttProvider = mock(SttProvider.class);

    private final InternalTranscriptionController controller = new InternalTranscriptionController(
            sessionRegistry, transcriptionCoordinator, sessionRepository,
            liveKitEgressService, egressTokenService, sttProvider);

    @Test
    void startIsIdempotentForTheSameRequestId() {
        TranscriptionSession existing = new TranscriptionSession(
                "session-1", "meeting-1", "room-1", "track-1", "egress-1",
                TranscriptionSessionStatus.ACTIVE, "request-1", Instant.now(), Instant.now());
        when(sessionRepository.findByRequestId("request-1")).thenReturn(Optional.of(existing));

        TranscriptionStartResponse response = controller.start(
                new StartTranscriptionRequest("meeting-1", "room-1", "track-1", null, "request-1"));

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.egressId()).isEqualTo("egress-1");
        verifyNoInteractions(transcriptionCoordinator);
        verify(sessionRegistry, never()).createMeetingSession(any(), any(), any(), any());
    }

    @Test
    void stopReturns404WhenMeetingIdDoesNotMatchTheSession() {
        TranscriptionSession session = new TranscriptionSession(
                "session-1", "meeting-1", "room-1", "track-1", "egress-1",
                TranscriptionSessionStatus.ACTIVE, "request-1", Instant.now(), Instant.now());
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> controller.stop("session-1", "meeting-2"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void activeSessionReturnsTheRegistryLookup() {
        when(sessionRegistry.findActiveMeetingSessionId("meeting-1")).thenReturn("session-1");

        ActiveSessionResponse response = controller.activeSession("meeting-1");

        assertThat(response.sessionId()).isEqualTo("session-1");
    }

    @Test
    void partialsMapsRegistryPartialsToResponseDtos() {
        when(sessionRegistry.getMeetingPartials("meeting-1"))
                .thenReturn(List.of(new SttSessionRegistry.PartialTranscript("A", "hel")));

        List<PartialResponse> partials = controller.partials("meeting-1");

        assertThat(partials).hasSize(1);
        assertThat(partials.get(0).speakerLabel()).isEqualTo("A");
        assertThat(partials.get(0).text()).isEqualTo("hel");
    }
}
