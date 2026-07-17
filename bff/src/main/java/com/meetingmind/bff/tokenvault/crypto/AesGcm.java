package com.meetingmind.bff.tokenvault.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class AesGcm {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte FORMAT_VERSION = 1;

    private AesGcm() {}

    static byte[] encrypt(byte[] plaintext, byte[] key, byte[] authenticatedData, SecureRandom secureRandom)
            throws GeneralSecurityException {
        validateKey(key);
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(authenticatedData);
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] encoded = new byte[1 + IV_BYTES + ciphertext.length];
        encoded[0] = FORMAT_VERSION;
        System.arraycopy(iv, 0, encoded, 1, IV_BYTES);
        System.arraycopy(ciphertext, 0, encoded, 1 + IV_BYTES, ciphertext.length);
        return encoded;
    }

    static byte[] decrypt(byte[] encoded, byte[] key, byte[] authenticatedData) throws GeneralSecurityException {
        validateKey(key);
        if (encoded == null || encoded.length < 1 + IV_BYTES + 16 || encoded[0] != FORMAT_VERSION) {
            throw new GeneralSecurityException("invalid ciphertext format");
        }

        byte[] iv = Arrays.copyOfRange(encoded, 1, 1 + IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(encoded, 1 + IV_BYTES, encoded.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(authenticatedData);
        return cipher.doFinal(ciphertext);
    }

    private static void validateKey(byte[] key) throws GeneralSecurityException {
        if (key == null || key.length != KEY_BYTES) {
            throw new GeneralSecurityException("AES-256 key required");
        }
    }
}
