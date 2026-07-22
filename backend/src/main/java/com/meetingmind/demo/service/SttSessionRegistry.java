package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.TranscriptEntryResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class SttSessionRegistry {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path TRANSCRIPT_DIR = Path.of("output", "transcripts");

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    // ponytail: 데모 규모 동시 회의 수 가정, 방 하나당 파일 락 하나. 방 수가 많아지면 락 맵을 정리(evict)하는 로직 추가.
    private final Map<String, Object> roomFileLocks = new ConcurrentHashMap<>();
    private final WorkspaceDomainService workspaceDomainService;
    private final SttStreamClientFactory streamClientFactory;

    public SttSessionRegistry(
            WorkspaceDomainService workspaceDomainService,
            SttStreamClientFactory streamClientFactory
    ) {
        this.workspaceDomainService = workspaceDomainService;
        this.streamClientFactory = streamClientFactory;
    }

    public String create(String roomName, String displayName) {
        return create(roomName, displayName, null);
    }

    public String createMeetingSession(String meetingId, String roomName, String displayName) {
        return create(roomName, displayName, meetingId);
    }

    private String create(String roomName, String displayName, String meetingId) {
        String sessionId = UUID.randomUUID().toString();
        List<TranscriptEntryResponse> transcript = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Boolean> failed = new AtomicReference<>(false);
        AtomicReference<String> partialText = new AtomicReference<>();
        Instant startedAt = Instant.now();
        String speakerLabel = "stt-" + sessionId;

        Consumer<String> onFinal = text -> {
            // Clova가 확정 신호(epFlag=true)만 보내고 text는 비워서 보내는 경우가 있어서,
            // 그럴 땐 직전까지 쌓인 partial 텍스트를 최종 결과로 사용한다.
            String finalText = (text == null || text.isBlank()) ? partialText.get() : text;
            partialText.set(null);
            if (finalText == null || finalText.isBlank()) {
                return;
            }
            transcript.add(new TranscriptEntryResponse(LocalTime.now().format(TIME_FORMAT), displayName, finalText));
            if (meetingId != null) {
                int endMs = (int) Duration.between(startedAt, Instant.now()).toMillis();
                int startMs = Math.max(0, endMs - 1_000);
                workspaceDomainService.appendTranscriptSegment(
                        meetingId, speakerLabel, displayName, startMs, Math.max(startMs, endMs), finalText
                );
            } else {
                appendToTranscriptFile(roomName, displayName, finalText);
            }
        };
        Consumer<String> onPartial = partialText::set;
        Consumer<Throwable> onError = ignored -> {
            if (meetingId != null && failed.compareAndSet(false, true)) {
                workspaceDomainService.failMeetingTranscript(meetingId);
            }
        };

        // 세션 하나가 통째로 침묵-발화를 반복하는 동안, 실제 Clova gRPC 연결은 침묵 5초마다
        // 끊고 새로 여는 방식으로 문장 경계를 확정 짓는다. 자세한 이유는 클래스 주석 참고.
        SttStreamClient client = new SilenceSegmentingSttStreamClient(streamClientFactory, onFinal, onPartial, onError);

        sessions.put(sessionId, new SessionState(
                meetingId, roomName, speakerLabel, displayName, client, transcript, new AtomicReference<>(), failed, partialText
        ));
        return sessionId;
    }

    // ponytail: 화자(트랙)당 세션 하나라 partial은 세션별로 최대 1개만 들고 있으면 됨. 여러 명이 동시에
    // 말하는 회의라도 세션이 speakerLabel 단위로 이미 분리되어 있어 이 이상 복잡한 상태 관리는 불필요.
    public List<PartialTranscript> getMeetingPartials(String meetingId) {
        List<PartialTranscript> partials = new ArrayList<>();
        for (SessionState state : sessions.values()) {
            if (!meetingId.equals(state.meetingId())) {
                continue;
            }
            String text = state.partialText().get();
            if (text != null && !text.isBlank()) {
                partials.add(new PartialTranscript(state.speakerLabel(), state.displayName(), text));
            }
        }
        return partials;
    }

    public record PartialTranscript(String speakerLabel, String speakerName, String text) {
    }

    private void appendToTranscriptFile(String roomName, String displayName, String text) {
        Path file = TRANSCRIPT_DIR.resolve(sanitizeFileName(roomName) + ".txt");
        Object lock = roomFileLocks.computeIfAbsent(roomName, key -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(TRANSCRIPT_DIR);
                Files.writeString(
                        file,
                        displayName + ": " + text + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static String sanitizeFileName(String roomName) {
        return roomName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public SttStreamClient getStreamClient(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? null : state.client();
    }

    public void setEgressId(String sessionId, String egressId) {
        require(sessionId).egressId().set(egressId);
    }

    public String getEgressId(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? null : state.egressId().get();
    }

    public List<TranscriptEntryResponse> getSessionTranscript(String sessionId) {
        List<TranscriptEntryResponse> transcript = require(sessionId).transcript();
        synchronized (transcript) {
            return List.copyOf(transcript);
        }
    }

    // ponytail: 세션 수가 적은 데모 규모라 방별 인덱스 없이 전체 스캔. 세션이 많아지면 roomName -> sessionId 인덱스 추가.
    public List<TranscriptEntryResponse> getRoomTranscript(String roomName) {
        List<TranscriptEntryResponse> merged = new ArrayList<>();

        for (SessionState state : sessions.values()) {
            if (!state.roomName().equals(roomName)) {
                continue;
            }
            synchronized (state.transcript()) {
                merged.addAll(state.transcript());
            }
        }

        merged.sort(Comparator.comparing(TranscriptEntryResponse::time));
        return merged;
    }

    public boolean belongsToMeeting(String sessionId, String meetingId) {
        SessionState state = sessions.get(sessionId);
        return state != null && meetingId.equals(state.meetingId());
    }

    public void onEgressClosed(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }

        state.client().finishAudio();
        if (state.meetingId() != null) {
            close(sessionId);
        }
    }

    public void close(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            state.client().close();
            if (state.meetingId() != null && !state.failed().get()) {
                workspaceDomainService.completeMeetingTranscript(state.meetingId());
            }
        }
    }

    public void failAndClose(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            state.client().close();
            if (state.meetingId() != null && state.failed().compareAndSet(false, true)) {
                workspaceDomainService.failMeetingTranscript(state.meetingId());
            }
        }
    }

    private SessionState require(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new NoSuchElementException("세션을 찾을 수 없습니다: " + sessionId);
        }
        return state;
    }

    private record SessionState(
            String meetingId,
            String roomName,
            String speakerLabel,
            String displayName,
            SttStreamClient client,
            List<TranscriptEntryResponse> transcript,
            AtomicReference<String> egressId,
            AtomicReference<Boolean> failed,
            AtomicReference<String> partialText
    ) {
    }
}
