package com.trackgo.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduler that drives {@link SimulationService}. Kept tiny on purpose: all logic is in the
 * service, this class only supplies the heartbeat. Disable it with
 * {@code trackgo.simulator.enabled=false} (the tests do).
 */
@Component
@ConditionalOnProperty(prefix = "trackgo.simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RiderSimulator {

    private static final Logger log = LoggerFactory.getLogger(RiderSimulator.class);

    private final SimulationService simulation;

    public RiderSimulator(SimulationService simulation) {
        this.simulation = simulation;
    }

    @Scheduled(fixedDelayString = "${trackgo.simulator.tick-ms:3000}")
    public void heartbeat() {
        try {
            TickSummary summary = simulation.tick();
            if (!summary.isEmpty()) {
                log.debug("Simulation tick: {}", summary);
            }
        } catch (RuntimeException e) {
            log.warn("Simulation tick failed", e);
        }
    }
}
