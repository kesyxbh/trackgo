package com.trackgo.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects the tool calls of <em>one</em> chat request and enforces a cap on how many there may be.
 *
 * <p>A language model decides on its own how many tools to call, and a confused model can loop.
 * Rather than trusting it, every tool asks this recorder for permission first; once the cap is
 * reached the tool answers with an error instead of doing work, which makes the model stop.
 */
public final class ToolCallRecorder {

    private final int maxCalls;
    private final List<ToolCallRecord> calls = new ArrayList<>();

    public ToolCallRecorder(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    /** True while the request has not used up its tool-call budget. */
    public synchronized boolean hasBudget() {
        return calls.size() < maxCalls;
    }

    public synchronized void record(String name, Map<String, Object> args, boolean ok) {
        calls.add(new ToolCallRecord(name, args, ok));
    }

    public synchronized List<ToolCallRecord> calls() {
        return List.copyOf(calls);
    }
}
