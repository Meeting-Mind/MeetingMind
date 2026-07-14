package com.meetingmind.demo.dto;

public record TransferOwnerResponse(boolean transferred, String newOwnerMemberId, String previousOwnerRole) {
}
