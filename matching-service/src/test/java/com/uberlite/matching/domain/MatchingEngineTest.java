package com.uberlite.matching.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingEngineTest {
    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void findMatchReturnsMatchedResult() {
        MatchResult result = engine.findMatch("trip-1", "cell-123", 0);
        
        assertEquals("trip-1", result.tripId);
        assertEquals("MATCHED", result.status);
        assertEquals(1, result.attemptCount);
    }

    @Test
    void findMatchUnmatchedAfterMaxAttempts() {
        MatchResult result = engine.findMatch("trip-2", "cell-456", 3);
        
        assertEquals("trip-2", result.tripId);
        assertEquals("UNMATCHED", result.status);
        assertEquals(3, result.attemptCount);
    }
}
