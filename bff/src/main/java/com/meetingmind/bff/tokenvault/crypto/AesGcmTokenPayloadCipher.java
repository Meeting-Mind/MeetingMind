package com.meetingmind.bff.tokenvault.crypto;

import com.meetingmind.bff.tokenvault.TokenVaultException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AesGcmTokenPayloadCipher implements TokenPayloadCipher {

    private final EnvelopeKeyService keyService;
    private final SecureRandom secureRandom;

    public AesGcmTokenPayloadCipher(EnvelopeKeyService keyService) {
        this(keyService, new SecureRandom());
    }

    AesGcmTokenPayloadCipher(EnvelopeKeyService keyService, SecureRandom secureRandom) {
        this.keyService = keyService;
        this.secureRandom = secureRandom;
    }

    @Override
    public EncryptedEnvelope encrypt(TokenEncryptionContext context, byte[] plaintext) {
        try (EnvelopeKeyService.GeneratedDataKey dataKey = keyService.generateDataKey(context)) {
            byte[] plaintextKey = dataKey.plaintextKey();
            try {
                byte[] encryptedPayload =
                        AesGcm.encrypt(plaintext, plaintextKey, context.authenticatedData(), secureRandom);
                return new EncryptedEnvelope(encryptedPayload, dataKey.encryptedKey(), dataKey.keyId());
            } finally {
                Arrays.fill(plaintextKey, (byte) 0);
            }
        } catch (TokenVaultException exception) {
            throw exception;
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        }
    }

    @Override
    public byte[] decrypt(TokenEncryptionContext context, EncryptedEnvelope envelope) {
        byte[] plaintextKey = keyService.decryptDataKey(context, envelope.keyId(), envelope.encryptedDataKey());
        try {
            return AesGcm.decrypt(
                    envelope.encryptedPayload(), plaintextKey, context.authenticatedData());
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }
}
