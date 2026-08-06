package com.uberlite.tripservice.api.dto;

import com.uberlite.common.events.TripState;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record TransitionRequest(
        @NotNull TripState toState,
        Map<String, Object> payload
) {
}
