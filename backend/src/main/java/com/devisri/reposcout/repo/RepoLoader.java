package com.devisri.reposcout.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a pasted GitHub link into a folder of files on the server.
 * Each repo is downloaded once and reused for every later question.
 */
@Service
public class RepoLoader {

    private static final Logger log = LoggerFactory.getLogger(RepoLoader.class);

    private final GitHubClient github;
    private final RepoProperties props;
    private final ZipExtractor extractor;

    private final Map<String, LoadedRepo> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public RepoLoader(GitHubClient github, RepoProperties props) {
        this.github = github;
        this.props = props;
        this.extractor = new ZipExtractor(props.maxUnzippedBytes(), props.maxFileBytes(), props.maxFiles());
    }

    public LoadedRepo load(String repoUrl) {
        RepoRef ref = RepoRef.parse(repoUrl);
        String key = ref.cacheKey();

        LoadedRepo cached = cache.get(key);
        if (cached != null && Files.isDirectory(cached.root())) {
            return cached;
        }
        // One download per repo at a time; two users asking about the same repo share it.
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            cached = cache.get(key);
            if (cached != null && Files.isDirectory(cached.root())) {
                return cached;
            }
            LoadedRepo loaded = download(ref);
            cache.put(key, loaded);
            return loaded;
        }
    }

    private LoadedRepo download(RepoRef ref) {
        GitHubClient.RepoInfo info = github.fetchInfo(ref);
        if (info.isPrivate()) {
            throw new RepoLoadException("Only public repositories are supported.");
        }
        if (info.sizeKb() > props.maxRepoSizeKb()) {
            throw new RepoLoadException("Repository " + ref + " is too large (" + info.sizeKb() / 1024
                    + " MB). The limit is " + props.maxRepoSizeKb() / 1024 + " MB.");
        }

        String owner = info.owner() != null ? info.owner().login() : ref.owner();
        String name = info.name() != null ? info.name() : ref.name();
        String folder = ref.cacheKey().replace('/', '_').replaceAll("[^a-z0-9._-]", "_");
        Path root = props.workDir().resolve("repos").resolve(folder);
        Path zip = props.workDir().resolve("downloads").resolve(folder + ".zip");

        try {
            Files.createDirectories(zip.getParent());
            deleteRecursively(root);
            log.info("Downloading {} (branch {}, {} KB)", ref, info.defaultBranch(), info.sizeKb());
            github.downloadZip(ref, info.defaultBranch(), zip, props.maxDownloadBytes());
            ZipExtractor.Result result = extractor.extract(zip, root);
            log.info("Unpacked {}: {} files, {} skipped as too large", ref, result.filesWritten(), result.filesSkipped());
            return new LoadedRepo(owner, name, info.defaultBranch(), root);
        } catch (IOException e) {
            deleteQuietly(root);
            throw new RepoLoadException("Could not unpack " + ref + ".", e);
        } catch (RuntimeException e) {
            deleteQuietly(root);
            throw e;
        } finally {
            deleteQuietly(zip);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
