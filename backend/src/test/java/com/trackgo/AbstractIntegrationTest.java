package com.trackgo;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import com.trackgo.geo.GeoPoint;
import com.trackgo.repository.DeliveryRepository;
import com.trackgo.repository.RiderRepository;
import com.trackgo.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared plumbing for integration tests: the autowired collaborators, a few real Beijing
 * landmarks to build trips from, small data builders, and cleanup after every test.
 *
 * <p>The tests are deliberately <em>not</em> {@code @Transactional}: every request commits in its
 * own transaction, exactly like production, so lazy-loading and locking problems show up here
 * instead of on the first real call.
 */
@IntegrationTest
public abstract class AbstractIntegrationTest {

    // Real coordinates, so distances in the tests are plausible (and easy to sanity-check on a map).
    protected static final GeoPoint TIANANMEN = new GeoPoint(39.9087, 116.3975);
    protected static final GeoPoint WANGFUJING = new GeoPoint(39.9143, 116.4107);   // ~1.3 km from Tiananmen
    protected static final GeoPoint CBD = new GeoPoint(39.9087, 116.4605);          // ~5.4 km from Tiananmen
    protected static final GeoPoint ZHONGGUANCUN = new GeoPoint(39.9836, 116.3105); // ~11 km from Tiananmen

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected RiderRepository riders;
    @Autowired
    protected DeliveryRepository deliveries;

    @AfterEach
    void cleanDatabase() {
        // Children first: deliveries reference users and riders.
        deliveries.deleteAll();
        riders.deleteAll();
        users.deleteAll();
    }

    protected User newCustomer(String name) {
        return users.save(new User(name, name + "@example.com"));
    }

    protected Rider newRider(String name, RiderStatus status, GeoPoint at) {
        return riders.save(new Rider(name, status, at == null ? null : at.lat(), at == null ? null : at.lng(),
                at == null ? null : Instant.now()));
    }

    protected static String createJson(Long customerId, GeoPoint pickup, GeoPoint dropoff) {
        return """
                {"customerId": %d,
                 "pickupAddr": "1 Pickup St", "pickupLat": %s, "pickupLng": %s,
                 "dropoffAddr": "2 Dropoff Ave", "dropoffLat": %s, "dropoffLng": %s}
                """.formatted(customerId, pickup.lat(), pickup.lng(), dropoff.lat(), dropoff.lng());
    }

    /** Creates a delivery through the real HTTP API and returns its id. */
    protected long createDelivery(User customer, GeoPoint pickup, GeoPoint dropoff) throws Exception {
        MvcResult result = mvc.perform(post("/api/deliveries")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(customer.getId(), pickup, dropoff)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    protected ResultActions assign(long deliveryId, long riderId) throws Exception {
        return mvc.perform(post("/api/deliveries/{id}/assign", deliveryId)
                .contentType(APPLICATION_JSON)
                .content("{\"riderId\": " + riderId + "}"));
    }

    protected ResultActions setStatus(long deliveryId, String status) throws Exception {
        return mvc.perform(patch("/api/deliveries/{id}/status", deliveryId)
                .contentType(APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}"));
    }
}
