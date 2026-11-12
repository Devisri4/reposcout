package com.devisri.reposcout.agent;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqErrorHandlerTest {

    private final GroqErrorHandler handler = new GroqErrorHandler();

    private void assertThrows(HttpStatus status, String body, Class<? extends Exception> expected) {
        MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
        assertThatThrownBy(() -> handler.handleError(response))
                .isInstanceOf(expected)
                .hasMessageStartingWith("HTTP " + status.value());
    }

    @Test
    void retriesRateLimitsAndServerErrors() {
        assertThrows(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":{\"code\":\"rate_limit_exceeded\"}}", TransientAiException.class);
        assertThrows(HttpStatus.SERVICE_UNAVAILABLE, "busy", TransientAiException.class);
    }

    @Test
    void retriesTheTwoKnownModelGlitches() {
        // Real Groq bodies seen with openai/gpt-oss-120b.
        assertThrows(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"Tool call validation failed\",\"type\":\"invalid_request_error\",\"code\":\"tool_use_failed\"}}",
                TransientAiException.class);
        assertThrows(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"Parsing failed.\",\"type\":\"invalid_request_error\",\"code\":\"output_parse_failed\"}}",
                TransientAiException.class);
    }

    @Test
    void failsAtOnceForRealProblems() {
        assertThrows(HttpStatus.UNAUTHORIZED, "{\"error\":{\"code\":\"invalid_api_key\"}}", NonTransientAiException.class);
        assertThrows(HttpStatus.NOT_FOUND, "{\"error\":{\"code\":\"model_not_found\"}}", NonTransientAiException.class);
        assertThrows(HttpStatus.BAD_REQUEST, "{\"error\":{\"code\":\"context_length_exceeded\"}}", NonTransientAiException.class);
    }

    @Test
    void dailyLimitFailsAtOnceWithAClearMessage() {
        String body = "{\"error\":{\"message\":\"Rate limit reached for model `openai/gpt-oss-120b` on tokens per day (TPD):"
                + " Limit 200000, Used 199504, Requested 2414.\",\"code\":\"rate_limit_exceeded\"}}";
        assertThrows(HttpStatus.TOO_MANY_REQUESTS, body, NonTransientAiException.class);

        AgentException e = AgentException.from(new NonTransientAiException("HTTP 429 - " + body), "otherwise");
        assertThat(e.getMessage()).isEqualTo(AgentException.DAILY_LIMIT);
        assertThat(e.isTemporary()).isTrue();
    }
}
