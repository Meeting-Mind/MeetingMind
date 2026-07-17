package com.meetingmind.bff.proxy;

import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public record ProxyRequest(
        HttpMethod method,
        String path,
        MultiValueMap<String, String> query,
        String contentType,
        String accept,
        byte[] body) {

    public ProxyRequest {
        if (method == null || path == null || !path.startsWith("/api/v1/")) {
            throw new IllegalArgumentException("valid proxy method and path are required");
        }
        query = query == null ? new LinkedMultiValueMap<>() : new LinkedMultiValueMap<>(query);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
