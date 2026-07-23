package com.meetingmind.stt.websocket;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EgressTokenHandshakeInterceptor implements HandshakeInterceptor {

    private final EgressTokenService egressTokenService;

    public EgressTokenHandshakeInterceptor(EgressTokenService egressTokenService) {
        this.egressTokenService = egressTokenService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        List<String> tokenParam = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");
        String token = tokenParam == null || tokenParam.isEmpty() ? null : tokenParam.get(0);
        if (!egressTokenService.verify(sessionId, token)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }
}
