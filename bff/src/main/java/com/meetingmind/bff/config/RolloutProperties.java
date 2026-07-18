package com.meetingmind.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingmind.bff.rollout")
public record RolloutProperties(boolean acceptBrowserTraffic) {}
