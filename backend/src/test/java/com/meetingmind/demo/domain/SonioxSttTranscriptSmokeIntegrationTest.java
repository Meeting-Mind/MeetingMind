package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.demo.service.SttProvider;
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

/**
 * T440: `SMK-002` provider tier smoke.
 *
 * <p>기존 provider smoke는 `clova-nest`만 대상으로 했지만 실제 runtime 기본 provider는
 * `ConfiguredSttProvider`의 `soniox-realtime`이다. 즉 문서가 지정한 근거와 실제로 실행되는
 * 경로가 달랐다. 이 테스트는 실제 기본 경로를 검증한다.
 *
 * <p>기본적으로 비활성이며 `RUN_SONIOX_STT_SMOKE=true`일 때만 실행된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "RUN_SONIOX_STT_SMOKE", matches = "true")
class SonioxSttTranscriptSmokeIntegrationTest {

    private static final String PROVIDER_ID = "soniox-realtime";
    private static final int INPUT_SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHUNK_BYTES = 4_096;

    static {
        // ConfiguredSttProvider는 primary 생성이 실패하면 STT_FALLBACK_PROVIDER로 넘어간다.
        // 기본 fallback은 openai-realtime이므로, 그대로 두면 Soniox 자격증명이나 연결이
        // 잘못됐을 때 OpenAI로 조용히 대체되어 "통과"할 수 있다. 그건 거짓 양성이다.
        // fallback을 primary와 같게 두면 ConfiguredSttProvider가 원래 예외를 다시 던진다.
        System.setProperty("STT_PROVIDER", PROVIDER_ID);
        System.setProperty("STT_FALLBACK_PROVIDER", PROVIDER_ID);
    }

    @Autowired
    private WorkspaceStore store;

    @Autowired
    private WorkspaceDomainService workspaceDomainService;

    @Autowired
    private SttSessionRegistry sessionRegistry;

    @Autowired
    private SttProvider sttProvider;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void persistsSonioxRealtimeCallbacksAsCompletedDialogue() throws Exception {
        // 실제로 Soniox 경로를 타는지 먼저 고정한다. fallback으로 대체됐다면 여기서 끊는다.
        assertThat(sttProvider.providerId())
                .as("smoke must exercise the runtime default provider, not a fallback")
                .isEqualTo(PROVIDER_ID);

        Path pcmPath = Path.of(requiredEnvironment("SONIOX_STT_SMOKE_PCM_PATH"));
        byte[] pcm16k = Files.readAllBytes(pcmPath);
        assertThat(pcm16k).isNotEmpty();

        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(new User(
                "soniox-smoke-owner-" + suffix,
                suffix + "@meetingmind.test",
                "Soniox STT Host",
                null,
                "active",
                now,
                now
        ));
        WorkspaceDomainService.SpaceCreationResult space = workspaceDomainService.createSpace(
                owner.id(), "Soniox STT Smoke Space", "actual provider transcript"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = workspaceDomainService.createMeeting(
                owner.id(),
                space.space().id(),
                "Soniox STT Smoke Meeting",
                OffsetDateTime.of(2026, 7, 26, 15, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );

        workspaceDomainService.startMeetingTranscript(owner.id(), meeting.meeting().id(), PROVIDER_ID);
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
        assertThat(dialogue.transcript().status()).isEqualTo(TranscriptStatus.COMPLETED);
        assertThat(dialogue.transcript().provider()).isEqualTo(PROVIDER_ID);
        assertThat(dialogue.segments()).isNotEmpty();

        String text = dialogue.segments().stream().map(TranscriptSegment::text).reduce("", String::concat);
        assertThat(text).isNotBlank();
        // 샘플 오디오에 따라 기대 문구가 달라지므로 강제하지 않는다.
        // 특정 문구까지 확인하려면 SONIOX_STT_SMOKE_EXPECTED_TEXT를 설정한다.
        String expectedText = System.getenv("SONIOX_STT_SMOKE_EXPECTED_TEXT");
        if (expectedText != null && !expectedText.isBlank()) {
            assertThat(text).contains(expectedText.trim());
        }

        assertThat(jdbc.queryForObject(
                """
                select count(*) from embedding_jobs
                where meeting_id = ? and trigger_reason = 'TRANSCRIPT_COMPLETED'
                """,
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
        throw new AssertionError("Soniox STT did not return a transcript within 10 seconds.");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when RUN_SONIOX_STT_SMOKE=true.");
        }
        return value;
    }
}
