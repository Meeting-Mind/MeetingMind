package com.meetingmind.demo.service;

import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
class ClovaNestStreamClientFactory implements SttStreamClientFactory {

    @Override
    public SttStreamClient create(Consumer<String> onTranscript, Consumer<Throwable> onError) {
        return new ClovaNestStreamClient(onTranscript, onError);
    }
}
