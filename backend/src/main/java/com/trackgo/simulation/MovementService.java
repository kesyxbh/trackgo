package com.trackgo.simulation;

import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.entity.Rider;
import com.trackgo.geo.GeoPoint;
import com.trackgo.geo.GeoUtils;
import com.trackgo.repository.DeliveryRepository;
import com.trackgo.repository.RiderRepository;
import com.trackgo.service.DeliveryService;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves one rider one step along their current delivery. It is a separate bean from
 * {@link SimulationService} on purpose: {@code @Transactional} only works when a method is called
 * <em>through</em> the Spring proxy, so the per-delivery work lives here and the loop that calls it
 * lives next door.
 */
@Service
public class MovementService {

    private final DeliveryRepository deliveries;
    private final RiderRepository riders;
    private final DeliveryService deliveryService;
    private final Clock clock;

    public MovementService(DeliveryRepository deliveries, RiderRepository riders,
                           DeliveryService deliveryService, Clock clock) {
        this.deliveries = deliveries;
        this.riders = riders;
        this.deliveryService = deliveryService;
        this.clock = clock;
    }

    /**
     * Advances the rider of {@code deliveryId} by at most {@code stepKm} towards the current target:
     * the pickup point while the delivery is ASSIGNED, the drop-off point while it is IN_TRANSIT.
     * Arriving flips the delivery status, using the very same rules as a manual update.
     */
    @Transactional
    public MoveResult step(Long deliveryId, double stepKm, double arrivalThresholdKm) {
        // Same lock order as DeliveryService (delivery first, rider second), so a simulation step
        // and a dispatcher's manual action on the same delivery queue up instead of deadlocking.
        Delivery delivery = deliveries.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null) {
            return MoveResult.SKIPPED;
        }
        DeliveryStatus status = delivery.getStatus();
        boolean active = status == DeliveryStatus.ASSIGNED || status == DeliveryStatus.IN_TRANSIT;
        if (!active || delivery.getRider() == null || !delivery.hasCoordinates()) {
            return MoveResult.SKIPPED;
        }

        Rider rider = riders.findByIdForUpdate(delivery.getRider().getId()).orElse(null);
        if (rider == null) {
            return MoveResult.SKIPPED;
        }

        GeoPoint target = status == DeliveryStatus.ASSIGNED ? delivery.pickupPoint() : delivery.dropoffPoint();
        // A rider who never reported a position starts at the target (they are simply "already there").
        GeoPoint from = rider.position() != null ? rider.position() : target;
        GeoPoint next = GeoUtils.moveToward(from, target, stepKm);
        rider.updateLocation(next.lat(), next.lng(), clock.instant());

        if (GeoUtils.haversineKm(next, target) > arrivalThresholdKm) {
            return MoveResult.MOVED;
        }

        // Arrived. Go through DeliveryService so there is exactly one place that knows the state machine.
        if (status == DeliveryStatus.ASSIGNED) {
            deliveryService.updateStatus(deliveryId, DeliveryStatus.IN_TRANSIT);
            return MoveResult.PICKED_UP;
        }
        deliveryService.updateStatus(deliveryId, DeliveryStatus.DELIVERED);
        return MoveResult.DELIVERED;
    }
}
