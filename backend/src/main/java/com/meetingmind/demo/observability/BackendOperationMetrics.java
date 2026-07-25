package com.meetingmind.demo.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * `contracts/observability.md`가 Backend 추가 대상으로 지정한 custom metric을 한 곳에서 등록한다.
 *
 * <p>metric 이름은 Grafana dashboard(`infra/grafana/dashboards/meetingmind-stt-live.json`)가
 * 참조한다. 이름이 어긋나면 dashboard는 오류 없이 **빈 패널**이 되어 조용히 실패하므로
 * `BackendMetricNamesTest`가 Prometheus 노출명을 고정한다.
 *
 * <p>label에는 식별자를 넣지 않는다. meetingId나 userId를 label로 쓰면 시계열이 무한히 늘어나고
 * (cardinality 폭발) `NFR-LOG-01`의 식별 정보 비노출 원칙과도 충돌한다. outcome만 label로 둔다.
 */
@Component
public class BackendOperationMetrics {

    static final String TRANSCRIPTION_START = "meetingmind.stt.transcription.start";
    static final String TRANSCRIPTION_STOP = "meetingmind.stt.transcription.stop";
    static final String LIVEKIT_TOKEN_ISSUE = "meetingmind.livekit.token.issue";
    static final String REPORT_CONFIRM = "meetingmind.report.confirm";

    private final MeterRegistry meterRegistry;

    public BackendOperationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T recordTranscriptionStart(Supplier<T> operation) {
        return record(TRANSCRIPTION_START, operation);
    }

    public void recordTranscriptionStop(Runnable operation) {
        record(TRANSCRIPTION_STOP, () -> {
            operation.run();
            return null;
        });
    }

    public <T> T recordLiveKitTokenIssue(Supplier<T> operation) {
        return record(LIVEKIT_TOKEN_ISSUE, operation);
    }

    public <T> T recordReportConfirm(Supplier<T> operation) {
        return record(REPORT_CONFIRM, operation);
    }

    /**
     * 성공/실패를 모두 기록한다. 실패만 세면 실패율의 분모가 없고, 성공만 세면 장애가 보이지 않는다.
     * 예외는 그대로 다시 던져 호출부의 동작을 바꾸지 않는다.
     */
    private <T> T record(String name, Supplier<T> operation) {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            return operation.get();
        } catch (RuntimeException error) {
            outcome = "failure";
            throw error;
        } finally {
            meterRegistry.timer(name, "outcome", outcome)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }
}
