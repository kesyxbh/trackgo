package com.trackgo.simulation;

import static com.trackgo.repository.DeliverySpecifications.hasAnyStatus;

import com.trackgo.config.SimulatorProperties;
import com.trackgo.dto.SimulatorStatus;
import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.repository.DeliveryRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * The rider simulator's brain: one {@link #tick()} moves every rider that is on an active delivery
 * one step. It holds the runtime pause switch too, so the UI can freeze all riders to demonstrate
 * the stalled-rider alert.
 *
 * <p>{@code tick()} is a plain method (no scheduler involved), which is what makes the whole
 * delivery journey testable in milliseconds: a test just calls it in a loop.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final DeliveryRepository deliveries;
    private final MovementService movement;
    private final SimulatorProperties properties;
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public SimulationService(DeliveryRepository deliveries, MovementService movement, SimulatorProperties properties) {
        this.deliveries = deliveries;
        this.movement = movement;
        this.properties = properties;
    }

    /**
     * Moves every rider with an active delivery one step; failures of one delivery do not stop the
     * others. Does nothing while the simulator is paused.
     */
    public TickSummary tick() {
        if (paused.get()) {
            return new TickSummary(0, 0, 0);
        }
        List<Delivery> active = deliveries.findAll(
                hasAnyStatus(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_TRANSIT),
                Sort.by(Sort.Direction.ASC, "id"));

        int moved = 0;
        int pickedUp = 0;
        int delivered = 0;
        for (Delivery delivery : active) {
            try {
                switch (movement.step(delivery.getId(), properties.stepKm(), properties.arrivalThresholdKm())) {
                    case MOVED -> moved++;
                    case PICKED_UP -> pickedUp++;
                    case DELIVERED -> delivered++;
                    case SKIPPED -> { /* nothing happened */ }
                }
            } catch (RuntimeException e) {
                // For example a dispatcher changed the same delivery a moment earlier (409).
                // The next tick re-reads the delivery and carries on.
                log.warn("Simulation step for delivery {} failed: {}", delivery.getId(), e.getMessage());
            }
        }
        return new TickSummary(moved, pickedUp, delivered);
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public SimulatorStatus status() {
        return new SimulatorStatus(properties.enabled(), paused.get(), properties.speedKmh(), properties.tickMs());
    }
}
