package com.trackgo.ai;

import com.trackgo.config.AiProperties;
import com.trackgo.dto.AiChatResponse;
import com.trackgo.dto.AiStatusResponse;
import com.trackgo.exception.AiUnavailableException;
import com.trackgo.exception.ResourceNotFoundException;
import com.trackgo.service.CustomerService;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

/**
 * The delivery assistant: a chat conversation in which the model may look up the caller's own
 * deliveries through {@link DeliveryTools}.
 *
 * <p>One request = one round trip from the customer's point of view, but inside {@code call()}
 * Spring AI runs the whole function-calling loop:
 * <ol>
 *   <li>send the conversation + tool descriptions to the model,</li>
 *   <li>the model answers either with text (done) or with "please call tool X with these args",</li>
 *   <li>Spring AI executes the tool, appends the result to the conversation and goes back to 1.</li>
 * </ol>
 */
@Service
public class AiChatService {

    static final String NOT_CONFIGURED_REPLY = "The AI assistant is not configured on this server. "
            + "Set the LLM_API_KEY environment variable (and optionally LLM_BASE_URL and LLM_MODEL) "
            + "and restart the backend.";

    private static final String SYSTEM_PROMPT = """
            You are TrackGo's delivery assistant. You help a customer follow the delivery orders they placed.

            Rules:
            - Use the tools to look up real data. Never invent a status, position, time or rider.
            - If the customer does not say which delivery they mean, call listMyDeliveries first; if they have
              exactly one active delivery, assume that one and say which one you picked.
            - You can only read information. You cannot create, change or cancel deliveries; say so if asked.
            - If a tool returns an error, tell the customer plainly what it said.
            - Answer briefly, in plain English, without markdown.
            - If the question is not about deliveries, say that you can only help with delivery tracking.
            """;

    private final ChatClient chatClient;
    private final DeliveryTools tools;
    private final AiProperties properties;
    private final CustomerService customers;

    public AiChatService(ChatClient.Builder builder, DeliveryTools tools, AiProperties properties,
                         CustomerService customers) {
        this.tools = tools;
        this.properties = properties;
        this.customers = customers;

        // In-memory sliding window: only the last N messages of a conversation are sent to the model.
        // It lives in this JVM, so conversations vanish on restart (acceptable for a demo).
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(properties.memoryMessages())
                .build();
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public AiStatusResponse status() {
        return new AiStatusResponse(properties.isConfigured(), properties.model());
    }

    /**
     * @param customerId     who is asking; comes from the request header, never from the model
     * @param conversationId client-chosen id that groups messages into one conversation
     */
    public AiChatResponse chat(Long customerId, String conversationId, String message) {
        if (!properties.isConfigured()) {
            return new AiChatResponse(NOT_CONFIGURED_REPLY, List.of());
        }
        if (!customers.exists(customerId)) {
            throw new ResourceNotFoundException("Customer " + customerId + " not found");
        }

        ToolCallRecorder recorder = new ToolCallRecorder(properties.maxToolCalls());
        // The customer id is part of the memory key: guessing somebody else's conversationId must
        // not reveal their conversation.
        String memoryKey = "customer-" + customerId + ":" + conversationId;

        try {
            String reply = chatClient.prompt()
                    .user(message)
                    .tools(tools)
                    .toolContext(Map.of(DeliveryTools.CUSTOMER_ID, customerId, DeliveryTools.RECORDER, recorder))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, memoryKey))
                    .call()
                    .content();
            return new AiChatResponse(reply == null ? "" : reply.trim(), recorder.calls());
        } catch (RuntimeException e) {
            throw new AiUnavailableException("The AI service could not answer right now. Please try again.", e);
        }
    }
}
