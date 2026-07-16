package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SttSessionRegistryTest {

    @Test
    void completesTargetTranscriptWhenEgressSocketCloses() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttStreamClient client = mock(SttStreamClient.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                factoryReturning(client)
        );

        String sessionId = registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        registry.onEgressClosed(sessionId);

        verify(client).finishAudio();
        verify(client).close();
        verify(workspaceDomainService).completeMeetingTranscript("meeting-1");
        assertThatCode(() -> registry.onEgressClosed(sessionId)).doesNotThrowAnyException();
    }

    @Test
    void preservesLegacySessionAfterEgressSocketCloses() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        SttStreamClient client = mock(SttStreamClient.class);
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                factoryReturning(client)
        );

        String sessionId = registry.create("legacy-room", "Host");
        registry.onEgressClosed(sessionId);

        verify(client).finishAudio();
        verifyNoInteractions(workspaceDomainService);
        assertThatCode(() -> registry.getSessionTranscript(sessionId)).doesNotThrowAnyException();
    }

    private static SttStreamClientFactory factoryReturning(SttStreamClient client) {
        return new SttStreamClientFactory() {
            @Override
            public SttStreamClient create(Consumer<String> onTranscript, Consumer<Throwable> onError) {
                return client;
            }
        };
    }
}
