package com.trackgo.controller;

import com.trackgo.ai.AiChatService;
import com.trackgo.dto.AiChatRequest;
import com.trackgo.dto.AiChatResponse;
import com.trackgo.dto.AiStatusResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService service;

    public AiChatController(AiChatService service) {
        this.service = service;
    }

    /** Lets the UI show whether an LLM is configured, without sending a chat message. */
    @GetMapping("/status")
    public AiStatusResponse status() {
        return service.status();
    }

    /**
     * The caller is the customer named in the {@code X-Customer-Id} header. This application has no
     * login, so the header is a stand-in for "the authenticated user"; the important part is that
     * identity enters here, on the server side of the tool boundary, and never as model output.
     */
    @PostMapping("/chat")
    public AiChatResponse chat(@RequestHeader("X-Customer-Id") Long customerId,
                               @Valid @RequestBody AiChatRequest request) {
        return service.chat(customerId, request.conversationId(), request.message());
    }
}
