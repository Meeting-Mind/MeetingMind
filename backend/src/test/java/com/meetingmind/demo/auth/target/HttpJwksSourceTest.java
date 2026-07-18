package com.meetingmind.demo.auth.target;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpJwksSourceTest {

    @Test
    void usesEtagConditionalGetAndReadsCacheLifetime() throws Exception {
        AtomicReference<String> receivedEtag = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            receivedEtag.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            exchange.getResponseHeaders().add("Cache-Control", "max-age=300, public");
            exchange.getResponseHeaders().add("ETag", "\"etag-1\"");
            if (receivedEtag.get() != null) {
                exchange.sendResponseHeaders(304, -1);
            } else {
                byte[] body = "{\"keys\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            HttpJwksSource source = new HttpJwksSource(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                            + "/.well-known/jwks.json"),
                    HttpClient.newHttpClient(),
                    Duration.ofSeconds(2)
            );

            JwksSource.Response first = source.fetch(null);
            JwksSource.Response cached = source.fetch(first.etag());

            assertThat(first.notModified()).isFalse();
            assertThat(first.body()).isEqualTo("{\"keys\":[]}");
            assertThat(first.etag()).isEqualTo("\"etag-1\"");
            assertThat(first.maxAge()).isEqualTo(Duration.ofMinutes(5));
            assertThat(cached.notModified()).isTrue();
            assertThat(receivedEtag.get()).isEqualTo("\"etag-1\"");
        } finally {
            server.stop(0);
        }
    }
}
