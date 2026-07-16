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
        Instant startedAt = Instant.now();
        String speakerLabel = "stt-" + sessionId;

        SttStreamClient client = streamClientFactory.create(text -> {
            transcript.add(new TranscriptEntryResponse(LocalTime.now().format(TIME_FORMAT), displayName, text));
            if (meetingId != null) {
                int endMs = (int) Duration.between(startedAt, Instant.now()).toMillis();
                int startMs = Math.max(0, endMs - 1_000);
                workspaceDomainService.appendTranscriptSegment(
                        meetingId, speakerLabel, displayName, startMs, Math.max(startMs, endMs), text
                );
            } else {
                appendToTranscriptFile(roomName, displayName, text);
            }
        }, ignored -> {
            if (meetingId != null && failed.compareAndSet(false, true)) {
                workspaceDomainService.failMeetingTranscript(meetingId);
            }
        });

        sessions.put(sessionId, new SessionState(
                meetingId, roomName, client, transcript, new AtomicReference<>(), failed
        ));
        return sessionId;
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
            SttStreamClient client,
            List<TranscriptEntryResponse> transcript,
            AtomicReference<String> egressId,
            AtomicReference<Boolean> failed
    ) {
    }
}
