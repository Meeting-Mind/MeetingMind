package com.meetingmind.stt.observability;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class RequestTrace {

    public static final String HEADER_NAME = "X-Request-ID";
    private static final String MDC_KEY = "traceId";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private RequestTrace() {
    }

    public static String normalize(String candidate) {
        String normalized = candidate == null ? "" : candidate.trim();
        return SAFE_TRACE_ID.matcher(normalized).matches() ? normalized : newTraceId();
    }

    public static String currentOrCreate() {
        String current = MDC.get(MDC_KEY);
        if (current != null && !current.isBlank()) {
            return current;
        }
        String traceId = newTraceId();
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    public static void bind(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
