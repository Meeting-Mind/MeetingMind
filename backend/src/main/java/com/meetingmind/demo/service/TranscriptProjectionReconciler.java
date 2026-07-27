package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.gateway.TranscriptionGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "meetingmind.stt.projection-reconciliation",
        name = "enabled",
        havingValue = "true"
)
class TranscriptProjectionReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranscriptProjectionReconciler.class);

    private final WorkspaceDomainService workspaceDomainService;
    private final TranscriptionGateway transcriptionGateway;
    private final int batchSize;

    TranscriptProjectionReconciler(
            WorkspaceDomainService workspaceDomainService,
            TranscriptionGateway transcriptionGateway,
            @Value("${meetingmind.stt.projection-reconciliation.batch-size:20}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("projection reconciliation batch size must be between 1 and 100");
        }
        this.workspaceDomainService = workspaceDomainService;
        this.transcriptionGateway = transcriptionGateway;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${meetingmind.stt.projection-reconciliation.fixed-delay:30s}")
    void reconcile() {
        int projected = 0;
        int skipped = 0;
        int failed = 0;
        var meetingIds = workspaceDomainService.transcriptProjectionCandidateMeetingIds(batchSize);

        for (String meetingId : meetingIds) {
            try {
                MeetingTranscriptGatewayResponse snapshot = transcriptionGateway.transcript(meetingId).orElse(null);
                if (snapshot == null || !isTerminal(snapshot.status())) {
                    skipped++;
                    continue;
                }
                if (!meetingId.equals(snapshot.meetingId())) {
                    throw new IllegalArgumentException("STT transcript meeting id mismatch");
                }
                workspaceDomainService.reconcileRemoteMeetingTranscript(
                        meetingId,
                        snapshot.status(),
                        snapshot.segments().stream()
                                .map(segment -> new WorkspaceDomainService.RemoteTranscriptSegment(
                                        segment.id(),
                                        segment.speakerId(),
                                        segment.speakerLabel(),
                                        segment.speakerName(),
                                        Math.toIntExact(segment.startMs()),
                                        Math.toIntExact(segment.endMs()),
                                        segment.text()
                                ))
                                .toList()
                );
                projected++;
            } catch (Exception error) {
                failed++;
                LOGGER.warn(
                        "Transcript projection reconciliation failed. meetingId={} errorType={}",
                        meetingId,
                        error.getClass().getSimpleName()
                );
            }
        }

        if (!meetingIds.isEmpty()) {
            LOGGER.info(
                    "Transcript projection reconciliation completed. candidateCount={} projectedCount={} skippedCount={} failedCount={}",
                    meetingIds.size(),
                    projected,
                    skipped,
                    failed
            );
        }
    }

    private boolean isTerminal(TranscriptStatus status) {
        return status == TranscriptStatus.COMPLETED || status == TranscriptStatus.FAILED;
    }
}
