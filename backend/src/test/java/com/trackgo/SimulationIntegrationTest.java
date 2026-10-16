package com.trackgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import com.trackgo.geo.GeoUtils;
import com.trackgo.simulation.MoveResult;
import com.trackgo.simulation.MovementService;
import com.trackgo.simulation.SimulationService;
import com.trackgo.simulation.TickSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The simulator is tested without its scheduler: a test calls {@code tick()} in a loop, which is
 * exactly what the scheduler would do every few seconds, only instantly.
 */
class SimulationIntegrationTest extends AbstractIntegrationTest {

    private static final int MAX_TICKS = 200;

    @Autowired
    private SimulationService simulation;
    @Autowired
    private MovementService movement;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = newCustomer("sam");
    }

    @AfterEach
    void resume() {
        simulation.resume(); // the service is a singleton shared by all tests in this context
    }

    @Test
    void ridesToPickupThenToDropoff_andFreesTheRider() throws Exception {
        Rider rider = newRider("sim", RiderStatus.IDLE, TIANANMEN);
        long id = createDelivery(customer, WANGFUJING, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());

        int pickedUp = 0;
        int delivered = 0;
        int ticks = 0;
        double previousDistanceToCurrentTarget = Double.MAX_VALUE;
        DeliveryStatus previousStatus = DeliveryStatus.ASSIGNED;

        while (statusOf(id) != DeliveryStatus.DELIVERED && ticks++ < MAX_TICKS) {
            TickSummary summary = simulation.tick();
            pickedUp += summary.pickedUp();
            delivered += summary.delivered();

            DeliveryStatus now = statusOf(id);
            Delivery delivery = deliveries.findById(id).orElseThrow();
            Rider current = riders.findById(rider.getId()).orElseThrow();
            if (now == previousStatus && now != DeliveryStatus.DELIVERED) {
                // Between status changes the rider must keep getting closer to the current target.
                var target = now == DeliveryStatus.ASSIGNED ? delivery.pickupPoint() : delivery.dropoffPoint();
                double distance = GeoUtils.haversineKm(current.position(), target);
                assertThat(distance).isLessThanOrEqualTo(previousDistanceToCurrentTarget);
                previousDistanceToCurrentTarget = distance;
            } else {
                previousDistanceToCurrentTarget = Double.MAX_VALUE; // a new leg starts
            }
            previousStatus = now;
        }

        assertThat(statusOf(id)).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(pickedUp).as("picked up exactly once").isEqualTo(1);
        assertThat(delivered).as("delivered exactly once").isEqualTo(1);

        // The rider ended exactly at the drop-off point and is available again.
        Rider after = riders.findById(rider.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RiderStatus.IDLE);
        assertThat(after.getCurrentLat()).isCloseTo(CBD.lat(), within(1e-9));
        assertThat(after.getCurrentLng()).isCloseTo(CBD.lng(), within(1e-9));
    }

    @Test
    void doesNothingWhilePaused_andContinuesAfterResume() throws Exception {
        Rider rider = newRider("sim", RiderStatus.IDLE, TIANANMEN);
        long id = createDelivery(customer, WANGFUJING, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());

        simulation.pause();
        assertThat(simulation.status().paused()).isTrue();
        TickSummary whilePaused = simulation.tick();
        assertThat(whilePaused.isEmpty()).isTrue();
        Rider frozen = riders.findById(rider.getId()).orElseThrow();
        assertThat(frozen.getCurrentLat()).isEqualTo(TIANANMEN.lat());
        assertThat(frozen.getCurrentLng()).isEqualTo(TIANANMEN.lng());

        simulation.resume();
        assertThat(simulation.tick().moved()).isEqualTo(1);
        Rider moving = riders.findById(rider.getId()).orElseThrow();
        assertThat(moving.getCurrentLng()).isNotEqualTo(TIANANMEN.lng());
    }

    @Test
    void coexistsWithAHumanWhoAdvancesTheSameDelivery() throws Exception {
        Rider rider = newRider("sim", RiderStatus.IDLE, TIANANMEN);
        long id = createDelivery(customer, WANGFUJING, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());

        // A rider presses "picked up" in the app while the simulator is still driving to the pickup.
        setStatus(id, "IN_TRANSIT").andExpect(status().isOk());

        // The simulator simply re-reads the delivery and now heads for the drop-off.
        int ticks = 0;
        while (statusOf(id) != DeliveryStatus.DELIVERED && ticks++ < MAX_TICKS) {
            simulation.tick();
        }
        assertThat(statusOf(id)).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(riders.findById(rider.getId()).orElseThrow().getStatus()).isEqualTo(RiderStatus.IDLE);
    }

    @Test
    void ignoresDeliveriesThatAreNotActive() throws Exception {
        newRider("idle", RiderStatus.IDLE, TIANANMEN);
        long pending = createDelivery(customer, WANGFUJING, CBD); // PENDING: no rider, nothing to move

        assertThat(simulation.tick().isEmpty()).isTrue();
        assertThat(movement.step(pending, 0.5, 0.02)).isEqualTo(MoveResult.SKIPPED);
        assertThat(movement.step(999999L, 0.5, 0.02)).isEqualTo(MoveResult.SKIPPED);
    }

    @Test
    void positionReportsCarryATimestamp() throws Exception {
        Rider rider = newRider("sim", RiderStatus.IDLE, TIANANMEN);
        long id = createDelivery(customer, WANGFUJING, CBD);
        assign(id, rider.getId()).andExpect(status().isOk());
        var before = riders.findById(rider.getId()).orElseThrow().getLocationUpdatedAt();

        simulation.tick();

        var after = riders.findById(rider.getId()).orElseThrow().getLocationUpdatedAt();
        assertThat(after).isAfterOrEqualTo(before);
    }

    private DeliveryStatus statusOf(long deliveryId) {
        return deliveries.findById(deliveryId).orElseThrow().getStatus();
    }
}
