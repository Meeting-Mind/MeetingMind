package com.meetingmind.auth.runtime;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

final class TestSigningKeyProvider implements AsymmetricSigningKeyProvider {

    private final Map<String, KeyPair> keys;

    TestSigningKeyProvider(Map<String, KeyPair> keys) {
        this.keys = Map.copyOf(keys);
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public byte[] sign(String kmsKeyId, byte[] message) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keys.get(kmsKeyId).getPrivate());
            signature.update(message);
            return signature.sign();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public RSAPublicKey publicKey(String kmsKeyId) {
        return (RSAPublicKey) keys.get(kmsKeyId).getPublic();
    }
}
