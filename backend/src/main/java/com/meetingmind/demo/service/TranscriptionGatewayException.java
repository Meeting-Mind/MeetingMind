package com.meetingmind.demo.service;

public class TranscriptionGatewayException extends RuntimeException {

    public TranscriptionGatewayException(String message) {
        super(message);
    }

    public TranscriptionGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
