package com.meetingmind.bff.auth;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisRefreshSingleFlightLock implements RefreshSingleFlightLock {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String namespace;

    public RedisRefreshSingleFlightLock(StringRedisTemplate redisTemplate, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("Token Manager lock namespace is required");
        }
        this.redisTemplate = redisTemplate;
        this.namespace = namespace;
    }

    @Override
    public boolean tryAcquire(UUID tokenBundleId, String owner, Duration lease) {
        requireArguments(tokenBundleId, owner);
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("refresh lock lease must be positive");
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(tokenBundleId), owner, lease));
    }

    @Override
    public void release(UUID tokenBundleId, String owner) {
        requireArguments(tokenBundleId, owner);
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key(tokenBundleId)), owner);
    }

    private void requireArguments(UUID tokenBundleId, String owner) {
        if (tokenBundleId == null || owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("refresh lock identity is required");
        }
    }

    private String key(UUID tokenBundleId) {
        return namespace + ":" + tokenBundleId;
    }
}
