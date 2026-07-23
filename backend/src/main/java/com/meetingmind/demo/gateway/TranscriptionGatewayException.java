package com.meetingmind.demo.gateway;

public class TranscriptionGatewayException extends RuntimeException {

    public TranscriptionGatewayException(String message) {
        super(message);
    }

    public TranscriptionGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
