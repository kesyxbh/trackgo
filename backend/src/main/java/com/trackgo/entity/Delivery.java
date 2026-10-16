package com.trackgo.entity;

import com.trackgo.geo.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A delivery order. Each end of the trip has a human-readable address and a coordinate: the address
 * is what people read, the coordinate is what dispatching, the map and the simulator work with.
 */
@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    /** Null until a rider is assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id")
    private Rider rider;

    @Column(nullable = false)
    private String pickupAddr;

    // The coordinates are nullable in the database so that rows written before they existed still
    // load; the API requires them for every new delivery.
    private Double pickupLat;

    private Double pickupLng;

    @Column(nullable = false)
    private String dropoffAddr;

    private Double dropoffLat;

    private Double dropoffLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Delivery() {
        // required by JPA
    }

    public Delivery(User customer, String pickupAddr, double pickupLat, double pickupLng,
                    String dropoffAddr, double dropoffLat, double dropoffLng) {
        this.customer = customer;
        this.pickupAddr = pickupAddr;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffAddr = dropoffAddr;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
    }

    public void assignTo(Rider rider) {
        this.rider = rider;
        this.status = DeliveryStatus.ASSIGNED;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    /** True when both ends of the trip have a coordinate (always the case for deliveries made via the API). */
    public boolean hasCoordinates() {
        return pickupLat != null && pickupLng != null && dropoffLat != null && dropoffLng != null;
    }

    public GeoPoint pickupPoint() {
        return pickupLat == null || pickupLng == null ? null : new GeoPoint(pickupLat, pickupLng);
    }

    public GeoPoint dropoffPoint() {
        return dropoffLat == null || dropoffLng == null ? null : new GeoPoint(dropoffLat, dropoffLng);
    }

    public Long getId() {
        return id;
    }

    public User getCustomer() {
        return customer;
    }

    public Rider getRider() {
        return rider;
    }

    public String getPickupAddr() {
        return pickupAddr;
    }

    public Double getPickupLat() {
        return pickupLat;
    }

    public Double getPickupLng() {
        return pickupLng;
    }

    public String getDropoffAddr() {
        return dropoffAddr;
    }

    public Double getDropoffLat() {
        return dropoffLat;
    }

    public Double getDropoffLng() {
        return dropoffLng;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
