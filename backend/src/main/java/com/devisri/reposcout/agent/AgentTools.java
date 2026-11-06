package com.devisri.reposcout.agent;

import java.util.concurrent.atomic.AtomicInteger;

import com.devisri.reposcout.tools.RepoTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools as the AI sees them. One instance per question: it counts the calls and stops the agent
 * once the budget is used up.
 */
public class AgentTools {

    static final String BUDGET_USED_UP =
            "Tool limit reached. Do not call any more tools. Write your final answer now using what you have read.";

    private final RepoTools tools;
    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();

    public AgentTools(RepoTools tools, int maxCalls) {
        this.tools = tools;
        this.maxCalls = maxCalls;
    }

    @Tool(description = "List the files and sub-folders directly inside one folder of the repository. "
            + "Use an empty string for the top level. Folders end with '/'.")
    public String listFiles(
            @ToolParam(description = "Folder path from the repository root, e.g. \"\" or \"src/main/java\"") String folder) {
        if (!takeBudget()) {
            return BUDGET_USED_UP;
        }
        return tools.listFiles(folder);
    }

    @Tool(description = "Read a range of lines from a text file. Returns numbered lines. "
            + "At most " + RepoTools.MAX_LINES_PER_READ + " lines per call; prefer about 100.")
    public String readFile(
            @ToolParam(description = "File path from the repository root, e.g. \"src/App.java\"") String path,
            @ToolParam(description = "First line to read, starting at 1") int startLine,
            @ToolParam(description = "Last line to read (inclusive)") int endLine) {
        if (!takeBudget()) {
            return BUDGET_USED_UP;
        }
        return tools.readFile(path, startLine, endLine);
    }

    @Tool(description = "Search all text files for a word, name or short phrase (case-insensitive, not a regex). "
            + "Returns matching lines as path:line: code. Good for finding where a class or function is defined or used.")
    public String searchCode(
            @ToolParam(description = "Text to look for, e.g. \"login\" or \"class UserService\"") String text) {
        if (!takeBudget()) {
            return BUDGET_USED_UP;
        }
        return tools.searchCode(text);
    }

    /** How many tool calls the AI made (including refused ones). */
    public int callCount() {
        return calls.get();
    }

    private boolean takeBudget() {
        return calls.incrementAndGet() <= maxCalls;
    }
}
