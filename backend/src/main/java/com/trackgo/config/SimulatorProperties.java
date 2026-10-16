package com.trackgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the rider movement simulator ({@code trackgo.simulator.*}).
 *
 * @param enabled            whether the background scheduler runs at all
 * @param tickMs             pause between two simulation steps, in milliseconds
 * @param speedKmh           simulated rider speed; also used by the ETA estimate so both agree
 * @param arrivalThresholdKm a rider closer than this to the target counts as having arrived
 */
@ConfigurationProperties(prefix = "trackgo.simulator")
public record SimulatorProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3000") long tickMs,
        @DefaultValue("120") double speedKmh,
        @DefaultValue("0.02") double arrivalThresholdKm) {

    /** Distance a rider covers during one tick at the configured speed. */
    public double stepKm() {
        return speedKmh * tickMs / 3_600_000.0;
    }
}
