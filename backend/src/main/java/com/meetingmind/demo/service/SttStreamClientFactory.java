package com.meetingmind.demo.service;

import java.util.function.Consumer;

public interface SttStreamClientFactory {

    SttStreamClient create(Consumer<String> onFinalTranscript, Consumer<String> onPartialTranscript, Consumer<Throwable> onError);
}
