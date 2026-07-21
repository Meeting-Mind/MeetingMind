package com.meetingmind.demo.dto;

public record RestoreReportResponse(String id, String status, int version, String sourceReportId) {
}
