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
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpaceControllerTest {

    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-10T10:00:00+09:00");

    @Test
    void createSpaceAndListSpacesUseTargetShapeWithoutWorkspaceMock() {
        TestContext context = newContext();

        var created = context.controller.createSpace(
                "Bearer access-token",
                new CreateSpaceRequest("MeetingMind", "AI 회의 지식화 프로젝트")
        );
        var spaces = context.controller.listSpaces("Bearer access-token");

        assertThat(created.name()).isEqualTo("MeetingMind");
        assertThat(created.role()).isEqualTo("OWNER");
        assertThat(spaces.spaces()).hasSize(1);
        assertThat(spaces.spaces().getFirst().id()).isEqualTo(created.id());
        assertThat(spaces.spaces().getFirst().meetingCount()).isZero();
    }

    @Test
    void createMeetingUsesCreatedSpaceAndRegistersHost() {
        TestContext context = newContext();
        var space = context.controller.createSpace(
                "Bearer access-token",
                new CreateSpaceRequest("MeetingMind", null)
        );

        var meeting = context.controller.createMeeting(
                "Bearer access-token",
                space.id(),
                new CreateMeetingRequest("API 구조 논의", SCHEDULED_AT, List.of())
        );
        var spaces = context.controller.listSpaces("Bearer access-token");

        assertThat(meeting.status()).isEqualTo("SCHEDULED");
        assertThat(spaces.spaces().getFirst().meetingCount()).isEqualTo(1);
    }

    private TestContext newContext() {
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
                new InMemoryWorkspaceStore(),
                new SpaceAccessPolicy()
        );
        return new TestContext(new SpaceController(authService, workspace));
    }

    private record TestContext(SpaceController controller) {
    }
}
