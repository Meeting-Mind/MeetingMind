package com.meetingmind.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
class MigrationIntegrationTest {

    @Test
    void appliesAllMigrationsToPostgres() throws Exception {
        String url = System.getenv("CI_POSTGRES_URL");
        String user = System.getenv("CI_POSTGRES_USER");
        String password = System.getenv("CI_POSTGRES_PASSWORD");

        var v10Result = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("10")
                .load()
                .migrate();

        assertThat(v10Result.migrationsExecuted).isEqualTo(10);

        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into users (id, email, display_name)
                    values ('migration-user', 'migration@meetingmind.test', 'Migration User')
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
        }

        var v11Result = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(v11Result.migrationsExecuted).isEqualTo(1);

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

            assertThat(versions).containsExactly(
                    "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"
            );

            try (var statement = connection.createStatement()) {
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
