package com.uberlite.tripservice.api.dto;

import com.uberlite.common.dto.LocationDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTripRequest(
        @NotBlank String riderId,
        @NotNull LocationDto pickup,
        @NotNull LocationDto dropoff
) {
}
