package com.trackgo.dispatch;

import static com.trackgo.repository.DeliverySpecifications.hasStatus;

import com.trackgo.dto.DeliveryResponse;
import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.exception.InvalidStateException;
import com.trackgo.exception.ResourceNotFoundException;
import com.trackgo.geo.GeoPoint;
import com.trackgo.geo.GeoUtils;
import com.trackgo.repository.DeliveryRepository;
import com.trackgo.repository.RiderRepository;
import com.trackgo.service.DeliveryService;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Automatic dispatching: picks the closest idle rider for a pending delivery.
 *
 * <p>This class only <em>chooses</em>. The actual assignment is delegated to
 * {@link DeliveryService#assign}, which takes the row locks and re-checks the rules. That means a
 * choice made from a slightly stale read can never produce a double booking: if somebody else got
 * the rider first, {@code assign} refuses and we simply try the next candidate.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}: every {@code assign} call must
 * commit or fail on its own. Wrapping the loop in one transaction would let the first refused
 * candidate mark the whole transaction rollback-only.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final DeliveryRepository deliveries;
    private final RiderRepository riders;
    private final DeliveryService deliveryService;

    public DispatchService(DeliveryRepository deliveries, RiderRepository riders, DeliveryService deliveryService) {
        this.deliveries = deliveries;
        this.riders = riders;
        this.deliveryService = deliveryService;
    }

    /**
     * Strict version used by the "auto-assign" endpoint: either the delivery ends up assigned, or
     * an exception explains why not.
     */
    public DeliveryResponse autoAssign(Long deliveryId) {
        Delivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery " + deliveryId + " not found"));
        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new InvalidStateException("Delivery " + deliveryId + " is " + delivery.getStatus()
                    + "; only PENDING deliveries can be assigned");
        }
        GeoPoint pickup = delivery.pickupPoint();
        if (pickup == null) {
            throw new InvalidStateException("Delivery " + deliveryId + " has no pickup coordinates");
        }

        for (Rider candidate : idleRidersNear(pickup)) {
            try {
                DeliveryResponse assigned = deliveryService.assign(deliveryId, candidate.getId());
                log.info("Delivery {} auto-assigned to rider {} ({})", deliveryId, candidate.getId(), candidate.getName());
                return assigned;
            } catch (InvalidStateException raced) {
                // Between our read and the row lock somebody else took this rider (or this delivery).
                if (!isStillPending(deliveryId)) {
                    throw raced;
                }
                log.debug("Rider {} was taken while dispatching delivery {}; trying the next one",
                        candidate.getId(), deliveryId);
            }
        }
        throw new InvalidStateException("No idle rider with a known position is available for delivery " + deliveryId);
    }

    /** Lenient version for background use: never throws, just reports whether it worked. */
    public boolean tryAutoAssign(Long deliveryId) {
        try {
            autoAssign(deliveryId);
            return true;
        } catch (InvalidStateException | ResourceNotFoundException notPossible) {
            return false;
        }
    }

    /**
     * One dispatch round: tries every pending delivery, oldest first.
     *
     * @return how many deliveries were assigned in this round
     */
    public int dispatchPending() {
        if (riders.findByStatusOrderByIdAsc(RiderStatus.IDLE).isEmpty()) {
            return 0; // cheap exit: nothing to hand out
        }
        List<Delivery> pending = deliveries.findAll(hasStatus(DeliveryStatus.PENDING),
                Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        int assigned = 0;
        for (Delivery delivery : pending) {
            if (tryAutoAssign(delivery.getId())) {
                assigned++;
            }
        }
        return assigned;
    }

    /** Idle riders that have a known position, nearest first (ties broken by id for stable results). */
    private List<Rider> idleRidersNear(GeoPoint point) {
        return riders.findByStatusOrderByIdAsc(RiderStatus.IDLE).stream()
                .filter(rider -> rider.position() != null)
                .sorted(Comparator
                        .comparingDouble((Rider rider) -> GeoUtils.haversineKm(rider.position(), point))
                        .thenComparing(Rider::getId))
                .toList();
    }

    private boolean isStillPending(Long deliveryId) {
        return deliveries.findById(deliveryId)
                .map(d -> d.getStatus() == DeliveryStatus.PENDING)
                .orElse(false);
    }
}
