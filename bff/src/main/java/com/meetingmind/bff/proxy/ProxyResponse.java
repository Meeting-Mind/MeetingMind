package com.meetingmind.bff.proxy;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

public record ProxyResponse(
        HttpStatusCode status,
        MediaType contentType,
        String cacheControl,
        String etag,
        String contentDisposition,
        byte[] body) {

    public ProxyResponse {
        if (status == null) {
            throw new IllegalArgumentException("proxy response status is required");
        }
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
