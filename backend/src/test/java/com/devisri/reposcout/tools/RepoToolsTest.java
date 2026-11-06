package com.devisri.reposcout.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.devisri.reposcout.repo.LoadedRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RepoToolsTest {

    @TempDir
    Path tmp;

    private RepoTools tools;

    @BeforeEach
    void createRepo() throws IOException {
        Path root = tmp.resolve("repo");
        write(root.resolve("README.md"), "# Demo\nA login example.\n");
        write(root.resolve("src/auth/LoginService.java"), """
                package auth;

                public class LoginService {
                    public boolean login(String user, String password) {
                        return PasswordHasher.matches(user, password);
                    }
                }
                """);
        write(root.resolve("src/auth/PasswordHasher.java"), "class PasswordHasher { static boolean matches(String u, String p) { return true; } }\n");
        write(root.resolve("src/Big.java"), IntStream.rangeClosed(1, 450)
                .mapToObj(i -> "line " + i).collect(Collectors.joining("\n")));
        write(root.resolve("src/min.js"), "x".repeat(1000) + "\n");
        Files.write(root.resolve("logo.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 1});
        write(root.resolve("node_modules/lib/index.js"), "function login() {}\n");
        write(tmp.resolve("secret.txt"), "outside the repo");

        tools = new RepoTools(new LoadedRepo("acme", "demo", "main", root));
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // ---- listFiles

    @Test
    void listsTopLevelWithFoldersFirst() {
        String out = tools.listFiles("");

        assertThat(out).startsWith("Contents of /:");
        assertThat(out.indexOf("src/")).isLessThan(out.indexOf("README.md"));
        assertThat(out).contains("README.md  (").contains("logo.png").doesNotContain("node_modules");
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/auth", "/src/auth", "./src/auth/", "src\\auth"})
    void listsSubfoldersWhateverTheSlashes(String folder) {
        assertThat(tools.listFiles(folder))
                .contains("src/auth/LoginService.java")
                .contains("src/auth/PasswordHasher.java");
    }

    @Test
    void explainsMissingFolders() {
        assertThat(tools.listFiles("nope")).startsWith("Error: folder 'nope' does not exist");
    }

    // ---- readFile

    @Test
    void readsLinesWithNumbers() {
        String out = tools.readFile("src/auth/LoginService.java", 3, 5);

        assertThat(out).startsWith("File: src/auth/LoginService.java (lines 3-5 of 7)")
                .contains("4:     public boolean login(String user, String password) {")
                .doesNotContain("1: package auth;");
    }

    @Test
    void readsAtMost200LinesAndSaysWhereToContinue() {
        String out = tools.readFile("src/Big.java", 1, 1000);

        assertThat(out).contains("(lines 1-200 of 450)").contains("200: line 200")
                .doesNotContain("201: line 201").contains("startLine=201");
    }

    @Test
    void clampsTheEndAndDefaultsAMissingEnd() {
        assertThat(tools.readFile("src/Big.java", 440, 999)).contains("(lines 440-450 of 450)");
        assertThat(tools.readFile("src/Big.java", 10, 0)).contains("(lines 10-209 of 450)");
    }

    @Test
    void refusesStartPastTheEnd() {
        assertThat(tools.readFile("README.md", 50, 60)).isEqualTo("Error: 'README.md' has only 2 lines.");
    }

    @Test
    void refusesBinaryFiles() {
        assertThat(tools.readFile("logo.png", 1, 10)).contains("binary file");
    }

    @Test
    void cutsVeryLongLines() {
        assertThat(tools.readFile("src/min.js", 1, 1)).contains("...[line cut]").hasSizeLessThan(500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.txt", "src/../../secret.txt", "/../secret.txt"})
    void neverReadsOutsideTheRepo(String path) {
        assertThat(tools.readFile(path, 1, 10))
                .startsWith("Error:")
                .doesNotContain("outside the repo");
    }

    // ---- searchCode

    @Test
    void findsMatchesIgnoringCaseWithFileAndLine() {
        String out = tools.searchCode("LOGIN(");

        assertThat(out).contains("src/auth/LoginService.java:4: public boolean login(String user, String password) {");
    }

    @Test
    void skipsBinaryFilesAndNodeModules() {
        assertThat(tools.searchCode("function login")).startsWith("No matches");
        assertThat(tools.searchCode("PNG")).startsWith("No matches");
    }

    @Test
    void capsTheNumberOfMatches() {
        String out = tools.searchCode("line");

        assertThat(out).startsWith("450 match(es)").contains("more matches not shown");
        assertThat(out.lines().filter(l -> l.contains(".java:") || l.contains(".md:"))).hasSize(RepoTools.MAX_SEARCH_MATCHES);
    }

    @Test
    void refusesTooShortSearches() {
        assertThat(tools.searchCode(" a ")).startsWith("Error:");
    }

    // ---- helpers used by the citation checker

    @Test
    void countsLinesOnlyForRealTextFilesInsideTheRepo() {
        assertThat(tools.lineCount("src/auth/LoginService.java")).contains(7);
        assertThat(tools.lineCount("src/missing.java")).isEmpty();
        assertThat(tools.lineCount("logo.png")).isEmpty();
        assertThat(tools.lineCount("../secret.txt")).isEmpty();
    }
}
