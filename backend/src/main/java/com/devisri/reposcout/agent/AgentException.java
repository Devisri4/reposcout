package com.devisri.reposcout.agent;

/**
 * The AI side failed (no key, rate limit, model error). The message is safe to show to the user.
 */
public class AgentException extends RuntimeException {

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
}
