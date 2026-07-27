package com.meetingmind.stt.service;

import com.meetingmind.stt.domain.MeetingSpeaker;
import com.meetingmind.stt.domain.MeetingTranscript;
import com.meetingmind.stt.domain.TranscriptSegment;
import com.meetingmind.stt.domain.TranscriptStatus;
import com.meetingmind.stt.repository.MeetingSpeakerRepository;
import com.meetingmind.stt.repository.MeetingTranscriptRepository;
import com.meetingmind.stt.repository.TranscriptSegmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the meeting_transcripts / meeting_speakers / transcript_segments rows
 * in the STT database directly via Spring Data, replacing the previous
 * dependency on Core's WorkspaceDomainService (which lived in Core's DB and
 * required a MeetingParticipant lock STT no longer has access to). Core has
 * already checked meeting existence/permission before calling into STT, so
 * there is no meeting-level lock here — only the transcript row itself.
 */
@Service
public class TranscriptionCoordinator {

    private final MeetingTranscriptRepository transcriptRepository;
    private final MeetingSpeakerRepository speakerRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final Clock clock;

    // 생성자가 둘이면 Spring은 어느 쪽으로 주입할지 알 수 없어 기본 생성자를 찾고,
    // 없으면 기동에 실패한다. 아래 4-arg 생성자는 Clock을 고정하는 테스트 전용이므로
    // 주입 대상은 이쪽임을 명시한다.
    @Autowired
    public TranscriptionCoordinator(
            MeetingTranscriptRepository transcriptRepository,
            MeetingSpeakerRepository speakerRepository,
            TranscriptSegmentRepository segmentRepository) {
        this(transcriptRepository, speakerRepository, segmentRepository, Clock.systemUTC());
    }

    TranscriptionCoordinator(
            MeetingTranscriptRepository transcriptRepository,
            MeetingSpeakerRepository speakerRepository,
            TranscriptSegmentRepository segmentRepository,
            Clock clock) {
        this.transcriptRepository = transcriptRepository;
        this.speakerRepository = speakerRepository;
        this.segmentRepository = segmentRepository;
        this.clock = clock;
    }

    @Transactional
    public MeetingTranscript startTranscript(String meetingId, String provider, Instant retentionUntil) {
        MeetingTranscript existing = transcriptRepository.findById(meetingId).orElse(null);
        if (existing != null && existing.status() == TranscriptStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 전사가 있습니다.");
        }
        Instant now = Instant.now(clock);
        return transcriptRepository.save(new MeetingTranscript(
                meetingId,
                TranscriptStatus.PROCESSING,
                provider,
                "ko-KR",
                existing == null ? now : existing.startedAt(),
                null,
                null,
                retentionUntil,
                existing != null && existing.legalHold(),
                existing == null ? null : existing.purgedAt(),
                existing == null ? now : existing.createdAt(),
                now
        ));
    }

    @Transactional
    public TranscriptSegment appendSegment(
            String meetingId, String speakerLabel, String speakerName, int startMs, int endMs, String text) {
        requireProcessingTranscript(meetingId);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전사 내용은 비어 있을 수 없습니다.");
        }
        if (startMs < 0 || endMs < startMs) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전사 시간 범위가 올바르지 않습니다.");
        }
        MeetingSpeaker speaker = speakerRepository.findByMeetingIdAndLabel(meetingId, speakerLabel)
                .orElseGet(() -> speakerRepository.save(new MeetingSpeaker(
                        UUID.randomUUID().toString(), meetingId, speakerLabel, speakerName, Instant.now(clock))));
        int sequence = segmentRepository.countByMeetingId(meetingId);
        return segmentRepository.save(new TranscriptSegment(
                UUID.randomUUID().toString(),
                meetingId,
                speaker.id(),
                speaker.label(),
                speaker.displayName(),
                startMs,
                endMs,
                text.trim(),
                "stt",
                sequence
        ));
    }

    @Transactional
    public MeetingTranscript completeTranscript(String meetingId) {
        MeetingTranscript transcript = requireProcessingTranscript(meetingId);
        Instant now = Instant.now(clock);
        return transcriptRepository.save(new MeetingTranscript(
                transcript.meetingId(), TranscriptStatus.COMPLETED, transcript.provider(), transcript.language(),
                transcript.startedAt(), now, null, transcript.retentionUntil(), transcript.legalHold(),
                transcript.purgedAt(), transcript.createdAt(), now
        ));
    }

    @Transactional
    public MeetingTranscript failTranscript(String meetingId) {
        MeetingTranscript transcript = requireProcessingTranscript(meetingId);
        Instant now = Instant.now(clock);
        return transcriptRepository.save(new MeetingTranscript(
                transcript.meetingId(), TranscriptStatus.FAILED, transcript.provider(), transcript.language(),
                transcript.startedAt(), now, "STT provider 오류", transcript.retentionUntil(), transcript.legalHold(),
                transcript.purgedAt(), transcript.createdAt(), now
        ));
    }

    public MeetingTranscript getTranscript(String meetingId) {
        return transcriptRepository.findById(meetingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "전사를 찾을 수 없습니다."));
    }

    public List<TranscriptSegment> getSegments(String meetingId) {
        return segmentRepository.findByMeetingIdOrderBySequenceAsc(meetingId);
    }

    public int nextSegmentOffsetMs(String meetingId) {
        return segmentRepository.findByMeetingIdOrderBySequenceAsc(meetingId).stream()
                .mapToInt(TranscriptSegment::endMs)
                .max()
                .orElse(0);
    }

    private MeetingTranscript requireProcessingTranscript(String meetingId) {
        MeetingTranscript transcript = transcriptRepository.findById(meetingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "전사를 찾을 수 없습니다."));
        if (transcript.status() != TranscriptStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 전사가 아닙니다.");
        }
        return transcript;
    }
}
