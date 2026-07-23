package com.meetingmind.demo.gateway;

public class TranscriptionStopException extends RuntimeException {

    public TranscriptionStopException(String sessionId, Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
