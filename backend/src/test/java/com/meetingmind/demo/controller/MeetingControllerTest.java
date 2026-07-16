package com.meetingmind.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.domain.InMemoryWorkspaceStore;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateMeetingJoinRequestRequest;
import com.meetingmind.demo.dto.UpdateMeetingRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeetingControllerTest {

    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-07-10T10:00:00+09:00");

    @Test
    void createAndApproveJoinRequestUseCodeOnlyEntryPoint() {
        AuthService authService = mock(AuthService.class);
        AuthUserResponse owner = user("user-owner");
        AuthUserResponse guest = user("user-guest");
        when(authService.currentUser("Bearer owner-token")).thenReturn(owner);
        when(authService.currentUser("Bearer guest-token")).thenReturn(guest);

        WorkspaceDomainService workspace = new WorkspaceDomainService(
                new InMemoryWorkspaceStore(),
                new SpaceAccessPolicy()
        );
        workspace.ensureUser(owner.id(), owner.email(), owner.displayName(), null, owner.status());
        var space = workspace.createSpace(owner.id(), "MeetingMind", null);
        var meeting = workspace.createMeeting(
                owner.id(), space.space().id(), "참가 신청 회의", SCHEDULED_AT, List.of()
        );
        MeetingController controller = new MeetingController(authService, workspace);

        var created = controller.createJoinRequest(
                "Bearer guest-token",
                new CreateMeetingJoinRequestRequest(meeting.meeting().joinCode())
        );
        var approved = controller.approveJoinRequest(
                "Bearer owner-token",
                meeting.meeting().id(),
                created.requestId()
        );

        assertThat(created.meetingId()).isEqualTo(meeting.meeting().id());
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.participantType()).isEqualTo("guest");
    }

    @Test
    void detailUpdateAndDeleteUseTargetMeetingShape() {
        AuthService authService = mock(AuthService.class);
        AuthUserResponse owner = user("user-owner");
        when(authService.currentUser("Bearer owner-token")).thenReturn(owner);
        WorkspaceDomainService workspace = new WorkspaceDomainService(
                new InMemoryWorkspaceStore(),
                new SpaceAccessPolicy()
        );
        workspace.ensureUser(owner.id(), owner.email(), owner.displayName(), null, owner.status());
        var space = workspace.createSpace(owner.id(), "MeetingMind", null);
        var meeting = workspace.createMeeting(
                owner.id(), space.space().id(), "CRUD 회의", SCHEDULED_AT, List.of()
        );
        MeetingController controller = new MeetingController(authService, workspace);

        var detail = controller.getMeeting("Bearer owner-token", meeting.meeting().id());
        var updated = controller.updateMeeting(
                "Bearer owner-token",
                meeting.meeting().id(),
                new UpdateMeetingRequest("수정된 회의", SCHEDULED_AT.plusHours(1), "SCHEDULED")
        );
        var deleted = controller.deleteMeeting("Bearer owner-token", meeting.meeting().id());

        assertThat(detail.id()).isEqualTo(meeting.meeting().id());
        assertThat(detail.title()).isEqualTo("CRUD 회의");
        assertThat(detail.scheduledAt()).isEqualTo(SCHEDULED_AT.toString());
        assertThat(detail.status()).isEqualTo("SCHEDULED");
        assertThat(detail.myRole()).isEqualTo("HOST");
        assertThat(detail.participants()).hasSize(1);
        assertThat(updated.title()).isEqualTo("수정된 회의");
        assertThat(updated.status()).isEqualTo("SCHEDULED");
        assertThat(deleted.deleted()).isTrue();
    }

    private AuthUserResponse user(String id) {
        return new AuthUserResponse(id, id + "@meetingmind.ai", id, null, "active");
    }
}
