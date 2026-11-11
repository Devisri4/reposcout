package com.devisri.reposcout.store;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.devisri.reposcout.match.Evidence;
import com.devisri.reposcout.match.MatchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Flyway migration on a real (H2) database and round-trips a result through it. */
@DataJpaTest
@Import(MatchStore.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class MatchStoreTest {

    @Autowired
    private MatchStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private static MatchResult result(String id) {
        Evidence e = new Evidence("E1", "Spring Boot", "Devisri4/shop", "pom.xml", 1, 5, "spring-boot-starter",
                "1: <project>", "https://github.com/Devisri4/shop/blob/main/pom.xml#L1-L5");
        return new MatchResult(id, "Devisri4", "Java Developer", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                List.of("Devisri4/shop"), List.of(),
                List.of(new MatchResult.RequirementStatus("Spring Boot", MatchResult.Status.PROVEN, 1)),
                List.of(new MatchResult.Bullet("Built a shop backend with Spring Boot.", "Spring Boot", List.of(e))),
                List.of(new MatchResult.DroppedClaim("Ran it on Kubernetes.", "no evidence cited")),
                List.of("Kubernetes"), false);
    }

    @Test
    void savesAResultAndLoadsTheSameResultBack() {
        String id = UUID.randomUUID().toString();
        MatchResult original = result(id);

        assertThat(store.save(original)).isTrue();
        MatchResult loaded = store.find(id).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().ignoringFields("saved").isEqualTo(original);
        assertThat(loaded.saved()).isTrue();
        assertThat(jdbc.queryForObject("select github_user from job_match where id = ?", String.class, id)).isEqualTo("Devisri4");
    }

    @Test
    void findsNothingForUnknownOrMalformedIds() {
        assertThat(store.find(UUID.randomUUID().toString())).isEmpty();
        assertThat(store.find("not-a-uuid")).isEmpty();
        assertThat(store.find("1' or '1'='1")).isEmpty();
        assertThat(store.find(null)).isEmpty();
    }
}
