package com.uberlite.tripservice.api.dto;

import com.uberlite.common.events.TripState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TripHistoryDto(
        UUID id,
        TripState fromState,
        TripState toState,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
