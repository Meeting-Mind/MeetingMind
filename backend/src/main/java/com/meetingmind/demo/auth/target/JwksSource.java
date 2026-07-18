package com.meetingmind.demo.auth.target;

import java.time.Duration;

public interface JwksSource {

    Response fetch(String etag);

    record Response(boolean notModified, String body, String etag, Duration maxAge) {
        public Response {
            if (maxAge == null || maxAge.isNegative()) {
                throw new IllegalArgumentException("JWKS maxAge가 올바르지 않습니다.");
            }
            if (!notModified && (body == null || body.isBlank())) {
                throw new IllegalArgumentException("JWKS body가 필요합니다.");
            }
        }
    }
}
