package com.meetingmind.bff.auth;

public final class DownstreamUnauthorizedException extends RuntimeException {

    public DownstreamUnauthorizedException() {
        super("downstream rejected the access token");
    }
}
