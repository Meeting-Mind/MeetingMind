package com.meetingmind.auth.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Replays durable withdrawal revocations after a BFF-to-Core completion response is lost. */
@Component
class WithdrawalOutboxReconciler {

    private static final String TEST_PRINCIPAL_HEADER = "X-MeetingMind-Test-Principal";

    private final JdbcAuthRepository repository;
    private final RestClient.Builder restClientBuilder;
    private final AuthRuntimeProperties.WithdrawalReconciliation properties;
    private final Clock clock;
    private final boolean testPrincipalAllowed;

    WithdrawalOutboxReconciler(
            JdbcAuthRepository repository,
            RestClient.Builder restClientBuilder,
            AuthRuntimeProperties properties,
            Clock clock,
            Environment environment
    ) {
        this.repository = repository;
        this.restClientBuilder = restClientBuilder;
        this.properties = properties.withdrawalReconciliation();
        this.clock = clock;
        this.testPrincipalAllowed = environment.acceptsProfiles(Profiles.of("local", "test", "integration"));
    }

    @Scheduled(fixedDelayString = "${meetingmind.auth.withdrawal-reconciliation.fixed-delay:30s}")
    void reconcileWithdrawals() {
        if (!properties.enabled() || properties.coreBaseUrl().isBlank()) {
            return;
        }
        for (JdbcAuthRepository.WithdrawalOutboxEvent event : repository.findUnpublishedWithdrawalEvents(50)) {
            try {
                RestClient.RequestBodySpec request = restClientBuilder
                        .baseUrl(properties.coreBaseUrl())
                        .build()
                        .post()
                        .uri("/internal/v1/core/account-withdrawal/reconcile");
                if (!properties.testWorkloadPrincipal().isBlank() && testPrincipalAllowed) {
                    request.header(TEST_PRINCIPAL_HEADER, properties.testWorkloadPrincipal());
                }
                request.header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(new ReconciliationRequest(event.eventId(), event.userId()))
                        .retrieve()
                        .toBodilessEntity();
                repository.markOutboxPublished(event.eventId(), Instant.now(clock));
            } catch (RestClientException exception) {
                repository.recordOutboxDeliveryFailure(event.eventId(), "CORE_DELIVERY_FAILED");
            }
        }
    }

    private record ReconciliationRequest(UUID eventId, UUID authUserId) {
    }
}
