package com.trackgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trackgo.anomaly.AnomalyExplainer;
import com.trackgo.anomaly.AnomalyFacts;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Stalled-rider detection. Time is the hard part of this feature, so the test replaces the
 * application's Clock with one it can move by hand: no sleeping, no flakiness.
 */
class AnomalyIntegrationTest extends AbstractIntegrationTest {

    /** A clock that only moves when the test says so. */
    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A stand-in for the language model that counts how often it is asked. */
    static final class FakeExplainer implements AnomalyExplainer {
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean enabled = false;

        @Override
        public Optional<String> explain(AnomalyFacts facts) {
            calls.incrementAndGet();
            return enabled ? Optional.of("AI note for " + facts.riderName()) : Optional.empty();
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        FakeExplainer fakeExplainer() {
            return new FakeExplainer();
        }
    }

    @Autowired
    private MutableClock clock;
    @Autowired
    private FakeExplainer explainer;

    private User customer;
    private Rider rider;

    @BeforeEach
    void setUp() {
        customer = newCustomer("ann");
        // newRider() stamps the position with the real "now"; align it with the fake clock instead.
        rider = riders.save(new Rider("Quiet Rider", RiderStatus.IDLE,
                TIANANMEN.lat(), TIANANMEN.lng(), clock.instant()));
        explainer.calls.set(0);
        explainer.enabled = false;
    }

    @Test
    void freshRider_isNotAnomalous() throws Exception {
        long id = activeDelivery();

        mvcAnomaly(id).andExpect(jsonPath("$.detected").value(false));

        clock.advance(Duration.ofSeconds(14)); // just under the 15 s threshold
        mvcAnomaly(id).andExpect(jsonPath("$.detected").value(false));
    }

    @Test
    void silentRider_isReported_withTheRuleBasedFallbackText() throws Exception {
        long id = activeDelivery();

        clock.advance(Duration.ofSeconds(20));

        mvcAnomaly(id)
                .andExpect(jsonPath("$.detected").value(true))
                .andExpect(jsonPath("$.type").value("RIDER_STALLED"))
                .andExpect(jsonPath("$.staleSeconds").value(20))
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Quiet Rider")));
    }

    @Test
    void aNewPositionReport_clearsTheAnomaly() throws Exception {
        long id = activeDelivery();
        clock.advance(Duration.ofSeconds(30));
        mvcAnomaly(id).andExpect(jsonPath("$.detected").value(true));

        mvc.perform(patch("/api/riders/{id}/location", rider.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"lat\": 39.91, \"lng\": 116.40}"))
                .andExpect(status().isOk());

        mvcAnomaly(id).andExpect(jsonPath("$.detected").value(false));
    }

    @Test
    void deliveriesThatAreNotActive_areNeverAnomalous() throws Exception {
        long pending = createDelivery(customer, WANGFUJING, CBD);
        clock.advance(Duration.ofMinutes(10));

        mvcAnomaly(pending).andExpect(jsonPath("$.detected").value(false));

        long finished = activeDelivery();
        setStatus(finished, "IN_TRANSIT").andExpect(status().isOk());
        setStatus(finished, "DELIVERED").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));

        mvcAnomaly(finished).andExpect(jsonPath("$.detected").value(false));
    }

    @Test
    void modelNote_isUsedWhenAvailable_andCachedUntilTheSituationChanges() throws Exception {
        explainer.enabled = true;
        long id = activeDelivery();
        clock.advance(Duration.ofSeconds(20));

        mvcAnomaly(id)
                .andExpect(jsonPath("$.aiGenerated").value(true))
                .andExpect(jsonPath("$.message").value("AI note for Quiet Rider"));
        assertThat(explainer.calls).hasValue(1);

        // Polling again changes nothing: the cached note is served and the model is not asked again.
        clock.advance(Duration.ofSeconds(3));
        mvcAnomaly(id).andExpect(jsonPath("$.message").value("AI note for Quiet Rider"));
        mvcAnomaly(id).andExpect(jsonPath("$.message").value("AI note for Quiet Rider"));
        assertThat(explainer.calls).hasValue(1);

        // The rider reports once and goes quiet again: a new situation, so a new note.
        mvc.perform(patch("/api/riders/{id}/location", rider.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"lat\": 39.91, \"lng\": 116.40}"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(20));
        mvcAnomaly(id).andExpect(jsonPath("$.aiGenerated").value(true));
        assertThat(explainer.calls).hasValue(2);
    }

    @Test
    void unknownDelivery_is404() throws Exception {
        mvcAnomaly(999999).andExpect(status().isNotFound());
    }

    /** A delivery that is ASSIGNED to {@link #rider}. */
    private long activeDelivery() throws Exception {
        long id = createDelivery(customer, WANGFUJING, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());
        return id;
    }

    private ResultActions mvcAnomaly(long id) throws Exception {
        return mvc.perform(get("/api/deliveries/{id}/anomaly", id));
    }
}
