package com.meetingmind.demo.dto;

public record LiveKitTokenResponse(
        String serverUrl,
        String participantToken,
        String roomName,
        String identity,
        String name
) {
}
