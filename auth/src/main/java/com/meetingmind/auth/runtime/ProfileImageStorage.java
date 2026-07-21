package com.meetingmind.auth.runtime;

import java.util.UUID;

interface ProfileImageStorage {

    boolean isAvailable();

    String store(UUID userId, ProfileImageValidator.ValidatedImage image);

    void delete(String objectKey);

    boolean isManagedKey(String value);
}
