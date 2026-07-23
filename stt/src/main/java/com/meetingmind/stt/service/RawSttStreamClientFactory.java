package com.meetingmind.stt.service;

import java.util.function.Consumer;

interface RawSttStreamClientFactory {

    SttStreamClient create(
            Consumer<SttTranscriptChunk> onFinalTranscript,
            Consumer<SttTranscriptChunk> onPartialTranscript,
            Consumer<Throwable> onError
    );
}
