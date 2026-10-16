package com.trackgo.ai;

import java.util.Map;

/**
 * One tool invocation made while answering a chat message.
 *
 * @param name tool name as the model saw it
 * @param args the arguments the model supplied (never includes the caller's identity)
 * @param ok   false when the tool refused or failed, for example "delivery not found"
 */
public record ToolCallRecord(String name, Map<String, Object> args, boolean ok) {
}
