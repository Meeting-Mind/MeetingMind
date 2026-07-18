package com.meetingmind.bff.tokenvault.crypto;

import com.meetingmind.bff.tokenvault.TokenVaultException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

public final class AwsKmsEnvelopeKeyService implements EnvelopeKeyService {

    private final KmsClient kmsClient;
    private final String kmsKeyId;

    public AwsKmsEnvelopeKeyService(KmsClient kmsClient, String kmsKeyId) {
        if (kmsClient == null || kmsKeyId == null || kmsKeyId.isBlank()) {
            throw new IllegalStateException("BFF_TOKEN_VAULT_KMS_KEY_ID is required");
        }
        this.kmsClient = kmsClient;
        this.kmsKeyId = kmsKeyId;
    }

    @Override
    public GeneratedDataKey generateDataKey(TokenEncryptionContext context) {
        try {
            GenerateDataKeyResponse response = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .encryptionContext(context.kmsEncryptionContext())
                    .build());
            if (response.plaintext() == null || response.ciphertextBlob() == null || response.keyId() == null) {
                throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
            }
            byte[] plaintextKey = response.plaintext().asByteArray();
            try {
                return new GeneratedDataKey(
                        response.keyId(), plaintextKey, response.ciphertextBlob().asByteArray());
            } finally {
                java.util.Arrays.fill(plaintextKey, (byte) 0);
            }
        } catch (TokenVaultException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        }
    }

    @Override
    public byte[] decryptDataKey(TokenEncryptionContext context, String keyId, byte[] encryptedDataKey) {
        try {
            DecryptResponse response = kmsClient.decrypt(DecryptRequest.builder()
                    .keyId(keyId)
                    .ciphertextBlob(SdkBytes.fromByteArray(encryptedDataKey))
                    .encryptionContext(context.kmsEncryptionContext())
                    .build());
            if (response.plaintext() == null) {
                throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
            }
            byte[] plaintextKey = response.plaintext().asByteArray();
            if (plaintextKey.length != 32) {
                java.util.Arrays.fill(plaintextKey, (byte) 0);
                throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
            }
            return plaintextKey;
        } catch (TokenVaultException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        }
    }
}
