package com.meetingmind.bff.auth;

import java.io.Serializable;

public record BffAuthUser(
        String id,
        String email,
        String displayName,
        String pictureUrl,
        String status) implements Serializable {

    static BffAuthUser from(AuthTokenResponse.User user) {
        return new BffAuthUser(
                user.resourceUserId(),
                user.email(),
                user.displayName(),
                user.pictureUrl(),
                user.status());
    }
}
