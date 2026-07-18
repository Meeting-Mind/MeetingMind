package com.meetingmind.bff.auth;

public record BffAuthenticatedResponse(BffAuthUser user, BffSessionView session) {}
