package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.demo.service.SttProvider;
import com.meetingmind.demo.service.SttSessionRegistry;
import com.meetingmind.demo.service.SttStreamClient;
import com.meetingmind.demo.service.TranscriptEvent;
import com.meetingmind.demo.service.TranscriptEventType;
import com.meetingmind.demo.service.SttSessionContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@Import(SttTranscriptFlowIntegrationTest.SttTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
@Transactional
class SttTranscriptFlowIntegrationTest {

    static {
        // ConfiguredSttProvider는 유일한 @Primary SttProvider로 남아야 한다.
        // 테스트용 fake를 @Primary로 올리면 primary 후보가 둘이 되어 주입이 실패하므로,
        // 실제 운영 경로와 같게 provider 선택 설정으로 fake를 고르게 한다.
        System.setProperty("STT_PROVIDER", "fake-clova");
    }

    @Autowired
    private WorkspaceStore store;

    @Autowired
    private WorkspaceDomainService workspaceDomainService;

    @Autowired
    private SttSessionRegistry sessionRegistry;

    @Autowired
    private CapturingSttProvider streamClientFactory;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void persistsProviderCallbacksAsDialogueAndEnqueuesRagInput() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(new User(
                "stt-owner-" + suffix,
                suffix + "@meetingmind.test",
                "STT Host",
                null,
                "active",
                now,
                now
        ));
        WorkspaceDomainService.SpaceCreationResult space = workspaceDomainService.createSpace(
                owner.id(), "STT Flow Space", "provider callback persistence"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = workspaceDomainService.createMeeting(
                owner.id(),
                space.space().id(),
                "STT Flow Meeting",
                OffsetDateTime.of(2026, 7, 16, 14, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );

        MeetingTranscript processing = workspaceDomainService.startMeetingTranscript(
                owner.id(), meeting.meeting().id(), "fake-clova"
        );
        assertThat(processing.status()).isEqualTo(TranscriptStatus.PROCESSING);

        String sessionId = sessionRegistry.createMeetingSession(
                meeting.meeting().id(), meeting.meeting().id(), owner.displayName()
        );
        CapturingSttStreamClient client = streamClientFactory.latestClient();
        client.emitTranscript("회의 전사 결과를 저장합니다.");
        client.emitTranscript("완료 후 RAG 검색을 준비합니다.");
        sessionRegistry.close(sessionId);

        WorkspaceDomainService.MeetingTranscriptView dialogue = workspaceDomainService.meetingTranscript(
                owner.id(), meeting.meeting().id()
        );
        assertThat(dialogue.transcript().status()).isEqualTo(TranscriptStatus.COMPLETED);
        assertThat(dialogue.segments())
                .extracting(segment -> segment.text())
                .containsExactly("회의 전사 결과를 저장합니다.", "완료 후 RAG 검색을 준비합니다.");
        assertThat(dialogue.segments())
                .extracting(segment -> segment.speakerName())
                .containsOnly(owner.displayName());
        assertThat(client.closed()).isTrue();

        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForObject(
                """
                select count(*) from embedding_jobs
                where meeting_id = ? and trigger_reason = 'TRANSCRIPT_COMPLETED'
                """,
                Integer.class,
                meeting.meeting().id()
        )).isEqualTo(1);
    }

    @TestConfiguration
    static class SttTestConfiguration {

        @Bean
        CapturingSttProvider capturingSttProvider() {
            return new CapturingSttProvider();
        }
    }

    static class CapturingSttProvider implements SttProvider {

        private final AtomicReference<CapturingSttStreamClient> latest = new AtomicReference<>();

        @Override
        public String providerId() {
            return "fake-clova";
        }

        @Override
        public SttStreamClient createClient(
                SttSessionContext context,
                Consumer<TranscriptEvent> onTranscriptEvent,
                Consumer<Throwable> onError
        ) {
            CapturingSttStreamClient client = new CapturingSttStreamClient(context, onTranscriptEvent, onError);
            latest.set(client);
            return client;
        }

        CapturingSttStreamClient latestClient() {
            return latest.get();
        }
    }

    static class CapturingSttStreamClient implements SttStreamClient {

        private final SttSessionContext context;
        private final Consumer<TranscriptEvent> onTranscriptEvent;
        @SuppressWarnings("unused")
        private final Consumer<Throwable> onError;
        private boolean closed;

        CapturingSttStreamClient(
                SttSessionContext context,
                Consumer<TranscriptEvent> onTranscriptEvent,
                Consumer<Throwable> onError
        ) {
            this.context = context;
            this.onTranscriptEvent = onTranscriptEvent;
            this.onError = onError;
        }

        void emitTranscript(String text) {
            onTranscriptEvent.accept(new TranscriptEvent(
                    context.sessionId(), context.meetingId(), "fake-clova", UUID.randomUUID().toString(), null,
                    null, context.trackId(), TranscriptEventType.FINAL, text, 1, 0, 0L, null, true
            ));
        }

        void emitPartialTranscript(String text) {
            onTranscriptEvent.accept(new TranscriptEvent(
                    context.sessionId(), context.meetingId(), "fake-clova", UUID.randomUUID().toString(), null,
                    null, context.trackId(), TranscriptEventType.PARTIAL, text, 1, 0, null, null, false
            ));
        }

        boolean closed() {
            return closed;
        }

        @Override
        public void sendAudio(byte[] pcm16leMono16k) {
        }

        @Override
        public void finishAudio() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
