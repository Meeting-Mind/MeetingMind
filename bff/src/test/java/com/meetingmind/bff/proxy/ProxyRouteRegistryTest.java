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
        ProxyRoute coreRoute = registry.resolve(HttpMethod.GET, "/api/v1/spaces").orElseThrow();
        assertThat(coreRoute.service()).isEqualTo(DownstreamService.CORE);
        assertThat(coreRoute.audience()).isEqualTo("meetingmind-core");
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/meetings").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        ProxyRoute aiRoute = registry.resolve(
                        HttpMethod.POST,
                        "/api/v1/meetings/" + meetingId + "/ai/chat")
                .orElseThrow();
        assertThat(aiRoute.service()).isEqualTo(DownstreamService.AI);
        assertThat(aiRoute.audience()).isEqualTo("meetingmind-core");

        ProxyRoute liveKitRoute = registry.resolve(
                        HttpMethod.POST,
                        "/api/v1/meetings/" + meetingId + "/livekit-token")
                .orElseThrow();
        assertThat(liveKitRoute.service()).isEqualTo(DownstreamService.LIVEKIT);
        assertThat(liveKitRoute.audience()).isEqualTo("meetingmind-core");

        ProxyRoute transcriptionRoute = registry.resolve(
                        HttpMethod.POST,
                        "/api/v1/meetings/" + meetingId + "/transcription/stop")
                .orElseThrow();
        assertThat(transcriptionRoute.service()).isEqualTo(DownstreamService.LIVEKIT);
        assertThat(transcriptionRoute.audience()).isEqualTo("meetingmind-core");
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
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/glossary/categories").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.PATCH, "/api/v1/spaces/" + spaceId).orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces/" + spaceId).orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces/" + spaceId + "/knowledge/graph").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.GET, "/api/v1/spaces/" + spaceId + "/ai/usage").orElseThrow().service())
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
        assertThat(registry.resolve(HttpMethod.POST,
                "/api/v1/meetings/" + meetingId + "/invitations").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
        assertThat(registry.resolve(HttpMethod.POST,
                "/api/v1/meetings/" + meetingId + "/invitations/meeting-invitation-" + UUID.randomUUID() + "/accept").orElseThrow().service())
                .isEqualTo(DownstreamService.CORE);
    }

    @Test
    void allowsSeededDomainTermIds() {
        assertThat(registry.resolve(
                        HttpMethod.DELETE,
                        "/api/v1/spaces/" + spaceId + "/terms/term-naver-ai-93fadb8e79f1759359feaa3460468bb6"))
                .isPresent();
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
