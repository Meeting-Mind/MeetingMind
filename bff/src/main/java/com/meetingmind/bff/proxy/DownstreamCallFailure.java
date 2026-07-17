package com.meetingmind.bff.proxy;

final class DownstreamCallFailure extends RuntimeException {

    DownstreamCallFailure() {
        super("downstream call failed");
    }
}
