package com.uberlite.matchinganalytics.api;

import com.uberlite.matchinganalytics.api.dto.MatchLogEntryDto;
import com.uberlite.matchinganalytics.domain.MatchingEventLogger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Debugging read of the match log. Nothing in the marketplace depends on it. */
@RestController
public class MatchLogController {

    private final MatchingEventLogger logger;

    public MatchLogController(MatchingEventLogger logger) {
        this.logger = logger;
    }

    /**
     * @return {@code 200} with the trip's matching history, oldest first. An unknown trip gets an
     *     empty list rather than {@code 404}: this service does not own trips and cannot tell
     *     "no such trip" apart from "that trip never reached matching", so claiming the former
     *     would be a guess.
     */
    @GetMapping("/match-log/{tripId}")
    public List<MatchLogEntryDto> byTrip(@PathVariable String tripId) {
        return logger.findByTripId(tripId);
    }
}

