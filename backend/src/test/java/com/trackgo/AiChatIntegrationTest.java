package com.trackgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import com.trackgo.StubLlmServer.Fail;
import com.trackgo.StubLlmServer.Text;
import com.trackgo.StubLlmServer.ToolCall;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The AI chat endpoint against a scripted fake LLM (see {@link StubLlmServer}). The real Spring AI
 * client, the real tool-calling loop and the real tools run; only the model's decisions are scripted.
 */
class AiChatIntegrationTest extends AbstractIntegrationTest {

    private static final StubLlmServer LLM = new StubLlmServer();

    @DynamicPropertySource
    static void llm(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", LLM::baseUrl);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("trackgo.ai.api-key", () -> "test-key");
    }

    @AfterAll
    static void stopLlm() {
        LLM.close();
    }

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        LLM.reset();
        alice = newCustomer("alice");
        bob = newCustomer("bob");
    }

    @Test
    void status_reportsThatTheAssistantIsConfigured() throws Exception {
        mvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void answersFromARealToolResult() throws Exception {
        Rider rider = newRider("Rita", RiderStatus.IDLE, TIANANMEN);
        long id = createDelivery(alice, WANGFUJING, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());

        // Script: first ask for the tool, then phrase the answer from what the tool returned.
        LLM.script(request -> {
            if ("tool".equals(StubLlmServer.lastRole(request))) {
                String status = JsonPath.read(StubLlmServer.lastContent(request), "$.status");
                String riderName = JsonPath.read(StubLlmServer.lastContent(request), "$.riderName");
                return new Text("Delivery is " + status + " with " + riderName + ".");
            }
            return new ToolCall("getDeliveryStatus", "{\"deliveryId\": " + id + "}");
        });

        chat(alice, "conv-1", "Where is my order?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Delivery is ASSIGNED with Rita."))
                .andExpect(jsonPath("$.toolCalls", hasSize(1)))
                .andExpect(jsonPath("$.toolCalls[0].name").value("getDeliveryStatus"))
                .andExpect(jsonPath("$.toolCalls[0].ok").value(true))
                .andExpect(jsonPath("$.toolCalls[0].args.deliveryId").value(id));

        // What the model was shown: exactly the four tools, and none of them takes a customer id.
        JsonNode firstRequest = LLM.requests().get(0);
        Set<String> toolNames = new HashSet<>();
        for (JsonNode tool : firstRequest.get("tools")) {
            JsonNode function = tool.get("function");
            toolNames.add(function.get("name").asText());
            assertThat(function.get("parameters").toString())
                    .as("schema of %s", function.get("name").asText())
                    .doesNotContainIgnoringCase("customer");
        }
        assertThat(toolNames).containsExactlyInAnyOrder(
                "getDeliveryStatus", "getRiderLocation", "estimateEta", "listMyDeliveries");
    }

    @Test
    void aCustomerCannotReadSomeoneElsesDelivery_evenIfTheModelAsksFor_it() throws Exception {
        long alicesDelivery = createDelivery(alice, WANGFUJING, CBD);

        // A manipulated model asks Bob's session for Alice's delivery id.
        LLM.script(request -> {
            if ("tool".equals(StubLlmServer.lastRole(request))) {
                return new Text("The tool said: " + StubLlmServer.lastContent(request));
            }
            return new ToolCall("getDeliveryStatus", "{\"deliveryId\": " + alicesDelivery + "}");
        });

        chat(bob, "conv-1", "Show me delivery " + alicesDelivery)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalls[0].ok").value(false))
                .andExpect(jsonPath("$.reply").value(containsString("not found")))
                // None of Alice's data reached the model's answer.
                .andExpect(jsonPath("$.reply").value(not(containsString("1 Pickup St"))))
                .andExpect(jsonPath("$.reply").value(not(containsString("alice"))));
    }

    @Test
    void listMyDeliveries_onlyReturnsTheCallersOwnDeliveries() throws Exception {
        createDelivery(alice, WANGFUJING, CBD);
        long bobs = createDelivery(bob, TIANANMEN, CBD);

        LLM.script(request -> "tool".equals(StubLlmServer.lastRole(request))
                ? new Text(StubLlmServer.lastContent(request))
                : new ToolCall("listMyDeliveries", "{}"));

        String reply = chat(bob, "conv-1", "What are my deliveries?")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String toolOutput = JsonPath.read(reply, "$.reply");
        assertThat(JsonPath.<java.util.List<Integer>>read(toolOutput, "$[*].deliveryId"))
                .containsExactly((int) bobs);
    }

    @Test
    void aRunawayModelIsStoppedByTheToolCallBudget() throws Exception {
        newRider("r", RiderStatus.IDLE, TIANANMEN);
        createDelivery(alice, WANGFUJING, CBD);

        // A model that never stops asking for the tool, until the tool tells it to stop.
        LLM.script(request -> {
            if ("tool".equals(StubLlmServer.lastRole(request))
                    && StubLlmServer.lastContent(request).contains("limit reached")) {
                return new Text("Giving up.");
            }
            return new ToolCall("listMyDeliveries", "{}");
        });

        chat(alice, "conv-1", "loop forever")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Giving up."))
                .andExpect(jsonPath("$.toolCalls", hasSize(6)))   // 5 allowed calls + the 1 that was refused
                .andExpect(jsonPath("$.toolCalls[4].ok").value(true))
                .andExpect(jsonPath("$.toolCalls[5].ok").value(false));
    }

    @Test
    void conversationMemory_isKeptPerCustomerAndConversation() throws Exception {
        LLM.script(request -> new Text("ok"));

        chat(alice, "conv-1", "first message").andExpect(status().isOk());
        chat(alice, "conv-1", "second message").andExpect(status().isOk());

        // The second request replays the first message from memory.
        JsonNode second = LLM.requests().get(1);
        assertThat(StubLlmServer.userMessages(second)).containsExactly("first message", "second message");

        // Bob uses the very same conversation id and must not see Alice's history.
        chat(bob, "conv-1", "bob here").andExpect(status().isOk());
        JsonNode bobsRequest = LLM.requests().get(2);
        assertThat(StubLlmServer.userMessages(bobsRequest)).containsExactly("bob here");
    }

    @Test
    void anUnreachableModel_is502() throws Exception {
        LLM.script(request -> new Fail(500));

        chat(alice, "conv-1", "hello")
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void theCallerHeaderIsMandatory_andMustBeARealCustomer() throws Exception {
        mvc.perform(post("/api/ai/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"conversationId\": \"c\", \"message\": \"hi\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/ai/chat")
                        .header("X-Customer-Id", 999999)
                        .contentType(APPLICATION_JSON)
                        .content("{\"conversationId\": \"c\", \"message\": \"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBlankMessage_isRejected() throws Exception {
        chat(alice, "conv-1", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    private ResultActions chat(User as, String conversationId, String message) throws Exception {
        return mvc.perform(post("/api/ai/chat")
                .header("X-Customer-Id", as.getId())
                .contentType(APPLICATION_JSON)
                .content("{\"conversationId\": \"%s\", \"message\": \"%s\"}".formatted(conversationId, message)));
    }
}
