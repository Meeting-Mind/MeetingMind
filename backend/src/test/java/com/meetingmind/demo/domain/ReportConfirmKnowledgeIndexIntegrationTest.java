package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * SMK-003 local tier: 확정된 회의록이 색인 대상으로 등록되는지 검증한다.
 *
 * <p>`MigrationIntegrationTest`는 `meeting_reports`에 raw SQL로 직접 insert해 DB trigger가
 * `REPORT_CONFIRMED`를 enqueue하는지만 확인한다. 그것은 trigger 자체의 검증이고, 애플리케이션
 * 경로가 trigger 조건(`status='CONFIRMED' and is_current=true`)을 실제로 만족시키는지는
 * 검증하지 않는다. 이 테스트는 `WorkspaceDomainService.confirmMeetingReport`를 통해 그 연결을
 * 확인한다.
 *
 * <p>AI provider를 쓰지 않는다. 회의록 본문 생성(provider)과 embedding worker 처리는 범위
 * 밖이며, 여기서는 "확정이 색인 작업을 만든다"는 연결만 결정론적으로 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
@Transactional
class ReportConfirmKnowledgeIndexIntegrationTest {

    @Autowired
    private WorkspaceStore store;

    @Autowired
    private WorkspaceDomainService service;

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
    void confirmingReportThroughServiceEnqueuesReportConfirmedIndexJob() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(new User(
                "report-index-owner-" + suffix,
                suffix + "@meetingmind.test",
                "Report Index Host",
                null,
                "active",
                now,
                now
        ));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "Report Index Space", "confirmed report indexing"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = service.createMeeting(
                owner.id(),
                space.space().id(),
                "Report Index Meeting",
                OffsetDateTime.of(2026, 7, 26, 11, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        String meetingId = meeting.meeting().id();

        MeetingReport candidate = service.saveReportCandidate(
                meetingId,
                owner.id(),
                "확정 회의록",
                "확정 시 색인 작업이 생성되는지 확인한다.",
                "## 결정\n- 색인 파이프라인을 유지한다.",
                List.of(),
                List.of(),
                List.of()
        );
        assertThat(candidate.status()).isEqualTo(MeetingReportStatus.CANDIDATE);
        assertThat(candidate.current()).isFalse();

        entityManager.flush();
        assertThat(reportConfirmedJobs(meetingId))
                .as("CANDIDATE 상태에서는 색인 작업이 생기지 않아야 한다")
                .isZero();

        MeetingReport confirmed = service.confirmMeetingReport(meetingId, candidate.id());

        // trigger 조건은 status=CONFIRMED 와 is_current=true 두 가지다.
        // 애플리케이션이 둘을 모두 만족시켜야 색인이 걸린다.
        assertThat(confirmed.status()).isEqualTo(MeetingReportStatus.CONFIRMED);
        assertThat(confirmed.current()).isTrue();
        assertThat(confirmed.confirmedAt()).isNotNull();

        entityManager.flush();
        entityManager.clear();

        assertThat(reportConfirmedJobs(meetingId))
                .as("확정된 회의록은 REPORT_CONFIRMED 색인 작업을 만들어야 한다")
                .isEqualTo(1);

        // 색인 작업은 space 범위로 기록되고 knowledge 문서가 아닌 meeting source로 걸린다.
        assertThat(jdbc.queryForObject(
                """
                select count(*) from embedding_jobs
                where meeting_id = ? and trigger_reason = 'REPORT_CONFIRMED'
                  and space_id = ? and project_knowledge_id is null
                """,
                Integer.class,
                meetingId,
                space.space().id()
        )).isEqualTo(1);
    }

    private Integer reportConfirmedJobs(String meetingId) {
        return jdbc.queryForObject(
                """
                select count(*) from embedding_jobs
                where meeting_id = ? and trigger_reason = 'REPORT_CONFIRMED'
                """,
                Integer.class,
                meetingId
        );
    }
}
