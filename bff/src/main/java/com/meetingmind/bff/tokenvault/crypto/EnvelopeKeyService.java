package com.meetingmind.bff.tokenvault.crypto;

import java.util.Arrays;

public interface EnvelopeKeyService extends AutoCloseable {

    GeneratedDataKey generateDataKey(TokenEncryptionContext context);

    byte[] decryptDataKey(TokenEncryptionContext context, String keyId, byte[] encryptedDataKey);

    @Override
    default void close() {}

    final class GeneratedDataKey implements AutoCloseable {

        private final String keyId;
        private final byte[] plaintextKey;
        private final byte[] encryptedKey;

        public GeneratedDataKey(String keyId, byte[] plaintextKey, byte[] encryptedKey) {
            if (keyId == null
                    || keyId.isBlank()
                    || plaintextKey == null
                    || plaintextKey.length != 32
                    || encryptedKey == null
                    || encryptedKey.length == 0) {
                throw new IllegalArgumentException("invalid generated data key");
            }
            this.keyId = keyId;
            this.plaintextKey = plaintextKey.clone();
            this.encryptedKey = encryptedKey.clone();
        }

        public String keyId() {
            return keyId;
        }

        public byte[] plaintextKey() {
            return plaintextKey.clone();
        }

        public byte[] encryptedKey() {
            return encryptedKey.clone();
        }

        @Override
        public void close() {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }
}
