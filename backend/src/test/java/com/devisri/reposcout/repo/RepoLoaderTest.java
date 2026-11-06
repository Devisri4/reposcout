package com.devisri.reposcout.repo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real loader against a tiny local server that pretends to be GitHub. */
class RepoLoaderTest {

    @TempDir
    Path workDir;

    private HttpServer server;
    private final AtomicInteger downloads = new AtomicInteger();
    private final Map<String, String> repoJson = new LinkedHashMap<>();
    private byte[] zipBytes;

    @BeforeEach
    void startFakeGitHub() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("demo-main/README.md", "# Demo repo");
        files.put("demo-main/src/Main.java", "class Main {}");
        zipBytes = TestZips.zip(files);

        repoJson.put("acme/demo", """
                {"name":"Demo","owner":{"login":"Acme"},"default_branch":"main","size":12,"private":false,"stargazers_count":5}
                """);
        repoJson.put("acme/huge", """
                {"name":"huge","owner":{"login":"acme"},"default_branch":"main","size":999999,"private":false}
                """);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/repos/", ex -> {
            String key = ex.getRequestURI().getPath().substring("/api/repos/".length()).toLowerCase();
            String json = repoJson.get(key);
            if (json == null) {
                send(ex, 404, "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8));
            } else {
                send(ex, 200, json.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.createContext("/download/", ex -> {
            downloads.incrementAndGet();
            if (ex.getRequestURI().getPath().endsWith("/zip/refs/heads/main")) {
                send(ex, 200, zipBytes);
            } else {
                send(ex, 404, new byte[0]);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeGitHub() {
        server.stop(0);
    }

    private static void send(HttpExchange ex, int status, byte[] body) throws IOException {
        boolean json = ex.getRequestURI().getPath().startsWith("/api/");
        ex.getResponseHeaders().set("Content-Type", json ? "application/json" : "application/zip");
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private RepoLoader loader(long maxDownloadBytes) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        RepoProperties props = new RepoProperties(workDir, 51200, maxDownloadBytes, 10_000_000, 1_000_000, 1000,
                "", base + "/api", base + "/download");
        return new RepoLoader(new GitHubClient(RestClient.builder(), props), props);
    }

    @Test
    void downloadsAndUnpacksAPublicRepo() {
        LoadedRepo repo = loader(10_000_000).load("https://github.com/acme/demo");

        assertThat(repo.fullName()).isEqualTo("Acme/Demo");
        assertThat(repo.branch()).isEqualTo("main");
        assertThat(repo.root().resolve("README.md")).hasContent("# Demo repo");
        assertThat(repo.root().resolve("src/Main.java")).exists();
        assertThat(workDir.resolve("downloads/acme_demo.zip")).doesNotExist();
    }

    @Test
    void reusesTheDownloadForLaterQuestions() {
        RepoLoader loader = loader(10_000_000);

        LoadedRepo first = loader.load("acme/demo");
        LoadedRepo second = loader.load("https://github.com/ACME/demo.git");

        assertThat(second).isEqualTo(first);
        assertThat(downloads.get()).isEqualTo(1);
    }

    @Test
    void refusesReposOverTheSizeLimitWithoutDownloading() {
        assertThatThrownBy(() -> loader(10_000_000).load("acme/huge"))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("too large");
        assertThat(downloads.get()).isZero();
    }

    @Test
    void explainsMissingOrPrivateRepos() {
        assertThatThrownBy(() -> loader(10_000_000).load("acme/secret"))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void stopsDownloadsThatGrowPastTheLimit() {
        assertThatThrownBy(() -> loader(10).load("acme/demo"))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("too large");
        assertThat(workDir.resolve("repos/acme_demo")).doesNotExist();
    }
}
