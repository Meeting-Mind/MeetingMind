package com.meetingmind.auth.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WithdrawalOutboxReconcilerTest {

    private static final String CORE_URL = "http://core.test";
    private static final String AUTH_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-auth";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void marksOutboxPublishedOnlyAfterCoreAcceptsTheReconciliation() {
        JdbcAuthRepository repository = mock(JdbcAuthRepository.class);
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findUnpublishedWithdrawalEvents(50))
                .thenReturn(List.of(new JdbcAuthRepository.WithdrawalOutboxEvent(eventId, userId)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URI.create(CORE_URL + "/internal/v1/core/account-withdrawal/reconcile")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-MeetingMind-Test-Principal", AUTH_PRINCIPAL))
                .andExpect(content().json("""
                        {"eventId":"%s","authUserId":"%s"}
                        """.formatted(eventId, userId)))
                .andRespond(withNoContent());

        reconciler(repository, builder).reconcileWithdrawals();

        verify(repository).markOutboxPublished(eventId, Instant.now(CLOCK));
        server.verify();
    }

    @Test
    void recordsFailureAndLeavesOutboxUnpublishedWhenCoreRejectsTheDelivery() {
        JdbcAuthRepository repository = mock(JdbcAuthRepository.class);
        UUID eventId = UUID.randomUUID();
        when(repository.findUnpublishedWithdrawalEvents(50))
                .thenReturn(List.of(new JdbcAuthRepository.WithdrawalOutboxEvent(eventId, UUID.randomUUID())));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URI.create(CORE_URL + "/internal/v1/core/account-withdrawal/reconcile")))
                .andRespond(withServerError());

        reconciler(repository, builder).reconcileWithdrawals();

        verify(repository).recordOutboxDeliveryFailure(eq(eventId), eq("CORE_DELIVERY_FAILED"));
        verify(repository, org.mockito.Mockito.never()).markOutboxPublished(any(), any());
        server.verify();
    }

    private WithdrawalOutboxReconciler reconciler(JdbcAuthRepository repository, RestClient.Builder builder) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        return new WithdrawalOutboxReconciler(
                repository,
                builder,
                properties(),
                CLOCK,
                environment
        );
    }

    private AuthRuntimeProperties properties() {
        return new AuthRuntimeProperties(
                "test-only-refresh-hash-secret-32-bytes-minimum",
                Duration.ofDays(14),
                10,
                Duration.ofMinutes(10),
                Duration.ofMinutes(11),
                new AuthRuntimeProperties.Google(
                        List.of("meetingmind-test-client"),
                        URI.create("https://example.test/jwks"),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofHours(1)
                ),
                new AuthRuntimeProperties.Workload(
                        java.util.Set.of(AUTH_PRINCIPAL),
                        java.util.Set.of(AUTH_PRINCIPAL),
                        true
                ),
                new AuthRuntimeProperties.WithdrawalReconciliation(
                        true,
                        CORE_URL,
                        Duration.ofSeconds(30),
                        AUTH_PRINCIPAL
                )
        );
    }
}
