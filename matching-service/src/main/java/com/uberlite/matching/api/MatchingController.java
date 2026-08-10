package com.uberlite.matching.api;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.MatchRequestDto;
import com.uberlite.matching.domain.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matching Service API (ARCHITECTURE.md Sec. 4: Trip Service -> Matching Service).
 */
@RestController
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /**
     * Proposes the best available driver for a trip.
     *
     * @return 200 with the best {@link DriverCandidateDto}; 404 if no driver is available;
     *     400 on an invalid body; 502 if a downstream service is unreachable
     */
    @PostMapping("/matches")
    public ResponseEntity<DriverCandidateDto> match(@Valid @RequestBody MatchRequestDto request) {
        return ResponseEntity.ok(matchingService.findBestMatch(request));
    }
}
