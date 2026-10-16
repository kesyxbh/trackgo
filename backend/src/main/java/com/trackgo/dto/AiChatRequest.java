package com.trackgo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/ai/chat}. The caller is identified by the {@code X-Customer-Id} header,
 * never by a field in this body, and certainly never by something the model produces.
 */
public record AiChatRequest(
        @NotBlank @Size(max = 64) String conversationId,
        @NotBlank @Size(max = 1000) String message) {
}
