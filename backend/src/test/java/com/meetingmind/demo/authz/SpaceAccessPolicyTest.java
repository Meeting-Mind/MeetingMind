package com.meetingmind.demo.authz;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SpaceAccessPolicyTest {

    private final SpaceAccessPolicy policy = new SpaceAccessPolicy();

    @Test
    void activeSpaceRolesCanReadSpace() {
        policy.requireSpaceAccess(context(SpaceRole.OWNER));
        policy.requireSpaceAccess(context(SpaceRole.ADMIN));
        policy.requireSpaceAccess(context(SpaceRole.MEMBER));
    }

    @Test
    void missingMembershipIsDeniedByDefault() {
        assertThatThrownBy(() -> policy.requireSpaceAccess(new SpaceAccessPolicy.SpaceAccessContext(true, null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void inactiveMembershipIsDenied() {
        SpaceAccessPolicy.SpaceMembership inactive = new SpaceAccessPolicy.SpaceMembership(
                "space-1",
                "user-1",
                SpaceRole.MEMBER,
                false
        );

        assertThatThrownBy(() -> policy.requireSpaceAccess(new SpaceAccessPolicy.SpaceAccessContext(true, inactive)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void missingSpaceReturnsNotFound() {
        assertThatThrownBy(() -> policy.requireSpaceAccess(new SpaceAccessPolicy.SpaceAccessContext(false, null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND"));
    }

    @Test
    void onlyOwnerAndAdminCanManageMembers() {
        policy.requireMemberManagement(context(SpaceRole.OWNER));
        policy.requireMemberManagement(context(SpaceRole.ADMIN));

        assertThatThrownBy(() -> policy.requireMemberManagement(context(SpaceRole.MEMBER)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    @Test
    void meetingGuestDoesNotCreateSpaceAccess() {
        assertThatThrownBy(() -> policy.requireSpaceAccess(new SpaceAccessPolicy.SpaceAccessContext(true, null)))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthz(error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
    }

    private SpaceAccessPolicy.SpaceAccessContext context(SpaceRole role) {
        return new SpaceAccessPolicy.SpaceAccessContext(
                true,
                new SpaceAccessPolicy.SpaceMembership("space-1", "user-1", role, true)
        );
    }

    private void assertAuthz(Object error, HttpStatus status, String code) {
        AuthorizationException exception = (AuthorizationException) error;
        org.assertj.core.api.Assertions.assertThat(exception.status()).isEqualTo(status);
        org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo(code);
    }
}
