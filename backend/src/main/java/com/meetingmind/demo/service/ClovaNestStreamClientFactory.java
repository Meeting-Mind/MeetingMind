package com.meetingmind.demo.service;

import org.springframework.stereotype.Component;

@Component
class ClovaNestStreamClientFactory implements RawSttStreamClientFactory {

    @Override
    public SttStreamClient create(
            java.util.function.Consumer<SttTranscriptChunk> onFinalTranscript,
            java.util.function.Consumer<SttTranscriptChunk> onPartialTranscript,
            java.util.function.Consumer<Throwable> onError
    ) {
        return new ClovaNestStreamClient(onFinalTranscript, onPartialTranscript, onError);
    }
}
