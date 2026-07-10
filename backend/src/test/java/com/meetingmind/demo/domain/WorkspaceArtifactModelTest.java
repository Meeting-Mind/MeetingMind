package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceArtifactModelTest {

    private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");

    @Test
    void transcriptSegmentsKeepTimeRangeSpeakerAndSourceMetadata() {
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();

        TranscriptSegment second = store.addTranscriptSegment(
                "meeting-1",
                "speaker-1",
                "화자 1",
                "이미주",
                5_000,
                9_000,
                "권한은 회의 단위로 봐야 합니다.",
                "live-stt",
                2
        );
        TranscriptSegment first = store.addTranscriptSegment(
                "meeting-1",
                "speaker-1",
                "화자 1",
                "이미주",
                0,
                4_000,
                "회의를 시작하겠습니다.",
                "live-stt",
                1
        );

        List<TranscriptSegment> segments = store.findTranscriptSegments("meeting-1");

        assertThat(segments).extracting(TranscriptSegment::id).containsExactly(first.id(), second.id());
        assertThat(segments.getFirst().startMs()).isZero();
        assertThat(segments.getFirst().endMs()).isEqualTo(4_000);
        assertThat(segments.getFirst().speakerLabel()).isEqualTo("화자 1");
        assertThat(segments.getFirst().speakerName()).isEqualTo("이미주");
        assertThat(segments.getFirst().source()).isEqualTo("live-stt");
    }

    @Test
    void meetingReportKeepsCandidateStatusSourceIdsVersionAndCurrentFlag() {
        MeetingReport report = new MeetingReport(
                "report-1",
                "meeting-1",
                MeetingReportStatus.CANDIDATE,
                "API 구조 논의 회의록",
                "권한 모델과 API 구조를 논의했다.",
                List.of(new MeetingReport.ReportDecision(
                        "decision-1",
                        "회의 권한 분리",
                        "MeetingParticipant ACL을 사용한다.",
                        List.of("segment-1")
                )),
                List.of(new MeetingReport.ReportActionItem(
                        "action-1",
                        "권한 테스트 추가",
                        "user-backend",
                        "2026-07-12",
                        List.of("segment-2")
                )),
                1,
                false,
                NOW
        );

        assertThat(report.status()).isEqualTo(MeetingReportStatus.CANDIDATE);
        assertThat(report.version()).isEqualTo(1);
        assertThat(report.current()).isFalse();
        assertThat(report.decisions().getFirst().sourceIds()).containsExactly("segment-1");
        assertThat(report.actionItems().getFirst().sourceIds()).containsExactly("segment-2");
        assertThatThrownBy(() -> report.decisions().add(new MeetingReport.ReportDecision(
                "decision-2",
                "불변성 확인",
                "목록은 외부에서 바꾸지 않는다.",
                List.of()
        ))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void projectKnowledgeStartsWithEmbeddingStatusAndSourceMeetingMetadata() {
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        ProjectKnowledge knowledge = store.saveProjectKnowledge(new ProjectKnowledge(
                "knowledge-1",
                "space-1",
                KnowledgeType.REPORT,
                "3회차 공식 회의록",
                "확정된 회의록 본문",
                "meeting-1",
                "user-owner",
                KnowledgeStatus.PUBLISHED,
                EmbeddingStatus.PENDING,
                "embedding-job-1",
                NOW,
                NOW,
                null
        ));

        List<ProjectKnowledge> items = store.findProjectKnowledge("space-1");

        assertThat(items).containsExactly(knowledge);
        assertThat(knowledge.sourceMeetingId()).isEqualTo("meeting-1");
        assertThat(knowledge.status()).isEqualTo(KnowledgeStatus.PUBLISHED);
        assertThat(knowledge.embeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
        assertThat(knowledge.embeddingJobId()).isEqualTo("embedding-job-1");
    }

    @Test
    void embeddingChunkKeepsRagScopeSourceSegmentsAndEmbeddingText() {
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        EmbeddingChunk chunk = store.saveEmbeddingChunk(new EmbeddingChunk(
                "meeting-1:transcript:0001",
                "space-1",
                "space-1",
                "meeting-1",
                EmbeddingScope.MEETING,
                SourceType.TRANSCRIPT,
                "segment-window-1",
                List.of("segment-1", "segment-2"),
                "3회차 API 구조 논의",
                List.of("이미주", "김진수"),
                0,
                9_000,
                "이미주: 회의를 시작하겠습니다.\n김진수: 권한은 회의 단위로 봐야 합니다.",
                "회의: 3회차 API 구조 논의\n범위: meeting\n출처: transcript\n내용: 권한은 회의 단위로 봐야 합니다.",
                Map.of("language", "ko", "visibility", "meeting"),
                List.of(0.1, 0.2),
                NOW
        ));

        List<EmbeddingChunk> chunks = store.findEmbeddingChunksBySource(SourceType.TRANSCRIPT, "segment-window-1");

        assertThat(chunks).containsExactly(chunk);
        assertThat(chunk.scope()).isEqualTo(EmbeddingScope.MEETING);
        assertThat(chunk.sourceSegmentIds()).containsExactly("segment-1", "segment-2");
        assertThat(chunk.speakerNames()).containsExactly("이미주", "김진수");
        assertThat(chunk.startMs()).isZero();
        assertThat(chunk.endMs()).isEqualTo(9_000);
        assertThat(chunk.embeddingText()).contains("범위: meeting");
        assertThat(chunk.metadata()).containsEntry("visibility", "meeting");
        assertThatThrownBy(() -> chunk.sourceSegmentIds().add("segment-3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
