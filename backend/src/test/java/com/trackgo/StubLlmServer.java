package com.trackgo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A tiny fake of an OpenAI-compatible {@code POST /chat/completions} endpoint.
 *
 * <p>It lets the AI integration tests run the <em>real</em> Spring AI client, tool-calling loop and
 * our tools end to end with no network, no API key and no money. The test supplies a "script": a
 * function from the request the application sent to the reply the "model" gives (either some text,
 * or a request to call a tool). The server records every request so tests can also assert on what
 * was sent: which tools were advertised, which messages the memory replayed.
 *
 * <p>What this does <b>not</b> prove: that a real model picks sensible tools. It proves the plumbing.
 */
final class StubLlmServer implements AutoCloseable {

    /** What the fake model answers with. */
    sealed interface Reply permits Text, ToolCall, Fail {
    }

    record Text(String content) implements Reply {
    }

    record ToolCall(String name, String argumentsJson) implements Reply {
    }

    record Fail(int httpStatus) implements Reply {
    }

    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger callIds = new AtomicInteger();
    private volatile Function<JsonNode, Reply> script = request -> new Text("stub reply");

    StubLlmServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/chat/completions", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void script(Function<JsonNode, Reply> script) {
        this.script = script;
    }

    void reset() {
        requests.clear();
        script = request -> new Text("stub reply");
    }

    List<JsonNode> requests() {
        return new ArrayList<>(requests);
    }

    // ---- helpers for writing scripts and assertions ---------------------------------------------

    static String lastRole(JsonNode request) {
        JsonNode messages = request.get("messages");
        return messages.get(messages.size() - 1).path("role").asText();
    }

    static String lastContent(JsonNode request) {
        JsonNode messages = request.get("messages");
        return messages.get(messages.size() - 1).path("content").asText();
    }

    /** The text of every {@code user} message in the request, oldest first. */
    static List<String> userMessages(JsonNode request) {
        List<String> result = new ArrayList<>();
        for (JsonNode message : request.get("messages")) {
            if ("user".equals(message.path("role").asText())) {
                result.add(message.path("content").asText());
            }
        }
        return result;
    }

    // ---- server ---------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            JsonNode request = json.readTree(exchange.getRequestBody());
            requests.add(request);

            Reply reply = script.apply(request);
            if (reply instanceof Fail fail) {
                exchange.sendResponseHeaders(fail.httpStatus(), -1);
                return;
            }

            byte[] body = json.writeValueAsBytes(completion(reply));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    /** Builds the JSON of an OpenAI chat completion for the given reply. */
    private ObjectNode completion(Reply reply) {
        ObjectNode root = json.createObjectNode();
        root.put("id", "chatcmpl-stub");
        root.put("object", "chat.completion");
        root.put("created", 0);
        root.put("model", "stub-model");

        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");

        if (reply instanceof Text text) {
            message.put("content", text.content());
            choice.put("finish_reason", "stop");
        } else if (reply instanceof ToolCall toolCall) {
            message.putNull("content");
            ArrayNode calls = message.putArray("tool_calls");
            ObjectNode call = calls.addObject();
            call.put("id", "call_" + callIds.incrementAndGet());
            call.put("type", "function");
            ObjectNode function = call.putObject("function");
            function.put("name", toolCall.name());
            function.put("arguments", toolCall.argumentsJson());
            choice.put("finish_reason", "tool_calls");
        }

        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 1);
        usage.put("completion_tokens", 1);
        usage.put("total_tokens", 2);
        return root;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // Convenience for scripts that need the bytes of a prompt: kept here so tests stay readable.
    static String describe(JsonNode request) {
        return new String(request.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
