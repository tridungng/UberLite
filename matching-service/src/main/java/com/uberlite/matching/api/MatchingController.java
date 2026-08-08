package com.uberlite.matching.api;

import com.uberlite.matching.domain.MatchResult;
import com.uberlite.matching.domain.MatchingEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingController {
    private final MatchingEngine matchingEngine;

    public MatchingController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/match/propose")
    public ResponseEntity<MatchResult> proposeMatch(
            @RequestParam String tripId,
            @RequestParam String pickupH3Cell,
            @RequestParam(defaultValue = "0") int attemptCount) {
        
        MatchResult result = matchingEngine.findMatch(tripId, pickupH3Cell, attemptCount);
        return ResponseEntity.ok(result);
    }
}
