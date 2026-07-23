package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryTranscriptAssemblerTest {

    @Test
    void replacesLivePartialThenFinalizesItOnlyOnce() {
        TranscriptAssembler assembler = new InMemoryTranscriptAssembler();

        TranscriptChange firstPartial = assembler.accept(event(
                TranscriptEventType.PARTIAL, "partial-1", "speaker-1", "오늘 회의", 1, 0, 300
        ));
        TranscriptChange updatedPartial = assembler.accept(event(
                TranscriptEventType.PARTIAL, "partial-2", "speaker-1", "오늘 회의에서는", 2, 0, 500
        ));
        TranscriptChange finalChange = assembler.accept(event(
                TranscriptEventType.FINAL, "final-1", "speaker-1", "오늘 회의에서는 배포를 논의합니다.", 3, 0, 1_000
        ));
        TranscriptChange duplicateFinal = assembler.accept(event(
                TranscriptEventType.FINAL, "final-1", "speaker-1", "오늘 회의에서는 배포를 논의합니다.", 4, 0, 1_000
        ));

        assertThat(firstPartial.partialsUpserted()).extracting(TranscriptPartial::text).containsExactly("오늘 회의");
        assertThat(updatedPartial.partialsUpserted()).extracting(TranscriptPartial::text).containsExactly("오늘 회의에서는");
        assertThat(finalChange.partialIdsRemoved()).hasSize(1);
        assertThat(finalChange.finalized()).extracting(AssembledTranscriptSegment::text)
                .containsExactly("오늘 회의에서는 배포를 논의합니다.");
        assertThat(duplicateFinal).isEqualTo(TranscriptChange.empty());
        assertThat(assembler.partials("session-1")).isEmpty();
    }

    @Test
    void usesThePendingPartialWhenProviderSendsAnEmptyFinalBoundary() {
        TranscriptAssembler assembler = new InMemoryTranscriptAssembler();

        assembler.accept(event(TranscriptEventType.PARTIAL, "partial-1", "speaker-1", "여기까지 들었습니다", 1, 0, 500));
        TranscriptChange finalChange = assembler.accept(event(TranscriptEventType.FINAL, "final-1", "speaker-1", "<end>", 2, 0, 500));

        assertThat(finalChange.finalized()).extracting(AssembledTranscriptSegment::text)
                .containsExactly("여기까지 들었습니다");
    }

    private static TranscriptEvent event(
            TranscriptEventType type,
            String eventId,
            String segmentId,
            String text,
            long sequence,
            long startMs,
            long endMs
    ) {
        return new TranscriptEvent(
                "session-1", "meeting-1", "test", eventId, segmentId, "participant-1", "track-1",
                type, text, sequence, startMs, endMs, null, type == TranscriptEventType.FINAL
        );
    }
}
