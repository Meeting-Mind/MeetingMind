package com.meetingmind.demo.auth.target;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpJwksSource implements JwksSource {

    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)\\s*(?:,|$)");

    private final URI uri;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpJwksSource(URI uri, HttpClient httpClient, Duration requestTimeout) {
        if (uri == null || !uri.isAbsolute()) {
            throw new IllegalArgumentException("JWKS URI는 절대 URI여야 합니다.");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("JWKS HttpClient가 필요합니다.");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("JWKS request timeout은 양수여야 합니다.");
        }
        this.uri = uri;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Response fetch(String etag) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET();
            if (etag != null && !etag.isBlank()) {
                request.header("If-None-Match", etag);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            Duration maxAge = parseMaxAge(response.headers().firstValue("Cache-Control").orElse(""));
            String responseEtag = response.headers().firstValue("ETag").orElse(etag);
            if (response.statusCode() == 304) {
                return new Response(true, null, responseEtag, maxAge);
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("JWKS 응답 상태가 올바르지 않습니다.");
            }
            return new Response(false, response.body(), responseEtag, maxAge);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JWKS 조회가 중단됐습니다.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("JWKS를 조회할 수 없습니다.", exception);
        }
    }

    private Duration parseMaxAge(String cacheControl) {
        Matcher matcher = MAX_AGE.matcher(cacheControl);
        if (!matcher.find()) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(matcher.group(1)));
        } catch (ArithmeticException | NumberFormatException exception) {
            return Duration.ZERO;
        }
    }
}
