package com.devisri.reposcout.repo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The two GitHub calls RepoScout needs: repo info (1 API call) and the zip download (not rate limited
 * like the API). Works without a token for public repos.
 */
@Component
public class GitHubClient {

    /** The parts of GitHub's repo info we use. {@code size} is in KB. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepoInfo(
            @JsonProperty("name") String name,
            @JsonProperty("owner") Owner owner,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("size") long sizeKb,
            @JsonProperty("private") boolean isPrivate) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Owner(@JsonProperty("login") String login) {
        }
    }

    private final RestClient rest;
    private final RepoProperties props;

    public GitHubClient(RestClient.Builder builder, RepoProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(60));

        RestClient.Builder b = builder.requestFactory(factory)
                .defaultHeader("User-Agent", "RepoScout")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (props.githubToken() != null && !props.githubToken().isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + props.githubToken());
        }
        this.rest = b.build();
        this.props = props;
    }

    public RepoInfo fetchInfo(RepoRef ref) {
        URI uri = UriComponentsBuilder.fromUriString(props.githubApiUrl())
                .pathSegment("repos", ref.owner(), ref.name())
                .build().encode().toUri();
        try {
            RepoInfo info = rest.get().uri(uri)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new RepoLoadException("Repository " + ref + " was not found (it may be private).");
                    })
                    .onStatus(s -> s.value() == 403 || s.value() == 429, (req, res) -> {
                        throw new RepoLoadException("GitHub's rate limit was reached. Please try again in a few minutes.");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RepoLoadException("GitHub returned an error (" + res.getStatusCode().value() + ").");
                    })
                    .body(RepoInfo.class);
            if (info == null || info.defaultBranch() == null) {
                throw new RepoLoadException("GitHub returned no information for " + ref + ".");
            }
            return info;
        } catch (RestClientException e) {
            throw new RepoLoadException("Could not reach GitHub. Please try again.", e);
        }
    }

    /** Streams the branch zip to {@code target}, stopping if it grows past {@code maxBytes}. */
    public void downloadZip(RepoRef ref, String branch, Path target, long maxBytes) {
        URI uri = UriComponentsBuilder.fromUriString(props.githubDownloadUrl())
                .pathSegment(ref.owner(), ref.name(), "zip", "refs", "heads")
                .path("/" + branch)
                .build().encode().toUri();
        try {
            rest.get().uri(uri).exchange((req, res) -> {
                if (res.getStatusCode().isError()) {
                    throw new RepoLoadException("Could not download " + ref + " (HTTP " + res.getStatusCode().value() + ").");
                }
                copyWithLimit(res.getBody(), target, maxBytes);
                return null;
            });
        } catch (RestClientException e) {
            throw new RepoLoadException("Could not download " + ref + ". Please try again.", e);
        }
    }

    private static void copyWithLimit(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        try (in; OutputStream out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new RepoLoadException("The repository download is too large.");
                }
                out.write(buffer, 0, n);
            }
        }
    }
}
