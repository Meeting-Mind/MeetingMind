package com.meetingmind.demo.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.gateway.TranscriptionGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranscriptProjectionReconcilerTest {

    @Mock
    private WorkspaceDomainService workspaceDomainService;

    @Mock
    private TranscriptionGateway transcriptionGateway;

    @Test
    void reconcilesOnlyTerminalSnapshots() {
        when(workspaceDomainService.transcriptProjectionCandidateMeetingIds(20))
                .thenReturn(List.of("meeting-completed", "meeting-processing", "meeting-missing"));
        when(transcriptionGateway.transcript("meeting-completed")).thenReturn(Optional.of(snapshot(
                "meeting-completed", TranscriptStatus.COMPLETED, "segment-1"
        )));
        when(transcriptionGateway.transcript("meeting-processing")).thenReturn(Optional.of(snapshot(
                "meeting-processing", TranscriptStatus.PROCESSING, "segment-live"
        )));
        when(transcriptionGateway.transcript("meeting-missing")).thenReturn(Optional.empty());

        new TranscriptProjectionReconciler(workspaceDomainService, transcriptionGateway, 20).reconcile();

        verify(workspaceDomainService).reconcileRemoteMeetingTranscript(
                eq("meeting-completed"),
                eq(TranscriptStatus.COMPLETED),
                eq(List.of(new WorkspaceDomainService.RemoteTranscriptSegment(
                        "segment-1", "speaker-1", "화자 1", "Owner", 100, 900, "복구할 전사"
                )))
        );
        verify(workspaceDomainService, never()).reconcileRemoteMeetingTranscript(
                eq("meeting-processing"), eq(TranscriptStatus.PROCESSING), anyList()
        );
        verify(workspaceDomainService, never()).reconcileRemoteMeetingTranscript(
                eq("meeting-missing"), eq(TranscriptStatus.COMPLETED), anyList()
        );
    }

    @Test
    void isolatesInvalidSnapshotAndContinuesWithNextCandidate() {
        when(workspaceDomainService.transcriptProjectionCandidateMeetingIds(2))
                .thenReturn(List.of("meeting-mismatch", "meeting-valid"));
        when(transcriptionGateway.transcript("meeting-mismatch")).thenReturn(Optional.of(snapshot(
                "different-meeting", TranscriptStatus.COMPLETED, "segment-invalid"
        )));
        when(transcriptionGateway.transcript("meeting-valid")).thenReturn(Optional.of(snapshot(
                "meeting-valid", TranscriptStatus.COMPLETED, "segment-valid"
        )));

        new TranscriptProjectionReconciler(workspaceDomainService, transcriptionGateway, 2).reconcile();

        verify(workspaceDomainService, never()).reconcileRemoteMeetingTranscript(
                eq("meeting-mismatch"), eq(TranscriptStatus.COMPLETED), anyList()
        );
        verify(workspaceDomainService).reconcileRemoteMeetingTranscript(
                eq("meeting-valid"), eq(TranscriptStatus.COMPLETED), anyList()
        );
    }

    private MeetingTranscriptGatewayResponse snapshot(
            String meetingId,
            TranscriptStatus status,
            String segmentId
    ) {
        return new MeetingTranscriptGatewayResponse(
                meetingId,
                status,
                List.of(new MeetingTranscriptGatewayResponse.Segment(
                        segmentId, "speaker-1", "화자 1", "Owner", 100, 900, "복구할 전사"
                ))
        );
    }
}
