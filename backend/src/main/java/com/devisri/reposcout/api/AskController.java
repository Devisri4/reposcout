package com.devisri.reposcout.api;

import com.devisri.reposcout.agent.RepoAgent;
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

    public record AskResponse(String repo, String branch, String answer, int toolCalls) {
    }

    private final RepoAgent agent;

    public AskController(RepoAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        RepoAgent.Result result = agent.ask(request.repoUrl(), request.question().strip());
        return new AskResponse(result.repo().fullName(), result.repo().branch(), result.answer(), result.toolCalls());
    }
}
