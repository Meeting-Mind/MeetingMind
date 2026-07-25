package com.meetingmind.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetingmind.bff.observability.BffRolloutMetrics;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class BffHealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BffRolloutMetrics rolloutMetrics;

    @Test
    void exposesLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void exposesPrometheusMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.blankOrNullString())));
    }

    /**
     * `/actuator/prometheus`가 실제 Prometheus 노출 형식인지 확인한다.
     *
     * <p>`PrometheusScrapeController`에는 `PrometheusMeterRegistry`가 없을 때 동작하는 fallback이
     * 있고, 그 출력은 Prometheus가 파싱할 수 없는 형식이다(dot 이름 + `value=`). Spring Boot는
     * 테스트에서 metrics export auto-configuration을 기본으로 끄기 때문에 `@AutoConfigureObservability`
     * 없이는 항상 이 fallback이 응답한다. 기존 `exposesPrometheusMetrics`는 "비어 있지 않음"만
     * 단정해 이 상태를 통과시키고 있었다.
     */
    @Test
    void prometheusEndpointReturnsPrometheusExpositionFormat() throws Exception {
        String body = scrape();

        assertThat(body).startsWith("# HELP");
        // fallback 출력에는 dot 이름이 그대로 남는다. 그 형태가 아님을 함께 고정한다.
        assertThat(body).contains("# TYPE");
        // fallback은 metric 이름을 dot 그대로 줄 맨 앞에 찍는다. 그 형태가 아님을 고정한다.
        // (label 값에는 Java 패키지명 때문에 dot이 정상적으로 들어가므로 줄 시작으로만 판정한다.)
        assertThat(body.lines().filter(line -> line.startsWith("meetingmind.")).toList())
                .as("fallback 형식(dot 이름)이 아니어야 한다")
                .isEmpty();
    }

    /**
     * Grafana dashboard(`infra/grafana/dashboards/meetingmind-bff.json`)가 쓰는 metric 이름을
     * 고정한다. Micrometer는 dot을 underscore로 바꾸고 Counter에 `_total`, Timer에
     * `_seconds_count`/`_seconds_sum`을 붙인다. 이 이름이 어긋나면 dashboard는 오류 없이
     * **빈 패널**이 되어 조용히 실패하므로 여기서 잡는다.
     *
     * <p>Micrometer는 meter를 처음 쓸 때 등록하므로 단정 전에 한 번씩 기록해 둔다.
     */
    @Test
    void exposesMetricNamesUsedByGrafanaDashboard() throws Exception {
        rolloutMetrics.recordBrowserRequest("proxy", "success", 1_000_000L);
        rolloutMetrics.recordRefresh("success");
        rolloutMetrics.recordSessionInvalid();

        String body = scrape();

        assertThat(body)
                .contains("meetingmind_bff_browser_requests_seconds_count")
                .contains("meetingmind_bff_browser_requests_seconds_sum")
                .contains("meetingmind_bff_refresh_total")
                .contains("meetingmind_bff_session_invalid_total")
                .contains("meetingmind_bff_downstream_guard_open");
    }

    private String scrape() throws Exception {
        return mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
