package com.meetingmind.demo.service;

final class TranscriptTextSanitizer {

    private TranscriptTextSanitizer() {
    }

    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace("<end>", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.matches("^[\\p{Punct}\\s]+$")) {
            return "";
        }
        return normalized;
    }
}
