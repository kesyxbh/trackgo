package com.trackgo.entity;

/**
 * Delivery lifecycle: PENDING -> ASSIGNED -> IN_TRANSIT -> DELIVERED.
 * The only legal moves are the ones listed in {@link #canTransitionTo}.
 */
public enum DeliveryStatus {
    /** Created by the customer, waiting for a rider. */
    PENDING,
    /** A rider has been assigned but has not picked the parcel up yet. */
    ASSIGNED,
    /** The rider picked the parcel up and is on the way. */
    IN_TRANSIT,
    /** Terminal state. */
    DELIVERED;

    public boolean canTransitionTo(DeliveryStatus next) {
        return switch (this) {
            case PENDING -> next == ASSIGNED;
            case ASSIGNED -> next == IN_TRANSIT;
            case IN_TRANSIT -> next == DELIVERED;
            case DELIVERED -> false;
        };
    }
}
