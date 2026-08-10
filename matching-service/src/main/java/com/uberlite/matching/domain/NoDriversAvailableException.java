package com.uberlite.matching.domain;

/**
 * No driver could be proposed for the trip. Maps to HTTP 404 per the issue's contract.
 *
 * <p>Distinct from {@link DependencyFailedException} on purpose: 404 means "the marketplace is
 * genuinely empty here", which Trip Service treats as a retryable attempt against its k=3 budget
 * (ARCHITECTURE.md Sec. 3). A dependency outage must NOT be reported as 404, or Trip Service would
 * burn the budget and land the trip in UNMATCHED for an infrastructure reason.
 */
public class NoDriversAvailableException extends RuntimeException {

    private final String tripId;

    public NoDriversAvailableException(String tripId) {
        super("No available drivers near the pickup for trip " + tripId);
        this.tripId = tripId;
    }

    public String getTripId() {
        return tripId;
    }
}

