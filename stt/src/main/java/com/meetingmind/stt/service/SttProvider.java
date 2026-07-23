package com.meetingmind.stt.service;

import java.util.function.Consumer;

public interface SttProvider {

    String providerId();

    SttStreamClient createClient(
            SttSessionContext context,
            Consumer<TranscriptEvent> onTranscriptEvent,
            Consumer<Throwable> onError
    );
}
