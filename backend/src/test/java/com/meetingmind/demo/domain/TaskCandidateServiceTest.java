package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.MeetingRole;
import com.meetingmind.demo.authz.ParticipantType;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.dto.ConfirmTaskCandidateRequest;
import com.meetingmind.demo.dto.TaskCandidateGenerationResponse;
import com.meetingmind.demo.dto.ai.TaskAiGatewayRequest;
import com.meetingmind.demo.dto.ai.TaskAiGatewayResponse;
import com.meetingmind.demo.service.TaskAiGatewayClient;
import com.meetingmind.demo.service.TaskCandidateService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TaskCandidateServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-14T10:00:00+09:00");

    @Test
    void generateChecksEditAccessAndStoresOnlyCandidatesWithCanonicalSources() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner", "김오너");
        User assignee = context.user("user-assignee", "김진수");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), assignee.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of(assignee.id())
        );
        MeetingSpeaker speaker = context.store.addMeetingSpeaker(
                meeting.meeting().id(), "S1", assignee.displayName(), FIXED_CLOCK.instant()
        );
        TranscriptSegment segment = context.store.addTranscriptSegment(
                meeting.meeting().id(), speaker.id(), speaker.label(), speaker.displayName(),
                1_000, 5_000, "ERD 수정안을 문서화합니다.", "STT", 1
        );
        context.gateway.response = supportedResponse(segment.id());

        TaskCandidateGenerationResponse response = context.service.generate(
                "Bearer access-token", meeting.meeting().id()
        );

        assertThat(response.unsupported()).isFalse();
        assertThat(response.canConfirm()).isTrue();
        assertThat(response.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.status()).isEqualTo("CANDIDATE");
            assertThat(candidate.sourceIds()).containsExactly(segment.id());
            assertThat(candidate.suggestedAssigneeId()).isEqualTo(assignee.id());
        });
        assertThat(response.assignees())
                .extracting(com.meetingmind.demo.dto.TaskAssigneeResponse::id)
                .containsExactlyInAnyOrder(owner.id(), assignee.id());
        assertThat(context.gateway.captured.projectId()).isEqualTo(space.space().id());
        assertThat(context.gateway.captured.sources()).allSatisfy(source -> {
            assertThat(source.projectId()).isEqualTo(space.space().id());
            assertThat(source.meetingId()).isEqualTo(meeting.meeting().id());
        });
        assertThat(context.store.findTaskCandidates(meeting.meeting().id())).hasSize(1);
    }

    @Test
    void generateRejectsViewerBeforeAiAndDoesNotStoreUnsupported() {
        TestContext viewerContext = newContext("user-viewer");
        User owner = viewerContext.user("user-owner", "오너");
        User viewer = viewerContext.user("user-viewer", "뷰어");
        WorkspaceDomainService.SpaceCreationResult space = viewerContext.workspace.createSpace(owner.id(), "MeetingMind", null);
        viewerContext.store.addSpaceMember(space.space().id(), viewer.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = viewerContext.workspace.createMeeting(
                owner.id(), space.space().id(), "회의", SCHEDULED_AT, List.of(viewer.id())
        );

        assertThatThrownBy(() -> viewerContext.service.generate("Bearer access-token", meeting.meeting().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
        assertThat(viewerContext.gateway.captured).isNull();

        TestContext ownerContext = newContext("user-owner");
        User emptyOwner = ownerContext.user("user-owner", "오너");
        WorkspaceDomainService.SpaceCreationResult emptySpace = ownerContext.workspace.createSpace(
                emptyOwner.id(), "Empty", null
        );
        WorkspaceDomainService.MeetingCreationResult emptyMeeting = ownerContext.workspace.createMeeting(
                emptyOwner.id(), emptySpace.space().id(), "빈 회의", SCHEDULED_AT, List.of()
        );
        TaskCandidateGenerationResponse unsupported = ownerContext.service.generate(
                "Bearer access-token", emptyMeeting.meeting().id()
        );
        assertThat(unsupported.unsupported()).isTrue();
        assertThat(ownerContext.store.findTaskCandidates(emptyMeeting.meeting().id())).isEmpty();
    }

    @Test
    void listAllowsViewerButConfirmRequiresEditAccess() {
        TestContext context = newContext("user-viewer");
        User owner = context.user("user-owner", "오너");
        User viewer = context.user("user-viewer", "뷰어");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), viewer.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "회의", SCHEDULED_AT, List.of(viewer.id())
        );
        TaskCandidate candidate = context.workspace.saveTaskCandidate(
                meeting.meeting().id(), owner.id(), "문서화", null, null, null, List.of("segment-1")
        );

        var listResponse = context.service.list("Bearer access-token", meeting.meeting().id());
        assertThat(listResponse.candidates())
                .singleElement()
                .extracting(com.meetingmind.demo.dto.TaskCandidateResponse::id)
                .isEqualTo(candidate.id());
        assertThat(listResponse.assignees())
                .extracting(com.meetingmind.demo.dto.TaskAssigneeResponse::id)
                .isEmpty();
        assertThat(listResponse.canConfirm()).isFalse();
        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token",
                meeting.meeting().id(),
                candidate.id(),
                new ConfirmTaskCandidateRequest("문서화", null, null, null, "TODO")
        ))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
    }

    @Test
    void confirmCreatesOneCardAndRejectsDuplicateOrInvalidAssignee() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner", "오너");
        User outsider = context.user("user-outsider", "외부인");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "회의", SCHEDULED_AT, List.of()
        );
        TaskCandidate invalid = context.workspace.saveTaskCandidate(
                meeting.meeting().id(), owner.id(), "잘못된 담당자", null, null, null, List.of("segment-1")
        );
        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), invalid.id(),
                new ConfirmTaskCandidateRequest("잘못된 담당자", null, outsider.id(), null, "TODO")
        )).isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));

        TaskCandidate candidate = context.workspace.saveTaskCandidate(
                meeting.meeting().id(), owner.id(), "ERD 문서화", null, owner.id(), null, List.of("segment-1")
        );
        var response = context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), candidate.id(),
                new ConfirmTaskCandidateRequest(
                        "ERD 문서화 완료", "회의 합의 사항을 반영한다.", owner.id(), "2026-07-20", "IN_PROGRESS"
                )
        );

        assertThat(response.sourceCandidateId()).isEqualTo(candidate.id());
        assertThat(context.store.findTaskCardBySourceCandidateId(candidate.id())).get().satisfies(card -> {
            assertThat(card.title()).isEqualTo("ERD 문서화 완료");
            assertThat(card.description()).isEqualTo("회의 합의 사항을 반영한다.");
            assertThat(card.status()).isEqualTo(TaskCardStatus.IN_PROGRESS);
            assertThat(card.assigneeId()).isEqualTo(owner.id());
        });
        assertThat(context.store.findTaskCandidateById(candidate.id())).get()
                .extracting(TaskCandidate::status)
                .isEqualTo(TaskCandidateStatus.CONFIRMED);
        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), candidate.id(),
                new ConfirmTaskCandidateRequest("중복", null, owner.id(), null, "TODO")
        )).isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void confirmRejectsMeetingGuestEvenWhenGuestIsEditor() {
        TestContext context = newContext("user-guest");
        User owner = context.user("user-owner", "오너");
        User guest = context.user("user-guest", "게스트");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "회의", SCHEDULED_AT, List.of()
        );
        context.store.addMeetingParticipant(
                meeting.meeting().id(), guest.id(), MeetingRole.EDITOR, ParticipantType.GUEST
        );
        TaskCandidate candidate = context.workspace.saveTaskCandidate(
                meeting.meeting().id(), owner.id(), "문서화", null, null, null, List.of("segment-1")
        );

        TaskCandidateGenerationResponse generationResponse = context.service.generate(
                "Bearer access-token", meeting.meeting().id()
        );
        assertThat(generationResponse.canConfirm()).isFalse();
        assertThat(generationResponse.assignees()).isEmpty();

        assertThatThrownBy(() -> context.service.confirm(
                "Bearer access-token", meeting.meeting().id(), candidate.id(),
                new ConfirmTaskCandidateRequest("문서화", null, null, null, "TODO")
        )).isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    private TestContext newContext(String authUserId) {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(
                        authUserId, authUserId + "@meetingmind.ai", authUserId, null, "active"
                ));
        InMemoryWorkspaceStore store = new InMemoryWorkspaceStore();
        SpaceAccessPolicy spaceAccessPolicy = new SpaceAccessPolicy();
        WorkspaceDomainService workspace = new WorkspaceDomainService(store, spaceAccessPolicy, FIXED_CLOCK);
        FakeTaskAiGateway gateway = new FakeTaskAiGateway();
        TaskCandidateService service = new TaskCandidateService(
                authService,
                workspace,
                new MeetingAccessPolicy(spaceAccessPolicy),
                spaceAccessPolicy,
                gateway
        );
        return new TestContext(store, workspace, gateway, service);
    }

    private static TaskAiGatewayResponse supportedResponse(String sourceId) {
        return new TaskAiGatewayResponse(
                List.of(
                        new TaskAiGatewayResponse.Task(
                                "ERD 수정안 문서화", "김진수", "2026-07-20",
                                List.of(sourceId, "forged"), "candidate"
                        ),
                        new TaskAiGatewayResponse.Task(
                                "근거 없는 후보", null, null, List.of("forged"), "candidate"
                        )
                ),
                List.of(new TaskAiGatewayResponse.Source(
                        sourceId, "transcript", "Sprint Planning", "김진수", "00:00:01-00:00:05",
                        1_000, 5_000, "ERD 수정안을 문서화합니다."
                )),
                false,
                "test-model"
        );
    }

    private static void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(
            InMemoryWorkspaceStore store,
            WorkspaceDomainService workspace,
            FakeTaskAiGateway gateway,
            TaskCandidateService service
    ) {
        User user(String id, String displayName) {
            return store.saveUser(new User(
                    id, id + "@meetingmind.ai", displayName, null, "active",
                    FIXED_CLOCK.instant(), FIXED_CLOCK.instant()
            ));
        }
    }

    private static class FakeTaskAiGateway implements TaskAiGatewayClient {
        private TaskAiGatewayRequest captured;
        private TaskAiGatewayResponse response;

        @Override
        public TaskAiGatewayResponse extract(TaskAiGatewayRequest request) {
            captured = request;
            return response;
        }
    }
}
