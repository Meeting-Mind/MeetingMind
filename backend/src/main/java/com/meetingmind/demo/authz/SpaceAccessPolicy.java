package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SpaceAccessPolicy {

    public void requireSpaceAccess(SpaceAccessContext context) {
        ensureSpaceExists(context);
        if (!hasActiveMembership(context)) {
            throw spaceAccessDenied();
        }
    }

    public void requireMemberManagement(SpaceAccessContext context) {
        requireSpaceAccess(context);
        SpaceRole role = context.membership().role();
        if (role != SpaceRole.OWNER && role != SpaceRole.ADMIN) {
            throw spaceAccessDenied();
        }
    }

    public void requireOwnerManagement(SpaceAccessContext context) {
        requireSpaceAccess(context);
        if (context.membership().role() != SpaceRole.OWNER) {
            throw spaceAccessDenied();
        }
    }

    boolean hasActiveMembership(SpaceAccessContext context) {
        return context != null
                && context.membership() != null
                && context.membership().active()
                && context.membership().role() != null;
    }

    boolean hasOwnerOverride(SpaceAccessContext context) {
        return hasActiveMembership(context) && context.membership().role() == SpaceRole.OWNER;
    }

    boolean hasManagerOverride(SpaceAccessContext context) {
        return hasActiveMembership(context)
                && (context.membership().role() == SpaceRole.OWNER || context.membership().role() == SpaceRole.ADMIN);
    }

    private void ensureSpaceExists(SpaceAccessContext context) {
        if (context == null || !context.spaceExists()) {
            throw new AuthorizationException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "Space를 찾을 수 없습니다.");
        }
    }

    private AuthorizationException spaceAccessDenied() {
        return new AuthorizationException(HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED", "Space 접근 권한이 없습니다.");
    }

    public record SpaceAccessContext(boolean spaceExists, SpaceMembership membership) {
    }

    public record SpaceMembership(String spaceId, String userId, SpaceRole role, boolean active) {
    }
}
