package com.devisri.reposcout.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The AI side failed (no key, rate limit, model error). The message is safe to show to the user.
 */
public class AgentException extends RuntimeException {

    private static final Logger log = LoggerFactory.getLogger(AgentException.class);

    /** Used in application.properties when GROQ_API_KEY is missing, so the app can still start. */
    public static final String KEY_PLACEHOLDER = "not-set";

    private final boolean temporary;

    public AgentException(String message, boolean temporary, Throwable cause) {
        super(message, cause);
        this.temporary = temporary;
    }

    /** True when trying again later may work (rate limit, AI service briefly down). */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * Turns a failed AI call into a message for the user. Rate limits and outages are "temporary" (HTTP 429);
     * a rejected key is reported clearly; anything else uses {@code otherwise}.
     */
    public static AgentException from(RuntimeException e, String otherwise) {
        if (e instanceof AgentException already) {
            return already;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof TransientAiException || e instanceof ResourceAccessException) {
            log.warn("AI call failed (temporary): {}", msg);
            return new AgentException("The AI service is busy or rate limited right now. Please try again in a minute.", true, e);
        }
        if (e instanceof NonTransientAiException) {
            log.error("AI call failed: {}", msg);
            if (msg.contains("429") || msg.contains("rate_limit")) {
                return new AgentException("The free AI quota is used up for the moment. Please try again in a minute.", true, e);
            }
            if (msg.contains("401") || msg.contains("invalid_api_key")) {
                return new AgentException("The server's AI key was rejected. Check GROQ_API_KEY.", false, e);
            }
            return new AgentException(otherwise, false, e);
        }
        // e.g. the model's JSON could not be parsed into the expected shape
        log.error("AI call failed unexpectedly", e);
        return new AgentException(otherwise, true, e);
    }
}
