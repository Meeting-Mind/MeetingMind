package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SttSessionRegistryTest {

    @Test
    void tracksPartialTranscriptUntilFinalArrives() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        Consumer<String>[] captured = new Consumer[2];
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                (onFinal, onPartial, onError) -> {
                    captured[0] = onFinal;
                    captured[1] = onPartial;
                    return mock(SttStreamClient.class);
                }
        );

        registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        assertThat(registry.getMeetingPartials("meeting-1")).isEmpty();

        captured[1].accept("듣는 중");
        assertThat(registry.getMeetingPartials("meeting-1"))
                .extracting(SttSessionRegistry.PartialTranscript::text)
                .containsExactly("듣는 중");

        captured[0].accept("확정된 문장입니다.");
        assertThat(registry.getMeetingPartials("meeting-1")).isEmpty();
        verify(workspaceDomainService).appendTranscriptSegment(
                anyString(), anyString(), anyString(), anyInt(), anyInt(), anyString()
        );
    }

    @Test
    void fallsBackToLastPartialWhenFinalArrivesWithoutText() {
        WorkspaceDomainService workspaceDomainService = mock(WorkspaceDomainService.class);
        Consumer<String>[] captured = new Consumer[2];
        SttSessionRegistry registry = new SttSessionRegistry(
                workspaceDomainService,
                (onFinal, onPartial, onError) -> {
                    captured[0] = onFinal;
                    captured[1] = onPartial;
                    return mock(SttStreamClient.class);
                }
        );

        registry.createMeetingSession("meeting-1", "meeting-1", "Host");
        captured[1].accept("여기까지 들었습니다");
        captured[0].accept("");

        assertThat(registry.getMeetingPartials("meeting-1")).isEmpty();
        verify(workspaceDomainService).appendTranscriptSegment(
                eq("meeting-1"), anyString(), anyString(), anyInt(), anyInt(), eq("여기까지 들었습니다")
        );
    }

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
            public SttStreamClient create(
                    Consumer<String> onFinalTranscript, Consumer<String> onPartialTranscript, Consumer<Throwable> onError) {
                return client;
            }
        };
    }
}
