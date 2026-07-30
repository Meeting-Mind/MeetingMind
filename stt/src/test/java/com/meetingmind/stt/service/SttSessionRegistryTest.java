package com.meetingmind.stt.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.stt.domain.TranscriptionSession;
import com.meetingmind.stt.domain.TranscriptionSessionStatus;
import com.meetingmind.stt.repository.TranscriptionSessionRepository;
import com.meetingmind.stt.websocket.EgressTokenService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SttSessionRegistryTest {

    @Test
    void completesTheSharedTranscriptOnlyAfterTheLastParticipantSessionCloses() {
        RepositoryFixture repository = new RepositoryFixture();
        TranscriptionCoordinator coordinator = mock(TranscriptionCoordinator.class);
        SttSessionRegistry registry = registry(coordinator, repository.repository());
        String hostSession = registry.createMeetingSession(
                "meeting-1", "room-1", "track-host", "request-host", "Host");
        String memberSession = registry.createMeetingSession(
                "meeting-1", "room-1", "track-member", "request-member", "Member");

        registry.close(hostSession);

        verify(coordinator, never()).completeTranscript("meeting-1");

        registry.close(memberSession);

        verify(coordinator).completeTranscript("meeting-1");
    }

    @Test
    void participantFailureDoesNotFailTheSharedTranscriptWhileAnotherSessionIsActive() {
        RepositoryFixture repository = new RepositoryFixture();
        TranscriptionCoordinator coordinator = mock(TranscriptionCoordinator.class);
        SttSessionRegistry registry = registry(coordinator, repository.repository());
        String hostSession = registry.createMeetingSession(
                "meeting-1", "room-1", "track-host", "request-host", "Host");
        String memberSession = registry.createMeetingSession(
                "meeting-1", "room-1", "track-member", "request-member", "Member");

        registry.failAndClose(memberSession);

        verify(coordinator, never()).failTranscript("meeting-1");

        registry.close(hostSession);

        verify(coordinator).completeTranscript("meeting-1");
    }

    @Test
    void failsTheSharedTranscriptWhenTheLastParticipantSessionFails() {
        RepositoryFixture repository = new RepositoryFixture();
        TranscriptionCoordinator coordinator = mock(TranscriptionCoordinator.class);
        SttSessionRegistry registry = registry(coordinator, repository.repository());
        String sessionId = registry.createMeetingSession(
                "meeting-1", "room-1", "track-host", "request-host", "Host");

        registry.failAndClose(sessionId);

        verify(coordinator).failTranscript("meeting-1");
    }

    @Test
    void crossPodCloseUsesPersistedSessionsBeforeCompletingTheSharedTranscript() {
        RepositoryFixture repository = new RepositoryFixture();
        TranscriptionCoordinator coordinator = mock(TranscriptionCoordinator.class);
        SttSessionRegistry registry = registry(coordinator, repository.repository());
        Instant now = Instant.now();
        repository.put(session("session-host", "meeting-1", TranscriptionSessionStatus.STOPPING, now));
        repository.put(session("session-member", "meeting-1", TranscriptionSessionStatus.ACTIVE, now));

        registry.close("session-host");

        verify(coordinator, never()).completeTranscript("meeting-1");

        registry.close("session-member");

        verify(coordinator).completeTranscript("meeting-1");
    }

    @Test
    void staleSessionFailureDoesNotFailTheSharedTranscriptWhileAnotherSessionIsActive() {
        RepositoryFixture repository = new RepositoryFixture();
        TranscriptionCoordinator coordinator = mock(TranscriptionCoordinator.class);
        SttSessionRegistry registry = registry(coordinator, repository.repository());
        Instant now = Instant.now();
        repository.put(session(
                "session-stale", "meeting-1", TranscriptionSessionStatus.ACTIVE, now.minusSeconds(120)));
        repository.put(session("session-live", "meeting-1", TranscriptionSessionStatus.ACTIVE, now));

        registry.reapStaleSessions();

        verify(coordinator, never()).failTranscript("meeting-1");
    }

    private static SttSessionRegistry registry(
            TranscriptionCoordinator coordinator,
            TranscriptionSessionRepository repository
    ) {
        SttProvider provider = new SttProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public SttStreamClient createClient(
                    SttSessionContext context,
                    Consumer<TranscriptEvent> onTranscriptEvent,
                    Consumer<Throwable> onError
            ) {
                return mock(SttStreamClient.class);
            }
        };
        return new SttSessionRegistry(
                coordinator,
                repository,
                provider,
                mock(LiveKitEgressService.class),
                mock(EgressTokenService.class),
                new InMemoryTranscriptAssembler()
        );
    }

    private static TranscriptionSession session(
            String sessionId,
            String meetingId,
            TranscriptionSessionStatus status,
            Instant updatedAt
    ) {
        return new TranscriptionSession(
                sessionId, meetingId, "room-1", "track-1", "egress-1",
                status, "request-" + sessionId, updatedAt, updatedAt
        );
    }

    private static final class RepositoryFixture {
        private final Map<String, TranscriptionSession> rows = new LinkedHashMap<>();
        private final TranscriptionSessionRepository repository = mock(TranscriptionSessionRepository.class);

        private RepositoryFixture() {
            when(repository.save(any(TranscriptionSession.class))).thenAnswer(invocation -> {
                TranscriptionSession row = invocation.getArgument(0);
                rows.put(row.sessionId(), row);
                return row;
            });
            when(repository.findById(anyString())).thenAnswer(invocation ->
                    Optional.ofNullable(rows.get(invocation.getArgument(0))));
            when(repository.findAll()).thenAnswer(ignored -> new ArrayList<>(rows.values()));
            when(repository.findByMeetingIdAndStatusIn(anyString(), anyList())).thenAnswer(invocation -> {
                String meetingId = invocation.getArgument(0);
                List<String> statuses = invocation.getArgument(1);
                return rows.values().stream()
                        .filter(row -> meetingId.equals(row.meetingId()))
                        .filter(row -> statuses.contains(row.status().name()))
                        .toList();
            });
        }

        private TranscriptionSessionRepository repository() {
            return repository;
        }

        private void put(TranscriptionSession row) {
            rows.put(row.sessionId(), row);
        }
    }
}
