package com.meetingmind.demo.auth.target;

import java.util.Optional;
import java.util.UUID;

/** Core-owned projection of an Auth Service subject onto the legacy Core user ID. */
public interface AuthUserMappingStore {

    Optional<String> findCoreUserId(UUID authUserId);

    boolean create(UUID authUserId, String coreUserId, String source, long sourceVersion);
}
