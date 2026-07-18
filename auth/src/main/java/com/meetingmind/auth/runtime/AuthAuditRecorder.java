package com.meetingmind.auth.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuthAuditRecorder {

    private final JdbcAuthRepository repository;
    private final Clock clock;

    AuthAuditRecorder(JdbcAuthRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void failure(String eventType, String reasonCode, String traceId) {
        repository.insertAudit(
                null,
                null,
                eventType,
                reasonCode,
                Instant.now(clock),
                traceId,
                Map.of()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void failure(UUID userId, String eventType, String reasonCode, String traceId) {
        repository.insertAudit(
                userId,
                null,
                eventType,
                reasonCode,
                Instant.now(clock),
                traceId,
                Map.of()
        );
    }
}
