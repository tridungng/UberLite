package com.uberlite.matching.domain;

import org.springframework.stereotype.Service;

@Service
public class MatchingEngine {
    private static final int MAX_MATCH_ATTEMPTS = 3;

    public MatchResult findMatch(String tripId, String pickupH3Cell, int attemptCount) {
        if (attemptCount >= MAX_MATCH_ATTEMPTS) {
            return new MatchResult(tripId, null, "UNMATCHED", attemptCount);
        }

        // Placeholder: In production, this would call Driver Discovery Service
        // and apply sophisticated matching algorithm
        String mockDriverId = "driver-" + System.currentTimeMillis() % 1000;
        return new MatchResult(tripId, mockDriverId, "MATCHED", attemptCount + 1);
    }
}
