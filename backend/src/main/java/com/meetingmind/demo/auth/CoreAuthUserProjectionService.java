package com.meetingmind.demo.auth;

import com.meetingmind.demo.auth.target.AuthUserMappingStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class CoreAuthUserProjectionService {

    private static final String SOURCE = "AUTH_PROJECTION";
    private static final long SOURCE_VERSION = 1L;

    private final AuthStore store;
    private final AuthUserMappingStore mappings;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CoreAuthUserProjectionService(
            AuthStore store,
            AuthUserMappingStore mappings,
            ObjectProvider<Clock> clockProvider
    ) {
        this(store, mappings, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    CoreAuthUserProjectionService(
            AuthStore store,
            AuthUserMappingStore mappings,
            Clock clock
    ) {
        this.store = store;
        this.mappings = mappings;
        this.clock = clock;
    }

    @Transactional
    public AuthUserResponse provision(UUID authUserId, ProjectionRequest request) {
        String expectedCoreUserId = coreUserId(authUserId);
        String email = AuthStore.normalizeEmail(request.email());
        String existingMapping = mappings.findCoreUserId(authUserId).orElse(null);
        if (existingMapping != null) {
            if (!expectedCoreUserId.equals(existingMapping)) {
                throw conflict();
            }
            AuthUser existing = store.findUserById(existingMapping).orElseThrow(this::conflict);
            if (!email.equals(AuthStore.normalizeEmail(existing.email()))) {
                throw conflict();
            }
            return AuthUserResponse.from(store.updateUserProfile(
                    existing.id(), request.displayName().trim(), request.pictureUrl(), Instant.now(clock)));
        }
        AuthUser current = store.findUserById(expectedCoreUserId).orElse(null);
        if (current == null) {
            current = store.createUserWithId(
                    expectedCoreUserId, email, request.displayName().trim(), request.pictureUrl(), Instant.now(clock));
        } else if (!email.equals(AuthStore.normalizeEmail(current.email()))) {
            throw conflict();
        } else {
            current = store.updateUserProfile(
                    current.id(), request.displayName().trim(), request.pictureUrl(), Instant.now(clock));
        }

        try {
            if (!mappings.create(authUserId, expectedCoreUserId, SOURCE, SOURCE_VERSION)) {
                String reconciled = mappings.findCoreUserId(authUserId).orElseThrow(this::conflict);
                if (!expectedCoreUserId.equals(reconciled)) {
                    throw conflict();
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
        return AuthUserResponse.from(current);
    }

    private String coreUserId(UUID authUserId) {
        return "user-" + authUserId;
    }

    private AuthException conflict() {
        return new AuthException(HttpStatus.CONFLICT, "AUTH_USER_MAPPING_CONFLICT", "사용자 계정을 안전하게 연결할 수 없습니다.");
    }

    public record ProjectionRequest(String email, String displayName, String pictureUrl) {
    }
}
