package com.meetingmind.demo.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EgressAudioWebSocketHandler egressAudioWebSocketHandler;

    public WebSocketConfig(EgressAudioWebSocketHandler egressAudioWebSocketHandler) {
        this.egressAudioWebSocketHandler = egressAudioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(egressAudioWebSocketHandler, "/ws/egress-audio/*").setAllowedOrigins("*");
    }
}
