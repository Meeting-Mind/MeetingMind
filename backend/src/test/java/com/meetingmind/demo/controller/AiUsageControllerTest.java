package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.domain.InMemoryWorkspaceStore;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateMeetingRequest;
import com.meetingmind.demo.dto.CreateSpaceRequest;
import com.meetingmind.demo.dto.RecordAiUsageEventRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiUsageControllerTest {

    @Test
    void recordsAndAggregatesSpaceAiUsage() {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(
                        "user-owner",
                        "owner@meetingmind.ai",
                        "오너",
                        null,
                        "active"
                ));
        WorkspaceDomainService workspace = new WorkspaceDomainService(
                new InMemoryWorkspaceStore(), new SpaceAccessPolicy()
        );
        SpaceController spaceController = new SpaceController(authService, workspace);
        AiUsageController controller = new AiUsageController(authService, workspace, 0);

        var space = spaceController.createSpace(
                "Bearer access-token",
                new CreateSpaceRequest("MeetingMind", null)
        );
        var meeting = spaceController.createMeeting(
                "Bearer access-token",
                space.id(),
                new CreateMeetingRequest(
                        "API 구조 논의",
                        OffsetDateTime.parse("2026-07-25T10:00:00+09:00"),
                        List.of()
                )
        );

        var recorded = controller.recordAiUsageEvent(
                "Bearer access-token",
                new RecordAiUsageEventRequest(
                        null,
                        meeting.id(),
                        "meeting-ai",
                        "openai",
                        "responses",
                        false,
                        120,
                        48,
                        168,
                        900L
                )
        );
        var usage = controller.spaceAiUsage("Bearer access-token", space.id(), "month");

        assertThat(recorded.recorded()).isTrue();
        assertThat(recorded.spaceId()).isEqualTo(space.id());
        assertThat(usage.totalRequests()).isEqualTo(1);
        assertThat(usage.totalInputTokens()).isEqualTo(120);
        assertThat(usage.totalOutputTokens()).isEqualTo(48);
        assertThat(usage.features())
                .filteredOn(feature -> feature.feature().equals("meeting-ai"))
                .singleElement()
                .satisfies(feature -> {
                    assertThat(feature.requests()).isEqualTo(1);
                    assertThat(feature.inputTokens()).isEqualTo(120);
                    assertThat(feature.outputTokens()).isEqualTo(48);
                });
        // quota 미설정(0)이면 limit과 usagePercent를 노출하지 않는다.
        // 0을 내려보내면 클라이언트가 "한도 0"으로 오해한다.
        assertThat(usage.limit()).isNull();
        assertThat(usage.usagePercent()).isNull();
    }

    @Test
    void exposesQuotaLimitAndUsagePercentWhenQuotaIsConfigured() {
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(
                        "user-owner",
                        "owner@meetingmind.ai",
                        "오너",
                        null,
                        "active"
                ));
        WorkspaceDomainService workspace = new WorkspaceDomainService(
                new InMemoryWorkspaceStore(), new SpaceAccessPolicy()
        );
        SpaceController spaceController = new SpaceController(authService, workspace);
        AiUsageController controller = new AiUsageController(authService, workspace, 1_000);

        var space = spaceController.createSpace(
                "Bearer access-token",
                new CreateSpaceRequest("MeetingMind", null)
        );
        var meeting = spaceController.createMeeting(
                "Bearer access-token",
                space.id(),
                new CreateMeetingRequest(
                        "API 구조 논의",
                        OffsetDateTime.parse("2026-07-25T10:00:00+09:00"),
                        List.of()
                )
        );
        controller.recordAiUsageEvent(
                "Bearer access-token",
                new RecordAiUsageEventRequest(
                        null, meeting.id(), "meeting-ai", "openai", "responses", false, 120, 48, 168, 900L
                )
        );

        var usage = controller.spaceAiUsage("Bearer access-token", space.id(), "month");

        // 양성 대조: 사용량이 실제로 집계돼야 percent 단정이 의미를 가진다.
        assertThat(usage.totalInputTokens() + usage.totalOutputTokens()).isEqualTo(168);
        assertThat(usage.limit()).isEqualTo(1_000);
        assertThat(usage.usagePercent()).isEqualTo(16.8);
    }

    @Test
    void quotaOverrunIsReportedButNeverBlocks() {
        // 결정: quota는 표시 전용이다. 초과해도 호출을 막지 않는다.
        AuthService authService = mock(AuthService.class);
        when(authService.currentUser("Bearer access-token"))
                .thenReturn(new AuthUserResponse(
                        "user-owner",
                        "owner@meetingmind.ai",
                        "오너",
                        null,
                        "active"
                ));
        WorkspaceDomainService workspace = new WorkspaceDomainService(
                new InMemoryWorkspaceStore(), new SpaceAccessPolicy()
        );
        SpaceController spaceController = new SpaceController(authService, workspace);
        AiUsageController controller = new AiUsageController(authService, workspace, 100);

        var space = spaceController.createSpace(
                "Bearer access-token",
                new CreateSpaceRequest("MeetingMind", null)
        );
        var meeting = spaceController.createMeeting(
                "Bearer access-token",
                space.id(),
                new CreateMeetingRequest(
                        "API 구조 논의",
                        OffsetDateTime.parse("2026-07-25T10:00:00+09:00"),
                        List.of()
                )
        );
        controller.recordAiUsageEvent(
                "Bearer access-token",
                new RecordAiUsageEventRequest(
                        null, meeting.id(), "meeting-ai", "openai", "responses", false, 120, 48, 168, 900L
                )
        );

        var usage = controller.spaceAiUsage("Bearer access-token", space.id(), "month");
        assertThat(usage.usagePercent()).isGreaterThan(100.0);

        // 초과 이후에도 기록과 조회가 계속 성공한다.
        var afterOverrun = controller.recordAiUsageEvent(
                "Bearer access-token",
                new RecordAiUsageEventRequest(
                        null, meeting.id(), "meeting-ai", "openai", "responses", false, 10, 5, 15, 100L
                )
        );
        assertThat(afterOverrun.recorded()).isTrue();
        assertThat(controller.spaceAiUsage("Bearer access-token", space.id(), "month").totalRequests())
                .isEqualTo(2);
    }
}
