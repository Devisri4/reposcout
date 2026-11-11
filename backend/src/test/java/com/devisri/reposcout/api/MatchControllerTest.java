package com.devisri.reposcout.api;

import java.time.Instant;
import java.util.List;

import com.devisri.reposcout.match.MatchResult;
import com.devisri.reposcout.match.MatchService;
import com.devisri.reposcout.repo.RepoLoadException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchService service;

    @MockitoBean
    private RateLimiter limiter;

    @MockitoBean
    private com.devisri.reposcout.store.MatchStore store;

    private static final String JOB = "We are hiring a Java developer with Spring Boot, REST APIs and React experience.";

    private static String body(String user, String job) {
        return "{\"githubUser\":\"" + user + "\",\"jobDescription\":\"" + job + "\"}";
    }

    @Test
    void returnsTheMatchResult() throws Exception {
        when(service.match("Devisri4", JOB)).thenReturn(new MatchResult("abc", "Devisri4", "Java Developer", Instant.EPOCH,
                List.of("Devisri4/shop"), List.of(),
                List.of(new MatchResult.RequirementStatus("Spring Boot", MatchResult.Status.PROVEN, 2)),
                List.of(), List.of(), List.of("Kubernetes"), true));

        mvc.perform(post("/api/match").contentType(MediaType.APPLICATION_JSON).content(body("Devisri4", JOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc"))
                .andExpect(jsonPath("$.requirements[0].status").value("PROVEN"))
                .andExpect(jsonPath("$.gaps[0]").value("Kubernetes"));
    }

    @Test
    void opensASavedMatchByItsShareLink() throws Exception {
        String id = "3f1e2d4c-5b6a-4789-8abc-def012345678";
        when(store.find(id)).thenReturn(java.util.Optional.of(new MatchResult(id, "Devisri4", "Java Developer", Instant.EPOCH,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true)));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/matches/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUser").value("Devisri4"))
                .andExpect(jsonPath("$.saved").value(true));
        verifyNoInteractions(limiter);
    }

    @Test
    void saysWhenASharedMatchDoesNotExist() throws Exception {
        when(store.find(any())).thenReturn(java.util.Optional.empty());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/matches/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("This job match was not found. It may have been deleted."));
    }

    @Test
    void rejectsATooShortJobDescription() throws Exception {
        mvc.perform(post("/api/match").contentType(MediaType.APPLICATION_JSON).content(body("Devisri4", "Java dev")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("jobDescription must be between 40 and 8000 characters"));
        verifyNoInteractions(service);
    }

    @Test
    void reportsAnUnknownGitHubUserClearly() throws Exception {
        when(service.match(any(), any())).thenThrow(new RepoLoadException("GitHub user nobody-xyz was not found."));

        mvc.perform(post("/api/match").contentType(MediaType.APPLICATION_JSON).content(body("nobody-xyz", JOB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GitHub user nobody-xyz was not found."));
    }

    @Test
    void countsTowardsTheVisitorLimit() throws Exception {
        doThrow(new RateLimiter.LimitReachedException("You've asked 10 questions in the last hour."))
                .when(limiter).check(any());

        mvc.perform(post("/api/match").contentType(MediaType.APPLICATION_JSON).content(body("Devisri4", JOB)))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(service);
    }
}
