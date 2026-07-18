package com.meetingmind.bff.tokenvault;

public record VersionedTokenBundle(long version, TokenBundlePayload payload) {

    public VersionedTokenBundle {
        if (version <= 0 || payload == null) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }
}
