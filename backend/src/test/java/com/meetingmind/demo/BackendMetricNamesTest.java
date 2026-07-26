package com.meetingmind.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetingmind.demo.observability.BackendOperationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Grafana dashboard(`infra/grafana/dashboards/meetingmind-stt-live.json`)가 쓰는 Prometheus
 * metric 이름을 고정한다.
 *
 * <p>이름이 어긋나면 Grafana는 오류를 내지 않고 **빈 패널**을 보여준다. 즉 조용히 실패한다.
 * 그래서 dashboard 쪽이 아니라 여기서 잡는다.
 *
 * <p>`@AutoConfigureObservability`가 필요한 이유: Spring Boot는 테스트에서 metrics export
 * auto-configuration을 기본으로 끈다. 그러면 `PrometheusScrapeController`의 fallback이
 * Prometheus가 파싱할 수 없는 텍스트를 반환하고, 이름 단정은 의미를 잃는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class BackendMetricNamesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BackendOperationMetrics metrics;

    @Test
    void exposesSttAndLiveKitMetricNamesUsedByGrafanaDashboard() throws Exception {
        // Micrometer는 meter를 처음 쓸 때 등록한다. 단정 전에 성공/실패를 한 번씩 흘린다.
        metrics.recordTranscriptionStart(() -> "handle");
        metrics.recordTranscriptionStop(() -> { });
        metrics.recordLiveKitTokenIssue(() -> "token");
        metrics.recordReportConfirm(() -> "report");

        String body = scrape();

        // 형식이 Prometheus가 아니면 이름 단정이 통과해도 의미가 없다. 형식을 먼저 고정한다.
        assertThat(body).startsWith("# HELP");

        assertThat(body)
                .contains("meetingmind_stt_transcription_start_seconds_count")
                .contains("meetingmind_stt_transcription_stop_seconds_count")
                .contains("meetingmind_livekit_token_issue_seconds_count")
                .contains("meetingmind_report_confirm_seconds_count");
        // 지연 패널은 sum/count로 평균을 낸다. sum이 없으면 패널이 빈다.
        assertThat(body)
                .contains("meetingmind_stt_transcription_start_seconds_sum")
                .contains("meetingmind_stt_transcription_stop_seconds_sum")
                .contains("meetingmind_livekit_token_issue_seconds_sum")
                .contains("meetingmind_report_confirm_seconds_sum");
    }

    @Test
    void recordsFailureOutcomeAndRethrows() throws Exception {
        // 실패만 세면 실패율의 분모가 없고, 성공만 세면 장애가 안 보인다. 둘 다 기록해야 한다.
        assertThatThrownBy(() -> metrics.recordLiveKitTokenIssue(() -> {
            throw new IllegalStateException("provider down");
        }))
                .as("계측이 예외를 삼키면 호출부 동작이 바뀐다")
                .isInstanceOf(IllegalStateException.class);

        String body = scrape();

        assertThat(body).contains("outcome=\"failure\"");
    }

    private String scrape() throws Exception {
        return mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
