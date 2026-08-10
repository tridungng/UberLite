package com.uberlite.tripservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.events.TripState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The full trip as returned by every {@code /trips} endpoint.
 *
 * <p>Quote, driver and exclusion fields are included because orchestration makes them the visible
 * outcome of a call: a client that gets back {@code PRICED} needs the price, and one that gets back
 * {@code DRIVER_PROPOSED} needs the driver, without a second round trip. Fields that are not yet
 * meaningful for the trip's state are omitted rather than serialised as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripResponse(
        UUID id,
        String riderId,
        LocationDto pickup,
        LocationDto dropoff,
        String pickupH3,
        String dropoffH3,
        TripState state,
        BigDecimal quotedPrice,
        String quoteCurrency,
        Map<String, Object> quoteBreakdown,
        String driverId,
        List<String> declinedDriverIds,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt,
        List<TripHistoryDto> history
) {
}
