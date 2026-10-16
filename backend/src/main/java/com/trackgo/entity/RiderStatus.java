package com.trackgo.entity;

public enum RiderStatus {
    /** Online and available for a new delivery. */
    IDLE,
    /** Currently working on a delivery. */
    BUSY,
    /** Not available; cannot be assigned. */
    OFFLINE
}
