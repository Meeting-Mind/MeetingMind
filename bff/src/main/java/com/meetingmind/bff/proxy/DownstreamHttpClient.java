package com.meetingmind.bff.proxy;

import com.meetingmind.bff.auth.DownstreamUnauthorizedException;
import com.meetingmind.bff.config.DownstreamProxyProperties;
import com.meetingmind.bff.config.DownstreamProxyProperties.ServicePolicy;
import com.meetingmind.bff.config.InternalHttpClientFactory;
import com.meetingmind.bff.observability.DownstreamGuardMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class DownstreamHttpClient {

    private final Map<DownstreamService, RestClient> clients;
    private final Map<DownstreamService, DownstreamGuard> guards;

    public DownstreamHttpClient(DownstreamProxyProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this(properties, clock, null, meterRegistry);
    }

    public DownstreamHttpClient(
            DownstreamProxyProperties properties,
            Clock clock,
            InternalHttpClientFactory internalHttpClientFactory,
            MeterRegistry meterRegistry) {
        this.clients = new EnumMap<>(DownstreamService.class);
        this.guards = new EnumMap<>(DownstreamService.class);
        for (DownstreamService service : DownstreamService.values()) {
            ServicePolicy policy = properties.policy(service);
            clients.put(service, restClient(policy, internalHttpClientFactory));
            guards.put(service, new DownstreamGuard(
                    policy.maxConcurrent(),
                    policy.failureThreshold(),
                    policy.openDuration(),
                    clock,
                    new DownstreamGuardMetrics(meterRegistry, service.name().toLowerCase())));
        }
    }

    DownstreamHttpClient(
            Map<DownstreamService, RestClient> clients,
            Map<DownstreamService, DownstreamGuard> guards) {
        this.clients = new EnumMap<>(clients);
        this.guards = new EnumMap<>(guards);
    }

    public ProxyResponse execute(
            DownstreamService service,
            ProxyRequest request,
            String authorizationHeader) {
        RestClient client = require(clients, service);
        DownstreamGuard guard = require(guards, service);
        try {
            return guard.execute(() -> exchange(client, request, authorizationHeader));
        } catch (DownstreamUnauthorizedException exception) {
            throw exception;
        } catch (DownstreamCallFailure | DownstreamGuardRejectedException exception) {
            throw BffProxyException.unavailable(service);
        }
    }

    private ProxyResponse exchange(
            RestClient client, ProxyRequest request, String authorizationHeader) {
        try {
            RestClient.RequestBodySpec specification = client.method(request.method())
                    .uri(uriBuilder -> {
                        uriBuilder.path(request.path());
                        request.query().forEach((name, values) ->
                                values.forEach(value -> uriBuilder.queryParam(name, value)));
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader);
            if (request.contentType() != null && !request.contentType().isBlank()) {
                specification.header(HttpHeaders.CONTENT_TYPE, request.contentType());
            }
            if (request.accept() != null && !request.accept().isBlank()) {
                specification.header(HttpHeaders.ACCEPT, request.accept());
            }
            if (request.body().length > 0) {
                specification.body(request.body());
            }
            return specification.exchange((downstreamRequest, response) -> {
                if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                    throw new DownstreamUnauthorizedException();
                }
                if (response.getStatusCode().is5xxServerError()) {
                    throw new DownstreamCallFailure();
                }
                try {
                    return new ProxyResponse(
                            response.getStatusCode(),
                            response.getHeaders().getContentType(),
                            response.getHeaders().getCacheControl(),
                            response.getHeaders().getETag(),
                            response.getBody().readAllBytes());
                } catch (IOException exception) {
                    throw new DownstreamCallFailure();
                }
            });
        } catch (DownstreamUnauthorizedException | DownstreamCallFailure exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new DownstreamCallFailure();
        }
    }

    private RestClient restClient(
            ServicePolicy policy,
            InternalHttpClientFactory internalHttpClientFactory) {
        HttpClient.Builder builder = internalHttpClientFactory == null
                ? HttpClient.newBuilder()
                : internalHttpClientFactory.newBuilder();
        HttpClient httpClient = builder
                .connectTimeout(policy.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(policy.readTimeout());
        return RestClient.builder()
                .baseUrl(policy.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    private <T> T require(Map<DownstreamService, T> values, DownstreamService service) {
        T value = values.get(service);
        if (value == null) {
            throw new IllegalStateException("downstream service is not configured");
        }
        return value;
    }
}
