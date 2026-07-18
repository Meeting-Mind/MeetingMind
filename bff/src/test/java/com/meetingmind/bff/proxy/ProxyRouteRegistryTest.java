package com.meetingmind.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ProxyRouteRegistryTest {

    private final ProxyRouteRegistry registry = new ProxyRouteRegistry();
    private final String spaceId = "space-" + UUID.randomUUID();
    private final String meetingId = "meeting-" + UUID.randomUUID();

    @Test
    void classifiesCoreAiAndLiveKitRoutes() {
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(
                                HttpMethod.POST,
                                "/api/v1/meetings/" + meetingId + "/ai/chat")
                        .orElseThrow()
                        .service())
                .isEqualTo(DownstreamService.AI);
        assertThat(registry.resolve(
                                HttpMethod.POST,
                                "/api/v1/meetings/" + meetingId + "/livekit-token")
                        .orElseThrow()
                        .service())
                .isEqualTo(DownstreamService.LIVEKIT);
    }

    @Test
    void rejectsUnknownMethodsAuthRoutesAndWrongEntityIdVariables() {
        assertThat(registry.resolve(HttpMethod.PUT, "/api/v1/spaces")).isEmpty();
        assertThat(registry.resolve(HttpMethod.POST, "/api/v1/auth/refresh")).isEmpty();
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces/not-a-uuid/meetings")).isEmpty();
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces/meeting-" + UUID.randomUUID() + "/meetings"))
                .isEmpty();
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/dashboard")).isEmpty();
    }

    @Test
    void rejectsEncodedOrMatrixPathVariants() {
        assertThat(registry.resolve(
                        HttpMethod.GET,
                        "/api/v1/spaces/" + spaceId + "%2Fmeetings"))
                .isEmpty();
        assertThat(registry.resolve(
                        HttpMethod.GET,
                        "/api/v1/spaces/" + spaceId + ";x=1/meetings"))
                .isEmpty();
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1//spaces")).isEmpty();
    }
}
