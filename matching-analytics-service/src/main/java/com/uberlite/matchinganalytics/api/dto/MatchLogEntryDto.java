package com.uberlite.matchinganalytics.api.dto;

import com.uberlite.matchinganalytics.domain.MatchOutcome;

import java.time.Instant;

/**
 * One row of {@code GET /match-log/{tripId}}.
 *
 * <p>Local to this module rather than in {@code common}: the endpoint is a debugging aid
 * (issue scope: "no query API required beyond a basic {@code GET /match-log/{tripId}}"), no other
 * service consumes it, and promoting it to a shared contract would freeze a shape nobody depends
 * on yet.
 */
public record MatchLogEntryDto(
        String tripId,
        String driverId,
        MatchOutcome outcome,
        Instant occurredAt
) {
}

