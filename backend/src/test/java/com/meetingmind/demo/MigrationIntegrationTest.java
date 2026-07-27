package com.meetingmind.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
class MigrationIntegrationTest {

    /** `.target("10")` 시점까지의 legacy 체크포인트. 마이그레이션은 append-only이므로 고정값이다. */
    private static final int LEGACY_CHECKPOINT = 10;

    /**
     * 기대 버전 목록을 하드코딩하지 않고 실제 마이그레이션 파일에서 유도한다.
     * 하드코딩하면 마이그레이션을 추가할 때마다 정상 변경이 CI 실패로 나타난다.
     */
    private static List<String> migrationVersionsOnClasspath() throws Exception {
        var resource = MigrationIntegrationTest.class.getClassLoader().getResource("db/migration");
        assertThat(resource)
                .as("db/migration must be on the test classpath")
                .isNotNull();

        List<String> versions;
        try (Stream<Path> files = Files.list(Path.of(resource.toURI()))) {
            versions = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql") && name.contains("__"))
                    .map(name -> name.substring(1, name.indexOf("__")))
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .toList();
        }

        // 탐색이 실패해 빈 목록이 되면 아래 단정들이 공허하게 통과한다. 그 상태를 먼저 막는다.
        assertThat(versions)
                .as("migration files must be discovered, otherwise the assertions below are vacuous")
                .hasSizeGreaterThan(LEGACY_CHECKPOINT);
        assertThat(versions).doesNotHaveDuplicates();
        // 기존 하드코딩 목록이 암묵적으로 보장했던 "1부터 빈틈없이 이어짐"을 유지한다.
        // 의도적으로 번호를 건너뛰게 되면 이 단정을 명시적으로 조정한다.
        assertThat(versions)
                .as("migration versions must be contiguous starting at 1")
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, versions.size())
                                .mapToObj(String::valueOf)
                                .toList()
                );
        return versions;
    }

    @Test
    void appliesAllMigrationsToPostgres() throws Exception {
        String url = System.getenv("CI_POSTGRES_URL");
        String user = System.getenv("CI_POSTGRES_USER");
        String password = System.getenv("CI_POSTGRES_PASSWORD");

        List<String> expectedVersions = migrationVersionsOnClasspath();

        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    do $$
                    begin
                        if not exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
                            create role meetingmind_core_app nologin;
                        end if;
                    end
                    $$
                    """);
        }

        var v10Result = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target(String.valueOf(LEGACY_CHECKPOINT))
                .load()
                .migrate();

        assertThat(v10Result.migrationsExecuted).isEqualTo(LEGACY_CHECKPOINT);

        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into users (id, email, display_name)
                    values ('migration-user', 'migration@meetingmind.test', 'Migration User')
                    """);
            statement.executeUpdate("""
                    insert into users (id, email, display_name)
                    values (
                        'user-11111111-1111-4111-8111-111111111111',
                        'auth-migration@meetingmind.test',
                        'Auth Migration User'
                    )
                    """);
            statement.executeUpdate("""
                    insert into auth_identities (
                        id, user_id, provider, provider_user_id, password_hash
                    ) values (
                        'identity-22222222-2222-4222-8222-222222222222',
                        'user-11111111-1111-4111-8111-111111111111',
                        'local',
                        'auth-migration@meetingmind.test',
                        '$2a$10$migrationfixturehash'
                    )
                    """);
            statement.executeUpdate("""
                    insert into spaces (id, name, created_by)
                    values ('migration-space', 'Migration Space', 'migration-user')
                    """);
            statement.executeUpdate("""
                    insert into meetings (id, space_id, title, scheduled_at)
                    values ('migration-meeting', 'migration-space', 'Migration Meeting', now())
                    """);
            statement.executeUpdate("""
                    insert into embedding_jobs (id, space_id, meeting_id, generation)
                    values ('migration-job', 'migration-space', 'migration-meeting', 1)
                    """);
            // 운영 bootstrap에서 남을 수 있는 과도한 기존 권한도 후속 migration이 회수해야 한다.
            statement.executeUpdate("""
                    grant insert, update on table chunk_source_segments to meetingmind_core_app
                    """);
        }

        var workspaceResult = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(workspaceResult.migrationsExecuted)
                .isEqualTo(expectedVersions.size() - LEGACY_CHECKPOINT);

        try (var connection = DriverManager.getConnection(url, user, password)) {
            List<String> versions = new ArrayList<>();
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "select version from flyway_schema_history where success order by installed_rank"
                 )) {
                while (rows.next()) {
                    versions.add(rows.getString("version"));
                }
            }

            assertThat(versions).containsExactlyElementsOf(expectedVersions);

            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("""
                        select
                            has_table_privilege('meetingmind_core_app', 'ai_usage_events', 'SELECT'),
                            has_table_privilege('meetingmind_core_app', 'ai_usage_events', 'INSERT'),
                            has_table_privilege('meetingmind_core_app', 'chunk_source_segments', 'SELECT'),
                            has_table_privilege('meetingmind_core_app', 'chunk_source_segments', 'DELETE'),
                            has_table_privilege('meetingmind_core_app', 'chunk_source_segments', 'INSERT'),
                            has_table_privilege('meetingmind_core_app', 'chunk_source_segments', 'UPDATE'),
                            has_table_privilege('meetingmind_core_app', 'meeting_ai_messages', 'SELECT'),
                            has_table_privilege('meetingmind_core_app', 'meeting_ai_messages', 'INSERT'),
                            has_table_privilege('meetingmind_core_app', 'meeting_ai_messages', 'UPDATE'),
                            has_table_privilege('meetingmind_core_app', 'meeting_ai_messages', 'DELETE')
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                    assertThat(rows.getBoolean(2)).isTrue();
                    assertThat(rows.getBoolean(3)).isTrue();
                    assertThat(rows.getBoolean(4)).isTrue();
                    assertThat(rows.getBoolean(5)).isFalse();
                    assertThat(rows.getBoolean(6)).isFalse();
                    assertThat(rows.getBoolean(7)).isTrue();
                    assertThat(rows.getBoolean(8)).isTrue();
                    assertThat(rows.getBoolean(9)).isFalse();
                    assertThat(rows.getBoolean(10)).isFalse();
                }
                try (var rows = statement.executeQuery("""
                        select auth_user_id
                        from users
                        where id = 'user-11111111-1111-4111-8111-111111111111'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("auth_user_id").toString())
                            .isEqualTo("11111111-1111-4111-8111-111111111111");
                }
                try (var rows = statement.executeQuery("""
                        select auth_user_id
                        from users
                        where id = 'migration-user'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("auth_user_id")).isNull();
                }
                try (var rows = statement.executeQuery("""
                        select pg_get_constraintdef(oid)
                        from pg_constraint
                        where conname = 'task_cards_status_check'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).contains("IN_REVIEW");
                }
                try (var rows = statement.executeQuery("""
                        select column_name
                        from information_schema.columns
                        where table_name = 'meetings'
                          and column_name in ('deleted_at', 'deleted_by', 'description', 'scheduled_end_at')
                        order by column_name
                        """)) {
                    List<String> columns = new ArrayList<>();
                    while (rows.next()) {
                        columns.add(rows.getString("column_name"));
                    }
                    assertThat(columns).containsExactly("deleted_at", "deleted_by", "description", "scheduled_end_at");
                }
                try (var rows = statement.executeQuery("""
                        select scheduled_end_at = scheduled_at + interval '1 hour'
                        from meetings
                        where id = 'migration-meeting'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                }
                try (var rows = statement.executeQuery("""
                        select column_name
                        from information_schema.columns
                        where table_name = 'task_cards'
                          and column_name in ('deleted_at', 'priority', 'labels')
                        order by column_name
                        """)) {
                    List<String> columns = new ArrayList<>();
                    while (rows.next()) {
                        columns.add(rows.getString("column_name"));
                    }
                    assertThat(columns).containsExactly("deleted_at", "labels", "priority");
                }
                try (var rows = statement.executeQuery("""
                        select updated_at is not null
                        from spaces where id = 'migration-space'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                }
                List<String> extensions = new ArrayList<>();
                try (var rows = statement.executeQuery(
                        "select extname from pg_extension where extname in ('vector', 'pg_trgm') order by extname"
                )) {
                    while (rows.next()) {
                        extensions.add(rows.getString("extname"));
                    }
                }
                assertThat(extensions).containsExactly("pg_trgm", "vector");

                try (var rows = statement.executeQuery("""
                        select format_type(a.atttypid, a.atttypmod)
                        from pg_attribute a
                        join pg_class c on c.oid = a.attrelid
                        where c.relname = 'embedding_chunks' and a.attname = 'embedding'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("vector(1536)");
                }

                statement.executeUpdate("""
                        insert into meeting_speakers (id, meeting_id, label)
                        values ('migration-speaker', 'migration-meeting', 'Speaker 1')
                        """);
                statement.executeUpdate("""
                        insert into transcript_segments (
                            id, meeting_id, speaker_id, speaker_label, sequence,
                            start_ms, end_ms, text
                        ) values (
                            'migration-segment', 'migration-meeting', 'migration-speaker',
                            'Speaker 1', 0, 0, 1000, 'Vector trigger test'
                        )
                        """);
                assertThat(countMeetingJobs(statement)).isEqualTo(1);

                statement.executeUpdate("""
                        insert into meeting_transcripts (
                            meeting_id, status, started_at, completed_at
                        ) values (
                            'migration-meeting', 'COMPLETED', now(), now()
                        )
                        """);
                statement.executeUpdate("""
                        update meeting_transcripts set updated_at = now()
                        where meeting_id = 'migration-meeting'
                        """);
                assertThat(countMeetingJobs(statement)).isEqualTo(2);

                statement.executeUpdate("""
                        update meeting_speakers set display_name = 'Migration Speaker'
                        where id = 'migration-speaker'
                        """);
                statement.executeUpdate("""
                        update meetings set title = 'Renamed Migration Meeting'
                        where id = 'migration-meeting'
                        """);
                assertThat(countMeetingJobs(statement)).isEqualTo(4);

                statement.executeUpdate("""
                        insert into meeting_reports (
                            id, meeting_id, status, title, summary, version
                        ) values (
                            'migration-report', 'migration-meeting', 'CANDIDATE',
                            'Migration Report', 'Candidate summary', 1
                        )
                        """);
                statement.executeUpdate("""
                        update meeting_reports set status = 'DRAFT'
                        where id = 'migration-report'
                        """);
                assertThat(countMeetingJobs(statement)).isEqualTo(4);
                statement.executeUpdate("""
                        update meeting_reports
                        set status = 'CONFIRMED', is_current = true, confirmed_at = now()
                        where id = 'migration-report'
                        """);
                assertThat(countMeetingJobs(statement)).isEqualTo(5);

                statement.executeUpdate("""
                        insert into project_knowledge (
                            id, space_id, type, title, content, status
                        ) values (
                            'migration-knowledge', 'migration-space', 'manual',
                            'Migration Knowledge', 'Initial content', 'PUBLISHED'
                        )
                        """);
                statement.executeUpdate("""
                        update project_knowledge set content = 'Updated content'
                        where id = 'migration-knowledge'
                        """);
                assertThat(countKnowledgeJobs(statement)).isEqualTo(2);

                statement.executeUpdate("""
                        insert into embedding_chunks (
                            id, space_id, project_id, project_knowledge_id, scope,
                            source_type, source_id, title, content, embedding_text,
                            embedding_job_id, generation
                        ) values (
                            'migration-knowledge-chunk', 'migration-space', 'migration-space',
                            'migration-knowledge', 'project', 'projectKnowledge',
                            'migration-knowledge', 'Migration Knowledge', 'Updated content',
                            'Updated content',
                            (select id from embedding_jobs where project_knowledge_id = 'migration-knowledge'
                             order by generation desc limit 1),
                            2
                        )
                        """);
                statement.executeUpdate("""
                        update project_knowledge set status = 'ARCHIVED'
                        where id = 'migration-knowledge'
                        """);
                try (var rows = statement.executeQuery("""
                        select is_active from embedding_chunks
                        where id = 'migration-knowledge-chunk'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean("is_active")).isFalse();
                }
                assertThat(countKnowledgeJobs(statement)).isEqualTo(2);
                statement.executeUpdate("""
                        update project_knowledge set status = 'PUBLISHED'
                        where id = 'migration-knowledge'
                        """);
                assertThat(countKnowledgeJobs(statement)).isEqualTo(3);

                try (var rows = statement.executeQuery("""
                        select generation, trigger_reason
                        from embedding_jobs
                        where meeting_id = 'migration-meeting'
                        order by generation
                        """)) {
                    List<String> reasons = new ArrayList<>();
                    while (rows.next()) {
                        reasons.add(rows.getInt("generation") + ":" + rows.getString("trigger_reason"));
                    }
                    assertThat(reasons).containsExactly(
                            "1:FULL_REINDEX",
                            "2:TRANSCRIPT_COMPLETED",
                            "3:SPEAKER_UPDATED",
                            "4:FULL_REINDEX",
                            "5:REPORT_CONFIRMED"
                    );
                }

                statement.executeUpdate("""
                        insert into embedding_chunks (
                            id, space_id, project_id, meeting_id, scope, source_type, source_id,
                            title, content, embedding_text, embedding, embedding_job_id, generation
                        ) values (
                            'migration-chunk', 'migration-space', 'migration-space', 'migration-meeting',
                            'meeting', 'transcript', 'migration-source', 'Migration', 'PostgreSQL vector search',
                            'PostgreSQL vector search', array_fill(0.01, array[1536])::vector,
                            'migration-job', 1
                        )
                        """);
                statement.executeUpdate("""
                        update embedding_jobs
                        set status = 'PROCESSING', model = 'text-embedding-3-small', dimension = 1536,
                            started_at = now(), lease_expires_at = now() + interval '5 minutes'
                        where id = 'migration-job'
                        """);
                statement.executeUpdate("""
                        update embedding_jobs
                        set status = 'COMPLETED', completed_at = now(), lease_expires_at = null
                        where id = 'migration-job'
                        """);

                try (var rows = statement.executeQuery("""
                        select id
                        from embedding_chunks
                        order by embedding <=> array_fill(0.01, array[1536])::vector
                        limit 1
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("id")).isEqualTo("migration-chunk");
                }

                try (var rows = statement.executeQuery("""
                        select id
                        from embedding_chunks
                        where embedding_text % 'PostgreSQL vector search'
                        order by similarity(embedding_text, 'PostgreSQL vector search') desc
                        limit 1
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("id")).isEqualTo("migration-chunk");
                }

                statement.executeUpdate("""
                        insert into chunk_source_segments (id, chunk_id, segment_id)
                        values ('migration-link', 'migration-chunk', 'migration-segment')
                        """);
                statement.executeUpdate("""
                        update meeting_transcripts set purged_at = now()
                        where meeting_id = 'migration-meeting'
                        """);
                try (var rows = statement.executeQuery("""
                        select is_active from embedding_chunks where id = 'migration-chunk'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean("is_active")).isFalse();
                }
                try (var rows = statement.executeQuery("""
                        select count(*) from chunk_source_segments where id = 'migration-link'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
                assertThat(countMeetingJobs(statement)).isEqualTo(5);
            }
        }
    }

    private static int countMeetingJobs(java.sql.Statement statement) throws Exception {
        try (var rows = statement.executeQuery("""
                select count(*) from embedding_jobs where meeting_id = 'migration-meeting'
                """)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private static int countKnowledgeJobs(java.sql.Statement statement) throws Exception {
        try (var rows = statement.executeQuery("""
                select count(*) from embedding_jobs where project_knowledge_id = 'migration-knowledge'
                """)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }
}
