package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingJoinRequestSummaryResponse(List<Request> requests) {
    public record Request(String id, String userId, String status, String requestedAt) {
    }
}
