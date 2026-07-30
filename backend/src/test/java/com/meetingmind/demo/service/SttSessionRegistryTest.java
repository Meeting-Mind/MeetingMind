package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SttSessionRegistryTest {

    @BeforeEach
    void configurePublicWebSocketUrl() {
        System.setProperty("PUBLIC_WS_BASE_URL", "https://stt-test.example");
    }

    @AfterEach
    void clearPublicWebSocketUrl() {
        System.clearProperty("PUBLIC_WS_BASE_URL");
    }

    @Test
    void tracksPartialTranscriptUntilFinalArrives() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        Consumer<TranscriptEvent>[] captured = new Consumer[1];
        AtomicReference<SttSessionContext> context = new AtomicReference<>();
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                providerReturning((sessionContext, onEvent, onError) -> {
                    context.set(sessionContext);
                    captured[0] = onEvent;
                    return mock(SttStreamClient.class);
                }),
                liveKitEgressService,
                new InMemoryTranscriptAssembler()
        );

        registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        assertThat(registry.getMeetingPartials("meeting-1")).isEmpty();

        captured[0].accept(event(context.get(), TranscriptEventType.PARTIAL, "듣는", 1, 0, 200));
        captured[0].accept(event(context.get(), TranscriptEventType.PARTIAL, "중", 2, 200, 400));
        assertThat(registry.getMeetingPartials("meeting-1"))
                .extracting(SttSessionRegistry.PartialTranscript::text)
                .containsExactly("듣는중");

        captured[0].accept(event(context.get(), TranscriptEventType.FINAL, "확정된 문장입니다.", 3, 0, 0));
        assertThat(registry.getMeetingPartials("meeting-1")).isEmpty();
        verify(workspaceDomainService).appendTranscriptSegment(
                anyString(), anyString(), anyString(), anyInt(), anyInt(), anyString()
        );
    }

    @Test
    void fallsBackToLastPartialWhenFinalArrivesWithoutText() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        Consumer<TranscriptEvent>[] captured = new Consumer[1];
        AtomicReference<SttSessionContext> context = new AtomicReference<>();
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                providerReturning((sessionContext, onEvent, onError) -> {
                    context.set(sessionContext);
                    captured[0] = onEvent;
                    return mock(SttStreamClient.class);
                }),
                liveKitEgressService,
                new InMemoryTranscriptAssembler()
        );

        registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        captured[0].accept(event(context.get(), TranscriptEventType.PARTIAL, "여기까지", 1, 0, 200));
        captured[0].accept(event(context.get(), TranscriptEventType.PARTIAL, "들었습니다", 2, 200, 400));
        captured[0].accept(event(context.get(), TranscriptEventType.FINAL, "", 3, 0, 0));

        assertThat(registry.getMeetingPartials("meeting-1")).isEmpty();
        verify(workspaceDomainService).appendTranscriptSegment(
                eq("meeting-1"), anyString(), anyString(), anyInt(), anyInt(), eq("여기까지들었습니다")
        );
    }

    @Test
    void restartsMeetingEgressWhenSocketClosesUnexpectedly() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        SttStreamClient client = mock(SttStreamClient.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                factoryReturning(client),
                liveKitEgressService,
                new InMemoryTranscriptAssembler()
        );
        when(liveKitEgressService.startTrackEgress(eq("meeting-1"), eq("track-1"), anyString()))
                .thenReturn("egress-2");

        String previousBaseUrl = System.getProperty("PUBLIC_WS_BASE_URL");
        System.setProperty("PUBLIC_WS_BASE_URL", "https://stt.test.example");
        try {
            String sessionId = registry.createMeetingSession("meeting-1", "meeting-1", "Host");
            registry.setTrackId(sessionId, "track-1");
            registry.onEgressClosed(sessionId);

            verify(client, never()).finishAudio();
            verify(client, never()).close();
            verify(liveKitEgressService).startTrackEgress(eq("meeting-1"), eq("track-1"), anyString());
            verify(workspaceDomainService, never()).completeMeetingTranscript("meeting-1");
            assertThatCode(() -> registry.onEgressClosed(sessionId)).doesNotThrowAnyException();
        } finally {
            if (previousBaseUrl == null) {
                System.clearProperty("PUBLIC_WS_BASE_URL");
            } else {
                System.setProperty("PUBLIC_WS_BASE_URL", previousBaseUrl);
            }
        }
    }

    @Test
    void preservesLegacySessionAfterEgressSocketCloses() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        SttStreamClient client = mock(SttStreamClient.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                factoryReturning(client),
                liveKitEgressService,
                new InMemoryTranscriptAssembler()
        );

        String sessionId = registry.create("legacy-room", "Host");
        registry.onEgressClosed(sessionId);

        verify(client).finishAudio();
        verifyNoInteractions(workspaceDomainService);
        assertThatCode(() -> registry.getSessionTranscript(sessionId)).doesNotThrowAnyException();
    }

    @Test
    void completesMeetingTranscriptWhenSocketClosesAfterExplicitStop() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        SttStreamClient client = mock(SttStreamClient.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                factoryReturning(client),
                liveKitEgressService,
                new InMemoryTranscriptAssembler()
        );

        String sessionId = registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        registry.markStopping(sessionId);
        registry.onEgressClosed(sessionId);

        verify(client).finishAudio();
        verify(client).close();
        verify(workspaceDomainService).completeMeetingTranscript("meeting-1");
    }

    @Test
    void ignoresProviderErrorWhileStoppingAndCompletesTranscript() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        LiveKitEgressService liveKitEgressService = mock(LiveKitEgressService.class);
        AtomicReference<Consumer<Throwable>> capturedError = new AtomicReference<>();
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                providerReturning((context, onTranscriptEvent, onError) -> {
                    capturedError.set(onError);
                    return mock(SttStreamClient.class);
                }),
                liveKitEgressService,
                new InMemoryTranscriptAssembler()
        );

        String sessionId = registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        registry.markStopping(sessionId);
        capturedError.get().accept(new IllegalStateException("socket closed"));
        registry.close(sessionId);

        verify(workspaceDomainService, never()).failMeetingTranscript("meeting-1");
        verify(workspaceDomainService).completeMeetingTranscript("meeting-1");
    }

    @Test
    void completesTheSharedTranscriptOnlyAfterTheLastParticipantSessionCloses() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                providerReturning((context, onTranscriptEvent, onError) -> mock(SttStreamClient.class)),
                mock(LiveKitEgressService.class),
                new InMemoryTranscriptAssembler()
        );
        String hostSession = registry.createMeetingSession("meeting-1", "meeting-1", "Host", "track-host");
        String memberSession = registry.createMeetingSession("meeting-1", "meeting-1", "Member", "track-member");

        registry.close(hostSession);

        verify(workspaceDomainService, never()).completeMeetingTranscript("meeting-1");

        registry.close(memberSession);

        verify(workspaceDomainService).completeMeetingTranscript("meeting-1");
    }

    @Test
    void participantFailureDoesNotFailTheSharedTranscriptWhileAnotherSessionIsActive() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                providerReturning((context, onTranscriptEvent, onError) -> mock(SttStreamClient.class)),
                mock(LiveKitEgressService.class),
                new InMemoryTranscriptAssembler()
        );
        String hostSession = registry.createMeetingSession("meeting-1", "meeting-1", "Host", "track-host");
        String memberSession = registry.createMeetingSession("meeting-1", "meeting-1", "Member", "track-member");

        registry.failAndClose(memberSession);

        verify(workspaceDomainService, never()).failMeetingTranscript("meeting-1");

        registry.close(hostSession);

        verify(workspaceDomainService).completeMeetingTranscript("meeting-1");
    }

    @Test
    void failsTheSharedTranscriptWhenTheLastParticipantSessionFails() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                providerReturning((context, onTranscriptEvent, onError) -> mock(SttStreamClient.class)),
                mock(LiveKitEgressService.class),
                new InMemoryTranscriptAssembler()
        );
        String sessionId = registry.createMeetingSession("meeting-1", "meeting-1", "Host", "track-host");

        registry.failAndClose(sessionId);

        verify(workspaceDomainService).failMeetingTranscript("meeting-1");
    }

    private static SttProvider factoryReturning(SttStreamClient client) {
        return providerReturning((context, onTranscriptEvent, onError) -> client);
    }

    private static SttProvider providerReturning(EventStreamClientFactory factory) {
        return new SttProvider() {
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
                return factory.create(context, onTranscriptEvent, onError);
            }
        };
    }

    private static TranscriptEvent event(
            SttSessionContext context,
            TranscriptEventType type,
            String text,
            long sequence,
            long startMs,
            long endMs
    ) {
        return new TranscriptEvent(
                context.sessionId(), context.meetingId(), "test", "event-" + sequence, "segment-1", null, context.trackId(),
                type, text, sequence, startMs, endMs, null, type == TranscriptEventType.FINAL
        );
    }

    @FunctionalInterface
    private interface EventStreamClientFactory {
        SttStreamClient create(
                SttSessionContext context,
                Consumer<TranscriptEvent> onTranscriptEvent,
                Consumer<Throwable> onError
        );
    }
}
