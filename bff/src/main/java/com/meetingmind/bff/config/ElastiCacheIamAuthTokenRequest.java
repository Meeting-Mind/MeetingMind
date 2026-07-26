package com.meetingmind.bff.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

final class ElastiCacheIamAuthTokenRequest {

    private static final Pattern CACHE_NAME_PATTERN =
            Pattern.compile("[a-z](?:[a-z0-9]|-(?=[a-z0-9])){0,39}");
    private static final Duration TOKEN_EXPIRY = Duration.ofMinutes(15);
    private static final String REQUEST_PROTOCOL = "http://";
    private static final String SERVICE_NAME = "elasticache";

    private final String userId;
    private final String cacheName;
    private final String region;

    ElastiCacheIamAuthTokenRequest(String userId, String cacheName, String region) {
        this.userId = requireText("Redis IAM user", userId);
        this.cacheName = requireText("Redis IAM cache name", cacheName).toLowerCase(Locale.ROOT);
        this.region = requireText("Redis IAM AWS region", region);
        if (!CACHE_NAME_PATTERN.matcher(this.cacheName).matches()) {
            throw new IllegalArgumentException("Redis IAM cache name must be a lowercase DNS label");
        }
    }

    String toSignedRequestUri(AwsCredentials credentials) {
        Objects.requireNonNull(credentials, "AWS credentials must not be null");

        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.GET)
                .uri(URI.create(REQUEST_PROTOCOL + cacheName + "/"))
                .appendRawQueryParameter("Action", "connect")
                .appendRawQueryParameter("User", userId)
                .build();

        SignedRequest signedRequest = AwsV4HttpSigner.create()
                .sign(builder -> builder.identity(credentials)
                        .request(request)
                        .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SERVICE_NAME)
                        .putProperty(AwsV4HttpSigner.REGION_NAME, region)
                        .putProperty(
                                AwsV4HttpSigner.AUTH_LOCATION,
                                AwsV4HttpSigner.AuthLocation.QUERY_STRING)
                        .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, TOKEN_EXPIRY)
                        .build());

        String signedUri = signedRequest.request().getUri().toString();
        if (!signedUri.startsWith(REQUEST_PROTOCOL)) {
            throw new IllegalStateException("Redis IAM signer returned an unexpected URI");
        }
        return signedUri.substring(REQUEST_PROTOCOL.length());
    }

    private static String requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
