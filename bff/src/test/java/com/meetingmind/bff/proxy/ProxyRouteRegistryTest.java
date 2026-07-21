package com.meetingmind.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ProxyRouteRegistryTest {

    private final ProxyRouteRegistry registry = new ProxyRouteRegistry();
    private final String spaceId = "space-" + UUID.randomUUID();
    private final String meetingId = "meeting-" + UUID.randomUUID();
    private final String reportId = "report-" + UUID.randomUUID();
    private final String taskId = "task-" + UUID.randomUUID();

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
    }

    @Test
    void allowsWorkspaceCrudCalendarAndReportRoutes() {
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/dashboard").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/calendar/events").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.PATCH, "/api/v1/spaces/" + spaceId).orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces/" + spaceId).orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.POST, "/api/v1/spaces/" + spaceId + "/tasks").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.PATCH,
                "/api/v1/spaces/" + spaceId + "/tasks/" + taskId).orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.GET,
                "/api/v1/meetings/" + meetingId + "/reports/" + reportId + "/download").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.POST,
                "/api/v1/meetings/" + meetingId + "/reports/" + reportId + "/ai-edits").orElseThrow().service())
                .isEqualTo(DownstreamService.AI);
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
