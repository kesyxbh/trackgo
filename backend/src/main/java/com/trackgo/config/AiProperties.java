package com.trackgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the AI assistant ({@code trackgo.ai.*}).
 *
 * @param apiKey          the LLM API key; blank means "AI is not configured" and the chat endpoint
 *                        answers with an explanation instead of failing
 * @param model           model name, shown by {@code GET /api/ai/status}
 * @param maxToolCalls    safety valve: how many tool calls one chat request may trigger
 * @param memoryMessages  how many past messages of a conversation are sent back to the model
 */
@ConfigurationProperties(prefix = "trackgo.ai")
public record AiProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("deepseek-flash") String model,
        @DefaultValue("5") int maxToolCalls,
        @DefaultValue("20") int memoryMessages) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
