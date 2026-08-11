package com.uberlite.forecasting.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Marker that a trip's {@code REQUESTED} event has already been folded into {@code demand_counts}.
 *
 * <p>Kafka gives at-least-once delivery, so without this a consumer restart mid-batch (or an offset
 * commit that never landed) would re-count the same trip and quietly bias the forecast upward.
 */
@Entity
@Table(name = "counted_trips")
public class CountedTripEntity {

    @Id
    @Column(name = "trip_id", nullable = false, length = 64)
    private String tripId;

    @Column(name = "counted_at", nullable = false)
    private Instant countedAt;

    protected CountedTripEntity() {
    }

    public CountedTripEntity(String tripId, Instant countedAt) {
        this.tripId = tripId;
        this.countedAt = countedAt;
    }

    public String getTripId() {
        return tripId;
    }

    public Instant getCountedAt() {
        return countedAt;
    }
}

