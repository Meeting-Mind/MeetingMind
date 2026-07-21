package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.demo.auth.target.AuthUserMappingStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreAuthUserProjectionServiceTest {

    private final InMemoryAuthStore store = new InMemoryAuthStore();
    private final InMemoryMappings mappings = new InMemoryMappings();
    private final CoreAuthUserProjectionService service = new CoreAuthUserProjectionService(
            store,
            mappings,
            Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void provisionsDeterministicCoreUserAndRefreshesMutableProfileProjection() {
        UUID authUserId = UUID.fromString("5d9e9b91-0b7d-4a75-8d3b-1fdf1ab257cb");

        AuthUserResponse first = service.provision(
                authUserId,
                new CoreAuthUserProjectionService.ProjectionRequest(
                        "member@meetingmind.test", "Member", null));
        AuthUserResponse replay = service.provision(
                authUserId,
                new CoreAuthUserProjectionService.ProjectionRequest(
                        "member@meetingmind.test", "Changed by replay", "https://example.test/image.png"));

        assertThat(first.id()).isEqualTo("user-" + authUserId);
        assertThat(replay.displayName()).isEqualTo("Changed by replay");
        assertThat(replay.pictureUrl()).isEqualTo("https://example.test/image.png");
        assertThat(mappings.coreUserIdByAuthUser).containsEntry(authUserId, first.id());
        assertThat(mappings.sourceByAuthUser).containsEntry(authUserId, "AUTH_PROJECTION:1");
    }

    @Test
    void rejectsExistingDeterministicCoreIdWithDifferentEmail() {
        UUID authUserId = UUID.fromString("1b7994b5-2cf1-4d2d-bbd4-74c3c7378c83");
        store.createUserWithId(
                "user-" + authUserId,
                "other@meetingmind.test",
                "Other",
                null,
                Instant.parse("2026-07-20T00:00:00Z")
        );

        assertThatThrownBy(() -> service.provision(
                authUserId,
                new CoreAuthUserProjectionService.ProjectionRequest(
                        "member@meetingmind.test", "Member", null)))
                .isInstanceOf(AuthException.class)
                .hasMessage("사용자 계정을 안전하게 연결할 수 없습니다.");
    }

    private static final class InMemoryMappings implements AuthUserMappingStore {

        private final Map<UUID, String> coreUserIdByAuthUser = new HashMap<>();
        private final Map<UUID, String> sourceByAuthUser = new HashMap<>();

        @Override
        public Optional<String> findCoreUserId(UUID authUserId) {
            return Optional.ofNullable(coreUserIdByAuthUser.get(authUserId));
        }

        @Override
        public boolean create(UUID authUserId, String coreUserId, String source, long sourceVersion) {
            if (coreUserIdByAuthUser.containsKey(authUserId)) {
                return false;
            }
            coreUserIdByAuthUser.put(authUserId, coreUserId);
            sourceByAuthUser.put(authUserId, source + ":" + sourceVersion);
            return true;
        }
    }
}
