package com.meetingmind.demo.auth.target;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !db")
class UnavailableAuthUserMappingStore implements AuthUserMappingStore {

    @Override
    public Optional<String> findCoreUserId(UUID authUserId) {
        return Optional.empty();
    }

    @Override
    public boolean create(UUID authUserId, String coreUserId, String source, long sourceVersion) {
        return false;
    }
}
