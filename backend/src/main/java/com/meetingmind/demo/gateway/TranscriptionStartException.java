package com.meetingmind.demo.gateway;

/**
 * 전사 시작에 실패했을 때 던진다. sessionId가 null이면 세션조차 만들어지지 않은 것이므로
 * 호출부(Core)가 자신이 이미 PROCESSING으로 바꿔둔 상태를 되돌려야 한다.
 */
public class TranscriptionStartException extends RuntimeException {

    private final String sessionId;

    public TranscriptionStartException(String sessionId, Throwable cause) {
        super(cause.getMessage(), cause);
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return sessionId;
    }
}
