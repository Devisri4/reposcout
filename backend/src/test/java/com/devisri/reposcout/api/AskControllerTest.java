package com.devisri.reposcout.api;

import java.nio.file.Path;

import com.devisri.reposcout.agent.AgentException;
import com.devisri.reposcout.agent.RepoAgent;
import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.repo.RepoLoadException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AskController.class)
class AskControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepoAgent agent;

    private static final String BODY = """
            {"repoUrl":"https://github.com/acme/demo","question":"How does login work?"}
            """;

    @Test
    void returnsTheAgentsAnswer() throws Exception {
        LoadedRepo repo = new LoadedRepo("acme", "demo", "main", Path.of("unused"));
        when(agent.ask("https://github.com/acme/demo", "How does login work?"))
                .thenReturn(new RepoAgent.Result(repo, "Login is in [src/Login.java:1-5]", 4));

        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo").value("acme/demo"))
                .andExpect(jsonPath("$.answer").value("Login is in [src/Login.java:1-5]"))
                .andExpect(jsonPath("$.toolCalls").value(4));
    }

    @Test
    void rejectsAMissingQuestion() throws Exception {
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"acme/demo\",\"question\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question is required"));
    }

    @Test
    void rejectsBodiesThatAreNotJson() throws Exception {
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void showsRepoProblemsAsBadRequest() throws Exception {
        when(agent.ask(any(), any())).thenThrow(new RepoLoadException("Repository acme/demo was not found."));

        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Repository acme/demo was not found."));
    }

    @Test
    void showsRateLimitsAs429AndMissingKeyAs503() throws Exception {
        when(agent.ask(any(), any())).thenThrow(new AgentException("busy", true, null));
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());

        doThrow(new AgentException("no key", false, null)).when(agent).ask(any(), any());
        mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("no key"));
    }
}
