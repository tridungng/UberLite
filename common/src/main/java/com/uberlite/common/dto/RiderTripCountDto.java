package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One rider's completed-trip count, as served by Trip Service's
 * {@code GET /trips/rider-trip-counts} and consumed by Discounts Analytics' nightly batch
 * (ARCHITECTURE.md Sec. 2, background services).
 *
 * <p>Trip Service is the only component that owns trip state, so the count is computed there
 * rather than replicated — the MVP deliberately skips a read-replica or materialised view.
 */
public class RiderTripCountDto {

    private final String riderId;
    private final long completedTrips;

    @JsonCreator
    public RiderTripCountDto(
            @JsonProperty("riderId") String riderId,
            @JsonProperty("completedTrips") long completedTrips) {
        this.riderId = riderId;
        this.completedTrips = completedTrips;
    }

    public String getRiderId() {
        return riderId;
    }

    public long getCompletedTrips() {
        return completedTrips;
    }
}

