package com.trackgo.service;

import static com.trackgo.repository.DeliverySpecifications.hasCustomer;
import static com.trackgo.repository.DeliverySpecifications.hasRider;
import static com.trackgo.repository.DeliverySpecifications.hasStatus;

import com.trackgo.dto.DeliveryCreateRequest;
import com.trackgo.dto.DeliveryResponse;
import com.trackgo.dto.TrackingResponse;
import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import com.trackgo.exception.InvalidStateException;
import com.trackgo.exception.ResourceNotFoundException;
import com.trackgo.repository.DeliveryRepository;
import com.trackgo.repository.RiderRepository;
import com.trackgo.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The business rules of a delivery's life: creating it, assigning a rider, moving it through its
 * status machine, and answering "where is it?". Every method that changes data runs in one
 * transaction, so a half-applied change is never visible.
 */
@Service
public class DeliveryService {

    private final DeliveryRepository deliveries;
    private final UserRepository users;
    private final RiderRepository riders;
    private final EtaService eta;

    public DeliveryService(DeliveryRepository deliveries, UserRepository users, RiderRepository riders,
                           EtaService eta) {
        this.deliveries = deliveries;
        this.users = users;
        this.riders = riders;
        this.eta = eta;
    }

    @Transactional
    public DeliveryResponse create(DeliveryCreateRequest request) {
        User customer = users.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + request.customerId() + " not found"));
        Delivery delivery = deliveries.save(new Delivery(
                customer,
                request.pickupAddr().trim(), request.pickupLat(), request.pickupLng(),
                request.dropoffAddr().trim(), request.dropoffLat(), request.dropoffLng()));
        return DeliveryResponse.from(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse get(Long id) {
        Delivery delivery = deliveries.findById(id)
                .orElseThrow(() -> deliveryNotFound(id));
        return DeliveryResponse.from(delivery);
    }

    /** The delivery plus its arrival estimate: what the tracking page polls. */
    @Transactional(readOnly = true)
    public TrackingResponse getTracking(Long id) {
        Delivery delivery = deliveries.findById(id)
                .orElseThrow(() -> deliveryNotFound(id));
        return toTracking(delivery);
    }

    /**
     * Same as {@link #getTracking} but only for the delivery's owner. A delivery that belongs to
     * somebody else is reported as "not found", exactly like one that does not exist, so the
     * answer does not even reveal which ids are taken. The AI tools use this method.
     */
    @Transactional(readOnly = true)
    public TrackingResponse getTrackingForCustomer(Long customerId, Long deliveryId) {
        Delivery delivery = deliveries.findById(deliveryId)
                .filter(d -> d.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> deliveryNotFound(deliveryId));
        return toTracking(delivery);
    }

    /** All filters are optional; newest deliveries first. */
    @Transactional(readOnly = true)
    public List<DeliveryResponse> search(Long customerId, Long riderId, DeliveryStatus status) {
        Specification<Delivery> spec = (root, query, cb) -> cb.conjunction();
        if (customerId != null) {
            spec = spec.and(hasCustomer(customerId));
        }
        if (riderId != null) {
            spec = spec.and(hasRider(riderId));
        }
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        return deliveries.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt", "id")).stream()
                .map(DeliveryResponse::from)
                .toList();
    }

    /**
     * Dispatch: PENDING -> ASSIGNED, and the rider becomes BUSY.
     * Rows are locked delivery-first, rider-second everywhere in this class, so two dispatchers
     * can neither double-book a rider nor deadlock each other.
     */
    @Transactional
    public DeliveryResponse assign(Long deliveryId, Long riderId) {
        Delivery delivery = deliveries.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> deliveryNotFound(deliveryId));
        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new InvalidStateException("Delivery " + deliveryId + " is " + delivery.getStatus()
                    + "; only PENDING deliveries can be assigned");
        }

        Rider rider = riders.findByIdForUpdate(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider " + riderId + " not found"));
        if (rider.getStatus() != RiderStatus.IDLE) {
            throw new InvalidStateException("Rider " + riderId + " is " + rider.getStatus() + ", not IDLE");
        }

        delivery.assignTo(rider);
        rider.markBusy();
        return DeliveryResponse.from(delivery);
    }

    /** Rider progress updates: ASSIGNED -> IN_TRANSIT -> DELIVERED. Delivering frees the rider. */
    @Transactional
    public DeliveryResponse updateStatus(Long deliveryId, DeliveryStatus target) {
        if (target == DeliveryStatus.ASSIGNED) {
            throw new InvalidStateException("Use POST /api/deliveries/" + deliveryId + "/assign to assign a rider");
        }

        Delivery delivery = deliveries.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> deliveryNotFound(deliveryId));
        if (!delivery.getStatus().canTransitionTo(target)) {
            throw new InvalidStateException("Delivery " + deliveryId + " cannot move from "
                    + delivery.getStatus() + " to " + target);
        }

        delivery.setStatus(target);
        if (target == DeliveryStatus.DELIVERED) {
            // A delivery in IN_TRANSIT always has a rider (it can only get there through assign()).
            riders.findByIdForUpdate(delivery.getRider().getId()).ifPresent(Rider::markIdle);
        }
        return DeliveryResponse.from(delivery);
    }

    private TrackingResponse toTracking(Delivery delivery) {
        EtaEstimate estimate = eta.estimate(delivery);
        return new TrackingResponse(DeliveryResponse.from(delivery),
                estimate.remainingKm(), estimate.etaMinutes(), estimate.speedKmh());
    }

    private static ResourceNotFoundException deliveryNotFound(Long id) {
        return new ResourceNotFoundException("Delivery " + id + " not found");
    }
}
