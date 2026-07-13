package com.meetingmind.demo.service;

public class AiGatewayException extends RuntimeException {

    public AiGatewayException(String message) {
        super(message);
    }

    public AiGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
