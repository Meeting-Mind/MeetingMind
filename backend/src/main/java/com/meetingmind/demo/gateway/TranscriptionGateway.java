package com.meetingmind.demo.gateway;

import com.meetingmind.demo.dto.MeetingDialogueResponse;
import java.util.List;

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
}
