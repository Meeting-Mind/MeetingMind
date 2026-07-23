package com.meetingmind.demo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTranscriptAssembler implements TranscriptAssembler {

    private final Map<String, SessionAssembly> sessions = new HashMap<>();

    @Override
    public synchronized TranscriptChange accept(TranscriptEvent event) {
        if (event == null || event.sessionId() == null || event.sessionId().isBlank()) {
            return TranscriptChange.empty();
        }
        SessionAssembly session = sessions.computeIfAbsent(event.sessionId(), ignored -> new SessionAssembly());
        return switch (event.type()) {
            case PARTIAL -> acceptPartial(session, event);
            case FINAL -> acceptFinal(session, event);
            case REVISION, ERROR -> TranscriptChange.empty();
        };
    }

    @Override
    public synchronized List<TranscriptPartial> partials(String sessionId) {
        SessionAssembly session = sessions.get(sessionId);
        if (session == null) {
            return List.of();
        }
        return session.partials.values().stream()
                .map(PendingPartial::toPublic)
                .sorted(Comparator.comparingLong(TranscriptPartial::startedAtMs))
                .toList();
    }

    @Override
    public synchronized List<AssembledTranscriptSegment> flush(String sessionId) {
        SessionAssembly session = sessions.remove(sessionId);
        if (session == null) {
            return List.of();
        }
        return session.partials.values().stream()
                .map(PendingPartial::finalizeSegment)
                .toList();
    }

    @Override
    public synchronized void discard(String sessionId) {
        sessions.remove(sessionId);
    }

    private TranscriptChange acceptPartial(SessionAssembly session, TranscriptEvent event) {
        String text = TranscriptTextSanitizer.sanitize(event.text());
        if (text.isBlank()) {
            return TranscriptChange.empty();
        }
        String partialId = partialId(event);
        PendingPartial existing = session.partials.get(partialId);
        PendingPartial next = new PendingPartial(partialId, event, mergePartialText(existing, text));
        if (existing != null && existing.text().equals(next.text())) {
            return TranscriptChange.empty();
        }
        session.partials.put(partialId, next);
        return new TranscriptChange(List.of(next.toPublic()), List.of(), List.of(), List.of());
    }

    private TranscriptChange acceptFinal(SessionAssembly session, TranscriptEvent event) {
        String eventKey = event.provider() + "|" + event.providerEventId();
        if (!event.providerEventId().isBlank() && !session.finalEventKeys.add(eventKey)) {
            return TranscriptChange.empty();
        }

        List<PendingPartial> matchingPartials = session.partials.values().stream()
                .filter(partial -> sameTrack(partial.event(), event))
                .toList();
        String text = TranscriptTextSanitizer.sanitize(event.text());
        if (text.isBlank() && !matchingPartials.isEmpty()) {
            text = matchingPartials.getLast().text();
        }
        if (text.isBlank()) {
            return TranscriptChange.empty();
        }

        long startMs = event.startedAtMs();
        long endMs = event.endedAtMs() == null ? startMs : event.endedAtMs();
        if (!matchingPartials.isEmpty()) {
            startMs = Math.min(startMs, matchingPartials.getFirst().startedAtMs());
            endMs = Math.max(endMs, matchingPartials.getLast().updatedAtMs());
        }
        AssembledTranscriptSegment finalSegment = new AssembledTranscriptSegment(
                finalSegmentId(event),
                event.sessionId(),
                event.trackId(),
                event.provider(),
                event.providerEventId(),
                text,
                Math.max(0, startMs),
                Math.max(Math.max(0, startMs), endMs)
        );
        String fingerprint = finalSegment.trackId() + "|" + finalSegment.startedAtMs() + "|" + finalSegment.endedAtMs() + "|" + finalSegment.text();
        if (!session.finalFingerprints.add(fingerprint)) {
            return TranscriptChange.empty();
        }

        List<String> removed = new ArrayList<>();
        for (PendingPartial partial : matchingPartials) {
            if (session.partials.remove(partial.partialId()) != null) {
                removed.add(partial.partialId());
            }
        }
        return new TranscriptChange(List.of(), removed, List.of(finalSegment), List.of());
    }

    private static String partialId(TranscriptEvent event) {
        String segment = event.providerSegmentId();
        if (segment == null || segment.isBlank()) {
            segment = "live";
        }
        return event.sessionId() + "|" + trackKey(event.trackId()) + "|" + segment;
    }

    private static String finalSegmentId(TranscriptEvent event) {
        if (event.providerSegmentId() != null && !event.providerSegmentId().isBlank()) {
            return event.sessionId() + "|" + event.providerSegmentId();
        }
        return event.sessionId() + "|" + event.providerEventId();
    }

    private static boolean sameTrack(TranscriptEvent left, TranscriptEvent right) {
        return trackKey(left.trackId()).equals(trackKey(right.trackId()));
    }

    private static String trackKey(String trackId) {
        return trackId == null ? "unknown-track" : trackId;
    }

    private static String mergePartialText(PendingPartial existing, String incoming) {
        if (existing == null) {
            return incoming;
        }
        String previous = existing.text();
        if (incoming.startsWith(previous)) {
            return incoming;
        }
        if (previous.endsWith(incoming)) {
            return previous;
        }
        char left = previous.charAt(previous.length() - 1);
        char right = incoming.charAt(0);
        boolean tightJoin = Character.isLetterOrDigit(left) && Character.isLetterOrDigit(right)
                || isHangul(left)
                || isHangul(right);
        return tightJoin ? previous + incoming : previous + " " + incoming;
    }

    private static boolean isHangul(char value) {
        return value >= '\uAC00' && value <= '\uD7A3';
    }

    private record SessionAssembly(
            Map<String, PendingPartial> partials,
            Set<String> finalEventKeys,
            Set<String> finalFingerprints
    ) {
        private SessionAssembly() {
            this(new HashMap<>(), new HashSet<>(), new HashSet<>());
        }
    }

    private record PendingPartial(String partialId, TranscriptEvent event, String text) {
        TranscriptPartial toPublic() {
            long endMs = event.endedAtMs() == null ? event.startedAtMs() : event.endedAtMs();
            return new TranscriptPartial(partialId, event.sessionId(), event.trackId(), text, event.startedAtMs(), endMs);
        }

        long startedAtMs() {
            return event.startedAtMs();
        }

        long updatedAtMs() {
            return event.endedAtMs() == null ? event.startedAtMs() : event.endedAtMs();
        }

        AssembledTranscriptSegment finalizeSegment() {
            return new AssembledTranscriptSegment(
                    partialId,
                    event.sessionId(),
                    event.trackId(),
                    event.provider(),
                    event.providerEventId(),
                    text,
                    startedAtMs(),
                    Math.max(startedAtMs(), updatedAtMs())
            );
        }
    }
}
