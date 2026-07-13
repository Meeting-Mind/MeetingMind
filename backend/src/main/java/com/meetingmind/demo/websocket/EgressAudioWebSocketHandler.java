package com.meetingmind.demo.websocket;

import com.meetingmind.demo.service.ClovaNestStreamClient;
import com.meetingmind.demo.service.PcmResampler;
import com.meetingmind.demo.service.SttSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Component
public class EgressAudioWebSocketHandler extends BinaryWebSocketHandler {

    private final SttSessionRegistry sessionRegistry;

    public EgressAudioWebSocketHandler(SttSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ClovaNestStreamClient client = sessionRegistry.getStreamClient(extractSessionId(session));
        if (client == null) {
            return;
        }

        byte[] pcm48k = new byte[message.getPayloadLength()];
        message.getPayload().get(pcm48k);
        client.sendAudio(PcmResampler.downsample48kTo16kMono(pcm48k));
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
