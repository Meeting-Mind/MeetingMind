package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;
import com.meetingmind.demo.dto.ai.ReportCandidateGenerationResponse;
import com.meetingmind.demo.service.AiGatewayException;
import com.meetingmind.demo.service.ReportAiGatewayClient;
import com.meetingmind.demo.service.ReportCandidateService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportCandidateServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-13T10:00:00+09:00");

    @Test
    void generateChecksEditAccessSendsSingleMeetingSourcesAndStoresCandidate() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of()
        );
        MeetingSpeaker speaker = context.store.addMeetingSpeaker(
                meeting.meeting().id(), "S1", "김진수", FIXED_CLOCK.instant()
        );
        TranscriptSegment segment = context.store.addTranscriptSegment(
                meeting.meeting().id(), speaker.id(), speaker.label(), speaker.displayName(),
                1_000, 5_000, "권한 필터를 먼저 적용합니다.", "STT", 1
        );
        context.gateway.response = supportedResponse(segment.id());

        ReportCandidateGenerationResponse response = context.service.generate(
                "Bearer access-token", meeting.meeting().id()
        );

        assertThat(response.unsupported()).isFalse();
        assertThat(response.candidate()).isNotNull();
        assertThat(response.candidate().status()).isEqualTo("CANDIDATE");
        assertThat(response.candidate().createdBy()).isEqualTo(owner.id());
        assertThat(response.candidate().sourceIds()).containsExactly(segment.id());
        assertThat(response.candidate().decisions().getFirst().sourceIds()).containsExactly(segment.id());
        assertThat(response.candidate().actionItems().getFirst().sourceIds()).isEmpty();
        assertThat(context.gateway.captured.meetingId()).isEqualTo(meeting.meeting().id());
        assertThat(context.gateway.captured.sources())
                .allSatisfy(source -> assertThat(source.meetingId()).isEqualTo(meeting.meeting().id()));
        assertThat(context.store.findMeetingReports(meeting.meeting().id()))
                .singleElement()
                .satisfies(report -> {
                    assertThat(report.status()).isEqualTo(MeetingReportStatus.CANDIDATE);
                    assertThat(report.version()).isEqualTo(1);
                    assertThat(report.current()).isFalse();
                    assertThat(report.markdown()).startsWith("## 요약");
                });
    }

    @Test
    void generateRejectsViewerBeforeLoadingAiGateway() {
        TestContext context = newContext("user-viewer");
        User owner = context.user("user-owner");
        User viewer = context.user("user-viewer");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        context.store.addSpaceMember(space.space().id(), viewer.id(), SpaceRole.MEMBER, FIXED_CLOCK.instant());
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of(viewer.id())
        );

        assertThatThrownBy(() -> context.service.generate("Bearer access-token", meeting.meeting().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED"));
        assertThat(context.gateway.captured).isNull();
    }

    @Test
    void generateDoesNotStoreUnsupportedResponse() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "빈 회의", SCHEDULED_AT, List.of()
        );
        ReportCandidateGenerationResponse response = context.service.generate(
                "Bearer access-token", meeting.meeting().id()
        );

        assertThat(response.unsupported()).isTrue();
        assertThat(response.candidate()).isNull();
        assertThat(context.store.findMeetingReports(meeting.meeting().id())).isEmpty();
        assertThat(context.gateway.captured).isNull();
    }

    @Test
    void generateMapsGatewayFailureToContractError() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of()
        );
        MeetingSpeaker speaker = context.store.addMeetingSpeaker(
                meeting.meeting().id(), "S1", "김진수", FIXED_CLOCK.instant()
        );
        context.store.addTranscriptSegment(
                meeting.meeting().id(), speaker.id(), speaker.label(), speaker.displayName(),
                1_000, 5_000, "회의 내용입니다.", "STT", 1
        );
        context.gateway.failure = new AiGatewayException("boom");

        assertThatThrownBy(() -> context.service.generate("Bearer access-token", meeting.meeting().id()))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void editCreatesANewCandidateAndSendsOnlySingleMeetingSources() {
        TestContext context = newContext("user-owner");
        User owner = context.user("user-owner");
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace(owner.id(), "MeetingMind", null);
        WorkspaceDomainService.MeetingCreationResult meeting = context.workspace.createMeeting(
                owner.id(), space.space().id(), "Sprint Planning", SCHEDULED_AT, List.of()
        );
        MeetingSpeaker speaker = context.store.addMeetingSpeaker(meeting.meeting().id(), "S1", "김진수", FIXED_CLOCK.instant());
        TranscriptSegment segment = context.store.addTranscriptSegment(
                meeting.meeting().id(), speaker.id(), speaker.label(), speaker.displayName(),
                1_000, 5_000, "권한 필터를 먼저 적용합니다.", "STT", 1
        );
        context.gateway.response = supportedResponse(segment.id());
        ReportCandidateGenerationResponse original = context.service.generate("Bearer access-token", meeting.meeting().id());

        ReportCandidateGenerationResponse edited = context.service.edit(
                "Bearer access-token", meeting.meeting().id(), original.candidate().id(), "요약을 두 문장으로 줄여줘"
        );

        assertThat(edited.candidate()).isNotNull();
        assertThat(edited.candidate().version()).isEqualTo(2);
        assertThat(context.gateway.captured.instruction()).isEqualTo("요약을 두 문장으로 줄여줘");
        assertThat(context.gateway.captured.currentReportMarkdown()).isEqualTo(original.candidate().markdown());
        assertThat(context.gateway.captured.sources())
                .allSatisfy(source -> assertThat(source.meetingId()).isEqualTo(meeting.meeting().id()));
        assertThat(context.store.findMeetingReports(meeting.meeting().id())).hasSize(2);
    }

    private static ReportAiGatewayResponse supportedResponse(String sourceId) {
        return new ReportAiGatewayResponse(
                "권한 필터를 먼저 적용하기로 했습니다.",
                List.of(new ReportAiGatewayResponse.Decision(
                        "권한 필터 선적용", "AI 호출 전에 권한을 확인합니다.", List.of(sourceId, "forged")
                )),
                List.of(new ReportAiGatewayResponse.ActionItem(
                        "권한 테스트 추가", "김진수", null, List.of("forged"), "candidate"
                )),
                "## 요약\n권한 필터를 먼저 적용하기로 했습니다.",
                List.of(new ReportAiGatewayResponse.Source(
                        sourceId, "transcript", "Sprint Planning", "김진수", "00:00:01-00:00:05",
                        1_000, 5_000, "권한 필터를 먼저 적용합니다."
                )),
                false,
                "test-model"
        );
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
        FakeReportAiGateway gateway = new FakeReportAiGateway();
        ReportCandidateService service = new ReportCandidateService(
                authService, workspace, new MeetingAccessPolicy(spaceAccessPolicy), gateway
        );
        return new TestContext(store, workspace, gateway, service);
    }

    private static void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private record TestContext(
            InMemoryWorkspaceStore store,
            WorkspaceDomainService workspace,
            FakeReportAiGateway gateway,
            ReportCandidateService service
    ) {
        User user(String id) {
            return store.saveUser(new User(
                    id, id + "@meetingmind.ai", id, null, "active", FIXED_CLOCK.instant(), FIXED_CLOCK.instant()
            ));
        }
    }

    private static class FakeReportAiGateway implements ReportAiGatewayClient {
        private ReportAiGatewayRequest captured;
        private ReportAiGatewayResponse response;
        private RuntimeException failure;

        @Override
        public ReportAiGatewayResponse generate(ReportAiGatewayRequest request) {
            captured = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
