package com.meetingmind.demo.gateway;

public class TranscriptionSessionNotFoundException extends RuntimeException {

    public TranscriptionSessionNotFoundException(String sessionId) {
        super("STT session not found: " + sessionId);
    }
}
