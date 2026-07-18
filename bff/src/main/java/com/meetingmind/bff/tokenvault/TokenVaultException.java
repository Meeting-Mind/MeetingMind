package com.meetingmind.bff.tokenvault;

public final class TokenVaultException extends RuntimeException {

    public enum Code {
        BUNDLE_NOT_FOUND,
        BUNDLE_ALREADY_EXISTS,
        CONCURRENT_UPDATE,
        INVALID_BUNDLE,
        CRYPTO_FAILURE,
        STORAGE_FAILURE
    }

    private final Code code;

    private TokenVaultException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static TokenVaultException of(Code code) {
        return new TokenVaultException(code);
    }
}
