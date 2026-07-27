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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void persistsWorkspaceArtifactsAndUsesHashedJoinCode() {
        assertThat(store).isInstanceOf(JpaWorkspaceStore.class);

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
                "JPA schedule persistence",
                OffsetDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 7, 15, 11, 30, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        Meeting updatedMeeting = service.updateMeeting(
                owner.id(), meeting.meeting().id(), "JDBC Meeting Updated", "Updated schedule persistence", null, null, "SCHEDULED"
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

        entityManager.flush();
        entityManager.clear();

        JdbcWorkspaceStore reloaded = new JdbcWorkspaceStore(jdbc, objectMapper);
        assertThat(reloaded.findSpaceById(space.space().id())).contains(space.space());
        assertThat(reloaded.findMeetingByJoinCode(meeting.meeting().joinCode()))
                .get()
                .satisfies(found -> {
                    assertThat(found.id()).isEqualTo(meeting.meeting().id());
                    assertThat(found.title()).isEqualTo(updatedMeeting.title());
                    assertThat(found.description()).isEqualTo("Updated schedule persistence");
                    assertThat(found.scheduledEndAt()).isEqualTo(OffsetDateTime.of(2026, 7, 15, 11, 30, 0, 0, ZoneOffset.UTC));
                });
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
        assertThat(reloadedService.listMeetings(owner.id(), space.space().id()))
                .extracting(summary -> summary.meeting().id())
                .containsExactly(meeting.meeting().id(), inaccessibleMeeting.meeting().id());
        assertThat(reloadedService.listMeetings(member.id(), space.space().id()))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.meeting().id()).isEqualTo(meeting.meeting().id());
                    assertThat(summary.myRole()).isEqualTo(com.meetingmind.demo.authz.MeetingRole.VIEWER);
                });
        assertThatThrownBy(() -> reloadedService.projectAiContextCandidates(guest.id(), space.space().id()))
                .isInstanceOf(AuthorizationException.class)
                .extracting("code")
                .isEqualTo("SPACE_ACCESS_DENIED");

        assertThat(reloadedService.deleteMeeting(owner.id(), inaccessibleMeeting.meeting().id())).isTrue();
        assertThat(reloaded.findMeetingById(inaccessibleMeeting.meeting().id())).isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from meetings where id = ? and deleted_at is not null and deleted_by = ?",
                Integer.class,
                inaccessibleMeeting.meeting().id(),
                owner.id()
        )).isEqualTo(1);
        assertThat(reloadedService.projectAiContextCandidates(owner.id(), space.space().id()).meetings())
                .extracting(Meeting::id)
                .containsExactly(meeting.meeting().id());
    }

    @Test
    void persistsAndResolvesSpaceInvitationThroughJpaStore() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("invitation-owner-" + suffix, now));
        User invitee = store.saveUser(user("invitation-invitee-" + suffix, now));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(owner.id(), "Invitation Space", null);

        WorkspaceDomainService.SpaceInvitationCreation created = service.createSpaceInvitation(
                owner.id(), space.space().id(), invitee.email(), "MEMBER"
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(SpaceInvitation.class, created.invitation().id()))
                .satisfies(invitation -> {
                    assertThat(invitation.email()).isEqualTo(invitee.email());
                    assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
                });

        WorkspaceDomainService.SpaceInvitationResolution resolved = service.resolveSpaceInvitation(
                invitee.id(), invitee.email(), space.space().id(), created.invitation().id(), created.token(), true
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(resolved.member()).isNotNull();
        assertThat(entityManager.find(SpaceInvitation.class, created.invitation().id()).status())
                .isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(store.findSpaceMember(space.space().id(), invitee.id())).isPresent();
    }

    @Test
    void persistsTaskSoftDeleteAndReportDraftVersionThroughJpaStore() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("artifact-owner-" + suffix, now));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(owner.id(), "Artifact Space", null);
        WorkspaceDomainService.MeetingCreationResult meeting = service.createMeeting(
                owner.id(), space.space().id(), "Artifact Meeting",
                OffsetDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC), List.of()
        );
        TaskCard task = service.createTaskCard(
                owner.id(), space.space().id(), "삭제 대상 태스크", null, null, null, null,
                "HIGH", List.of("persistence", "kanban")
        );
        MeetingReport candidate = service.saveReportCandidate(
                meeting.meeting().id(), owner.id(), "초안 원본", "원본 요약", "# 원본", List.of(), List.of(), List.of()
        );
        MeetingReport draft = service.updateMeetingReport(
                owner.id(), meeting.meeting().id(), candidate.id(),
                new WorkspaceDomainService.ReportPatch("수정 초안", true, null, false, "# 수정", true)
        );
        service.deleteTaskCard(owner.id(), space.space().id(), task.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(TaskCard.class, task.id()))
                .satisfies(persisted -> {
                    assertThat(persisted.deletedAt()).isNotNull();
                    assertThat(persisted.priority()).isEqualTo(TaskCardPriority.HIGH);
                    assertThat(persisted.labels()).containsExactly("persistence", "kanban");
                });
        assertThat(store.findTaskCardById(space.space().id(), task.id())).isEmpty();
        assertThat(entityManager.find(MeetingReport.class, draft.id()))
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo(MeetingReportStatus.DRAFT);
                    assertThat(report.version()).isEqualTo(candidate.version() + 1);
                });
        assertThat(store.findMeetingReports(meeting.meeting().id())).hasSize(2);
    }

    @Test
    void completesTranscriptAndEnqueuesOneEmbeddingJob() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("transcript-owner-" + suffix, now));
        User viewer = store.saveUser(user("transcript-viewer-" + suffix, now));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "Transcript Space", "STT lifecycle persistence"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = service.createMeeting(
                owner.id(),
                space.space().id(),
                "Transcript Meeting",
                OffsetDateTime.of(2026, 7, 16, 10, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        store.addSpaceMember(space.space().id(), viewer.id(), com.meetingmind.demo.authz.SpaceRole.MEMBER, now);
        service.addMeetingParticipant(owner.id(), meeting.meeting().id(), viewer.id(), "VIEWER", "member");

        MeetingTranscript processing = service.startMeetingTranscript(owner.id(), meeting.meeting().id(), "clova-nest");
        assertThat(processing.status()).isEqualTo(TranscriptStatus.PROCESSING);
        assertThat(processing.retentionUntil()).isNotNull();

        // 모든 회의 참가자는 자신의 오디오 트랙용 STT 세션을 시작할 수 있고,
        // 회의 단위 transcript aggregate는 같은 PROCESSING row를 공유한다.
        MeetingTranscript shared = service.startMeetingTranscript(
                viewer.id(), meeting.meeting().id(), "clova-nest");
        assertThat(shared).isEqualTo(processing);

        // 완화된 정책이 회의 밖 사용자까지 열어주지는 않는다는 음성 검증은 유지한다.
        User outsider = store.saveUser(user("transcript-outsider-" + suffix, now));
        assertThatThrownBy(() -> service.startMeetingTranscript(outsider.id(), meeting.meeting().id(), "clova-nest"))
                .isInstanceOf(AuthorizationException.class)
                .extracting("code")
                .isEqualTo("MEETING_ACCESS_DENIED");

        TranscriptSegment first = service.appendTranscriptSegment(
                meeting.meeting().id(), "speaker-1", "김진수", 0, 1_200, "JPA 영속화를 시작합니다."
        );
        TranscriptSegment second = service.appendTranscriptSegment(
                meeting.meeting().id(), "speaker-1", "김진수", 1_200, 2_400, "완료되면 RAG 재색인을 요청합니다."
        );
        MeetingTranscript completed = service.completeMeetingTranscript(meeting.meeting().id());
        assertThat(completed.status()).isEqualTo(TranscriptStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();

        entityManager.flush();
        entityManager.clear();

        JdbcWorkspaceStore reloaded = new JdbcWorkspaceStore(jdbc, objectMapper);
        assertThat(reloaded.findMeetingTranscript(meeting.meeting().id()))
                .get()
                .satisfies(transcript -> {
                    assertThat(transcript.status()).isEqualTo(TranscriptStatus.COMPLETED);
                    assertThat(transcript.provider()).isEqualTo("clova-nest");
                });
        assertThat(reloaded.findTranscriptSegments(meeting.meeting().id()))
                .extracting(TranscriptSegment::id)
                .containsExactly(first.id(), second.id());
        assertThat(jdbc.queryForObject(
                """
                select count(*) from embedding_jobs
                where meeting_id = ? and trigger_reason = 'TRANSCRIPT_COMPLETED'
                """,
                Integer.class,
                meeting.meeting().id()
        )).isEqualTo(1);
    }

    @Test
    void projectsRemoteTranscriptWithStableIdsAndReindexesOnlyARevision() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("projection-owner-" + suffix, now));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "Remote Projection Space", "authoritative STT projection"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = service.createMeeting(
                owner.id(),
                space.space().id(),
                "Remote Projection Meeting",
                OffsetDateTime.of(2026, 7, 27, 10, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        service.startMeetingTranscript(owner.id(), meeting.meeting().id(), "soniox-realtime");
        List<WorkspaceDomainService.RemoteTranscriptSegment> snapshot = List.of(
                new WorkspaceDomainService.RemoteTranscriptSegment(
                        "remote-segment-" + suffix,
                        "remote-speaker-" + suffix,
                        "화자 1",
                        "Owner",
                        100,
                        900,
                        "원격 STT 전사를 Core에 투영합니다."
                )
        );

        service.projectRemoteMeetingTranscript(
                owner.id(), meeting.meeting().id(), TranscriptStatus.COMPLETED, snapshot
        );
        entityManager.flush();
        service.projectRemoteMeetingTranscript(
                owner.id(), meeting.meeting().id(), TranscriptStatus.COMPLETED, snapshot
        );
        entityManager.flush();

        assertThat(store.findTranscriptSegments(meeting.meeting().id()))
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.id()).isEqualTo("remote-segment-" + suffix);
                    assertThat(segment.speakerId()).isEqualTo("remote-speaker-" + suffix);
                    assertThat(segment.source()).isEqualTo("stt-remote");
                });
        assertThat(jdbc.queryForObject(
                "select count(*) from embedding_jobs where meeting_id = ?",
                Integer.class,
                meeting.meeting().id()
        )).isEqualTo(1);

        service.projectRemoteMeetingTranscript(
                owner.id(),
                meeting.meeting().id(),
                TranscriptStatus.COMPLETED,
                List.of(new WorkspaceDomainService.RemoteTranscriptSegment(
                        "remote-segment-" + suffix,
                        "remote-speaker-" + suffix,
                        "화자 1",
                        "Owner",
                        100,
                        950,
                        "수정된 원격 STT 전사입니다."
                ))
        );
        entityManager.flush();

        assertThat(store.findTranscriptSegments(meeting.meeting().id()))
                .singleElement()
                .extracting(TranscriptSegment::text)
                .isEqualTo("수정된 원격 STT 전사입니다.");
        assertThat(jdbc.queryForObject(
                "select count(*) from embedding_jobs where meeting_id = ? and trigger_reason = 'FULL_REINDEX'",
                Integer.class,
                meeting.meeting().id()
        )).isEqualTo(1);
    }

    @Test
    void projectKnowledgeCrudResolvesAuditSpace() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("knowledge-owner-" + suffix, now));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "Knowledge Audit Space", "project knowledge audit"
        );

        ProjectKnowledge knowledge = service.createProjectKnowledge(
                owner.id(), space.space().id(), "manual", "운영 기준", "AI 근거로 사용할 공식 지식", null
        );
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                """
                select count(*) from audit_logs
                where action = 'PROJECT_KNOWLEDGE_CREATED'
                  and target_id = ?
                  and space_id = ?
                """,
                Integer.class,
                knowledge.id(),
                space.space().id()
        )).isEqualTo(1);
    }

    @Test
    void reconciliationRestoresMissingProjectionAndCreatesNewGeneration() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User owner = store.saveUser(user("reconcile-owner-" + suffix, now));
        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "Reconciliation Space", "durable transcript projection"
        );
        WorkspaceDomainService.MeetingCreationResult meeting = service.createMeeting(
                owner.id(),
                space.space().id(),
                "Reconciliation Meeting",
                OffsetDateTime.of(2026, 7, 27, 12, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        service.startMeetingTranscript(owner.id(), meeting.meeting().id(), "soniox-realtime");
        List<WorkspaceDomainService.RemoteTranscriptSegment> snapshot = List.of(
                new WorkspaceDomainService.RemoteTranscriptSegment(
                        "reconcile-segment-" + suffix,
                        "reconcile-speaker-" + suffix,
                        "화자 1",
                        "Owner",
                        100,
                        900,
                        "누락된 전사를 자동 복구합니다."
                )
        );

        entityManager.flush();
        assertThat(service.transcriptProjectionCandidateMeetingIds(20)).contains(meeting.meeting().id());
        service.reconcileRemoteMeetingTranscript(meeting.meeting().id(), TranscriptStatus.COMPLETED, snapshot);
        entityManager.flush();
        assertThat(service.transcriptProjectionCandidateMeetingIds(20)).doesNotContain(meeting.meeting().id());

        jdbc.update("delete from transcript_segments where meeting_id = ?", meeting.meeting().id());
        assertThat(service.transcriptProjectionCandidateMeetingIds(20)).contains(meeting.meeting().id());
        service.reconcileRemoteMeetingTranscript(meeting.meeting().id(), TranscriptStatus.COMPLETED, snapshot);
        entityManager.flush();

        assertThat(store.findTranscriptSegments(meeting.meeting().id()))
                .singleElement()
                .extracting(TranscriptSegment::id)
                .isEqualTo("reconcile-segment-" + suffix);
        assertThat(jdbc.queryForObject(
                "select count(*) from embedding_jobs where meeting_id = ? and trigger_reason = 'FULL_REINDEX'",
                Integer.class,
                meeting.meeting().id()
        )).isEqualTo(1);
    }

    private User user(String id, Instant now) {
        return new User(id, id + "@meetingmind.test", id, null, "active", now, now);
    }
}
