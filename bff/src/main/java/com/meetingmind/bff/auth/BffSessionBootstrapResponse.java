package com.meetingmind.bff.auth;

public record BffSessionBootstrapResponse(
        boolean authenticated,
        BffAuthUser user,
        BffSessionView session) {

    static BffSessionBootstrapResponse unauthenticated() {
        return new BffSessionBootstrapResponse(false, null, null);
    }

    static BffSessionBootstrapResponse authenticated(
            BffAuthUser user, BffSessionView session) {
        return new BffSessionBootstrapResponse(true, user, session);
    }
}
