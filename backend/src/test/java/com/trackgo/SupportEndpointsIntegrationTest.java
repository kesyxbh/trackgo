package com.trackgo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trackgo.simulation.SimulationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Small endpoints that the UI relies on, plus the "AI is not configured" behaviour. */
class SupportEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SimulationService simulation;

    @AfterEach
    void resume() {
        simulation.resume();
    }

    @Test
    void customers_areListedInIdOrder() throws Exception {
        newCustomer("zed");
        newCustomer("amy");

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("zed"))   // created first, so lower id
                .andExpect(jsonPath("$[1].username").value("amy"));
    }

    @Test
    void simulator_canBePausedAndResumed() throws Exception {
        mvc.perform(get("/api/simulator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.speedKmh").value(600.0));

        mvc.perform(post("/api/simulator/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));
        mvc.perform(get("/api/simulator"))
                .andExpect(jsonPath("$.paused").value(true));

        mvc.perform(post("/api/simulator/resume"))
                .andExpect(jsonPath("$.paused").value(false));
    }

    @Test
    void withoutAnApiKey_theAssistantExplainsItselfInsteadOfFailing() throws Exception {
        mvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        long customerId = newCustomer("kim").getId();
        mvc.perform(post("/api/ai/chat")
                        .header("X-Customer-Id", customerId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"conversationId\": \"c\", \"message\": \"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(containsString("not configured")))
                .andExpect(jsonPath("$.toolCalls", hasSize(0)));
    }
}
