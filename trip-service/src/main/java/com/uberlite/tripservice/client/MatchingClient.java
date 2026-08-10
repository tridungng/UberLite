package com.uberlite.tripservice.client;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.MatchRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Matching Service (ARCHITECTURE.md Sec. 4: Trip Service -> Matching Service).
 *
 * <p>Contract: {@code POST /matches} -> {@link DriverCandidateDto}. The status code carries the
 * meaning and must not be flattened into a fallback:
 *
 * <ul>
 *   <li>{@code 200} — a driver was proposed
 *   <li>{@code 404} — empty marketplace; a real answer, consumes a matching attempt
 *   <li>{@code 502} — Matching's own dependency is down; retryable <em>without</em> consuming an
 *       attempt, so the trip must not be parked in {@code UNMATCHED} for an infrastructure reason
 * </ul>
 *
 * <p>There is deliberately no fallback/{@code @FeignClient(fallback = ...)} here: see
 * ARCHITECTURE.md Sec. 4, "the 404/502 split is load-bearing".
 */
@FeignClient(name = "matching-service", contextId = "matchingClient")
public interface MatchingClient {

    @PostMapping("/matches")
    DriverCandidateDto match(@RequestBody MatchRequestDto request);
}

