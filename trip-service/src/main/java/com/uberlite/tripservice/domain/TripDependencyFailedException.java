package com.uberlite.tripservice.domain;

import java.util.UUID;

/**
 * A {@link DependencyFailedException} that knows which trip it happened to.
 *
 * <p>The trip id matters: {@code POST /trips} commits the trip before quoting it, so when pricing
 * fails the caller must be told the id or it has no way to reach
 * {@code POST /trips/{id}/request-quote} and will create a duplicate trip instead.
 */
public class TripDependencyFailedException extends DependencyFailedException {

    private final UUID tripId;

    public TripDependencyFailedException(UUID tripId, DependencyFailedException cause) {
        super(cause);
        this.tripId = tripId;
    }

    public UUID getTripId() {
        return tripId;
    }
}


