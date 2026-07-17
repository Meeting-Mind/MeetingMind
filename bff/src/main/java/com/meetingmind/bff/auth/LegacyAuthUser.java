package com.meetingmind.bff.auth;

public record LegacyAuthUser(
        String id,
        String email,
        String displayName,
        String pictureUrl,
        String status) {}
