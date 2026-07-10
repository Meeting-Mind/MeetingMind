package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.MeetingLiveKitTokenResponse;
import com.meetingmind.demo.service.MeetingLiveKitTokenService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingLiveKitController {

    private final MeetingLiveKitTokenService meetingLiveKitTokenService;

    public MeetingLiveKitController(MeetingLiveKitTokenService meetingLiveKitTokenService) {
        this.meetingLiveKitTokenService = meetingLiveKitTokenService;
    }

    @PostMapping("/{meetingId}/livekit-token")
    public MeetingLiveKitTokenResponse issueMeetingToken(
            @PathVariable String meetingId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return meetingLiveKitTokenService.issueMeetingToken(authorizationHeader, meetingId);
    }
}
