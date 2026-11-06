package com.devisri.reposcout.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.devisri.reposcout.repo.LoadedRepo;

/**
 * The three things the agent can do inside a downloaded repo. Plain Java, no AI.
 *
 * <p>Every method returns text meant for the AI to read. Problems (missing file, bad path) come back
 * as a short message instead of an exception, so the AI can correct itself and try again.
 */
public class RepoTools {

    public static final int MAX_LIST_ENTRIES = 200;
    public static final int MAX_LINES_PER_READ = 200;
    public static final int MAX_SEARCH_MATCHES = 40;
    static final int MAX_LINE_LENGTH = 300;

    private static final Set<String> SKIPPED_FOLDERS = Set.of(".git", "node_modules");

    private final Path root;

    public RepoTools(LoadedRepo repo) {
        this.root = repo.root().toAbsolutePath().normalize();
    }

    // ---------------------------------------------------------------- listFiles

    public String listFiles(String folder) {
        Optional<Path> dir = resolve(folder);
        if (dir.isEmpty()) {
            return "Error: '" + folder + "' is not a valid path inside the repository.";
        }
        if (!Files.isDirectory(dir.get())) {
            return "Error: folder '" + clean(folder) + "' does not exist. Use listFiles(\"\") to see the top level.";
        }

        List<Path> children;
        try (Stream<Path> s = Files.list(dir.get())) {
            children = s.filter(p -> !SKIPPED_FOLDERS.contains(p.getFileName().toString()))
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            return "Error: could not list '" + clean(folder) + "'.";
        }

        String shown = relative(dir.get());
        StringBuilder out = new StringBuilder("Contents of /" + shown + (shown.isEmpty() ? "" : "/") + ":\n");
        if (children.isEmpty()) {
            out.append("(empty folder)\n");
        }
        for (Path child : children.stream().limit(MAX_LIST_ENTRIES).toList()) {
            String rel = relative(child);
            if (Files.isDirectory(child)) {
                out.append(rel).append("/\n");
            } else {
                out.append(rel).append("  (").append(humanSize(child)).append(")\n");
            }
        }
        if (children.size() > MAX_LIST_ENTRIES) {
            out.append("... and ").append(children.size() - MAX_LIST_ENTRIES).append(" more entries not shown.\n");
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- readFile

    public String readFile(String path, int startLine, int endLine) {
        Optional<Path> file = resolve(path);
        if (file.isEmpty()) {
            return "Error: '" + path + "' is not a valid path inside the repository.";
        }
        if (!Files.isRegularFile(file.get())) {
            return "Error: file '" + clean(path) + "' does not exist. Use listFiles or searchCode to find the right path.";
        }
        if (isBinary(file.get())) {
            return "Error: '" + clean(path) + "' is a binary file and cannot be read as text.";
        }

        List<String> lines;
        try {
            lines = readLines(file.get());
        } catch (IOException e) {
            return "Error: could not read '" + clean(path) + "'.";
        }
        int total = lines.size();
        if (total == 0) {
            return "File: " + relative(file.get()) + " is empty.";
        }

        int start = Math.max(1, startLine);
        if (start > total) {
            return "Error: '" + relative(file.get()) + "' has only " + total + " lines.";
        }
        int end = endLine < start ? start + MAX_LINES_PER_READ - 1 : endLine;
        end = Math.min(Math.min(end, total), start + MAX_LINES_PER_READ - 1);

        StringBuilder out = new StringBuilder()
                .append("File: ").append(relative(file.get()))
                .append(" (lines ").append(start).append('-').append(end).append(" of ").append(total).append(")\n");
        for (int i = start; i <= end; i++) {
            out.append(i).append(": ").append(shorten(lines.get(i - 1))).append('\n');
        }
        if (end < total) {
            out.append("... file continues. Call readFile again with startLine=").append(end + 1).append(".\n");
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- searchCode

    public String searchCode(String text) {
        if (text == null || text.strip().length() < 2) {
            return "Error: search text must be at least 2 characters.";
        }
        String needle = text.strip().toLowerCase(Locale.ROOT);

        List<String> matches = new ArrayList<>();
        int totalMatches = 0;
        for (Path file : allTextFiles()) {
            List<String> lines;
            try {
                lines = readLines(file);
            } catch (IOException e) {
                continue;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).toLowerCase(Locale.ROOT).contains(needle)) {
                    totalMatches++;
                    if (matches.size() < MAX_SEARCH_MATCHES) {
                        matches.add(relative(file) + ":" + (i + 1) + ": " + shorten(lines.get(i).strip()));
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            return "No matches for '" + text.strip() + "'.";
        }
        StringBuilder out = new StringBuilder(totalMatches + " match(es) for '" + text.strip() + "':\n");
        matches.forEach(m -> out.append(m).append('\n'));
        if (totalMatches > matches.size()) {
            out.append("... ").append(totalMatches - matches.size())
                    .append(" more matches not shown. Try a more specific search.\n");
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- shared helpers

    /**
     * Turns a repo-relative path from the AI into a real path, or empty if it would leave the repo.
     * Accepts "", "/", "src/App.java", "/src/App.java" and "./src/App.java".
     */
    public Optional<Path> resolve(String relativePath) {
        String p = clean(relativePath);
        try {
            Path resolved = root.resolve(p).normalize();
            return resolved.startsWith(root) ? Optional.of(resolved) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** Number of lines in a repo text file, or empty if it is missing, binary or outside the repo. */
    public Optional<Integer> lineCount(String relativePath) {
        Optional<Path> file = resolve(relativePath).filter(Files::isRegularFile);
        if (file.isEmpty() || isBinary(file.get())) {
            return Optional.empty();
        }
        try {
            return Optional.of(readLines(file.get()).size());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Repo-relative path with forward slashes, e.g. {@code src/App.java}. */
    public String relative(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    static String clean(String path) {
        if (path == null) {
            return "";
        }
        String p = path.strip().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    private List<Path> allTextFiles() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> root.relativize(p).getNameCount() > 0)
                    .filter(p -> {
                        for (Path part : root.relativize(p)) {
                            if (SKIPPED_FOLDERS.contains(part.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .filter(p -> !isBinary(p))
                    .sorted(Comparator.comparing(this::relative))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** A file is treated as binary if its first 8 KB contain a zero byte. */
    static boolean isBinary(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(8192);
            for (byte b : head) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /** Reads UTF-8, replacing broken characters instead of failing on odd encodings. */
    private static List<String> readLines(Path file) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder))) {
            return reader.lines().toList();
        }
    }

    private static String shorten(String line) {
        return line.length() <= MAX_LINE_LENGTH ? line : line.substring(0, MAX_LINE_LENGTH) + " ...[line cut]";
    }

    private static String humanSize(Path file) {
        try {
            long bytes = Files.size(file);
            return bytes < 1024 ? bytes + " B" : (bytes / 1024) + " KB";
        } catch (IOException e) {
            return "?";
        }
    }
}
