package com.meetingmind.bff.auth;

public record BffSessionBootstrapResponse(
        boolean authenticated,
        BffAuthUser user,
        BffSessionView session,
        boolean accountManagementAvailable) {

    static BffSessionBootstrapResponse unauthenticated() {
        return unauthenticated(false);
    }

    static BffSessionBootstrapResponse unauthenticated(boolean accountManagementAvailable) {
        return new BffSessionBootstrapResponse(false, null, null, accountManagementAvailable);
    }

    static BffSessionBootstrapResponse authenticated(
            BffAuthUser user, BffSessionView session) {
        return authenticated(user, session, false);
    }

    static BffSessionBootstrapResponse authenticated(
            BffAuthUser user, BffSessionView session, boolean accountManagementAvailable) {
        return new BffSessionBootstrapResponse(true, user, session, accountManagementAvailable);
    }
}
