package com.meetingmind.bff.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BffProxyController controller = new BffProxyController(
                new ProxyRouteRegistry(), downstreamClient, tokenManager);
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
                .execute(any(), any(DownstreamService.class), any(AuthorizedDownstreamCall.class));
        when(downstreamClient.execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access")))
                .thenReturn(new ProxyResponse(
                        HttpStatus.OK,
                        MediaType.APPLICATION_JSON,
                        "no-store",
                        "\"v1\"",
                        "attachment; filename=meeting-report.md",
                        "{\"items\":[]}".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/v1/spaces").queryParam("cursor", "next"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"v1\""))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=meeting-report.md"));

        ArgumentCaptor<ProxyRequest> request = ArgumentCaptor.forClass(ProxyRequest.class);
        verify(downstreamClient).execute(
                eq(DownstreamService.CORE), request.capture(), eq("Bearer internal-access"));
        org.assertj.core.api.Assertions.assertThat(request.getValue().query().getFirst("cursor"))
                .isEqualTo("next");
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
    @SuppressWarnings("unchecked")
    void proxiesTaskDeleteRouteThroughTheCoreService() throws Exception {
        String spaceId = "space-" + java.util.UUID.randomUUID();
        String taskId = "task-" + java.util.UUID.randomUUID();
        doAnswer(invocation -> {
                    AuthorizedDownstreamCall<ProxyResponse> call = invocation.getArgument(2);
                    return call.execute("Bearer internal-access");
                })
                .when(tokenManager)
                .execute(any(), any(DownstreamService.class), any(AuthorizedDownstreamCall.class));
        when(downstreamClient.execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access")))
                .thenReturn(new ProxyResponse(HttpStatus.OK, MediaType.APPLICATION_JSON, null, null, null,
                        "{\"deleted\":true}".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(delete("/api/v1/spaces/" + spaceId + "/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(downstreamClient).execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void proxiesMeetingAiRouteThroughTheCoreService() throws Exception {
        String meetingId = "meeting-" + java.util.UUID.randomUUID();
        doAnswer(invocation -> {
                    AuthorizedDownstreamCall<ProxyResponse> call = invocation.getArgument(2);
                    return call.execute("Bearer internal-access");
                })
                .when(tokenManager)
                .execute(any(), any(DownstreamService.class), any(AuthorizedDownstreamCall.class));
        when(downstreamClient.execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access")))
                .thenReturn(new ProxyResponse(HttpStatus.OK, MediaType.APPLICATION_JSON, null, null, null,
                        "{\"answer\":\"회의 근거입니다.\",\"sources\":[],\"unsupported\":false,\"model\":\"test\"}"
                                .getBytes(StandardCharsets.UTF_8)));

        mvc.perform(post("/api/v1/meetings/" + meetingId + "/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"무엇을 결정했나요?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("회의 근거입니다."));

        verify(downstreamClient).execute(eq(DownstreamService.CORE), any(), eq("Bearer internal-access"));
    }

    @Test
    void returnsTheServiceSpecificCommonErrorShape() throws Exception {
        String meetingId = "meeting-" + java.util.UUID.randomUUID();
        when(tokenManager.execute(any(), any(DownstreamService.class), any(AuthorizedDownstreamCall.class)))
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
