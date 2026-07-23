package com.meetingmind.demo.gateway;

import java.time.Instant;

// ponytail: retentionUntil/requestId는 In-process 구현에서는 아직 쓰이지 않지만, 작업지시서의
// STT 내부 API 계약(POST /internal/v1/transcriptions)과 필드를 맞춰둔 것. 원격 어댑터로 교체할 때
// 이 커맨드 타입과 호출부는 그대로 두고 구현체만 HTTP 클라이언트로 바꾸면 된다.
public record TranscriptionStartCommand(
        String meetingId,
        String roomName,
        String trackId,
        String participantDisplayName,
        Instant retentionUntil,
        String requestId
) {
}
