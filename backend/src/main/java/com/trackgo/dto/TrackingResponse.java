package com.trackgo.dto;

/**
 * Everything the tracking page needs in one poll: the delivery itself plus the arrival estimate.
 * {@code remainingKm} and {@code etaMinutes} are null while the answer is unknown (for example a
 * delivery that has no rider yet).
 */
public record TrackingResponse(
        DeliveryResponse delivery,
        Double remainingKm,
        Double etaMinutes,
        double assumedSpeedKmh) {
}
