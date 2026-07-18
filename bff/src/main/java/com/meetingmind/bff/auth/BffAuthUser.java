package com.meetingmind.bff.auth;

import java.io.Serializable;

public record BffAuthUser(
        String id,
        String email,
        String displayName,
        String pictureUrl,
        String status) implements Serializable {

    static BffAuthUser from(LegacyAuthUser user) {
        return new BffAuthUser(
                user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
    }
}
