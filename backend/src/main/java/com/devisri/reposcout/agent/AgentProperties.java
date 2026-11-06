package com.devisri.reposcout.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the agent ({@code reposcout.agent.*}).
 *
 * @param maxToolCalls most tool calls allowed per question; keeps us inside Groq's free limits
 * @param apiKey       the Groq key, read from the GROQ_API_KEY environment variable
 */
@ConfigurationProperties("reposcout.agent")
public record AgentProperties(
        @DefaultValue("15") int maxToolCalls,
        @DefaultValue("") String apiKey) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals(AgentException.KEY_PLACEHOLDER);
    }
}
