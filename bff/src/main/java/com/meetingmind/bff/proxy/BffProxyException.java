package com.meetingmind.bff.proxy;

import org.springframework.http.HttpStatus;

public final class BffProxyException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private BffProxyException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    static BffProxyException routeNotAllowed() {
        return new BffProxyException(
                HttpStatus.NOT_FOUND,
                "ROUTE_NOT_ALLOWED",
                "요청한 API 경로를 사용할 수 없습니다.");
    }

    static BffProxyException unavailable(DownstreamService service) {
        return new BffProxyException(
                HttpStatus.SERVICE_UNAVAILABLE,
                service.unavailableCode(),
                service.unavailableMessage());
    }
}
