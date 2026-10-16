package com.trackgo.dto;

import com.trackgo.ai.ToolCallRecord;
import java.util.List;

/**
 * @param reply     the assistant's answer
 * @param toolCalls the tools the assistant used to produce it, so the UI can show that the answer
 *                  came from real system data and not from the model's imagination
 */
public record AiChatResponse(String reply, List<ToolCallRecord> toolCalls) {
}
