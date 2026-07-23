package com.meetingmind.demo.gateway;

import com.meetingmind.demo.config.DotenvConfig;
import com.meetingmind.demo.dto.MeetingDialogueResponse;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttSessionRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * STT가 같은 JVM 안에서 SttSessionRegistry/LiveKitEgressService로 동작하던 현재 구현을
 * {@link TranscriptionGateway} 계약 뒤로 감싼 것. STT가 별도 서비스로 분리되면 이 클래스 대신
 * 내부 HTTP API를 호출하는 어댑터로 교체한다.
 */
@Component
public class InProcessTranscriptionGateway implements TranscriptionGateway {

    private static final Logger log = LoggerFactory.getLogger(InProcessTranscriptionGateway.class);

    private final SttSessionRegistry sessionRegistry;
    private final LiveKitEgressService liveKitEgressService;

    public InProcessTranscriptionGateway(SttSessionRegistry sessionRegistry, LiveKitEgressService liveKitEgressService) {
        this.sessionRegistry = sessionRegistry;
        this.liveKitEgressService = liveKitEgressService;
    }

    @Override
    public TranscriptionHandle start(TranscriptionStartCommand command) {
        String sessionId = null;
        try {
            sessionId = sessionRegistry.createMeetingSession(
                    command.meetingId(), command.roomName(), command.participantDisplayName(), command.trackId()
            );
            String publicWsBaseUrl = DotenvConfig.require("PUBLIC_WS_BASE_URL");
            String egressId = liveKitEgressService.startTrackEgress(
                    command.roomName(), command.trackId(), LiveKitEgressService.egressWebSocketUrl(publicWsBaseUrl, sessionId)
            );
            sessionRegistry.setEgressId(sessionId, egressId);
            return new TranscriptionHandle(sessionId, egressId);
        } catch (IllegalStateException exception) {
            if (sessionId != null) {
                sessionRegistry.failAndClose(sessionId);
            }
            log.warn(
                    "Failed to start meeting transcription. meetingId={} trackId={} sessionId={} reason={}",
                    command.meetingId(), command.trackId(), sessionId, exception.getMessage(), exception
            );
            throw new TranscriptionStartException(sessionId, exception);
        }
    }

    @Override
    public String activeSessionId(String meetingId) {
        return sessionRegistry.findActiveMeetingSessionId(meetingId);
    }

    @Override
    public void stop(String meetingId, String sessionId) {
        if (!sessionRegistry.belongsToMeeting(sessionId, meetingId)) {
            throw new TranscriptionSessionNotFoundException(sessionId);
        }
        String egressId = sessionRegistry.getEgressId(sessionId);
        try {
            if (egressId != null) {
                sessionRegistry.markStopping(sessionId);
                liveKitEgressService.stopEgress(egressId);
            }
        } catch (IllegalStateException exception) {
            sessionRegistry.failAndClose(sessionId);
            log.warn(
                    "Failed to stop meeting transcription. meetingId={} sessionId={} egressId={} reason={}",
                    meetingId, sessionId, egressId, exception.getMessage(), exception
            );
            throw new TranscriptionStopException(sessionId, exception);
        }
        sessionRegistry.close(sessionId);
    }

    @Override
    public List<MeetingDialogueResponse.Partial> partials(String meetingId) {
        return sessionRegistry.getMeetingPartials(meetingId).stream()
                .map(partial -> new MeetingDialogueResponse.Partial(partial.speakerLabel(), partial.speakerName(), partial.text()))
                .toList();
    }
}
