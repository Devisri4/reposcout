package com.devisri.reposcout.match;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.devisri.reposcout.repo.GitHubClient;
import com.devisri.reposcout.repo.GitHubClient.RepoSummary;
import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.repo.RepoLoadException;
import com.devisri.reposcout.repo.RepoLoader;
import com.devisri.reposcout.repo.RepoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceFinderTest {

    @TempDir
    Path tmp;

    private LoadedRepo shop;
    private LoadedRepo ui;

    private static final Requirement SPRING = new Requirement("Spring Boot", List.of("@SpringBootApplication", "spring-boot-starter"));
    private static final Requirement REACT = new Requirement("React", List.of("react-dom", "useState"));
    private static final Requirement K8S = new Requirement("Kubernetes", List.of("kind: Deployment", "kubectl"));

    @BeforeEach
    void createRepos() throws IOException {
        Path a = tmp.resolve("shop");
        write(a.resolve("pom.xml"), "<project>\n  <dependencies>\n    <artifactId>spring-boot-starter-web</artifactId>\n  </dependencies>\n</project>\n");
        write(a.resolve("src/main/java/shop/ShopApp.java"), "package shop;\n\n@SpringBootApplication\npublic class ShopApp {}\n");
        write(a.resolve("README.md"), "Built with spring-boot-starter.\n");
        write(a.resolve("package-lock.json"), "\"react-dom\": \"18\"\n");
        shop = new LoadedRepo("devi", "shop", "main", a);

        Path b = tmp.resolve("ui");
        write(b.resolve("package.json"), "{\n  \"dependencies\": {\n    \"react-dom\": \"18.3.1\"\n  }\n}\n");
        write(b.resolve("src/App.jsx"), "import { useState } from 'react';\nexport default function App() { const [a] = useState(0); }\n");
        ui = new LoadedRepo("devi", "ui", "master", b);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void findsTheMostConvincingSnippetsPerRequirement() {
        List<Evidence> evidence = EvidenceFinder.findInRepos(List.of(shop, ui), List.of(SPRING, REACT, K8S));

        assertThat(evidence).extracting(Evidence::id, Evidence::requirement, Evidence::repo, Evidence::path).containsExactly(
                org.assertj.core.groups.Tuple.tuple("E1", "Spring Boot", "devi/shop", "pom.xml"),
                org.assertj.core.groups.Tuple.tuple("E2", "Spring Boot", "devi/shop", "src/main/java/shop/ShopApp.java"),
                org.assertj.core.groups.Tuple.tuple("E3", "React", "devi/ui", "package.json"),
                org.assertj.core.groups.Tuple.tuple("E4", "React", "devi/ui", "src/App.jsx"));
        // Kubernetes has no evidence; the lock file in "shop" never counts as React evidence.
        assertThat(evidence).noneMatch(e -> e.requirement().equals("Kubernetes") || e.path().endsWith(".json") && e.repo().equals("devi/shop"));
    }

    @Test
    void givesEachSnippetItsLinesAndAGitHubLink() {
        Evidence pom = EvidenceFinder.findInRepos(List.of(shop), List.of(SPRING)).get(0);

        assertThat(pom.startLine()).isEqualTo(1);
        assertThat(pom.endLine()).isEqualTo(5);
        assertThat(pom.matchedTerm()).isEqualTo("spring-boot-starter");
        assertThat(pom.excerpt()).startsWith("1: <project>").contains("3:     <artifactId>spring-boot-starter-web</artifactId>");
        assertThat(pom.url()).isEqualTo("https://github.com/devi/shop/blob/main/pom.xml#L1-L5");
    }

    @Test
    void spreadsEvidenceAcrossReposBeforeTakingMoreFromOne() throws IOException {
        Path c = tmp.resolve("api");
        write(c.resolve("src/Api.java"), "@SpringBootApplication\nclass Api {}\n");
        LoadedRepo api = new LoadedRepo("devi", "api", "main", c);

        List<Evidence> evidence = EvidenceFinder.findInRepos(List.of(shop, api), List.of(SPRING));

        assertThat(evidence).hasSize(EvidenceFinder.MAX_EVIDENCE_PER_REQUIREMENT)
                .extracting(Evidence::repo).containsExactly("devi/shop", "devi/api", "devi/shop");
    }

    @ParameterizedTest
    @CsvSource({
            "pom.xml, 4", "frontend/package.json, 4", "backend/Dockerfile, 4", ".github/workflows/ci.yml, 2",
            "src/main/java/App.java, 3", "src/App.jsx, 3",
            "src/test/java/AppTest.java, 2", "src/App.test.jsx, 2",
            "README.md, 1", "docs/notes.txt, 1",
            "package-lock.json, 0", "dist/app.min.js, 0", "yarn.lock, 0", "frontend/build/x.js, 0"})
    void ranksFilesByHowConvincingTheyAre(String path, int expected) {
        assertThat(EvidenceFinder.weight(path)).isEqualTo(expected);
    }

    @Test
    void readsWhatAProjectIsFromItsReadme() throws IOException {
        Path a = tmp.resolve("store");
        write(a.resolve("README.md"), """
                # VehicleStore — second-hand vehicle marketplace

                [![CI](https://example.com/badge.svg)](https://example.com)
                ![Homepage](docs/home.png)

                A **marketplace** for used cars and bikes, built with [React](https://react.dev) and Supabase.
                """);
        Path b = tmp.resolve("game");
        write(b.resolve("readme.md"), "# Memory Matcher — a Tkinter game\n\n![shot](a.png)\n");
        Path c = tmp.resolve("bare");
        Files.createDirectories(c);

        assertThat(EvidenceFinder.readmeSummary(a)).isEqualTo("A marketplace for used cars and bikes, built with React and Supabase.");
        assertThat(EvidenceFinder.readmeSummary(b)).isEqualTo("a Tkinter game");
        assertThat(EvidenceFinder.readmeSummary(c)).isNull();
    }

    @Test
    void picksTheUsersOwnRecentReposUnderTheSizeLimit() {
        List<RepoSummary> all = List.of(
                new RepoSummary("old", false, false, 10, "2024-01-01T00:00:00Z", null),
                new RepoSummary("forked", true, false, 10, "2026-11-01T00:00:00Z", null),
                new RepoSummary("archived", false, true, 10, "2026-11-01T00:00:00Z", null),
                new RepoSummary("empty", false, false, 0, "2026-11-01T00:00:00Z", null),
                new RepoSummary("huge", false, false, 999_999, "2026-11-01T00:00:00Z", null),
                new RepoSummary("newest", false, false, 10, "2026-11-10T00:00:00Z", null),
                new RepoSummary("middle", false, false, 10, "2025-06-01T00:00:00Z", null));

        assertThat(EvidenceFinder.selectRepos(all, 51200)).extracting(RepoSummary::name)
                .containsExactly("newest", "middle", "old");
    }

    @Test
    void keepsGoingWhenOneRepoCannotBeLoaded() {
        GitHubClient github = mock(GitHubClient.class);
        RepoLoader loader = mock(RepoLoader.class);
        when(github.listPublicRepos("devi")).thenReturn(List.of(
                new RepoSummary("shop", false, false, 10, "2026-11-10T00:00:00Z", null),
                new RepoSummary("broken", false, false, 10, "2026-11-09T00:00:00Z", null)));
        when(loader.load("devi/shop")).thenReturn(shop);
        when(loader.load("devi/broken")).thenThrow(new RepoLoadException("too large"));

        EvidenceFinder.Result result = new EvidenceFinder(github, loader, props()).find("devi", List.of(SPRING));

        assertThat(result.reposScanned()).containsExactly("devi/shop");
        assertThat(result.reposSkipped()).containsExactly("devi/broken");
        assertThat(result.evidence()).isNotEmpty();
    }

    @Test
    void explainsWhenTheUserHasNoUsableRepos() {
        GitHubClient github = mock(GitHubClient.class);
        when(github.listPublicRepos("nobody")).thenReturn(List.of(new RepoSummary("fork", true, false, 10, "2026-01-01T00:00:00Z", null)));

        assertThatThrownBy(() -> new EvidenceFinder(github, mock(RepoLoader.class), props()).find("nobody", List.of(SPRING)))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("no public repositories");
    }

    private RepoProperties props() {
        return new RepoProperties(tmp, 51200, 1, 1, 1, 1, "", "http://unused", "http://unused");
    }
}
