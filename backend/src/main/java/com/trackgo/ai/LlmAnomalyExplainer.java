package com.trackgo.ai;

import com.trackgo.anomaly.AnomalyExplainer;
import com.trackgo.anomaly.AnomalyFacts;
import com.trackgo.config.AiProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Lets the language model phrase the "your rider went quiet" note. It receives only facts the
 * rules already established and may not add causes or promises, so it cannot make the situation
 * sound better or worse than it is. Without a configured model it declines (returns empty) and the
 * caller falls back to fixed text.
 */
@Component
public class LlmAnomalyExplainer implements AnomalyExplainer {

    private static final Logger log = LoggerFactory.getLogger(LlmAnomalyExplainer.class);

    private static final String SYSTEM_PROMPT = """
            You write one short status note (at most two sentences) for a customer whose delivery has gone
            quiet because the rider has not reported a position for a while.

            Rules:
            - Use only the facts you are given.
            - Do not invent causes (no traffic, accidents, weather, breakdowns).
            - Do not promise an arrival time.
            - Do not quote exact seconds; say things like "a little while" or "over a minute".
            - Be calm and reassuring. Plain English, no markdown, no greeting, no sign-off.
            """;

    private final ChatClient chatClient;
    private final AiProperties properties;

    public LlmAnomalyExplainer(ChatClient.Builder builder, AiProperties properties) {
        this.properties = properties;
        // No memory and no tools: each note is a self-contained, single-shot completion.
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Override
    public Optional<String> explain(AnomalyFacts facts) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        // Addresses are deliberately left out: they are free text typed by users, and the note does
        // not need them. Less user-controlled text in the prompt means less to worry about.
        String factSheet = """
                Delivery status: %s
                Rider name: %s
                Seconds since the rider last reported a position: %d
                Remaining distance in km: %s
                """.formatted(facts.status(), facts.riderName(), facts.staleSeconds(),
                facts.remainingKm() == null ? "unknown" : String.format("%.1f", facts.remainingKm()));
        try {
            String text = chatClient.prompt().user(factSheet).call().content();
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        } catch (RuntimeException e) {
            // Never let a flaky model break the tracking page: the caller will use the fallback text.
            log.warn("Language model could not explain anomaly for delivery {}: {}", facts.deliveryId(), e.getMessage());
            return Optional.empty();
        }
    }
}
