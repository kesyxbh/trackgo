package com.trackgo.repository;

import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Building blocks for the optional filters of {@code GET /api/deliveries}. Each filter is only
 * added when the caller supplied a value, so no "(:param is null or ...)" tricks are needed.
 */
public final class DeliverySpecifications {

    private DeliverySpecifications() {
    }

    public static Specification<Delivery> hasCustomer(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Delivery> hasRider(Long riderId) {
        return (root, query, cb) -> cb.equal(root.get("rider").get("id"), riderId);
    }

    public static Specification<Delivery> hasStatus(DeliveryStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Matches any of the given statuses, e.g. every delivery that is currently "active". */
    public static Specification<Delivery> hasAnyStatus(DeliveryStatus... statuses) {
        return (root, query, cb) -> root.get("status").in((Object[]) statuses);
    }
}
