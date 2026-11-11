package com.devisri.reposcout.api;

import com.devisri.reposcout.match.MatchResult;
import com.devisri.reposcout.match.MatchService;
import com.devisri.reposcout.store.MatchStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MatchController {

    public record MatchRequest(
            @NotBlank(message = "githubUser is required") @Size(max = 100) String githubUser,
            @NotBlank(message = "jobDescription is required")
            @Size(min = 40, max = 8000, message = "jobDescription must be between 40 and 8000 characters") String jobDescription) {
    }

    private final MatchService service;
    private final MatchStore store;
    private final RateLimiter limiter;

    public MatchController(MatchService service, MatchStore store, RateLimiter limiter) {
        this.service = service;
        this.store = store;
        this.limiter = limiter;
    }

    @PostMapping("/match")
    public MatchResult match(@Valid @RequestBody MatchRequest request, HttpServletRequest http) {
        limiter.check(AskController.visitor(http));
        return service.match(request.githubUser(), request.jobDescription());
    }

    /** A saved result, for share links. Costs no AI, so it is not rate limited. */
    @GetMapping("/matches/{id}")
    public ResponseEntity<?> saved(@PathVariable String id) {
        return store.find(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiErrors.ErrorResponse("This job match was not found. It may have been deleted.")));
    }
}
