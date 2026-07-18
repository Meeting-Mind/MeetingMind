package com.meetingmind.bff.auth;

public final class BffSessionAttributes {

    public static final String RESOURCE_USER_ID = "meetingmind.resourceUserId";
    public static final String AUTH_USER_ID = "meetingmind.authUserId";
    public static final String AUTH_SESSION_ID = "meetingmind.authSessionId";
    public static final String TOKEN_BUNDLE_ID = "meetingmind.tokenBundleId";
    public static final String CREATED_AT = "meetingmind.createdAt";
    public static final String ABSOLUTE_EXPIRES_AT = "meetingmind.absoluteExpiresAt";
    public static final String REMEMBER_ME = "meetingmind.rememberMe";
    public static final String AUTHENTICATED_AT = "meetingmind.authenticatedAt";

    private BffSessionAttributes() {}
}
