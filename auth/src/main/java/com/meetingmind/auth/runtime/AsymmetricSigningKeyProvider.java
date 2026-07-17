package com.meetingmind.auth.runtime;

import java.security.interfaces.RSAPublicKey;

interface AsymmetricSigningKeyProvider {

    byte[] sign(String kmsKeyId, byte[] message);

    RSAPublicKey publicKey(String kmsKeyId);
}
