package com.meetingmind.bff.tokenvault.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public record TokenEncryptionContext(UUID bundleId, UUID authSessionId, long version) {

    public TokenEncryptionContext {
        if (bundleId == null || authSessionId == null || version < 1) {
            throw new IllegalArgumentException("invalid token encryption context");
        }
    }

    public byte[] authenticatedData() {
        return ("meetingmind-token-bundle:v1\n"
                        + "bundleId=" + bundleId + "\n"
                        + "authSessionId=" + authSessionId + "\n"
                        + "version=" + version)
                .getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, String> kmsEncryptionContext() {
        return Map.of(
                "service", "meetingmind-web-bff",
                "purpose", "token-bundle",
                "bundleId", bundleId.toString(),
                "authSessionId", authSessionId.toString(),
                "version", Long.toString(version));
    }
}
