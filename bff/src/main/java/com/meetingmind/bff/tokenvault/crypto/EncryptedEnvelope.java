package com.meetingmind.bff.tokenvault.crypto;

public record EncryptedEnvelope(byte[] encryptedPayload, byte[] encryptedDataKey, String keyId) {

    public EncryptedEnvelope {
        if (encryptedPayload == null
                || encryptedPayload.length == 0
                || encryptedDataKey == null
                || encryptedDataKey.length == 0
                || keyId == null
                || keyId.isBlank()) {
            throw new IllegalArgumentException("invalid encrypted envelope");
        }
        encryptedPayload = encryptedPayload.clone();
        encryptedDataKey = encryptedDataKey.clone();
    }

    @Override
    public byte[] encryptedPayload() {
        return encryptedPayload.clone();
    }

    @Override
    public byte[] encryptedDataKey() {
        return encryptedDataKey.clone();
    }
}
