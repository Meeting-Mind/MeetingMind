package com.meetingmind.bff.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetingmind.bff.auth.AuthorizedDownstreamCall;
import com.meetingmind.bff.auth.BffAuthExceptionHandler;
import com.meetingmind.bff.observability.BffRolloutMetrics;
import com.meetingmind.bff.auth.BffTokenManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BffProxyControllerTest {

    private final DownstreamHttpClient downstreamClient = mock(DownstreamHttpClient.class);
    private final BffTokenManager tokenManager = mock(BffTokenManager.class);
    private final AiUsageRecorder aiUsageRecorder = mock(AiUsageRecorder.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BffProxyController controller = new BffProxyController(
                new ProxyRouteRegistry(), downstreamClient, tokenManager, aiUsageRecorder);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new BffAuthExceptionHandler(
                        new BffRolloutMetrics(new SimpleMeterRegistry())))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void proxiesAnAllowedRouteThroughTheTokenManager() throws Exception {
        doAnswer(invocation -> {
                    AuthorizedDownstreamCall<ProxyResponse> call = invocation.getArgument(2);
                    return call.execute("Bearer internal-access");
                })
                .when(tokenManager)
                .execute(any(), anyString(), any(AuthorizedDownstreamCall.class));
        when(downstreamClient.execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access")))
                .thenReturn(new ProxyResponse(
                        HttpStatus.OK,
                        MediaType.APPLICATION_JSON,
                        "no-store",
                        "\"v1\"",
                        "{\"items\":[]}".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/v1/spaces").queryParam("cursor", "next"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""));

        ArgumentCaptor<ProxyRequest> request = ArgumentCaptor.forClass(ProxyRequest.class);
        verify(downstreamClient).execute(
                eq(DownstreamService.CORE), request.capture(), eq("Bearer internal-access"));
        verify(aiUsageRecorder).recordIfPresent(any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(request.getValue().query().getFirst("cursor"))
                .isEqualTo("next");
    }

    @Test
    @SuppressWarnings("unchecked")
    void proxiesInstantMeetingCreationRoute() throws Exception {
        doAnswer(invocation -> {
                    AuthorizedDownstreamCall<ProxyResponse> call = invocation.getArgument(2);
                    return call.execute("Bearer internal-access");
                })
                .when(tokenManager)
                .execute(any(), anyString(), any(AuthorizedDownstreamCall.class));
        when(downstreamClient.execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access")))
                .thenReturn(new ProxyResponse(
                        HttpStatus.CREATED,
                        MediaType.APPLICATION_JSON,
                        null,
                        null,
                        "{\"meetingId\":\"meeting-123\"}".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(post("/api/v1/spaces/space-123e4567-e89b-12d3-a456-426614174000/instant-meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"meetingId\":\"meeting-123\"}"));

        ArgumentCaptor<ProxyRequest> request = ArgumentCaptor.forClass(ProxyRequest.class);
        verify(downstreamClient).execute(
                eq(DownstreamService.CORE), request.capture(), eq("Bearer internal-access"));
        verify(aiUsageRecorder).recordIfPresent(any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(request.getValue().path())
                .isEqualTo("/api/v1/spaces/space-123e4567-e89b-12d3-a456-426614174000/instant-meetings");
    }

    @Test
    void rejectsUnknownUrlAndMethodBeforeTokenOrDownstreamUse() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_ALLOWED"));
        mvc.perform(get("/api/v1/http://attacker.example"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_ALLOWED"));
        mvc.perform(put("/api/v1/spaces").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_ALLOWED"));

        verifyNoInteractions(tokenManager, downstreamClient);
    }

    @Test
    void returnsTheServiceSpecificCommonErrorShape() throws Exception {
        String meetingId = "meeting-" + java.util.UUID.randomUUID();
        when(tokenManager.execute(any(), anyString(), any()))
                .thenThrow(BffProxyException.unavailable(DownstreamService.AI));

        mvc.perform(post("/api/v1/meetings/" + meetingId + "/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AI 기능을 일시적으로 사용할 수 없습니다."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
