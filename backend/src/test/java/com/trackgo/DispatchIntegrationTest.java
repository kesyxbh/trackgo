package com.trackgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trackgo.dispatch.DispatchService;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Nearest-idle-rider dispatching, both through the HTTP endpoint and through the service directly. */
class DispatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DispatchService dispatch;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = newCustomer("dina");
    }

    @Test
    void autoAssign_choosesTheNearestIdleRider() throws Exception {
        // The pickup is at Tiananmen. Candidates, by distance from it:
        //   busyNearest   0 km   but BUSY          -> not eligible
        //   offlineNear   0 km   but OFFLINE       -> not eligible
        //   near          1.3 km IDLE              -> the winner
        //   far          11 km   IDLE              -> eligible, but farther
        newRider("busyNearest", RiderStatus.BUSY, TIANANMEN);
        newRider("offlineNear", RiderStatus.OFFLINE, TIANANMEN);
        Rider near = newRider("near", RiderStatus.IDLE, WANGFUJING);
        newRider("far", RiderStatus.IDLE, ZHONGGUANCUN);
        long id = createDelivery(customer, TIANANMEN, CBD);

        mvc.perform(post("/api/deliveries/{id}/auto-assign", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.rider.id").value(near.getId()))
                .andExpect(jsonPath("$.rider.status").value("BUSY"));
    }

    @Test
    void autoAssign_withoutAnyIdleRider_is409_andTheDeliveryStaysPending() throws Exception {
        newRider("busy", RiderStatus.BUSY, TIANANMEN);
        long id = createDelivery(customer, TIANANMEN, CBD);

        mvc.perform(post("/api/deliveries/{id}/auto-assign", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("No idle rider")));

        mvc.perform(get("/api/deliveries/{id}", id))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void autoAssign_ignoresIdleRidersWhosePositionIsUnknown() throws Exception {
        newRider("lost", RiderStatus.IDLE, null); // idle, but never reported a position
        long id = createDelivery(customer, TIANANMEN, CBD);

        mvc.perform(post("/api/deliveries/{id}/auto-assign", id))
                .andExpect(status().isConflict());
    }

    @Test
    void autoAssign_rejectsADeliveryThatIsNotPending() throws Exception {
        Rider rider = newRider("r", RiderStatus.IDLE, TIANANMEN);
        long id = createDelivery(customer, TIANANMEN, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());

        mvc.perform(post("/api/deliveries/{id}/auto-assign", id))
                .andExpect(status().isConflict());
    }

    @Test
    void autoAssign_unknownDelivery_is404() throws Exception {
        mvc.perform(post("/api/deliveries/{id}/auto-assign", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void dispatchPending_servesOldestFirst_andStopsWhenRidersRunOut() throws Exception {
        newRider("r1", RiderStatus.IDLE, TIANANMEN);
        newRider("r2", RiderStatus.IDLE, WANGFUJING);
        long oldest = createDelivery(customer, TIANANMEN, CBD);
        long middle = createDelivery(customer, TIANANMEN, CBD);
        long newest = createDelivery(customer, TIANANMEN, CBD);

        int assigned = dispatch.dispatchPending();

        assertThat(assigned).isEqualTo(2);
        assertThat(statusOf(oldest)).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(statusOf(middle)).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(statusOf(newest)).isEqualTo(DeliveryStatus.PENDING);

        // A second round has nobody left to hand out.
        assertThat(dispatch.dispatchPending()).isZero();
    }

    @Test
    void dispatchPending_pickedUpLaterWhenARiderBecomesIdle() throws Exception {
        Rider rider = newRider("solo", RiderStatus.IDLE, TIANANMEN);
        long first = createDelivery(customer, TIANANMEN, WANGFUJING);
        long second = createDelivery(customer, TIANANMEN, WANGFUJING);

        assertThat(dispatch.dispatchPending()).isEqualTo(1); // only one rider: the first order gets them
        assertThat(statusOf(second)).isEqualTo(DeliveryStatus.PENDING);

        // The rider finishes the first delivery and becomes idle again...
        setStatus(first, "IN_TRANSIT").andExpect(status().isOk());
        setStatus(first, "DELIVERED").andExpect(status().isOk());

        // ...and the next dispatch round hands them the waiting order.
        assertThat(dispatch.dispatchPending()).isEqualTo(1);
        assertThat(statusOf(second)).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(deliveries.findById(second).orElseThrow().getRider().getId()).isEqualTo(rider.getId());
    }

    @Test
    void tryAutoAssign_neverThrows() throws Exception {
        long id = createDelivery(customer, TIANANMEN, CBD);

        assertThat(dispatch.tryAutoAssign(id)).isFalse();       // no riders at all
        assertThat(dispatch.tryAutoAssign(999999L)).isFalse();  // unknown delivery
    }

    private DeliveryStatus statusOf(long deliveryId) {
        return deliveries.findById(deliveryId).orElseThrow().getStatus();
    }
}
