package com.trackgo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/deliveries}. Every end of the trip needs an address (for people) and a
 * coordinate (for dispatching, the map and the simulator).
 */
public record DeliveryCreateRequest(
        @NotNull Long customerId,
        @NotBlank @Size(max = 255) String pickupAddr,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double pickupLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLng,
        @NotBlank @Size(max = 255) String dropoffAddr,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double dropoffLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double dropoffLng) {
}
