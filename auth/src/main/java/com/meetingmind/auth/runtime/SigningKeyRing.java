package com.meetingmind.auth.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class SigningKeyRing {

    static final Duration PREPUBLISH_WINDOW = Duration.ofMinutes(5);
    static final Duration PREVIOUS_KEY_OVERLAP = Duration.ofHours(1);
    private static final Duration STARTUP_CLOCK_SKEW = Duration.ofSeconds(60);
    private static final Pattern KID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final String activeKid;
    private final Instant activeSince;
    private final RotationMode rotationMode;
    private final List<SigningKey> keys;

    private SigningKeyRing(
            String activeKid,
            Instant activeSince,
            RotationMode rotationMode,
            List<SigningKey> keys,
            Instant now
    ) {
        this.activeKid = requireKid(activeKid);
        this.activeSince = activeSince;
        this.rotationMode = rotationMode;
        this.keys = List.copyOf(keys);
        validate(now);
    }

    static SigningKeyRing parse(ObjectMapper objectMapper, String json, Clock clock) {
        try {
            KeyRingDocument document = objectMapper.readValue(json, KeyRingDocument.class);
            List<SigningKey> keys = document.keys() == null
                    ? List.of()
                    : document.keys().stream().map(SigningKeyRing::toSigningKey).toList();
            RotationMode mode = document.rotationMode() == null || document.rotationMode().isBlank()
                    ? RotationMode.REGULAR
                    : RotationMode.valueOf(document.rotationMode().trim().toUpperCase(java.util.Locale.ROOT));
            return new SigningKeyRing(
                    document.activeKid(),
                    parseInstant(document.activeSince(), "activeSince"),
                    mode,
                    keys,
                    Instant.now(clock)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("signing key ring JSON을 읽을 수 없습니다.", exception);
        }
    }

    SigningKey activeKey(Instant now) {
        if (now.isBefore(activeSince)) {
            throw new IllegalStateException("active signing key 전환 시각이 아직 되지 않았습니다.");
        }
        SigningKey active = keys.stream()
                .filter(key -> key.kid().equals(activeKid))
                .findFirst()
                .orElseThrow();
        if (!active.isPublishedAt(now)) {
            throw new IllegalStateException("active signing key가 JWKS 공개 기간에 없습니다.");
        }
        return active;
    }

    List<SigningKey> publishedKeys(Instant now) {
        return keys.stream()
                .filter(key -> key.isPublishedAt(now))
                .sorted(Comparator.comparing(SigningKey::kid))
                .toList();
    }

    private void validate(Instant now) {
        if (activeSince == null) {
            throw new IllegalArgumentException("activeSince가 필요합니다.");
        }
        if (activeSince.isAfter(now.plus(STARTUP_CLOCK_SKEW))) {
            throw new IllegalArgumentException("activeSince가 현재보다 미래입니다.");
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("signing key가 하나 이상 필요합니다.");
        }
        Set<String> kids = new HashSet<>();
        Set<String> keyIds = new HashSet<>();
        for (SigningKey key : keys) {
            if (!kids.add(key.kid())) {
                throw new IllegalArgumentException("signing kid가 중복됩니다.");
            }
            if (!keyIds.add(key.kmsKeyId())) {
                throw new IllegalArgumentException("KMS signing key ID가 중복됩니다.");
            }
            if (key.publishUntil() != null && !key.publishUntil().isAfter(key.publishedAt())) {
                throw new IllegalArgumentException("publishUntil은 publishedAt 이후여야 합니다.");
            }
        }

        SigningKey active = keys.stream()
                .filter(key -> key.kid().equals(activeKid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("activeKid가 key ring에 없습니다."));
        if (active.publishedAt().plus(PREPUBLISH_WINDOW).isAfter(activeSince)) {
            throw new IllegalArgumentException("active key는 전환 최소 5분 전에 게시돼야 합니다.");
        }
        if (active.publishUntil() != null) {
            throw new IllegalArgumentException("active key에는 publishUntil을 둘 수 없습니다.");
        }

        if (rotationMode == RotationMode.REGULAR) {
            keys.stream()
                    .filter(key -> key.publishedAt().isBefore(active.publishedAt()))
                    .max(Comparator.comparing(SigningKey::publishedAt))
                    .ifPresent(previous -> {
                        Instant requiredUntil = activeSince.plus(PREVIOUS_KEY_OVERLAP);
                        if (previous.publishUntil() == null || previous.publishUntil().isBefore(requiredUntil)) {
                            throw new IllegalArgumentException("이전 key는 active 전환 뒤 최소 1시간 게시돼야 합니다.");
                        }
                    });
        }
    }

    private static SigningKey toSigningKey(KeyDocument key) {
        if (key == null) {
            throw new IllegalArgumentException("signing key 항목이 비어 있습니다.");
        }
        String kmsKeyId = key.kmsKeyId() == null ? "" : key.kmsKeyId().trim();
        if (kmsKeyId.isBlank() || kmsKeyId.length() > 2048) {
            throw new IllegalArgumentException("KMS signing key ID가 필요합니다.");
        }
        return new SigningKey(
                requireKid(key.kid()),
                kmsKeyId,
                parseInstant(key.publishedAt(), "publishedAt"),
                key.publishUntil() == null || key.publishUntil().isBlank()
                        ? null
                        : parseInstant(key.publishUntil(), "publishUntil")
        );
    }

    private static String requireKid(String kid) {
        String normalized = kid == null ? "" : kid.trim();
        if (!KID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("kid 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException(field + "는 ISO-8601 시각이어야 합니다.", exception);
        }
    }

    enum RotationMode {
        REGULAR,
        EMERGENCY
    }

    record SigningKey(String kid, String kmsKeyId, Instant publishedAt, Instant publishUntil) {
        boolean isPublishedAt(Instant instant) {
            return !instant.isBefore(publishedAt)
                    && (publishUntil == null || instant.isBefore(publishUntil));
        }
    }

    private record KeyRingDocument(
            String activeKid,
            String activeSince,
            String rotationMode,
            List<KeyDocument> keys
    ) {
    }

    private record KeyDocument(
            String kid,
            String kmsKeyId,
            String publishedAt,
            String publishUntil
    ) {
    }
}
