package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.TranscriptEntryResponse;
import java.time.LocalTime;
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

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public String create(String roomName, String speaker) {
        String sessionId = UUID.randomUUID().toString();
        List<TranscriptEntryResponse> transcript = Collections.synchronizedList(new ArrayList<>());

        ClovaNestStreamClient client = new ClovaNestStreamClient(text ->
                transcript.add(new TranscriptEntryResponse(LocalTime.now().format(TIME_FORMAT), speaker, text)));

        sessions.put(sessionId, new SessionState(roomName, client, transcript, new AtomicReference<>()));
        return sessionId;
    }

    public ClovaNestStreamClient getStreamClient(String sessionId) {
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

    public void close(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            state.client().close();
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
            String roomName,
            ClovaNestStreamClient client,
            List<TranscriptEntryResponse> transcript,
            AtomicReference<String> egressId
    ) {
    }
}
