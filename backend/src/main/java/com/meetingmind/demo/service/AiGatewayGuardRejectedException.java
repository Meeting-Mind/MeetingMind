package com.meetingmind.demo.service;

final class AiGatewayGuardRejectedException extends RuntimeException {

    AiGatewayGuardRejectedException() {
        super("AI gateway circuit is open.");
    }
}
