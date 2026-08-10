package com.uberlite.tripservice.repository.entity;

import com.uberlite.common.events.TripState;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(schema = "trip", name = "trips")
public class TripEntity {
    @Id
    private UUID id;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripState state;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lon", nullable = false)
    private double pickupLon;

    @Column(name = "pickup_h3", nullable = false)
    private String pickupH3;

    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @Column(name = "dropoff_lon", nullable = false)
    private double dropoffLon;

    @Column(name = "dropoff_h3", nullable = false)
    private String dropoffH3;

    @Column(name = "quoted_price", precision = 19, scale = 2)
    private BigDecimal quotedPrice;

    @Column(name = "quote_currency", length = 8)
    private String quoteCurrency;

    /**
     * The Price Estimation breakdown, kept verbatim. Storing only the amount would leave us unable
     * to answer "why was I charged this?" without re-quoting, which by then returns a different
     * number because surge has moved.
     */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "quote_breakdown", columnDefinition = "text")
    private Map<String, Object> quoteBreakdown;

    @Column(name = "driver_id")
    private String driverId;

    /**
     * Drivers who declined this trip. Matching is stateless and tracks no exclusions
     * (ARCHITECTURE.md Sec. 4), so this list is the only thing stopping it re-proposing them.
     */
    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "declined_driver_ids", nullable = false, columnDefinition = "text")
    private List<String> declinedDriverIds = new ArrayList<>();

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /**
     * Whether this trip is currently counted in the Surge Pricing pending-request gauge. Guards
     * against double-increment (retried quote) and double-decrement (re-sent terminal transition),
     * either of which would permanently skew surge for the cell.
     */
    @Column(name = "surge_pending_registered", nullable = false)
    private boolean surgePendingRegistered;

    /**
     * A transition can now be driven by the rider <em>and</em> by the orchestrator's
     * auto-transition. Optimistic locking turns a lost update into a detectable conflict.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRiderId() {
        return riderId;
    }

    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }

    public TripState getState() {
        return state;
    }

    public void setState(TripState state) {
        this.state = state;
    }

    public double getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(double pickupLat) {
        this.pickupLat = pickupLat;
    }

    public double getPickupLon() {
        return pickupLon;
    }

    public void setPickupLon(double pickupLon) {
        this.pickupLon = pickupLon;
    }

    public String getPickupH3() {
        return pickupH3;
    }

    public void setPickupH3(String pickupH3) {
        this.pickupH3 = pickupH3;
    }

    public double getDropoffLat() {
        return dropoffLat;
    }

    public void setDropoffLat(double dropoffLat) {
        this.dropoffLat = dropoffLat;
    }

    public double getDropoffLon() {
        return dropoffLon;
    }

    public void setDropoffLon(double dropoffLon) {
        this.dropoffLon = dropoffLon;
    }

    public String getDropoffH3() {
        return dropoffH3;
    }

    public void setDropoffH3(String dropoffH3) {
        this.dropoffH3 = dropoffH3;
    }

    public BigDecimal getQuotedPrice() {
        return quotedPrice;
    }

    public void setQuotedPrice(BigDecimal quotedPrice) {
        this.quotedPrice = quotedPrice;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public Map<String, Object> getQuoteBreakdown() {
        return quoteBreakdown;
    }

    public void setQuoteBreakdown(Map<String, Object> quoteBreakdown) {
        this.quoteBreakdown = quoteBreakdown;
    }

    public List<String> getDeclinedDriverIds() {
        return declinedDriverIds == null ? List.of() : List.copyOf(declinedDriverIds);
    }

    public void setDeclinedDriverIds(List<String> declinedDriverIds) {
        this.declinedDriverIds = declinedDriverIds == null ? new ArrayList<>() : new ArrayList<>(declinedDriverIds);
    }

    /** Records a decline. Idempotent, so a re-sent DRIVER_DECLINED cannot duplicate an entry. */
    public void addDeclinedDriver(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return;
        }
        if (declinedDriverIds == null) {
            declinedDriverIds = new ArrayList<>();
        }
        if (!declinedDriverIds.contains(driverId)) {
            declinedDriverIds.add(driverId);
        }
    }

    public boolean isSurgePendingRegistered() {
        return surgePendingRegistered;
    }

    public void setSurgePendingRegistered(boolean surgePendingRegistered) {
        this.surgePendingRegistered = surgePendingRegistered;
    }

    public long getVersion() {
        return version;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
