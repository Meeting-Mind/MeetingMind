package com.meetingmind.demo.service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class SttSessionRegistry {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public String create() {
        String sessionId = UUID.randomUUID().toString();
        StringBuilder transcript = new StringBuilder();
        ClovaNestStreamClient client = new ClovaNestStreamClient(text -> {
            synchronized (transcript) {
                transcript.append(text);
            }
        });
        sessions.put(sessionId, new SessionState(client, transcript, new AtomicReference<>()));
        return sessionId;
    }

    public ClovaNestStreamClient getStreamClient(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? null : state.client();
    }

    public String getTranscript(String sessionId) {
        return require(sessionId).transcriptSnapshot();
    }

    public void setEgressId(String sessionId, String egressId) {
        require(sessionId).egressId().set(egressId);
    }

    public String getEgressId(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? null : state.egressId().get();
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

    private record SessionState(ClovaNestStreamClient client, StringBuilder transcript, AtomicReference<String> egressId) {
        String transcriptSnapshot() {
            synchronized (transcript) {
                return transcript.toString();
            }
        }
    }
}
