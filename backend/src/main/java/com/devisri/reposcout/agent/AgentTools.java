package com.devisri.reposcout.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devisri.reposcout.citations.ReadRanges;
import com.devisri.reposcout.tools.RepoTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools as the AI sees them. One instance per question: it counts the calls, stops the agent
 * once the budget is used up, logs every step and remembers which lines were really read.
 */
public class AgentTools {

    static final String BUDGET_USED_UP =
            "Tool limit reached. Do not call any more tools. Write your final answer now using what you have read.";

    // Matches the first line RepoTools.readFile returns: "File: src/App.java (lines 1-100 of 450)"
    private static final Pattern READ_HEADER = Pattern.compile("^File: (.+) \\(lines (\\d+)-(\\d+) of \\d+\\)");

    private final RepoTools tools;
    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<AgentStep> steps = new ArrayList<>();
    private final ReadRanges readRanges = new ReadRanges();

    public AgentTools(RepoTools tools, int maxCalls) {
        this.tools = tools;
        this.maxCalls = maxCalls;
    }

    @Tool(description = "List the files and sub-folders directly inside one folder of the repository. "
            + "Use an empty string for the top level. Folders end with '/'.")
    public String listFiles(
            @ToolParam(description = "Folder path from the repository root, e.g. \"\" or \"src/main/java\"") String folder) {
        String shown = folder == null || folder.isBlank() ? "/" : folder;
        return run("listFiles", shown, () -> tools.listFiles(folder), out -> {
            long entries = out.lines().skip(1).filter(l -> !l.startsWith("...") && !l.startsWith("(")).count();
            return entries + " entries";
        });
    }

    @Tool(description = "Read a range of lines from a text file. Returns numbered lines. "
            + "At most " + RepoTools.MAX_LINES_PER_READ + " lines per call; prefer about 100.")
    public String readFile(
            @ToolParam(description = "File path from the repository root, e.g. \"src/App.java\"") String path,
            @ToolParam(description = "First line to read, starting at 1") int startLine,
            @ToolParam(description = "Last line to read (inclusive)") int endLine) {
        return run("readFile", path + " lines " + startLine + "-" + endLine, () -> tools.readFile(path, startLine, endLine), out -> {
            Matcher m = READ_HEADER.matcher(out);
            if (!m.find()) {
                return firstLine(out);
            }
            int from = Integer.parseInt(m.group(2));
            int to = Integer.parseInt(m.group(3));
            readRanges.add(m.group(1), from, to);
            return "read lines " + from + "-" + to;
        });
    }

    @Tool(description = "Search all text files for a word, name or short phrase (case-insensitive, not a regex). "
            + "Returns matching lines as path:line: code. Good for finding where a class or function is defined or used.")
    public String searchCode(
            @ToolParam(description = "Text to look for, e.g. \"login\" or \"class UserService\"") String text) {
        return run("searchCode", "\"" + text + "\"", () -> tools.searchCode(text), out ->
                out.startsWith("No matches") ? "no matches" : firstLine(out).replaceFirst(" for '.*", ""));
    }

    /** How many tool calls the AI made (including refused ones). */
    public int callCount() {
        return calls.get();
    }

    public synchronized List<AgentStep> steps() {
        return List.copyOf(steps);
    }

    /** The file lines the agent actually saw, used to check its citations. */
    public ReadRanges readRanges() {
        return readRanges;
    }

    private String run(String tool, String input, Supplier<String> call, Function<String, String> summarize) {
        int number = calls.incrementAndGet();
        if (number > maxCalls) {
            record(new AgentStep(number, tool, input, "refused: tool limit reached", 0));
            return BUDGET_USED_UP;
        }
        long started = System.nanoTime();
        String out = call.get();
        long millis = (System.nanoTime() - started) / 1_000_000;
        String summary = out.startsWith("Error:") ? firstLine(out) : summarize.apply(out);
        record(new AgentStep(number, tool, input, summary, millis));
        return out;
    }

    private synchronized void record(AgentStep step) {
        steps.add(step);
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
