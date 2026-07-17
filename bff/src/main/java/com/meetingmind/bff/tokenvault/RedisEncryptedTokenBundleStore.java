package com.meetingmind.bff.tokenvault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisEncryptedTokenBundleStore implements EncryptedTokenBundleStore {

    private static final DefaultRedisScript<Long> REPLACE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]); return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String namespace;

    public RedisEncryptedTokenBundleStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Clock clock, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("Token Vault namespace is required");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.namespace = namespace;
    }

    @Override
    public void create(EncryptedTokenBundle bundle) {
        String value = serialize(bundle);
        try {
            Boolean created = redisTemplate
                    .opsForValue()
                    .setIfAbsent(key(bundle.id()), value, timeToLive(bundle));
            if (!Boolean.TRUE.equals(created)) {
                throw TokenVaultException.of(TokenVaultException.Code.BUNDLE_ALREADY_EXISTS);
            }
        } catch (TokenVaultException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.STORAGE_FAILURE);
        }
    }

    @Override
    public Optional<EncryptedTokenBundle> findById(UUID bundleId) {
        try {
            String value = redisTemplate.opsForValue().get(key(bundleId));
            return value == null ? Optional.empty() : Optional.of(deserialize(value));
        } catch (TokenVaultException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.STORAGE_FAILURE);
        }
    }

    @Override
    public boolean replace(long expectedVersion, EncryptedTokenBundle replacement) {
        String redisKey = key(replacement.id());
        try {
            String currentValue = redisTemplate.opsForValue().get(redisKey);
            if (currentValue == null || deserialize(currentValue).version() != expectedVersion) {
                return false;
            }
            String replacementValue = serialize(replacement);
            Long replaced = redisTemplate.execute(
                    REPLACE_SCRIPT,
                    List.of(redisKey),
                    currentValue,
                    replacementValue,
                    Long.toString(timeToLive(replacement).toMillis()));
            return Long.valueOf(1).equals(replaced);
        } catch (TokenVaultException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.STORAGE_FAILURE);
        }
    }

    @Override
    public void deleteById(UUID bundleId) {
        try {
            redisTemplate.delete(key(bundleId));
        } catch (RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.STORAGE_FAILURE);
        }
    }

    String redisKey(UUID bundleId) {
        return key(bundleId);
    }

    private String key(UUID bundleId) {
        if (bundleId == null) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        return namespace + ":" + bundleId;
    }

    private Duration timeToLive(EncryptedTokenBundle bundle) {
        Duration duration = Duration.between(clock.instant(), bundle.refreshExpiresAt());
        if (duration.isZero() || duration.isNegative()) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        return duration;
    }

    private String serialize(EncryptedTokenBundle bundle) {
        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (JsonProcessingException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.STORAGE_FAILURE);
        }
    }

    private EncryptedTokenBundle deserialize(String value) {
        try {
            return objectMapper.readValue(value, EncryptedTokenBundle.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.STORAGE_FAILURE);
        }
    }
}
