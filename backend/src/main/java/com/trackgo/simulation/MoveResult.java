package com.trackgo.simulation;

/** What one simulation step did to one delivery. */
public enum MoveResult {
    /** Nothing to do: the delivery is not active, has no rider, or has no coordinates. */
    SKIPPED,
    /** The rider moved closer but has not arrived yet. */
    MOVED,
    /** The rider reached the pickup point; the delivery is now IN_TRANSIT. */
    PICKED_UP,
    /** The rider reached the drop-off point; the delivery is now DELIVERED. */
    DELIVERED
}
