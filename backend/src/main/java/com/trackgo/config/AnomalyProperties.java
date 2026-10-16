package com.trackgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the stalled-rider detection ({@code trackgo.anomaly.*}).
 *
 * @param staleSeconds how long an active delivery's rider may go without a position update
 *                     before the delivery is reported as stalled
 */
@ConfigurationProperties(prefix = "trackgo.anomaly")
public record AnomalyProperties(@DefaultValue("15") long staleSeconds) {
}
