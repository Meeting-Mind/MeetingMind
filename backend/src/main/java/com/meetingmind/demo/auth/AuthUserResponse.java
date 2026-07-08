package com.meetingmind.demo.auth;

public record AuthUserResponse(
        String id,
        String email,
        String displayName,
        String pictureUrl,
        String status
) {
    static AuthUserResponse from(AuthUser user) {
        return new AuthUserResponse(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
    }
}
