package com.meetingmind.bff.proxy;

public enum DownstreamService {
    CORE("meetingmind-core", "CORE_SERVICE_UNAVAILABLE", "업무 서비스에 일시적으로 연결할 수 없습니다."),
    AI("meetingmind-ai", "AI_PROVIDER_UNAVAILABLE", "AI 기능을 일시적으로 사용할 수 없습니다."),
    LIVEKIT("meetingmind-livekit", "LIVEKIT_SERVICE_UNAVAILABLE", "실시간 회의 연결을 일시적으로 사용할 수 없습니다.");

    private final String audience;
    private final String unavailableCode;
    private final String unavailableMessage;

    DownstreamService(String audience, String unavailableCode, String unavailableMessage) {
        this.audience = audience;
        this.unavailableCode = unavailableCode;
        this.unavailableMessage = unavailableMessage;
    }

    public String audience() {
        return audience;
    }

    public String unavailableCode() {
        return unavailableCode;
    }

    public String unavailableMessage() {
        return unavailableMessage;
    }
}
