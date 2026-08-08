package com.uberlite.matching.domain;

public class MatchResult {
    public String tripId;
    public String driverId;
    public String status; // MATCHED or UNMATCHED
    public int attemptCount;

    public MatchResult(String tripId, String driverId, String status, int attemptCount) {
        this.tripId = tripId;
        this.driverId = driverId;
        this.status = status;
        this.attemptCount = attemptCount;
    }
}
