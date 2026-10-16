package com.trackgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of automatic dispatching ({@code trackgo.dispatch.*}).
 *
 * @param enabled whether the background scheduler assigns pending deliveries on its own.
 *                The manual "auto-assign" endpoint works regardless of this flag.
 * @param retryMs pause between two dispatch rounds, in milliseconds
 */
@ConfigurationProperties(prefix = "trackgo.dispatch")
public record DispatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3000") long retryMs) {
}
