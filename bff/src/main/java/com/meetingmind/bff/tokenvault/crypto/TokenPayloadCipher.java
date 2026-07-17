package com.meetingmind.bff.tokenvault.crypto;

public interface TokenPayloadCipher {

    EncryptedEnvelope encrypt(TokenEncryptionContext context, byte[] plaintext);

    byte[] decrypt(TokenEncryptionContext context, EncryptedEnvelope envelope);
}
