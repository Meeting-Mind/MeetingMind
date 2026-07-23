package com.meetingmind.stt.service;

import com.meetingmind.stt.config.DotenvConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class ConfiguredSttProvider implements SttProvider {

    private final SttProvider primary;
    private final SttProvider fallback;

    ConfiguredSttProvider(List<SttProvider> providers) {
        String configured = DotenvConfig.optional("STT_PROVIDER").orElse("soniox-realtime");
        String fallbackConfigured = DotenvConfig.optional("STT_FALLBACK_PROVIDER").orElse("openai-realtime");
        Map<String, SttProvider> byId = providers.stream().collect(java.util.stream.Collectors.toMap(
                SttProvider::providerId,
                Function.identity()
        ));
        this.primary = byId.getOrDefault(configured, byId.get("clova-nest"));
        this.fallback = byId.get(fallbackConfigured);
        if (this.primary == null) {
            throw new IllegalStateException("기본 STT provider를 찾을 수 없습니다.");
        }
    }

    @Override
    public String providerId() {
        return primary.providerId();
    }

    @Override
    public SttStreamClient createClient(
            SttSessionContext context,
            java.util.function.Consumer<TranscriptEvent> onTranscriptEvent,
            java.util.function.Consumer<Throwable> onError
    ) {
        try {
            return primary.createClient(context, onTranscriptEvent, onError);
        } catch (RuntimeException primaryException) {
            if (fallback == null || fallback.providerId().equals(primary.providerId())) {
                throw primaryException;
            }
            try {
                return fallback.createClient(context, onTranscriptEvent, onError);
            } catch (RuntimeException fallbackException) {
                fallbackException.addSuppressed(primaryException);
                throw fallbackException;
            }
        }
    }
}
