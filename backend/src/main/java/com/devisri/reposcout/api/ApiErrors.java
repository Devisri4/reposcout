package com.devisri.reposcout.api;

import com.devisri.reposcout.agent.AgentException;
import com.devisri.reposcout.repo.RepoLoadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns failures into {@code {"error": "..."}} with a fitting status, so the UI can show the message. */
@RestControllerAdvice
public class ApiErrors {

    public record ErrorResponse(String error) {
    }

    @ExceptionHandler(RepoLoadException.class)
    ResponseEntity<ErrorResponse> repo(RepoLoadException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(RateLimiter.LimitReachedException.class)
    ResponseEntity<ErrorResponse> limited(RateLimiter.LimitReachedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(AgentException.class)
    ResponseEntity<ErrorResponse> agent(AgentException e) {
        HttpStatus status = e.isTemporary() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst().orElse("Invalid request.");
        return ResponseEntity.badRequest().body(new ErrorResponse(msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Request body must be JSON with repoUrl and question."));
    }
}
