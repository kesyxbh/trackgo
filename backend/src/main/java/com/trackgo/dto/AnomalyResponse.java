package com.trackgo.dto;

import java.time.Instant;

/**
 * Result of the stalled-rider check for one delivery.
 *
 * @param detected      whether something is wrong right now
 * @param type          machine-readable kind ({@code RIDER_STALLED}), null when nothing is wrong
 * @param staleSeconds  seconds since the rider last reported a position, null when nothing is wrong
 * @param lastLocationAt when the rider last reported a position
 * @param message       text for the customer, null when nothing is wrong
 * @param aiGenerated   true when {@code message} was written by the language model, false when it
 *                      is the fixed rule-based fallback text
 */
public record AnomalyResponse(
        boolean detected,
        String type,
        Long staleSeconds,
        Instant lastLocationAt,
        String message,
        boolean aiGenerated) {

    public static AnomalyResponse none() {
        return new AnomalyResponse(false, null, null, null, null, false);
    }
}
