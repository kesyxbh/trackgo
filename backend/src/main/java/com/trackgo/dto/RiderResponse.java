package com.trackgo.dto;

import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import java.time.Instant;

public record RiderResponse(Long id, String name, RiderStatus status, Double lat, Double lng,
                            Instant locationUpdatedAt) {

    public static RiderResponse from(Rider rider) {
        return new RiderResponse(rider.getId(), rider.getName(), rider.getStatus(),
                rider.getCurrentLat(), rider.getCurrentLng(), rider.getLocationUpdatedAt());
    }
}
