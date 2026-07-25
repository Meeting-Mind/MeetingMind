package com.meetingmind.bff.observability;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrometheusScrapeController {

    private final MeterRegistry meterRegistry;
    private final ObjectProvider<PrometheusMeterRegistry> prometheusMeterRegistryProvider;

    public PrometheusScrapeController(
            MeterRegistry meterRegistry,
            ObjectProvider<PrometheusMeterRegistry> prometheusMeterRegistryProvider) {
        this.meterRegistry = meterRegistry;
        this.prometheusMeterRegistryProvider = prometheusMeterRegistryProvider;
    }

    @GetMapping(value = "/actuator/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> scrape() {
        PrometheusMeterRegistry prometheusMeterRegistry = prometheusMeterRegistryProvider.getIfAvailable();
        if (prometheusMeterRegistry != null) {
            return ResponseEntity.ok(prometheusMeterRegistry.scrape());
        }

        StringBuilder body = new StringBuilder();
        for (Meter meter : meterRegistry.getMeters()) {
            body.append(meter.getId().getName()).append('\n');
            for (Measurement measurement : meter.measure()) {
                body.append("  ")
                        .append(measurement.getStatistic().name().toLowerCase(Locale.ROOT))
                        .append('=')
                        .append(measurement.getValue())
                        .append('\n');
            }
        }
        return ResponseEntity.ok(body.toString());
    }
}
