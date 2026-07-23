package com.meetingmind.stt.service;

import java.util.List;

public record TranscriptChange(
        List<TranscriptPartial> partialsUpserted,
        List<String> partialIdsRemoved,
        List<AssembledTranscriptSegment> finalized,
        List<AssembledTranscriptSegment> revised
) {

    static TranscriptChange empty() {
        return new TranscriptChange(List.of(), List.of(), List.of(), List.of());
    }
}
