package com.devisri.reposcout.agent;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Decides which Groq errors are worth retrying. Replaces Spring AI's default handler.
 *
 * <p>Retried (temporary): 429 rate limits, 5xx server errors, and two known model glitches where simply
 * sending the same request again usually works:
 * <ul>
 *   <li>{@code tool_use_failed}: the model called a tool with wrong argument names</li>
 *   <li>{@code output_parse_failed}: the model wrote text where a tool call was expected</li>
 * </ul>
 * Everything else (bad key, unknown model, the free tier's daily limit, ...) fails at once.
 */
@Component
public class GroqErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        handleError(response);
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String message = "HTTP " + status + " - " + body;
        if (isTemporary(status, body)) {
            throw new TransientAiException(message);
        }
        throw new NonTransientAiException(message);
    }

    static boolean isTemporary(int status, String body) {
        if (isDailyLimit(body)) {
            return false; // waiting seconds does not help; the daily quota refills over hours
        }
        if (status == 429 || status >= 500) {
            return true;
        }
        return status == 400 && (body.contains("\"tool_use_failed\"") || body.contains("\"output_parse_failed\""));
    }

    /** Groq's free tier also limits tokens and requests per day, e.g. "tokens per day (TPD)". */
    static boolean isDailyLimit(String body) {
        return body.contains("per day (TPD)") || body.contains("per day (RPD)");
    }
}
