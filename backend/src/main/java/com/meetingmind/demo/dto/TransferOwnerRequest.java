package com.meetingmind.demo.dto;

public record TransferOwnerRequest(String targetMemberId, String confirmationText, String previousOwnerRole) {
}
