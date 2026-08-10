package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /price-estimates} on the Price Estimation Service.
 *
 * <p>Cross-service contract (Trip Service -> Price Estimation Service, ARCHITECTURE.md Sec. 4),
 * so it lives in {@code common} rather than being duplicated in each module.
 */
public class PriceEstimateRequestDto {

    @NotBlank(message = "riderId is required")
    private final String riderId;

    @Min(value = 0, message = "riderTripCount must be >= 0")
    private final int riderTripCount;

    @NotNull(message = "pickup is required")
    @Valid
    private final LocationDto pickup;

    @NotNull(message = "dropoff is required")
    @Valid
    private final LocationDto dropoff;

    @JsonCreator
    public PriceEstimateRequestDto(
            @JsonProperty("riderId") String riderId,
            @JsonProperty("riderTripCount") int riderTripCount,
            @JsonProperty("pickup") LocationDto pickup,
            @JsonProperty("dropoff") LocationDto dropoff) {
        this.riderId = riderId;
        this.riderTripCount = riderTripCount;
        this.pickup = pickup;
        this.dropoff = dropoff;
    }

    public String getRiderId() {
        return riderId;
    }

    public int getRiderTripCount() {
        return riderTripCount;
    }

    public LocationDto getPickup() {
        return pickup;
    }

    public LocationDto getDropoff() {
        return dropoff;
    }
}

