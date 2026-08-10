package com.uberlite.discountsanalytics.client;

import com.uberlite.common.dto.RiderTripCountDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Trip Service (ARCHITECTURE.md Sec. 2). Contract: {@code GET /trips/rider-trip-counts}.
 *
 * <p>No fallback, deliberately. An empty list from a fallback is indistinguishable from "nobody has
 * completed a trip", and the batch would read that as "every rider qualifies" — or, once stale
 * candidates are swept, as "nobody does". A failed nightly run that leaves yesterday's candidates
 * in place is the safer outcome.
 */
@FeignClient(name = "trip-service", contextId = "tripServiceClient")
public interface TripServiceClient {

    @GetMapping("/trips/rider-trip-counts")
    List<RiderTripCountDto> riderTripCounts();
}

