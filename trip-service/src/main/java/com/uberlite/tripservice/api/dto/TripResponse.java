package com.uberlite.tripservice.api.dto;

import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.events.TripState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id,
        String riderId,
        LocationDto pickup,
        LocationDto dropoff,
        String pickupH3,
        String dropoffH3,
        TripState state,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt,
        List<TripHistoryDto> history
) {
}
