package com.meetingmind.bff.tokenvault.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.bff.tokenvault.TokenVaultException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

class AwsKmsEnvelopeKeyServiceTest {

    @Test
    void generatesAndDecryptsDataKeysWithTheBoundEncryptionContext() {
        KmsClient kmsClient = mock(KmsClient.class);
        byte[] plaintextKey = new byte[32];
        Arrays.fill(plaintextKey, (byte) 7);
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenReturn(GenerateDataKeyResponse.builder()
                        .keyId("arn:aws:kms:ap-northeast-2:123456789012:key/key-id")
                        .plaintext(SdkBytes.fromByteArray(plaintextKey))
                        .ciphertextBlob(SdkBytes.fromByteArray(new byte[] {1, 2, 3}))
                        .build());
        when(kmsClient.decrypt(any(DecryptRequest.class)))
                .thenReturn(DecryptResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextKey))
                        .build());
        AwsKmsEnvelopeKeyService service = new AwsKmsEnvelopeKeyService(kmsClient, "alias/token-vault");
        TokenEncryptionContext context =
                new TokenEncryptionContext(UUID.randomUUID(), UUID.randomUUID(), 3);

        try (EnvelopeKeyService.GeneratedDataKey generated = service.generateDataKey(context)) {
            assertThat(generated.plaintextKey()).containsOnly((byte) 7);
            assertThat(generated.encryptedKey()).containsExactly(1, 2, 3);
        }
        assertThat(service.decryptDataKey(context, "key-id", new byte[] {1, 2, 3}))
                .containsOnly((byte) 7);

        ArgumentCaptor<GenerateDataKeyRequest> generateRequest =
                ArgumentCaptor.forClass(GenerateDataKeyRequest.class);
        verify(kmsClient).generateDataKey(generateRequest.capture());
        assertThat(generateRequest.getValue().keyId()).isEqualTo("alias/token-vault");
        assertThat(generateRequest.getValue().keySpec()).isEqualTo(DataKeySpec.AES_256);
        assertThat(generateRequest.getValue().encryptionContext())
                .containsEntry("bundleId", context.bundleId().toString())
                .containsEntry("authSessionId", context.authSessionId().toString())
                .containsEntry("version", "3");

        ArgumentCaptor<DecryptRequest> decryptRequest = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(kmsClient).decrypt(decryptRequest.capture());
        assertThat(decryptRequest.getValue().keyId()).isEqualTo("key-id");
        assertThat(decryptRequest.getValue().encryptionContext())
                .isEqualTo(context.kmsEncryptionContext());
    }

    @Test
    void mapsKmsFailureToASecretFreeFailClosedError() {
        KmsClient kmsClient = mock(KmsClient.class);
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenThrow(new IllegalStateException("provider failure"));
        AwsKmsEnvelopeKeyService service = new AwsKmsEnvelopeKeyService(kmsClient, "alias/token-vault");
        TokenEncryptionContext context =
                new TokenEncryptionContext(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertThatThrownBy(() -> service.generateDataKey(context))
                .isInstanceOfSatisfying(TokenVaultException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TokenVaultException.Code.CRYPTO_FAILURE);
                    assertThat(exception).hasMessage("CRYPTO_FAILURE").hasNoCause();
                });
    }
}
