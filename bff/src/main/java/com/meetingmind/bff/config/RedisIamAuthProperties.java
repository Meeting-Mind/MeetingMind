package com.meetingmind.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingmind.bff.redis.iam-auth")
public record RedisIamAuthProperties(boolean enabled, String cacheName, String region) {}
