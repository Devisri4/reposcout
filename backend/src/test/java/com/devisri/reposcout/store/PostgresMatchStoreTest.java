package com.devisri.reposcout.store;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.devisri.reposcout.match.MatchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** The same migration and round trip on a real Postgres 16 (what Neon runs). Needs Docker, so it runs in CI. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(MatchStore.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class PostgresMatchStoreTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MatchStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesAndRoundTripsOnRealPostgres() {
        String id = UUID.randomUUID().toString();
        MatchResult original = new MatchResult(id, "Devisri4", "Java Developer", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                List.of("Devisri4/shop"), List.of(), List.of(), List.of(),
                List.of(new MatchResult.DroppedClaim("x".repeat(5000), "long text fits in TEXT")), List.of(), false);

        assertThat(store.save(original)).isTrue();

        assertThat(store.find(id).orElseThrow()).usingRecursiveComparison().ignoringFields("saved").isEqualTo(original);
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns "
                + "where table_name = 'job_match' and column_name = 'result_json'", String.class)).isEqualTo("text");
    }
}
