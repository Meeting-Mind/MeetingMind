package com.meetingmind.auth.runtime;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

final class AwsKmsSigningKeyProvider implements AsymmetricSigningKeyProvider {

    private static final SigningAlgorithmSpec SIGNING_ALGORITHM =
            SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256;

    private final KmsClient kmsClient;
    private final ConcurrentMap<String, RSAPublicKey> publicKeys = new ConcurrentHashMap<>();

    AwsKmsSigningKeyProvider(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @Override
    public byte[] sign(String kmsKeyId, byte[] message) {
        try {
            var response = kmsClient.sign(SignRequest.builder()
                            .keyId(kmsKeyId)
                            .message(SdkBytes.fromByteArray(message))
                            .messageType(MessageType.RAW)
                            .signingAlgorithm(SIGNING_ALGORITHM)
                            .build());
            if (response.signingAlgorithm() != SIGNING_ALGORITHM || response.signature() == null) {
                throw new IllegalArgumentException("KMS signature metadata가 계약과 다릅니다.");
            }
            return response.signature().asByteArray();
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public RSAPublicKey publicKey(String kmsKeyId) {
        try {
            return publicKeys.computeIfAbsent(kmsKeyId, this::loadPublicKey);
        } catch (AuthRuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private RSAPublicKey loadPublicKey(String kmsKeyId) {
        try {
            GetPublicKeyResponse response = kmsClient.getPublicKey(GetPublicKeyRequest.builder()
                    .keyId(kmsKeyId)
                    .build());
            if (response.keySpec() != KeySpec.RSA_2048
                    || response.keyUsage() != KeyUsageType.SIGN_VERIFY
                    || !response.signingAlgorithms().contains(SIGNING_ALGORITHM)
                    || response.publicKey() == null) {
                throw new IllegalArgumentException("KMS signing key metadata가 계약과 다릅니다.");
            }
            var key = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(response.publicKey().asByteArray())
            );
            if (!(key instanceof RSAPublicKey rsaPublicKey)
                    || rsaPublicKey.getModulus().bitLength() != 2048) {
                throw new IllegalArgumentException("KMS public key가 RSA-2048이 아닙니다.");
            }
            return rsaPublicKey;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private AuthRuntimeException unavailable(Exception cause) {
        AuthRuntimeException exception = AuthRuntimeException.serviceUnavailable(
                "TOKEN_ISSUER_UNAVAILABLE",
                "access token 서명기를 사용할 수 없습니다."
        );
        exception.initCause(cause);
        return exception;
    }
}
