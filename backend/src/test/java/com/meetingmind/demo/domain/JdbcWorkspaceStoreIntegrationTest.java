package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import java.time.Instant;
import java.time.LocalDate;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
@Transactional
class JdbcWorkspaceStoreIntegrationTest {

    @Autowired
    private WorkspaceStore store;

    @Autowired
    private WorkspaceDomainService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void persistsWorkspaceArtifactsAndUsesHashedJoinCode() {
        assertThat(store).isInstanceOf(JdbcWorkspaceStore.class);

        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("owner-" + suffix, now));
        User member = store.saveUser(user("member-" + suffix, now));
        User guest = store.saveUser(user("guest-" + suffix, now));

        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "JDBC Space", "runtime persistence"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = service.createMeeting(
                owner.id(),
                space.space().id(),
                "JDBC Meeting",
                OffsetDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        store.addSpaceMember(space.space().id(), member.id(), com.meetingmind.demo.authz.SpaceRole.MEMBER, now);
        service.addMeetingParticipant(
                owner.id(), meeting.meeting().id(), member.id(), "VIEWER", "member"
        );
        WorkspaceDomainService.MeetingCreationResult inaccessibleMeeting = service.createMeeting(
                owner.id(),
                space.space().id(),
                "ACL 밖 Meeting",
                OffsetDateTime.of(2026, 7, 16, 10, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );

        String storedJoinCodeHash = jdbc.queryForObject(
                "select join_code_hash from meetings where id = ?",
                String.class,
                meeting.meeting().id()
        );
        assertThat(storedJoinCodeHash)
                .hasSize(64)
                .isNotEqualTo(meeting.meeting().joinCode());

        MeetingJoinRequest joinRequest = service.createMeetingJoinRequest(
                guest.id(), meeting.meeting().joinCode()
        );
        service.approveMeetingJoinRequest(owner.id(), meeting.meeting().id(), joinRequest.id());

        MeetingSpeaker speaker = store.addMeetingSpeaker(
                meeting.meeting().id(), "S1", "김진수", now
        );
        TranscriptSegment segment = store.addTranscriptSegment(
                meeting.meeting().id(), speaker.id(), speaker.label(), speaker.displayName(),
                1000, 2500, "PostgreSQL 영속화를 진행합니다.", "stt", 0
        );
        MeetingReport candidate = service.saveReportCandidate(
                meeting.meeting().id(),
                owner.id(),
                "영속화 회의록",
                "Backend 저장소를 PostgreSQL로 전환합니다.",
                "# 영속화 회의록",
                List.of(new MeetingReport.ReportDecision(
                        "decision-" + suffix, "DB 전환", "Spring JDBC를 사용한다.", List.of(segment.id())
                )),
                List.of(new MeetingReport.ReportActionItem(
                        "action-" + suffix, "통합 테스트", owner.displayName(), "2026-07-20", List.of(segment.id())
                )),
                List.of(segment.id())
        );
        MeetingReport confirmed = service.confirmMeetingReport(meeting.meeting().id(), candidate.id());

        TaskCandidate taskCandidate = service.saveTaskCandidate(
                meeting.meeting().id(), owner.id(), "JDBC 통합 테스트", owner.displayName(),
                owner.id(), LocalDate.of(2026, 7, 20), List.of(segment.id())
        );
        WorkspaceDomainService.TaskConfirmationResult task = service.confirmTaskCandidate(
                meeting.meeting().id(), taskCandidate.id(), "JDBC 통합 테스트", "DB round-trip 검증",
                owner.id(), LocalDate.of(2026, 7, 20), TaskCardStatus.TODO
        );

        store.saveProjectKnowledge(new ProjectKnowledge(
                "knowledge-" + suffix,
                space.space().id(),
                KnowledgeType.MANUAL,
                "Persistence Boundary",
                "Backend는 권한 필터된 관계형 원천 데이터를 제공한다.",
                meeting.meeting().id(),
                owner.id(),
                KnowledgeStatus.PUBLISHED,
                EmbeddingStatus.COMPLETED,
                null,
                now,
                now,
                null
        ));

        JdbcWorkspaceStore reloaded = new JdbcWorkspaceStore(jdbc, objectMapper);
        assertThat(reloaded.findSpaceById(space.space().id())).contains(space.space());
        assertThat(reloaded.findMeetingByJoinCode(meeting.meeting().joinCode()))
                .get()
                .extracting(Meeting::id)
                .isEqualTo(meeting.meeting().id());
        assertThat(reloaded.findMeetingParticipant(meeting.meeting().id(), guest.id()))
                .get()
                .satisfies(participant -> {
                    assertThat(participant.participantType()).isEqualTo(com.meetingmind.demo.authz.ParticipantType.GUEST);
                    assertThat(participant.accessStatus()).isEqualTo(com.meetingmind.demo.authz.ParticipantAccessStatus.ACTIVE);
                });
        assertThat(reloaded.findTranscriptSegments(meeting.meeting().id()))
                .extracting(TranscriptSegment::text)
                .containsExactly(segment.text());
        assertThat(reloaded.findMeetingReportById(confirmed.id()))
                .get()
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo(MeetingReportStatus.CONFIRMED);
                    assertThat(report.current()).isTrue();
                    assertThat(report.decisions()).hasSize(1);
                    assertThat(report.actionItems()).hasSize(1);
                    assertThat(report.sourceIds()).containsExactly(segment.id());
                });
        assertThat(reloaded.findTaskCardBySourceCandidateId(taskCandidate.id())).contains(task.taskCard());
        assertThat(reloaded.findProjectKnowledge(space.space().id()))
                .extracting(ProjectKnowledge::title)
                .contains("Persistence Boundary");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where space_id = ?",
                Integer.class,
                space.space().id()
        )).isGreaterThanOrEqualTo(2);

        SpaceAccessPolicy spacePolicy = new SpaceAccessPolicy();
        WorkspaceDomainService reloadedService = new WorkspaceDomainService(
                reloaded,
                spacePolicy,
                new MeetingAccessPolicy(spacePolicy),
                java.time.Clock.systemUTC()
        );
        assertThat(reloadedService.meetingAiContext(meeting.meeting().id()).transcriptSegments())
                .extracting(TranscriptSegment::id)
                .contains(segment.id());
        WorkspaceDomainService.ProjectAiContextCandidates ownerCandidates =
                reloadedService.projectAiContextCandidates(owner.id(), space.space().id());
        assertThat(ownerCandidates.meetings())
                .extracting(Meeting::id)
                .containsExactlyInAnyOrder(meeting.meeting().id(), inaccessibleMeeting.meeting().id());
        assertThat(ownerCandidates.projectKnowledge())
                .extracting(ProjectKnowledge::title)
                .containsExactly("Persistence Boundary");
        WorkspaceDomainService.ProjectAiContextCandidates memberCandidates =
                reloadedService.projectAiContextCandidates(member.id(), space.space().id());
        assertThat(memberCandidates.meetings())
                .extracting(Meeting::id)
                .containsExactly(meeting.meeting().id());
        assertThatThrownBy(() -> reloadedService.projectAiContextCandidates(guest.id(), space.space().id()))
                .isInstanceOf(AuthorizationException.class)
                .extracting("code")
                .isEqualTo("SPACE_ACCESS_DENIED");
    }

    private User user(String id, Instant now) {
        return new User(id, id + "@meetingmind.test", id, null, "active", now, now);
    }
}
