package com.meetingmind.demo.gateway;

import com.meetingmind.demo.dto.MeetingDialogueResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * NonProd 전환 전까지는 in-process 구현을 기본으로 쓰고, STT_GATEWAY_MODE=remote일 때만
 * HttpTranscriptionGateway로 바꾼다. Core의 기존 STT 코드가 아직 제거되지 않았으므로
 * (STT-서비스-분리-작업지시서 구현 순서 5번) 두 구현 모두 계속 존재한다.
 */
@Component
@Primary
class ConfiguredTranscriptionGateway implements TranscriptionGateway {

    private final TranscriptionGateway delegate;

    ConfiguredTranscriptionGateway(
            List<TranscriptionGateway> gateways,
            @Value("${meetingmind.stt.gateway-mode:in-process}") String mode
    ) {
        Map<String, TranscriptionGateway> byMode = gateways.stream().collect(Collectors.toMap(
                gateway -> gateway instanceof HttpTranscriptionGateway ? "remote" : "in-process",
                Function.identity()
        ));
        this.delegate = byMode.getOrDefault(mode, byMode.get("in-process"));
        if (this.delegate == null) {
            throw new IllegalStateException("TranscriptionGateway 구현체를 찾을 수 없습니다: " + mode);
        }
    }

    @Override
    public TranscriptionHandle start(TranscriptionStartCommand command) {
        return delegate.start(command);
    }

    @Override
    public String activeSessionId(String meetingId) {
        return delegate.activeSessionId(meetingId);
    }

    @Override
    public void stop(String meetingId, String sessionId) {
        delegate.stop(meetingId, sessionId);
    }

    @Override
    public List<MeetingDialogueResponse.Partial> partials(String meetingId) {
        return delegate.partials(meetingId);
    }
}
