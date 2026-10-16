package com.trackgo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import com.trackgo.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drives the delivery life cycle through the real HTTP API against the H2 database. */
class DeliveryFlowIntegrationTest extends AbstractIntegrationTest {

    private User customer;
    private Rider idleRider;
    private Rider offlineRider;

    @BeforeEach
    void setUp() {
        customer = newCustomer("carol");
        idleRider = newRider("Test Rider", RiderStatus.IDLE, TIANANMEN);
        offlineRider = newRider("Sleeping Rider", RiderStatus.OFFLINE, WANGFUJING);
    }

    @Test
    void happyPath_fromOrderToDelivered() throws Exception {
        long id = createDelivery(customer, WANGFUJING, CBD);

        mvc.perform(get("/api/deliveries/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customer.username").value("carol"))
                .andExpect(jsonPath("$.pickupLat").value(WANGFUJING.lat()))
                .andExpect(jsonPath("$.dropoffLng").value(CBD.lng()))
                .andExpect(jsonPath("$.rider").value(nullValue()));

        assign(id, idleRider.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.rider.id").value(idleRider.getId()))
                .andExpect(jsonPath("$.rider.status").value("BUSY"));

        mvc.perform(patch("/api/riders/{id}/location", idleRider.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"lat\": 31.2304, \"lng\": 121.4737}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(31.2304))
                .andExpect(jsonPath("$.locationUpdatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("BUSY")); // the location update must not undo BUSY

        // What a polling client sees: status plus the rider's latest position.
        mvc.perform(get("/api/deliveries/{id}", id))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.rider.lat").value(31.2304))
                .andExpect(jsonPath("$.rider.lng").value(121.4737));

        setStatus(id, "IN_TRANSIT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        setStatus(id, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.rider.status").value("IDLE"));

        mvc.perform(get("/api/riders").param("status", "IDLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(idleRider.getId().intValue())));
    }

    @Test
    void create_rejectsBlankAddress_withFieldErrors() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"customerId": %d, "pickupAddr": "   ", "pickupLat": 39.9, "pickupLng": 116.4,
                                 "dropoffAddr": "2 Dropoff Ave", "dropoffLat": 39.9, "dropoffLng": 116.5}
                                """.formatted(customer.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.pickupAddr").exists());
    }

    @Test
    void create_rejectsMissingAndOutOfRangeCoordinates() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"customerId": %d, "pickupAddr": "A", "pickupLat": 95.0, "pickupLng": 116.4,
                                 "dropoffAddr": "B", "dropoffLat": 39.9}
                                """.formatted(customer.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pickupLat").exists())   // 95 is not a latitude
                .andExpect(jsonPath("$.errors.dropoffLng").exists()); // missing entirely
    }

    @Test
    void create_unknownCustomer_is404() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(999999L, WANGFUJING, CBD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unknownDelivery_is404() throws Exception {
        mvc.perform(get("/api/deliveries/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void assign_rejectsBusyOfflineAndAlreadyAssigned() throws Exception {
        long first = createDelivery(customer, WANGFUJING, CBD);
        long second = createDelivery(customer, WANGFUJING, CBD);
        assign(first, idleRider.getId()).andExpect(status().isOk());

        assign(second, idleRider.getId()).andExpect(status().isConflict());    // rider is now BUSY
        assign(second, offlineRider.getId()).andExpect(status().isConflict()); // rider is OFFLINE
        assign(first, idleRider.getId()).andExpect(status().isConflict());     // delivery no longer PENDING
        assign(second, 999999).andExpect(status().isNotFound());               // unknown rider

        mvc.perform(post("/api/deliveries/{id}/assign", second)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_rejectsIllegalTransitions() throws Exception {
        long id = createDelivery(customer, WANGFUJING, CBD);

        setStatus(id, "DELIVERED").andExpect(status().isConflict());  // skips two steps
        setStatus(id, "ASSIGNED").andExpect(status().isConflict());   // must go through /assign

        // Not a status at all: 400, and the message lists what is allowed.
        setStatus(id, "FLYING")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("allowed values")))
                .andExpect(jsonPath("$.detail").value(containsString("IN_TRANSIT")));

        assign(id, idleRider.getId()).andExpect(status().isOk());
        setStatus(id, "DELIVERED").andExpect(status().isConflict());  // must be IN_TRANSIT first

        setStatus(id, "IN_TRANSIT").andExpect(status().isOk());
        setStatus(id, "DELIVERED").andExpect(status().isOk());
        setStatus(id, "IN_TRANSIT").andExpect(status().isConflict()); // terminal state
    }

    @Test
    void updateLocation_validatesRangeAndRider() throws Exception {
        mvc.perform(patch("/api/riders/{id}/location", idleRider.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"lat\": 91.0, \"lng\": 116.4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lat").exists());

        mvc.perform(patch("/api/riders/{id}/location", idleRider.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"lat\": 39.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lng").exists());

        mvc.perform(patch("/api/riders/{id}/location", 999999)
                        .contentType(APPLICATION_JSON)
                        .content("{\"lat\": 39.9, \"lng\": 116.4}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filtersByStatusAndRider_newestFirst() throws Exception {
        long first = createDelivery(customer, WANGFUJING, CBD);
        long second = createDelivery(customer, WANGFUJING, CBD);
        assign(first, idleRider.getId()).andExpect(status().isOk());

        mvc.perform(get("/api/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second));

        mvc.perform(get("/api/deliveries").param("status", "PENDING"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second));

        mvc.perform(get("/api/deliveries").param("riderId", idleRider.getId().toString()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(first));

        mvc.perform(get("/api/deliveries").param("customerId", customer.getId().toString()))
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/deliveries").param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tracking_etaIsUnknownUntilARiderIsAssigned_thenFollowsTheRoute() throws Exception {
        long id = createDelivery(customer, WANGFUJING, CBD);

        // PENDING: nobody is coming yet, so there is nothing to estimate.
        mvc.perform(get("/api/deliveries/{id}/tracking", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("PENDING"))
                .andExpect(jsonPath("$.remainingKm").value(nullValue()))
                .andExpect(jsonPath("$.etaMinutes").value(nullValue()));

        // ASSIGNED: rider -> pickup, then pickup -> dropoff.
        assign(id, idleRider.getId()).andExpect(status().isOk());
        double expectedKm = GeoUtils.haversineKm(TIANANMEN, WANGFUJING) + GeoUtils.haversineKm(WANGFUJING, CBD);
        String body = mvc.perform(get("/api/deliveries/{id}/tracking", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingKm").value(org.hamcrest.Matchers.closeTo(expectedKm, 0.001)))
                .andReturn().getResponse().getContentAsString();
        double speed = ((Number) JsonPath.read(body, "$.assumedSpeedKmh")).doubleValue();
        double minutes = ((Number) JsonPath.read(body, "$.etaMinutes")).doubleValue();
        org.assertj.core.api.Assertions.assertThat(minutes)
                .isCloseTo(expectedKm / speed * 60.0, org.assertj.core.api.Assertions.within(0.001));

        // DELIVERED: nothing left to travel.
        setStatus(id, "IN_TRANSIT").andExpect(status().isOk());
        setStatus(id, "DELIVERED").andExpect(status().isOk());
        mvc.perform(get("/api/deliveries/{id}/tracking", id))
                .andExpect(jsonPath("$.remainingKm").value(0.0))
                .andExpect(jsonPath("$.etaMinutes").value(0.0));

        mvc.perform(get("/api/deliveries/{id}/tracking", 999999))
                .andExpect(status().isNotFound());
    }
}
