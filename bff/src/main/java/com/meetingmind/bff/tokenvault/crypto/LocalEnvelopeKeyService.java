package com.meetingmind.bff.tokenvault.crypto;

import com.meetingmind.bff.tokenvault.TokenVaultException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class LocalEnvelopeKeyService implements EnvelopeKeyService, AutoCloseable {

    private static final int DATA_KEY_BYTES = 32;

    private final String keyId;
    private final byte[] masterKey;
    private final SecureRandom secureRandom;

    public LocalEnvelopeKeyService(String keyId, String masterKeyBase64) {
        this.keyId = requireKeyId(keyId);
        this.masterKey = decode(masterKeyBase64);
        if (masterKey.length != DATA_KEY_BYTES) {
            Arrays.fill(masterKey, (byte) 0);
            throw new IllegalStateException("local Token Vault requires a 256-bit master key");
        }
        this.secureRandom = new SecureRandom();
    }

    LocalEnvelopeKeyService(String keyId, byte[] masterKey, SecureRandom secureRandom) {
        if (masterKey == null || masterKey.length != DATA_KEY_BYTES) {
            throw new IllegalStateException("local Token Vault requires a named 256-bit master key");
        }
        this.keyId = requireKeyId(keyId);
        this.masterKey = masterKey.clone();
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedDataKey generateDataKey(TokenEncryptionContext context) {
        byte[] dataKey = new byte[DATA_KEY_BYTES];
        secureRandom.nextBytes(dataKey);
        try {
            byte[] encryptedKey = AesGcm.encrypt(dataKey, masterKey, context.authenticatedData(), secureRandom);
            return new GeneratedDataKey(keyId, dataKey, encryptedKey);
        } catch (GeneralSecurityException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    @Override
    public byte[] decryptDataKey(TokenEncryptionContext context, String encryptedWithKeyId, byte[] encryptedDataKey) {
        if (!keyId.equals(encryptedWithKeyId)) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        }
        try {
            return AesGcm.decrypt(encryptedDataKey, masterKey, context.authenticatedData());
        } catch (GeneralSecurityException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        }
    }

    @Override
    public void close() {
        Arrays.fill(masterKey, (byte) 0);
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("BFF_TOKEN_VAULT_LOCAL_KEY_BASE64 is required");
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("BFF_TOKEN_VAULT_LOCAL_KEY_BASE64 must be valid Base64");
        }
    }

    private static String requireKeyId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("BFF_TOKEN_VAULT_LOCAL_KEY_ID is required");
        }
        return value;
    }
}
