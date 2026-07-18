package com.meetingmind.bff.tokenvault;

import java.util.Optional;
import java.util.UUID;

public interface EncryptedTokenBundleStore {

    void create(EncryptedTokenBundle bundle);

    Optional<EncryptedTokenBundle> findById(UUID bundleId);

    boolean replace(long expectedVersion, EncryptedTokenBundle replacement);

    void deleteById(UUID bundleId);
}
