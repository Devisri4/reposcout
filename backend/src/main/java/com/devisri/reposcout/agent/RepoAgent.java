package com.devisri.reposcout.agent;

import java.util.List;

import com.devisri.reposcout.citations.CitationChecker;
import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.repo.RepoLoader;
import com.devisri.reposcout.tools.RepoTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Answers one question about one repo: loads the repo, gives the AI the tools, returns its answer.
 * Spring AI runs the loop (AI picks a tool, we run it, the result goes back) until the AI answers.
 */
@Service
public class RepoAgent {

    private static final Logger log = LoggerFactory.getLogger(RepoAgent.class);

    static final String SYSTEM_PROMPT = """
            You are RepoScout. You answer questions about the GitHub repository {repo} (branch {branch}).
            You cannot see the code until you use your tools:
            - listFiles(folder): list one folder ("" is the top level)
            - searchCode(text): find lines containing a word or name
            - readFile(path, startLine, endLine): read numbered lines from a file

            How to work:
            1. Start with listFiles("") or searchCode with key words from the question.
            2. Open the most relevant files and follow the code (calls, imports) until you understand it.
            3. Read focused ranges of about 100 lines. You have at most {maxCalls} tool calls, so plan them.
            4. Never guess about code you have not read.

            How to answer:
            - Explain clearly in Markdown, using short paragraphs or bullet points, in under 300 words.
            - Back every claim about the code with a citation in exactly this form: [path/to/File.java:12-30]
              (path from the repository root, then the line range). Example: [src/auth/LoginService.java:4-6]
            - Only cite lines you actually read with readFile.
            - If the repository does not answer the question, say so honestly.
            """;

    private final ChatClient chat;
    private final RepoLoader loader;
    private final AgentProperties props;

    public RepoAgent(ChatClient.Builder chatBuilder, RepoLoader loader, AgentProperties props) {
        this.chat = chatBuilder.build();
        this.loader = loader;
        this.props = props;
    }

    /** What the agent produced for one question, after its citations were checked. */
    public record Result(LoadedRepo repo, String answer, List<CitationChecker.Citation> citations,
                         List<AgentStep> steps, int toolCalls) {
    }

    public Result ask(String repoUrl, String question) {
        if (!props.hasApiKey()) {
            throw new AgentException(
                    "The AI key is not configured on the server (set the GROQ_API_KEY environment variable).", false, null);
        }
        LoadedRepo repo = loader.load(repoUrl);
        RepoTools repoTools = new RepoTools(repo);
        AgentTools tools = new AgentTools(repoTools, props.maxToolCalls());

        long started = System.currentTimeMillis();
        try {
            String answer = chat.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT)
                            .param("repo", repo.fullName())
                            .param("branch", repo.branch())
                            .param("maxCalls", props.maxToolCalls()))
                    .user(question)
                    .tools(tools)
                    .call()
                    .content();
            log.info("Answered question on {} with {} tool calls in {} ms",
                    repo.fullName(), tools.callCount(), System.currentTimeMillis() - started);
            if (answer == null || answer.isBlank()) {
                throw new AgentException("The AI returned an empty answer. Please try again.", true, null);
            }
            CitationChecker.CheckedAnswer checked =
                    CitationChecker.check(answer.strip(), repo, repoTools, tools.readRanges());
            return new Result(repo, checked.answer(), checked.citations(), tools.steps(), tools.callCount());
        } catch (TransientAiException | ResourceAccessException e) {
            log.warn("AI call failed (temporary): {}", e.getMessage());
            throw new AgentException(
                    "The AI service is busy or rate limited right now. Please try again in a minute.", true, e);
        } catch (NonTransientAiException e) {
            log.error("AI call failed: {}", e.getMessage());
            throw new AgentException(friendly(e), e.getMessage() != null && e.getMessage().contains("429"), e);
        }
    }

    private static String friendly(NonTransientAiException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("429") || msg.contains("rate_limit")) {
            return "The free AI quota is used up for the moment. Please try again in a minute.";
        }
        if (msg.contains("401") || msg.contains("invalid_api_key")) {
            return "The server's AI key was rejected. Check GROQ_API_KEY.";
        }
        return "The AI could not finish this question. Please try again or rephrase it.";
    }
}
