package com.meetingmind.bff.proxy;

public enum DownstreamService {
    CORE("CORE_SERVICE_UNAVAILABLE", "업무 서비스에 일시적으로 연결할 수 없습니다."),
    AI("AI_PROVIDER_UNAVAILABLE", "AI 기능을 일시적으로 사용할 수 없습니다."),
    LIVEKIT("LIVEKIT_SERVICE_UNAVAILABLE", "실시간 회의 연결을 일시적으로 사용할 수 없습니다.");

    private final String unavailableCode;
    private final String unavailableMessage;

    DownstreamService(String unavailableCode, String unavailableMessage) {
        this.unavailableCode = unavailableCode;
        this.unavailableMessage = unavailableMessage;
    }

    public String unavailableCode() {
        return unavailableCode;
    }

    public String unavailableMessage() {
        return unavailableMessage;
    }
}
