package com.meetingmind.bff.config;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;

@Component
public final class InternalHttpClientFactory {

    private final SslBundles sslBundles;
    private final String bundleName;

    public InternalHttpClientFactory(
            SslBundles sslBundles,
            @Value("${meetingmind.internal-tls.bundle-name:}") String bundleName) {
        this.sslBundles = sslBundles;
        this.bundleName = bundleName == null ? "" : bundleName.trim();
    }

    public HttpClient.Builder newBuilder() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (!bundleName.isEmpty()) {
            builder.sslContext(sslBundles.getBundle(bundleName).createSslContext());
        }
        return builder;
    }
}
