package com.trackgo.service;

import com.trackgo.config.SimulatorProperties;
import com.trackgo.entity.Delivery;
import com.trackgo.geo.GeoPoint;
import com.trackgo.geo.GeoUtils;
import org.springframework.stereotype.Service;

/**
 * Estimates how long a delivery still needs. It is deliberately simple: straight-line distance
 * divided by one constant speed. The speed is the simulator's speed, so the estimate and the moving
 * dot on the map agree with each other.
 */
@Service
public class EtaService {

    private final SimulatorProperties simulator;

    public EtaService(SimulatorProperties simulator) {
        this.simulator = simulator;
    }

    /** Needs {@code delivery.getRider()} to be loaded, so call it inside a transaction or on a fetched entity. */
    public EtaEstimate estimate(Delivery delivery) {
        double speed = simulator.speedKmh();
        Double remaining = remainingKm(delivery);
        Double minutes = remaining == null ? null : remaining / speed * 60.0;
        return new EtaEstimate(remaining, minutes, speed);
    }

    private Double remainingKm(Delivery delivery) {
        GeoPoint pickup = delivery.pickupPoint();
        GeoPoint dropoff = delivery.dropoffPoint();
        GeoPoint rider = delivery.getRider() == null ? null : delivery.getRider().position();

        return switch (delivery.getStatus()) {
            case DELIVERED -> 0.0;
            // No rider yet, so there is nothing meaningful to estimate.
            case PENDING -> null;
            // Rider still has to reach the pickup, then drive the whole trip.
            case ASSIGNED -> rider == null || pickup == null || dropoff == null
                    ? null
                    : GeoUtils.haversineKm(rider, pickup) + GeoUtils.haversineKm(pickup, dropoff);
            // Parcel is on board: only the way to the drop-off is left.
            case IN_TRANSIT -> rider == null || dropoff == null
                    ? null
                    : GeoUtils.haversineKm(rider, dropoff);
        };
    }
}
