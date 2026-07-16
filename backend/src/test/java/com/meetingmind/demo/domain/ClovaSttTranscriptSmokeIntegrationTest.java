package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.demo.service.SttSessionRegistry;
import com.meetingmind.demo.service.SttStreamClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "RUN_CLOVA_STT_SMOKE", matches = "true")
class ClovaSttTranscriptSmokeIntegrationTest {

    private static final int INPUT_SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHUNK_BYTES = 4_096;

    @Autowired
    private WorkspaceStore store;

    @Autowired
    private WorkspaceDomainService workspaceDomainService;

    @Autowired
    private SttSessionRegistry sessionRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void persistsActualCloudSttCallbacksAsCompletedDialogue() throws Exception {
        Path pcmPath = Path.of(requiredEnvironment("CLOVA_STT_SMOKE_PCM_PATH"));
        byte[] pcm16k = Files.readAllBytes(pcmPath);
        assertThat(pcm16k).isNotEmpty();

        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(new User(
                "clova-smoke-owner-" + suffix,
                suffix + "@meetingmind.test",
                "Cloud STT Host",
                null,
                "active",
                now,
                now
        ));
        WorkspaceDomainService.SpaceCreationResult space = workspaceDomainService.createSpace(
                owner.id(), "Cloud STT Smoke Space", "actual provider transcript"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = workspaceDomainService.createMeeting(
                owner.id(),
                space.space().id(),
                "Cloud STT Smoke Meeting",
                OffsetDateTime.of(2026, 7, 16, 15, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );

        workspaceDomainService.startMeetingTranscript(owner.id(), meeting.meeting().id(), "clova-nest");
        String sessionId = sessionRegistry.createMeetingSession(
                meeting.meeting().id(), meeting.meeting().id(), owner.displayName()
        );
        SttStreamClient client = sessionRegistry.getStreamClient(sessionId);
        assertThat(client).isNotNull();

        try {
            streamAtRealtimePace(client, pcm16k);
            client.finishAudio();
            waitForTranscript(meeting.meeting().id());
        } finally {
            sessionRegistry.close(sessionId);
        }

        WorkspaceDomainService.MeetingTranscriptView dialogue = workspaceDomainService.meetingTranscript(
                owner.id(), meeting.meeting().id()
        );
        String text = dialogue.segments().stream().map(TranscriptSegment::text).reduce("", String::concat);
        assertThat(dialogue.transcript().status()).isEqualTo(TranscriptStatus.COMPLETED);
        assertThat(dialogue.segments()).isNotEmpty();
        assertThat(text).contains("미팅").contains("회의");
        assertThat(jdbc.queryForObject(
                "select count(*) from embedding_jobs where meeting_id = ? and trigger_reason = 'TRANSCRIPT_COMPLETED'",
                Integer.class,
                meeting.meeting().id()
        )).isEqualTo(1);
    }

    private void streamAtRealtimePace(SttStreamClient client, byte[] pcm16k) throws InterruptedException {
        for (int offset = 0; offset < pcm16k.length; offset += CHUNK_BYTES) {
            int length = Math.min(CHUNK_BYTES, pcm16k.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(pcm16k, offset, chunk, 0, length);
            client.sendAudio(chunk);
            long durationMillis = Math.max(1, (long) length * 1_000 / (INPUT_SAMPLE_RATE * BYTES_PER_SAMPLE));
            Thread.sleep(durationMillis);
        }
    }

    private void waitForTranscript(String meetingId) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (!store.findTranscriptSegments(meetingId).isEmpty()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Cloud STT did not return a transcript within 10 seconds.");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when RUN_CLOVA_STT_SMOKE=true.");
        }
        return value;
    }
}
