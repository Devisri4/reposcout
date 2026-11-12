package com.devisri.reposcout.match;

import java.util.ArrayList;
import java.util.List;

import com.devisri.reposcout.match.BulletWriter.Draft;
import com.devisri.reposcout.match.BulletWriter.DraftBullet;
import com.devisri.reposcout.match.MatchResult.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchVerifierTest {

    static final Requirement SPRING = new Requirement("Spring Boot", List.of("spring-boot-starter"));
    static final Requirement REACT = new Requirement("React", List.of("react-dom"));
    static final Requirement K8S = new Requirement("Kubernetes", List.of("kubectl"));
    static final List<Requirement> REQS = List.of(SPRING, REACT, K8S);

    static Evidence ev(String id, String requirement) {
        return new Evidence(id, requirement, "devi/shop", "pom.xml", 1, 5, "spring-boot-starter", "1: <project>",
                "https://github.com/devi/shop/blob/main/pom.xml#L1-L5");
    }

    static DraftBullet bullet(String text, String requirement, List<String> ids) {
        return new DraftBullet(text, null, requirement, ids);
    }

    static final List<Evidence> EVIDENCE = List.of(ev("E1", "Spring Boot"), ev("E2", "Spring Boot"), ev("E3", "React"));

    @Test
    void keepsBulletsBackedByRealEvidence() {
        Draft draft = new Draft(List.of(bullet("Built a REST API with Spring Boot.", "Spring Boot", List.of("E1", "e2"))));

        var result = MatchVerifier.verify(draft, REQS, EVIDENCE);

        assertThat(result.bullets()).singleElement().satisfies(b -> {
            assertThat(b.requirement()).isEqualTo("Spring Boot");
            assertThat(b.evidence()).extracting(Evidence::id).containsExactly("E1", "E2");
        });
        assertThat(result.dropped()).isEmpty();
    }

    @Test
    void removesMadeUpIdsAndDropsBulletsLeftWithNone() {
        Draft draft = new Draft(List.of(
                bullet("Used Spring Boot.", "Spring Boot", List.of("E1", "E99")),
                bullet("Deployed services to Kubernetes.", "Kubernetes", List.of("E42")),
                bullet("Led a team of 10.", "Spring Boot", List.of())));

        var result = MatchVerifier.verify(draft, REQS, EVIDENCE);

        assertThat(result.bullets()).singleElement().satisfies(b ->
                assertThat(b.evidence()).extracting(Evidence::id).containsExactly("E1"));
        assertThat(result.dropped()).extracting(MatchResult.DroppedClaim::reason).containsExactly(
                "cited evidence that does not exist (E42)", "no evidence cited");
    }

    @Test
    void labelsEachRequirementProvenEvidenceOnlyOrGap() {
        Draft draft = new Draft(List.of(bullet("Built APIs.", "Spring Boot", List.of("E1"))));

        var result = MatchVerifier.verify(draft, REQS, EVIDENCE);

        assertThat(result.requirements()).extracting(MatchResult.RequirementStatus::name, MatchResult.RequirementStatus::status,
                MatchResult.RequirementStatus::evidenceCount).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Spring Boot", Status.PROVEN, 2),
                org.assertj.core.groups.Tuple.tuple("React", Status.EVIDENCE_ONLY, 1),
                org.assertj.core.groups.Tuple.tuple("Kubernetes", Status.GAP, 0));
        assertThat(result.gaps()).containsExactly("Kubernetes");
    }

    @Test
    void usesTheEvidencesRequirementWhenTheModelNamesAnUnknownOne() {
        Draft draft = new Draft(List.of(bullet("Built a UI.", "Frontend magic", List.of("E3"))));

        var result = MatchVerifier.verify(draft, REQS, EVIDENCE);

        assertThat(result.bullets().get(0).requirement()).isEqualTo("React");
        assertThat(result.requirements().get(1).status()).isEqualTo(Status.PROVEN);
    }

    @Test
    void dropsDuplicatesOverlongBulletsAndAnythingPastEight() {
        List<DraftBullet> bullets = new ArrayList<>();
        bullets.add(bullet("Same bullet.", "Spring Boot", List.of("E1")));
        bullets.add(bullet("same bullet.", "Spring Boot", List.of("E1")));
        bullets.add(bullet("x".repeat(400), "Spring Boot", List.of("E1")));
        for (int i = 1; i <= 9; i++) {
            bullets.add(bullet("Bullet " + i, "React", List.of("E3")));
        }
        bullets.add(null);
        bullets.add(bullet(" ", "React", List.of("E3")));

        var result = MatchVerifier.verify(new Draft(bullets), REQS, EVIDENCE);

        assertThat(result.bullets()).hasSize(MatchVerifier.MAX_BULLETS);
        assertThat(result.dropped()).extracting(MatchResult.DroppedClaim::reason)
                .containsExactly("too long to be a resume bullet", "more than 8 bullets", "more than 8 bullets");
    }

    static Evidence evIn(String id, String requirement, String repo) {
        return new Evidence(id, requirement, repo, "pom.xml", 1, 5, "spring-boot-starter", "1: <project>",
                "https://github.com/" + repo + "/blob/main/pom.xml#L1-L5");
    }

    @Test
    void keepsOnlyEvidenceFromTheProjectTheBulletDescribes() {
        List<Evidence> evidence = List.of(evIn("E1", "Spring Boot", "devi/reposcout"),
                evIn("E2", "Spring Boot", "devi/finance"), evIn("E3", "React", "devi/finance"));
        Draft draft = new Draft(List.of(
                new DraftBullet("Built the AI repo explorer backend with Spring Boot.", "reposcout", "Spring Boot", List.of("E2")),
                new DraftBullet("Built a finance app with Spring Boot and React.", "DEVI/Finance", "Spring Boot", List.of("E1", "E2", "E3")),
                new DraftBullet("Built an API.", null, "Spring Boot", List.of("E1", "E2"))));

        var result = MatchVerifier.verify(draft, REQS, evidence);

        assertThat(result.dropped()).singleElement().satisfies(d -> {
            assertThat(d.text()).startsWith("Built the AI repo explorer");
            assertThat(d.reason()).isEqualTo("describes reposcout but cites code from finance");
        });
        assertThat(result.bullets()).hasSize(2);
        assertThat(result.bullets().get(0).evidence()).extracting(Evidence::id).containsExactly("E2", "E3");
        // No project named: the first evidence decides the project.
        assertThat(result.bullets().get(1).evidence()).extracting(Evidence::id).containsExactly("E1");
    }
}
