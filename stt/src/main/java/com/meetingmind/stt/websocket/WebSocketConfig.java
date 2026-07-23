package com.meetingmind.stt.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EgressAudioWebSocketHandler egressAudioWebSocketHandler;
    private final EgressTokenHandshakeInterceptor egressTokenHandshakeInterceptor;

    public WebSocketConfig(
            EgressAudioWebSocketHandler egressAudioWebSocketHandler,
            EgressTokenHandshakeInterceptor egressTokenHandshakeInterceptor) {
        this.egressAudioWebSocketHandler = egressAudioWebSocketHandler;
        this.egressTokenHandshakeInterceptor = egressTokenHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // ponytail: LiveKit egress worker is a backend service, not a browser — Origin
        // checks don't authenticate it. The one-time signed token (query param) does.
        registry.addHandler(egressAudioWebSocketHandler, "/ws/egress-audio/*")
                .addInterceptors(egressTokenHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
