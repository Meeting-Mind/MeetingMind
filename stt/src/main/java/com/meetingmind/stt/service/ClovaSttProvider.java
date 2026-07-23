package com.meetingmind.stt.service;

import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
class ClovaSttProvider implements SttProvider {

    private final RawSttStreamClientFactory rawFactory;

    ClovaSttProvider(ClovaNestStreamClientFactory rawFactory) {
        this.rawFactory = rawFactory;
    }

    @Override
    public String providerId() {
        return "clova-nest";
    }

    @Override
    public SttStreamClient createClient(
            SttSessionContext context,
            Consumer<TranscriptEvent> onTranscriptEvent,
            Consumer<Throwable> onError
    ) {
        SttTranscriptEventMapper mapper = new SttTranscriptEventMapper(providerId(), context);
        Consumer<SttTranscriptChunk> onChunk = chunk -> onTranscriptEvent.accept(mapper.map(chunk));
        return new SilenceSegmentingSttStreamClient(rawFactory, onChunk, onChunk, onError);
    }
}
