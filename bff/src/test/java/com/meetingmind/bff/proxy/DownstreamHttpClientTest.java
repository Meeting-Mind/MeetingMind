package com.meetingmind.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.meetingmind.bff.auth.DownstreamUnauthorizedException;
import com.meetingmind.bff.config.DownstreamProxyProperties;
import com.meetingmind.bff.config.DownstreamProxyProperties.ServicePolicy;
import com.meetingmind.bff.observability.DownstreamGuardMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

class DownstreamHttpClientTest {

    @Test
    void forwardsOnlyAllowlistedHeadersAndReturnsOnlySafeResponseHeaders() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DownstreamHttpClient client = client(builder.build());
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("cursor", "next value");
        server.expect(once(), requestTo("http://backend.example/api/v1/spaces?cursor=next%20value"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer internal-access"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andExpect(headerDoesNotExist("X-CSRF-TOKEN"))
                .andExpect(content().json("{\"name\":\"Space\"}"))
                .andRespond(withSuccess("{\"id\":\"space-id\"}", MediaType.APPLICATION_JSON)
                        .header("Cache-Control", "no-store")
                        .header("ETag", "\"v1\"")
                        .header("X-Internal-Secret", "must-not-forward"));

        ProxyResponse response = client.execute(
                DownstreamService.CORE,
                new ProxyRequest(
                        HttpMethod.POST,
                        "/api/v1/spaces",
                        query,
                        "application/json",
                        "application/json",
                        "{\"name\":\"Space\"}".getBytes()),
                "Bearer internal-access");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.cacheControl()).isEqualTo("no-store");
        assertThat(response.etag()).isEqualTo("\"v1\"");
        assertThat(new String(response.body())).contains("space-id");
        server.verify();
    }

    @Test
    void exposesUnauthorizedOnlyToTheTokenManager() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DownstreamHttpClient client = client(builder.build());
        server.expect(requestTo("http://backend.example/api/v1/spaces"))
                .andRespond(withUnauthorizedRequest().body("provider raw detail"));

        assertThatThrownBy(() -> client.execute(
                        DownstreamService.CORE,
                        request(HttpMethod.GET, "/api/v1/spaces"),
                        "Bearer expired"))
                .isInstanceOf(DownstreamUnauthorizedException.class)
                .hasMessageNotContaining("provider raw detail");
        server.verify();
    }

    @Test
    void normalizesFailuresWithDistinctServiceCodes() {
        for (DownstreamService service : DownstreamService.values()) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            DownstreamHttpClient client = client(service, builder.build());
            server.expect(requestTo("http://backend.example/api/v1/spaces"))
                    .andRespond(withServerError().body("provider raw detail"));

            assertThatThrownBy(() -> client.execute(
                            service,
                            request(HttpMethod.GET, "/api/v1/spaces"),
                            "Bearer access"))
                    .isInstanceOfSatisfying(BffProxyException.class, exception -> {
                        assertThat(exception.code()).isEqualTo(service.unavailableCode());
                        assertThat(exception.getMessage()).doesNotContain("provider raw detail");
                        assertThat(exception).hasNoCause();
                    });
            server.verify();
        }
    }

    @Test
    void enforcesTheConfiguredReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/spaces", exchange -> {
            try {
                Thread.sleep(250);
                byte[] body = "late response".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            ServicePolicy policy = new ServicePolicy(
                    origin,
                    Duration.ofMillis(100),
                    Duration.ofMillis(50),
                    2,
                    2,
                    Duration.ofSeconds(1));
            DownstreamHttpClient client = new DownstreamHttpClient(
                    new DownstreamProxyProperties(policy, policy, policy),
                    Clock.systemUTC(),
                    new SimpleMeterRegistry());

            assertThatThrownBy(() -> client.execute(
                            DownstreamService.CORE,
                            request(HttpMethod.GET, "/api/v1/spaces"),
                            "Bearer access"))
                    .isInstanceOfSatisfying(BffProxyException.class, exception ->
                            assertThat(exception.code()).isEqualTo("CORE_SERVICE_UNAVAILABLE"));
        } finally {
            server.stop(0);
        }
    }

    private DownstreamHttpClient client(RestClient restClient) {
        return client(DownstreamService.CORE, restClient);
    }

    private DownstreamHttpClient client(DownstreamService service, RestClient restClient) {
        Map<DownstreamService, RestClient> clients = new EnumMap<>(DownstreamService.class);
        clients.put(service, restClient);
        Map<DownstreamService, DownstreamGuard> guards = new EnumMap<>(DownstreamService.class);
        guards.put(
                service,
                new DownstreamGuard(
                        2,
                        3,
                        Duration.ofSeconds(30),
                        Clock.systemUTC(),
                        new DownstreamGuardMetrics(new SimpleMeterRegistry(), service.name().toLowerCase())));
        return new DownstreamHttpClient(clients, guards);
    }

    private ProxyRequest request(HttpMethod method, String path) {
        return new ProxyRequest(method, path, null, null, null, null);
    }
}
