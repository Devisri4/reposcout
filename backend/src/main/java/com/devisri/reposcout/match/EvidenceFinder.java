package com.devisri.reposcout.match;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.devisri.reposcout.repo.GitHubClient;
import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.repo.RepoLoadException;
import com.devisri.reposcout.repo.RepoLoader;
import com.devisri.reposcout.repo.RepoProperties;
import com.devisri.reposcout.tools.RepoTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Step 2 of a job match, in plain Java: loads the user's public repos and finds real code snippets for each
 * requirement. Each repo's files are read once and searched for every term at the same time.
 */
@Service
public class EvidenceFinder {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFinder.class);

    static final int MAX_REPOS = 6;
    static final int MAX_EVIDENCE_PER_REQUIREMENT = 3;
    static final int MAX_PER_REPO_PER_REQUIREMENT = 2;
    static final int MAX_LINES_PER_REPO = 200_000;
    private static final int CONTEXT_BEFORE = 2;
    private static final int CONTEXT_AFTER = 3;
    private static final int EXCERPT_LINE_LENGTH = 160;

    private static final Set<String> IGNORED_FILES = Set.of("package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "gradle-wrapper.properties", "mvnw", "mvnw.cmd", ".gitignore", "LICENSE", "LICENSE.md");
    private static final Set<String> BUILD_FILES = Set.of("pom.xml", "package.json", "build.gradle",
            "build.gradle.kts", "requirements.txt", "pyproject.toml", "go.mod", "Cargo.toml", "Dockerfile",
            "docker-compose.yml", "docker-compose.yaml", "compose.yaml", "vercel.json", "render.yaml",
            "application.properties", "application.yml", "tsconfig.json", "vite.config.js", "vite.config.ts");


    /** What was searched and what was found; {@code descriptions} maps "owner/name" (lower case) to the repo description. */
    public record Result(List<String> reposScanned, List<String> reposSkipped, List<Evidence> evidence,
                         Map<String, String> descriptions) {
        public Result(List<String> reposScanned, List<String> reposSkipped, List<Evidence> evidence) {
            this(reposScanned, reposSkipped, evidence, Map.of());
        }
    }

    private final GitHubClient github;
    private final RepoLoader loader;
    private final RepoProperties repoProps;

    public EvidenceFinder(GitHubClient github, RepoLoader loader, RepoProperties repoProps) {
        this.github = github;
        this.loader = loader;
        this.repoProps = repoProps;
    }

    public Result find(String username, List<Requirement> requirements) {
        List<GitHubClient.RepoSummary> chosen = selectRepos(github.listPublicRepos(username), repoProps.maxRepoSizeKb());
        if (chosen.isEmpty()) {
            throw new RepoLoadException("GitHub user " + username + " has no public repositories to search.");
        }
        List<LoadedRepo> loaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (GitHubClient.RepoSummary r : chosen) {
            try {
                loaded.add(loader.load(username + "/" + r.name()));
            } catch (RepoLoadException e) {
                log.warn("Skipping {}/{}: {}", username, r.name(), e.getMessage());
                skipped.add(username + "/" + r.name());
            }
        }
        List<Evidence> evidence = findInRepos(loaded, requirements);
        Map<String, String> descriptions = new HashMap<>();
        for (GitHubClient.RepoSummary r : chosen) {
            if (r.description() != null && !r.description().isBlank()) {
                String d = r.description().strip();
                descriptions.put((username + "/" + r.name()).toLowerCase(Locale.ROOT), d.length() > 200 ? d.substring(0, 200) : d);
            }
        }
        // Many repos have no GitHub description; the README's first sentence usually says what the project is.
        for (LoadedRepo repo : loaded) {
            descriptions.computeIfAbsent(repo.fullName().toLowerCase(Locale.ROOT), k -> readmeSummary(repo.root()));
        }
        descriptions.values().removeIf(java.util.Objects::isNull);
        return new Result(loaded.stream().map(LoadedRepo::fullName).toList(), skipped, evidence, descriptions);
    }

    /** Own, non-archived, non-empty repos under the size limit, most recently pushed first, at most 6. */
    static List<GitHubClient.RepoSummary> selectRepos(List<GitHubClient.RepoSummary> all, long maxSizeKb) {
        return all.stream()
                .filter(r -> !r.fork() && !r.archived() && r.sizeKb() > 0 && r.sizeKb() <= maxSizeKb)
                .sorted(Comparator.comparing((GitHubClient.RepoSummary r) -> r.pushedAt() == null ? "" : r.pushedAt())
                        .reversed())
                .limit(MAX_REPOS)
                .toList();
    }

    /** A place where a search term was found, with how convincing that file is as evidence. */
    private record Hit(Requirement requirement, LoadedRepo repo, String path, int line, String term,
                       List<String> lines, int weight) {
    }

    static List<Evidence> findInRepos(List<LoadedRepo> repos, List<Requirement> requirements) {
        Map<Requirement, List<Hit>> hits = new LinkedHashMap<>();
        requirements.forEach(r -> hits.put(r, new ArrayList<>()));

        for (LoadedRepo repo : repos) {
            RepoTools tools = new RepoTools(repo);
            int linesRead = 0;
            for (Path file : tools.allTextFiles()) {
                String rel = tools.relative(file);
                int weight = weight(rel);
                if (weight == 0) {
                    continue;
                }
                List<String> lines;
                try {
                    lines = RepoTools.readLines(file);
                } catch (IOException e) {
                    continue;
                }
                linesRead += lines.size();
                if (linesRead > MAX_LINES_PER_REPO) {
                    break;
                }
                // One hit per requirement per file: the first line where any of its terms appears.
                Set<Requirement> foundHere = new HashSet<>();
                for (int i = 0; i < lines.size() && foundHere.size() < requirements.size(); i++) {
                    String lower = lines.get(i).toLowerCase(Locale.ROOT);
                    for (Requirement req : requirements) {
                        if (foundHere.contains(req)) {
                            continue;
                        }
                        for (String term : req.searchTerms()) {
                            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                                hits.get(req).add(new Hit(req, repo, rel, i + 1, term, lines, weight));
                                foundHere.add(req);
                                break;
                            }
                        }
                    }
                }
            }
        }

        List<Evidence> evidence = new ArrayList<>();
        for (Map.Entry<Requirement, List<Hit>> e : hits.entrySet()) {
            List<Hit> sorted = new ArrayList<>(e.getValue());
            sorted.sort(Comparator.comparingInt(Hit::weight).reversed()); // stable: keeps repo/file order on ties
            Map<String, Integer> perRepo = new HashMap<>();
            Set<String> repoSeen = new HashSet<>();
            List<Hit> picked = new ArrayList<>();
            // First pass: the best hit from each repo (spread evidence across projects); second pass: fill up.
            for (int pass = 0; pass < 2 && picked.size() < MAX_EVIDENCE_PER_REQUIREMENT; pass++) {
                for (Hit h : sorted) {
                    if (picked.size() >= MAX_EVIDENCE_PER_REQUIREMENT || picked.contains(h)) {
                        continue;
                    }
                    String repoName = h.repo().fullName();
                    if (pass == 0 && repoSeen.contains(repoName)) {
                        continue;
                    }
                    if (perRepo.getOrDefault(repoName, 0) >= MAX_PER_REPO_PER_REQUIREMENT) {
                        continue;
                    }
                    picked.add(h);
                    repoSeen.add(repoName);
                    perRepo.merge(repoName, 1, Integer::sum);
                }
            }
            for (Hit h : picked) {
                evidence.add(toEvidence("E" + (evidence.size() + 1), h));
            }
        }
        return evidence;
    }

    /**
     * First real sentence of the top-level README (headings, badges, images and HTML skipped), at most 200 chars;
     * a heading such as "# VehicleStore — second-hand vehicle marketplace" is used if it has a description part.
     */
    static String readmeSummary(Path root) {
        Path readme;
        try (var files = java.nio.file.Files.list(root)) {
            readme = files.filter(p -> p.getFileName().toString().equalsIgnoreCase("README.md")).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
        if (readme == null) {
            return null;
        }
        List<String> lines;
        try {
            lines = RepoTools.readLines(readme);
        } catch (IOException e) {
            return null;
        }
        String headingWithDescription = null;
        for (String raw : lines.stream().limit(60).toList()) {
            String line = raw.strip();
            if (line.startsWith("#")) {
                String h = line.replaceFirst("^#+\\s*", "");
                if (headingWithDescription == null && h.matches(".+\\s[—–-]\\s.+")) {
                    headingWithDescription = h.replaceFirst("^.*?\\s[—–-]\\s", "");
                }
                continue;
            }
            if (line.isEmpty() || line.startsWith("[!") || line.startsWith("![") || line.startsWith("<")
                    || line.startsWith(">") || line.startsWith("|") || line.startsWith("```") || line.startsWith("-")
                    || line.startsWith("*")) {
                continue;
            }
            String text = line.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1").replaceAll("[*_`]", "").strip();
            if (text.length() >= 20) {
                return text.length() > 200 ? text.substring(0, 200) : text;
            }
        }
        return headingWithDescription;
    }

    /** 0 = ignore, 1 = docs, 2 = tests and CI workflows, 3 = source code, 4 = build file that proves the tech is used. */
    static int weight(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String lower = path.toLowerCase(Locale.ROOT);
        if (IGNORED_FILES.contains(name) || lower.endsWith(".lock") || lower.endsWith(".min.js")
                || lower.endsWith(".map") || lower.endsWith(".svg") || lower.contains("/dist/")
                || lower.startsWith("dist/") || lower.contains("/build/") || lower.contains("/target/")) {
            return 0;
        }
        if (BUILD_FILES.contains(name)) {
            return 4;
        }
        if (lower.startsWith(".github/workflows/")) {
            return 2; // a job named "Spring Boot" is weak proof; the build file or code is better
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.startsWith("docs/")) {
            return 1;
        }
        if (lower.contains("/test/") || lower.contains("/tests/") || lower.contains(".test.")
                || lower.contains(".spec.") || lower.contains("test_") || name.endsWith("Test.java")) {
            return 2;
        }
        return 3;
    }

    private static Evidence toEvidence(String id, Hit h) {
        int start = Math.max(1, h.line() - CONTEXT_BEFORE);
        int end = Math.min(h.lines().size(), h.line() + CONTEXT_AFTER);
        StringBuilder excerpt = new StringBuilder();
        for (int i = start; i <= end; i++) {
            String line = h.lines().get(i - 1);
            excerpt.append(i).append(": ")
                    .append(line.length() > EXCERPT_LINE_LENGTH ? line.substring(0, EXCERPT_LINE_LENGTH) + " ..." : line)
                    .append('\n');
        }
        String url = UriComponentsBuilder.fromUriString("https://github.com")
                .pathSegment(h.repo().owner(), h.repo().name(), "blob")
                .path("/" + h.repo().branch() + "/")
                .path(h.path())
                .fragment("L" + start + "-L" + end)
                .build().encode().toUriString();
        return new Evidence(id, h.requirement().name(), h.repo().fullName(), h.path(), start, end, h.term(),
                excerpt.toString().stripTrailing(), url);
    }
}
