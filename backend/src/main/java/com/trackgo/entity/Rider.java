package com.trackgo.entity;

import com.trackgo.geo.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A delivery rider. In this version the rider's last known position is stored right here as plain
 * columns (a later version could move it to Redis GEO), together with the time it was reported.
 */
@Entity
@Table(name = "riders")
// Location pings and status changes arrive independently. Without @DynamicUpdate Hibernate writes
// every column on each update, so a stale status could overwrite a concurrent assignment.
@DynamicUpdate
public class Rider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiderStatus status = RiderStatus.IDLE;

    private Double currentLat;

    private Double currentLng;

    /** When the position above was last reported; used to detect a rider who stopped reporting. */
    private Instant locationUpdatedAt;

    protected Rider() {
        // required by JPA
    }

    public Rider(String name, RiderStatus status, Double currentLat, Double currentLng, Instant locationUpdatedAt) {
        this.name = name;
        this.status = status;
        this.currentLat = currentLat;
        this.currentLng = currentLng;
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public void updateLocation(double lat, double lng, Instant reportedAt) {
        this.currentLat = lat;
        this.currentLng = lng;
        this.locationUpdatedAt = reportedAt;
    }

    public void markBusy() {
        this.status = RiderStatus.BUSY;
    }

    public void markIdle() {
        this.status = RiderStatus.IDLE;
    }

    /** The last known position, or {@code null} if the rider never reported one. */
    public GeoPoint position() {
        return currentLat == null || currentLng == null ? null : new GeoPoint(currentLat, currentLng);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RiderStatus getStatus() {
        return status;
    }

    public Double getCurrentLat() {
        return currentLat;
    }

    public Double getCurrentLng() {
        return currentLng;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }
}
