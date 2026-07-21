package com.meetingmind.demo.auth.target;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
class JdbcAuthUserMappingStore implements AuthUserMappingStore {

    private final JdbcTemplate jdbc;

    JdbcAuthUserMappingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findCoreUserId(UUID authUserId) {
        List<String> values = jdbc.query(
                "select core_user_id from auth_user_mappings where auth_user_id = ?",
                (rows, rowNumber) -> rows.getString(1),
                authUserId
        );
        return values.stream().findFirst();
    }

    @Override
    public boolean create(UUID authUserId, String coreUserId, String source, long sourceVersion) {
        return jdbc.update("""
                insert into auth_user_mappings (auth_user_id, core_user_id, source, source_version)
                values (?, ?, ?, ?)
                on conflict (auth_user_id) do nothing
                """, authUserId, coreUserId, source, sourceVersion) == 1;
    }
}
