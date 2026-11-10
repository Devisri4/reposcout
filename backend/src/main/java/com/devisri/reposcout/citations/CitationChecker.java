package com.devisri.reposcout.citations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.tools.RepoTools;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Checks the file references in the AI's answer in plain Java. The AI's citations are verified, not trusted.
 *
 * <p>A citation like {@code [src/App.java:12-30]} is kept only if the file exists, lines 12-30 are inside
 * it, and the agent really read those lines. Kept citations become GitHub links to the exact lines;
 * the rest are removed from the answer and reported with the reason.
 */
public final class CitationChecker {

    // [path:12] or [path:12-30]; the path has no spaces or brackets. A leading "/" or "./" is allowed.
    // Some models (e.g. gpt-oss) write 【path:12-30】 instead, and may use an en dash; both are accepted so
    // that no citation can skip the check just by using different brackets.
    private static final Pattern CITATION = Pattern.compile(
            "[\\[【]([^\\[\\]【】\\s:]+):(\\d+)(?:\\s*[-–]\\s*(\\d+))?[\\]】]");

    private CitationChecker() {
    }

    public record Citation(String path, int startLine, int endLine, boolean verified, String reason, String url) {
    }

    public record CheckedAnswer(String answer, List<Citation> citations) {
    }

    public static CheckedAnswer check(String answer, LoadedRepo repo, RepoTools tools, ReadRanges read) {
        Map<String, Citation> seen = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder();
        int removed = 0;

        Matcher m = CITATION.matcher(answer);
        int last = 0;
        while (m.find()) {
            out.append(answer, last, m.start());
            last = m.end();
            // A citation already turned into a Markdown link "[x:1-2](url)" is left alone.
            if (last < answer.length() && answer.charAt(last) == '(') {
                out.append(m.group());
                continue;
            }
            String path = cleanPath(m.group(1));
            int start = parse(m.group(2));
            int end = m.group(3) != null ? parse(m.group(3)) : start;

            Citation c = seen.computeIfAbsent(path + ":" + start + "-" + end,
                    k -> verify(path, start, end, repo, tools, read));
            if (c.verified()) {
                // Keep the link readable inside a sentence: a space before it, and only the file name shown
                // (the full path is in the link and in the citations list).
                if (!out.isEmpty() && !Character.isWhitespace(out.charAt(out.length() - 1))
                        && out.charAt(out.length() - 1) != '(') {
                    out.append(' ');
                }
                out.append('[').append(shortLabel(c)).append("](").append(c.url()).append(')');
            } else {
                removed++;
                // Drop the space before the removed reference so "see X [bad:1]." becomes "see X."
                while (!out.isEmpty() && (out.charAt(out.length() - 1) == ' ' || out.charAt(out.length() - 1) == '\t')) {
                    out.setLength(out.length() - 1);
                }
            }
        }
        out.append(answer.substring(last));

        String text = out.toString().strip();
        if (removed > 0) {
            text += "\n\n_Note: " + removed + " file reference(s) could not be verified and were removed._";
        }
        return new CheckedAnswer(text, new ArrayList<>(seen.values()));
    }

    private static Citation verify(String path, int start, int end, LoadedRepo repo, RepoTools tools, ReadRanges read) {
        if (start < 1 || end < start) {
            return new Citation(path, start, end, false, "invalid line range", null);
        }
        Optional<Integer> lines = tools.lineCount(path);
        if (lines.isEmpty()) {
            return new Citation(path, start, end, false, "file does not exist in the repository", null);
        }
        if (end > lines.get()) {
            return new Citation(path, start, end, false, "file has only " + lines.get() + " lines", null);
        }
        if (!read.covers(path, start, end)) {
            String why = read.readAny(path) ? "the agent did not read those lines" : "the agent never opened this file";
            return new Citation(path, start, end, false, why, null);
        }
        return new Citation(path, start, end, true, null, githubUrl(repo, path, start, end));
    }

    static String githubUrl(LoadedRepo repo, String path, int start, int end) {
        String anchor = start == end ? "L" + start : "L" + start + "-L" + end;
        return UriComponentsBuilder.fromUriString("https://github.com")
                .pathSegment(repo.owner(), repo.name(), "blob")
                .path("/" + repo.branch() + "/")
                .path(path)
                .fragment(anchor)
                .build().encode().toUriString();
    }

    /** Line numbers too large for an int are treated as invalid (0). */
    private static int parse(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** {@code src/auth/Login.java:10-20} is shown as {@code Login.java:10-20}. */
    private static String shortLabel(Citation c) {
        String file = c.path().substring(c.path().lastIndexOf('/') + 1);
        return file + ":" + (c.startLine() == c.endLine() ? c.startLine() : c.startLine() + "-" + c.endLine());
    }

    private static String cleanPath(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
