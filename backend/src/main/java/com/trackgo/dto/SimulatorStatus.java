package com.trackgo.dto;

/**
 * @param enabled  whether the background scheduler exists (the {@code trackgo.simulator.enabled} property)
 * @param paused   runtime switch toggled by the pause/resume endpoints
 * @param speedKmh simulated rider speed
 * @param tickMs   milliseconds between two simulation steps
 */
public record SimulatorStatus(boolean enabled, boolean paused, double speedKmh, long tickMs) {
}
