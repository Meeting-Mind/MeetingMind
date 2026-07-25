package com.meetingmind.demo.service;

import java.time.Duration;

record AiGatewayGuardPolicy(
        int maxConcurrent,
        int failureThreshold,
        Duration openDuration
) {

    AiGatewayGuardPolicy {
        if (maxConcurrent <= 0 || failureThreshold <= 0 || openDuration == null || !openDuration.isPositive()) {
            throw new IllegalArgumentException("ai gateway guard policy must be positive");
        }
    }
}
