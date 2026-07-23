package com.meetingmind.stt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.stt.domain.MeetingSpeaker;
import com.meetingmind.stt.domain.MeetingTranscript;
import com.meetingmind.stt.domain.TranscriptSegment;
import com.meetingmind.stt.domain.TranscriptStatus;
import com.meetingmind.stt.repository.MeetingSpeakerRepository;
import com.meetingmind.stt.repository.MeetingTranscriptRepository;
import com.meetingmind.stt.repository.TranscriptSegmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TranscriptionCoordinatorTest {

    private final MeetingTranscriptRepository transcriptRepository = mock(MeetingTranscriptRepository.class);
    private final MeetingSpeakerRepository speakerRepository = mock(MeetingSpeakerRepository.class);
    private final TranscriptSegmentRepository segmentRepository = mock(TranscriptSegmentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);
    private final TranscriptionCoordinator coordinator = new TranscriptionCoordinator(
            transcriptRepository, speakerRepository, segmentRepository, clock);

    @Test
    void startTranscriptRejectsWhenAlreadyProcessing() {
        MeetingTranscript existing = new MeetingTranscript(
                "meeting-1", TranscriptStatus.PROCESSING, "clova-nest", "ko-KR",
                Instant.now(clock), null, null, null, false, null, Instant.now(clock), Instant.now(clock));
        when(transcriptRepository.findById("meeting-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> coordinator.startTranscript("meeting-1", "clova-nest", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 진행 중");
    }

    @Test
    void appendSegmentRejectsWhenTranscriptIsNotProcessing() {
        when(transcriptRepository.findById("meeting-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coordinator.appendSegment("meeting-1", "stt-1", "Speaker", 0, 100, "hello"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void appendSegmentReusesExistingSpeakerAndAssignsNextSequence() {
        MeetingTranscript processing = new MeetingTranscript(
                "meeting-1", TranscriptStatus.PROCESSING, "clova-nest", "ko-KR",
                Instant.now(clock), null, null, null, false, null, Instant.now(clock), Instant.now(clock));
        when(transcriptRepository.findById("meeting-1")).thenReturn(Optional.of(processing));
        MeetingSpeaker speaker = new MeetingSpeaker("speaker-1", "meeting-1", "stt-1", "Speaker", Instant.now(clock));
        when(speakerRepository.findByMeetingIdAndLabel("meeting-1", "stt-1")).thenReturn(Optional.of(speaker));
        when(segmentRepository.countByMeetingId("meeting-1")).thenReturn(2);
        when(segmentRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        TranscriptSegment saved = coordinator.appendSegment("meeting-1", "stt-1", "Speaker", 100, 200, "hello world");

        assertThat(saved.sequence()).isEqualTo(2);
        assertThat(saved.speakerId()).isEqualTo("speaker-1");
        assertThat(saved.text()).isEqualTo("hello world");
    }

    @Test
    void completeTranscriptRequiresProcessingStatus() {
        MeetingTranscript completed = new MeetingTranscript(
                "meeting-1", TranscriptStatus.COMPLETED, "clova-nest", "ko-KR",
                Instant.now(clock), Instant.now(clock), null, null, false, null, Instant.now(clock), Instant.now(clock));
        when(transcriptRepository.findById("meeting-1")).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> coordinator.completeTranscript("meeting-1"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getSegmentsDelegatesToRepositoryOrderedBySequence() {
        List<TranscriptSegment> segments = List.of(
                new TranscriptSegment("seg-1", "meeting-1", "speaker-1", "stt-1", "Speaker", 0, 100, "hi", "stt", 0));
        when(segmentRepository.findByMeetingIdOrderBySequenceAsc("meeting-1")).thenReturn(segments);

        assertThat(coordinator.getSegments("meeting-1")).isEqualTo(segments);
    }
}
