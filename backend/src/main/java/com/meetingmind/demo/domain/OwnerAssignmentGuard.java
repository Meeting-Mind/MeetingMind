package com.meetingmind.demo.domain;

/** Blocks ownership grants while an account withdrawal reservation is pending. */
public interface OwnerAssignmentGuard {

    void requireOwnerAssignmentAllowed(String userId);

    static OwnerAssignmentGuard allowAll() {
        return userId -> { };
    }
}
