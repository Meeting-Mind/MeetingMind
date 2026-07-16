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

        var result = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(result.migrationsExecuted).isEqualTo(11);

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

            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("select extversion from pg_extension where extname = 'vector'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("extversion")).isNotBlank();
            }
        }
    }
}
