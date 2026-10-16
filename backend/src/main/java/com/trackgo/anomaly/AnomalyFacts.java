package com.trackgo.anomaly;

import com.trackgo.entity.DeliveryStatus;

/** The plain facts about a stalled delivery that an explainer may turn into a customer-friendly note. */
public record AnomalyFacts(
        Long deliveryId,
        DeliveryStatus status,
        String riderName,
        long staleSeconds,
        Double remainingKm,
        String pickupAddr,
        String dropoffAddr) {
}
