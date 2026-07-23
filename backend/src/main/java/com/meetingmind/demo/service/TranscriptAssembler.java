package com.meetingmind.demo.service;

import java.util.List;

public interface TranscriptAssembler {

    TranscriptChange accept(TranscriptEvent event);

    List<TranscriptPartial> partials(String sessionId);

    List<AssembledTranscriptSegment> flush(String sessionId);

    void discard(String sessionId);
}
