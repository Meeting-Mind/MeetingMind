package com.meetingmind.bff.auth;

@FunctionalInterface
public interface AuthorizedDownstreamCall<T> {

    T execute(String authorizationHeader);
}
