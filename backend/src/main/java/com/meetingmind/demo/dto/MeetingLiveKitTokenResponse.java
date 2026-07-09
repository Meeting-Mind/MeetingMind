package com.meetingmind.demo.dto;

public record MeetingLiveKitTokenResponse(
        String serverUrl,
        String participantToken,
        String roomName,
        String identity,
        String name,
        long expiresIn
) {
}
