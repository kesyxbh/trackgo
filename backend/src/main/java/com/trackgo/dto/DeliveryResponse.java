package com.trackgo.dto;

import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import java.time.Instant;

/**
 * What clients poll: the delivery status, both trip endpoints, and the assigned rider with their
 * last known position. {@code rider} is null until someone is assigned.
 */
public record DeliveryResponse(
        Long id,
        DeliveryStatus status,
        String pickupAddr,
        Double pickupLat,
        Double pickupLng,
        String dropoffAddr,
        Double dropoffLat,
        Double dropoffLng,
        CustomerSummary customer,
        RiderResponse rider,
        Instant createdAt,
        Instant updatedAt) {

    public static DeliveryResponse from(Delivery d) {
        return new DeliveryResponse(
                d.getId(),
                d.getStatus(),
                d.getPickupAddr(),
                d.getPickupLat(),
                d.getPickupLng(),
                d.getDropoffAddr(),
                d.getDropoffLat(),
                d.getDropoffLng(),
                CustomerSummary.from(d.getCustomer()),
                d.getRider() == null ? null : RiderResponse.from(d.getRider()),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
