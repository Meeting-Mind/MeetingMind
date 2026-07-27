package com.meetingmind.demo.gateway;

import com.meetingmind.demo.dto.MeetingDialogueResponse;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStatusGatewayResponse;
import java.util.List;
import java.util.Optional;

/**
 * Core가 STT 기능을 사용하는 유일한 경로. 지금은 {@link InProcessTranscriptionGateway}가 같은 JVM 안의
 * SttSessionRegistry/LiveKitEgressService를 직접 호출하지만, STT가 별도 서비스로 분리되면 이 인터페이스의
 * 구현체만 내부 HTTP 클라이언트로 교체하면 되고 호출부(Controller)는 바뀌지 않는다.
 */
public interface TranscriptionGateway {

    TranscriptionHandle start(TranscriptionStartCommand command);

    String activeSessionId(String meetingId);

    void stop(String meetingId, String sessionId);

    List<MeetingDialogueResponse.Partial> partials(String meetingId);

    /**
     * 별도 STT 서비스가 transcript source of truth인 경우 authoritative snapshot을 반환한다.
     * in-process 호환 구현은 Core DB를 그대로 사용하므로 빈 값을 반환한다.
     */
    default Optional<MeetingTranscriptGatewayResponse> transcript(String meetingId) {
        return Optional.empty();
    }

    default Optional<TranscriptionStatusGatewayResponse> status(String meetingId) {
        return Optional.empty();
    }
}
