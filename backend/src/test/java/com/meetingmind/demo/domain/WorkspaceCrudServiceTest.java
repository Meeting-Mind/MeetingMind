package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class WorkspaceCrudServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-21T10:00:00+09:00");

    @Test
    void ownerUpdatesAndSoftDeletesSpaceSoItNoLongerAppearsInList() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "Before", null);

        Space updated = context.workspace.updateSpace("owner", space.space().id(), "After", true, null, true);
        boolean deleted = context.workspace.deleteSpace("owner", space.space().id());

        assertThat(updated.name()).isEqualTo("After");
        assertThat(updated.description()).isNull();
        assertThat(updated.updatedAt()).isEqualTo(CLOCK.instant());
        assertThat(deleted).isTrue();
        assertThat(context.workspace.listSpaces("owner")).isEmpty();
        assertThat(context.store.findSpaceById(space.space().id())).isEmpty();
    }

    @Test
    void invitationIsBoundToInvitedEmailAndCreatesMemberOnlyAfterAcceptance() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        WorkspaceDomainService.SpaceInvitationCreation created = context.workspace.createSpaceInvitation(
                "owner", space.space().id(), "member@example.com", "MEMBER"
        );

        assertThat(created.invitation().tokenHash()).isNotEqualTo(created.token());
        assertThatThrownBy(() -> context.workspace.resolveSpaceInvitation(
                "other", "other@example.com", space.space().id(), created.invitation().id(), created.token(), true
        )).isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));

        WorkspaceDomainService.SpaceInvitationResolution accepted = context.workspace.resolveSpaceInvitation(
                "member", "member@example.com", space.space().id(), created.invitation().id(), created.token(), true
        );

        assertThat(accepted.invitation().status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(accepted.member()).isNotNull();
        assertThat(accepted.member().role()).isEqualTo(SpaceRole.MEMBER);
    }

    @Test
    void memberCanCreateUpdateAndSoftDeleteGeneralTaskCard() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), "member", SpaceRole.MEMBER, CLOCK.instant());

        TaskCard created = context.workspace.createTaskCard(
                "member", space.space().id(), "문서 정리", null, "member", null, null
        );
        assertThat(created.priority()).isEqualTo(TaskCardPriority.MEDIUM);
        assertThat(created.labels()).isEmpty();
        TaskCard updated = context.workspace.updateTaskCard("member", space.space().id(), created.id(),
                new WorkspaceDomainService.TaskCardPatch(
                        null, false, "API 명세를 갱신한다.", true, null, false,
                        null, false, "IN_REVIEW", true, "HIGH", true, List.of("backend", "api"), true
                ));

        assertThat(updated.status()).isEqualTo(TaskCardStatus.IN_REVIEW);
        assertThat(updated.priority()).isEqualTo(TaskCardPriority.HIGH);
        assertThat(updated.labels()).containsExactly("backend", "api");
        assertThat(context.workspace.listTaskCards("member", space.space().id(), null, null, "명세"))
                .extracting(view -> view.task().id()).containsExactly(created.id());
        assertThat(context.workspace.deleteTaskCard("member", space.space().id(), created.id())).isTrue();
        assertThat(context.workspace.listTaskCards("member", space.space().id(), null, null, null)).isEmpty();
    }

    @Test
    void taskCardRejectsDuplicateLabelsIgnoringCase() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);

        assertThatThrownBy(() -> context.workspace.createTaskCard(
                "owner", space.space().id(), "API 계약 확인", null, null, null, null,
                "MEDIUM", List.of("API", "api")
        )).isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void spaceMemberWithoutMeetingAccessCannotSeeTaskMeetingSource() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), "member", SpaceRole.MEMBER, CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                "owner", space.space().id(), "Private Sprint", SCHEDULED_AT, List.of()
        );
        TaskCard task = context.workspace.createTaskCard(
                "owner", space.space().id(), "Private follow-up", "회의 액션 아이템", null, null, meeting.meeting().id()
        );

        WorkspaceDomainService.TaskCardView memberView = context.workspace
                .listTaskCards("member", space.space().id(), null, null, null)
                .stream().filter(view -> view.task().id().equals(task.id())).findFirst().orElseThrow();
        WorkspaceDomainService.TaskCardView ownerView = context.workspace
                .listTaskCards("owner", space.space().id(), null, null, null)
                .stream().filter(view -> view.task().id().equals(task.id())).findFirst().orElseThrow();

        assertThat(memberView.meetingSourceVisible()).isFalse();
        assertThat(ownerView.meetingSourceVisible()).isTrue();
    }

    @Test
    void reportEditCreatesNewDraftVersionAndKeepsOriginalHistory() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                "owner", space.space().id(), "Sprint", SCHEDULED_AT, List.of()
        );
        MeetingReport candidate = context.workspace.saveReportCandidate(
                meeting.meeting().id(), "owner", "원본", "원본 요약", "# 원본", List.of(), List.of(), List.of("segment-1")
        );

        MeetingReport draft = context.workspace.updateMeetingReport("owner", meeting.meeting().id(), candidate.id(),
                new WorkspaceDomainService.ReportPatch("수정본", true, null, false, "# 수정본", true));

        assertThat(draft.status()).isEqualTo(MeetingReportStatus.DRAFT);
        assertThat(draft.version()).isEqualTo(candidate.version() + 1);
        assertThat(context.store.findMeetingReports(meeting.meeting().id())).hasSize(2);
        assertThat(context.workspace.listMeetingReports("owner", meeting.meeting().id(), null))
                .extracting(MeetingReport::id).containsExactly(draft.id(), candidate.id());
    }

    @Test
    void reportRestoreCreatesNewDraftWithoutChangingHistoricalVersion() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                "owner", space.space().id(), "Sprint", SCHEDULED_AT, List.of()
        );
        MeetingReport original = context.workspace.saveReportCandidate(
                meeting.meeting().id(), "owner", "원본", "원본 요약", "# 원본", List.of(), List.of(), List.of("segment-1")
        );
        MeetingReport changed = context.workspace.updateMeetingReport("owner", meeting.meeting().id(), original.id(),
                new WorkspaceDomainService.ReportPatch("수정본", true, "수정 요약", true, "# 수정", true));

        MeetingReport restored = context.workspace.restoreMeetingReport("owner", meeting.meeting().id(), original.id());

        assertThat(restored.status()).isEqualTo(MeetingReportStatus.DRAFT);
        assertThat(restored.version()).isEqualTo(changed.version() + 1);
        assertThat(restored.title()).isEqualTo(original.title());
        assertThat(restored.markdown()).isEqualTo(original.markdown());
        assertThat(context.workspace.meetingReportDetail("owner", meeting.meeting().id(), original.id()).id())
                .isEqualTo(original.id());
    }

    @Test
    void expiredReportCandidateCannotBeEditedOrConfirmed() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                "owner", space.space().id(), "Sprint", SCHEDULED_AT, List.of()
        );
        MeetingReport expired = context.store.saveMeetingReport(new MeetingReport(
                "report-expired", meeting.meeting().id(), MeetingReportStatus.CANDIDATE,
                "만료 후보", "만료 요약", "# 만료", List.of(), List.of(), List.of("segment-1"), "owner", 1,
                false, CLOCK.instant().minus(java.time.Duration.ofDays(7)), null
        ));

        assertThatThrownBy(() -> context.workspace.confirmMeetingReport(meeting.meeting().id(), expired.id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "CANDIDATE_EXPIRED"));
        assertThatThrownBy(() -> context.workspace.updateMeetingReport("owner", meeting.meeting().id(), expired.id(),
                new WorkspaceDomainService.ReportPatch("수정", true, null, false, null, false)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.CONFLICT, "CANDIDATE_EXPIRED"));
    }

    private static void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private TestContext newContext() {
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        store.saveUser(user("owner", "owner@example.com"));
        store.saveUser(user("member", "member@example.com"));
        store.saveUser(user("other", "other@example.com"));
        return new TestContext(store, new WorkspaceDomainService(store, new SpaceAccessPolicy(), CLOCK));
    }

    private User user(String id, String email) {
        return new User(id, email, id, null, "active", CLOCK.instant(), CLOCK.instant());
    }

    private record TestContext(InMemoryWorkspaceStore store, WorkspaceDomainService workspace) {
    }
}
