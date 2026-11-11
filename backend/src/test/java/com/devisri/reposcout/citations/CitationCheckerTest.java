package com.devisri.reposcout.citations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.tools.RepoTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CitationCheckerTest {

    @TempDir
    Path tmp;

    private LoadedRepo repo;
    private RepoTools tools;
    private ReadRanges read;

    @BeforeEach
    void createRepo() throws IOException {
        Path file = tmp.resolve("src/auth/Login.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, IntStream.rangeClosed(1, 50).mapToObj(i -> "line " + i).collect(Collectors.joining("\n")));
        Files.writeString(tmp.resolve("README.md"), "# Demo\n");
        repo = new LoadedRepo("acme", "demo", "main", tmp);
        tools = new RepoTools(repo);
        read = new ReadRanges();
        read.add("src/auth/Login.java", 1, 30);
    }

    private CitationChecker.CheckedAnswer check(String answer) {
        return CitationChecker.check(answer, repo, tools, read);
    }

    @Test
    void turnsVerifiedCitationsIntoGitHubLinks() {
        var result = check("Login happens in [src/auth/Login.java:10-20].");

        assertThat(result.answer()).isEqualTo(
                "Login happens in [Login.java:10-20](https://github.com/acme/demo/blob/main/src/auth/Login.java#L10-L20).");
        assertThat(result.citations()).singleElement().satisfies(c -> {
            assertThat(c.verified()).isTrue();
            assertThat(c.url()).endsWith("#L10-L20");
        });
    }

    @Test
    void acceptsSingleLinesAndLeadingSlashes() {
        var result = check("See [/src/auth/Login.java:5] and [./src/auth/Login.java:6 - 7].");

        assertThat(result.citations()).allMatch(CitationChecker.Citation::verified).hasSize(2);
        assertThat(result.answer()).contains("Login.java#L5)").contains("#L6-L7)");
    }

    @Test
    void checksCitationsWrittenWithOtherBracketsToo() {
        // gpt-oss on Groq writes 【path:lines】; these must be verified like [path:lines], never skipped.
        var result = check("Saved here【src/auth/Login.java:10–12】 and here【src/auth/Fake.java:1】.");

        assertThat(result.answer())
                .startsWith("Saved here [Login.java:10-12](https://github.com/acme/demo/blob/main/src/auth/Login.java#L10-L12) and here.")
                .doesNotContain("【");
        assertThat(result.citations()).extracting(CitationChecker.Citation::verified).containsExactly(true, false);
    }

    @Test
    void acceptsEveryKindOfDashTheModelWrites() {
        // Real answer from gpt-oss used a non-breaking hyphen (U+2011): 【src/flask/app.py:547‑552】
        for (String dash : new String[]{"-", "‐", "‑", "‒", "–", "—", "−"}) {
            var result = check("See【src/auth/Login.java:10" + dash + "12】.");
            assertThat(result.citations()).as("dash %s", dash).singleElement()
                    .satisfies(c -> assertThat(c.endLine()).isEqualTo(12));
        }
    }

    @Test
    void neverShowsABracketedReferenceItCouldNotCheck() {
        var result = check("It is in【src/auth/Login.java, around line 10】 and【weird】.");

        assertThat(result.answer()).doesNotContain("【").doesNotContain("】")
                .startsWith("It is in and.")
                .contains("2 file reference(s) could not be verified");
    }

    @Test
    void removesFilesThatDoNotExist() {
        var result = check("It uses a hasher [src/auth/Hasher.java:1-5].");

        assertThat(result.answer()).startsWith("It uses a hasher.").contains("1 file reference(s) could not be verified");
        assertThat(result.citations()).singleElement().satisfies(c -> {
            assertThat(c.verified()).isFalse();
            assertThat(c.reason()).isEqualTo("file does not exist in the repository");
        });
    }

    @Test
    void removesLineRangesPastTheEndOfTheFile() {
        assertThat(check("[src/auth/Login.java:40-99]").citations().get(0).reason()).isEqualTo("file has only 50 lines");
    }

    @Test
    void removesLinesTheAgentNeverRead() {
        assertThat(check("[src/auth/Login.java:25-35]").citations().get(0).reason())
                .isEqualTo("the agent did not read those lines");
        assertThat(check("[README.md:1]").citations().get(0).reason())
                .isEqualTo("the agent never opened this file");
    }

    @Test
    void removesInvalidRangesAndPathsOutsideTheRepo() {
        assertThat(check("[src/auth/Login.java:20-10]").citations().get(0).reason()).isEqualTo("invalid line range");
        assertThat(check("[src/auth/Login.java:0]").citations().get(0).verified()).isFalse();
        assertThat(check("[../secret.txt:1]").citations().get(0).verified()).isFalse();
        assertThat(check("[src/auth/Login.java:99999999999]").citations().get(0).verified()).isFalse();
    }

    @Test
    void listsRepeatedCitationsOnceAndLeavesNormalTextAlone() {
        String code = "```java\nint x = a ? b : c;\n```";
        var result = check("Twice [src/auth/Login.java:1-2] and [src/auth/Login.java:1-2]. " + code + " [not a citation]");

        assertThat(result.citations()).hasSize(1);
        assertThat(result.answer()).contains(code).contains("[not a citation]");
    }
}
