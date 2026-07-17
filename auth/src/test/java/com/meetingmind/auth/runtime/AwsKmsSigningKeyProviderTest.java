package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

class AwsKmsSigningKeyProviderTest {

    @Test
    void usesRawRs256AndRequiresRsa2048SignVerifyMetadata() throws Exception {
        KeyPair pair = TestSigningKeyProvider.generateKeyPair();
        KmsClient kmsClient = mock(KmsClient.class);
        when(kmsClient.getPublicKey(any(GetPublicKeyRequest.class))).thenReturn(
                GetPublicKeyResponse.builder()
                        .keySpec(KeySpec.RSA_2048)
                        .keyUsage(KeyUsageType.SIGN_VERIFY)
                        .signingAlgorithms(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                        .publicKey(SdkBytes.fromByteArray(pair.getPublic().getEncoded()))
                        .build()
        );
        when(kmsClient.sign(any(SignRequest.class))).thenAnswer(invocation -> {
            SignRequest request = invocation.getArgument(0);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(pair.getPrivate());
            signer.update(request.message().asByteArray());
            return SignResponse.builder()
                    .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                    .signature(SdkBytes.fromByteArray(signer.sign()))
                    .build();
        });
        AwsKmsSigningKeyProvider provider = new AwsKmsSigningKeyProvider(kmsClient);
        byte[] message = "header.payload".getBytes(StandardCharsets.US_ASCII);

        assertThat(provider.publicKey("kms-key")).isEqualTo(pair.getPublic());
        assertThat(provider.publicKey("kms-key")).isEqualTo(pair.getPublic());
        assertThat(provider.sign("kms-key", message)).isNotEmpty();

        ArgumentCaptor<SignRequest> request = ArgumentCaptor.forClass(SignRequest.class);
        verify(kmsClient).sign(request.capture());
        assertThat(request.getValue().keyId()).isEqualTo("kms-key");
        assertThat(request.getValue().messageType()).isEqualTo(MessageType.RAW);
        assertThat(request.getValue().signingAlgorithm())
                .isEqualTo(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256);
        verify(kmsClient).getPublicKey(any(GetPublicKeyRequest.class));
    }
}
