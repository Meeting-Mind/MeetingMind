package com.meetingmind.bff.auth;

public interface CoreUserProjectionClient {

    void project(AuthTokenResponse tokens);
}
