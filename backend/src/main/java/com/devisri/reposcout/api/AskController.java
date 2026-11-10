package com.devisri.reposcout.api;

import java.util.List;

import com.devisri.reposcout.agent.AgentStep;
import com.devisri.reposcout.agent.RepoAgent;
import com.devisri.reposcout.citations.CitationChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AskController {

    public record AskRequest(
            @NotBlank(message = "repoUrl is required") @Size(max = 300) String repoUrl,
            @NotBlank(message = "question is required") @Size(max = 500, message = "question must be under 500 characters") String question) {
    }

    public record AskResponse(String repo, String branch, String answer, List<CitationChecker.Citation> citations,
                              List<AgentStep> steps, int toolCalls) {
    }

    private final RepoAgent agent;
    private final RateLimiter limiter;

    public AskController(RepoAgent agent, RateLimiter limiter) {
        this.agent = agent;
        this.limiter = limiter;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request, HttpServletRequest http) {
        limiter.check(visitor(http));
        RepoAgent.Result result = agent.ask(request.repoUrl(), request.question().strip());
        return new AskResponse(result.repo().fullName(), result.repo().branch(), result.answer(),
                result.citations(), result.steps(), result.toolCalls());
    }

    /**
     * The visitor's IP. Behind Render's proxy it is the last X-Forwarded-For entry (added by the proxy);
     * earlier entries are ignored because a visitor can fake them.
     */
    static String visitor(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].strip();
        }
        return http.getRemoteAddr();
    }
}
