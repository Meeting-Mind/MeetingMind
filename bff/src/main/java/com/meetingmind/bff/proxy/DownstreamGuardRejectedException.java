package com.meetingmind.bff.proxy;

final class DownstreamGuardRejectedException extends RuntimeException {

    DownstreamGuardRejectedException() {
        super("downstream call rejected");
    }
}
