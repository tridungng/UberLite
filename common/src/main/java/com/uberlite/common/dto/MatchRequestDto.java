package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body of {@code POST /matches} on the Matching Service (ARCHITECTURE.md Sec. 4, "MS").
 *
 * <p>Lives in {@code common} because Trip Service is the caller — it drives the
 * {@code ACCEPTED_BY_RIDER -> DRIVER_PROPOSED} transition and must not duplicate this shape.
 *
 * <p>Note there is deliberately no {@code excludedDriverIds} field in the MVP: the Matching Service
 * is stateless and does not remember which drivers already declined. Trip Service owns the retry
 * budget (k=3, ARCHITECTURE.md Sec. 3) and the declined-driver list on the trip row, and simply
 * calls {@code /matches} again with the same {@code tripId}. See this module's README for the
 * swap-out point where batch-optimal assignment (paper Sec. 4.1) would take over.
 */
public class MatchRequestDto {

    @NotBlank(message = "tripId must not be blank")
    private final String tripId;

    @NotNull(message = "pickup must not be null")
    @Valid
    private final LocationDto pickup;

    @JsonCreator
    public MatchRequestDto(@JsonProperty("tripId") String tripId,
                           @JsonProperty("pickup") LocationDto pickup) {
        this.tripId = tripId;
        this.pickup = pickup;
    }

    public String getTripId() {
        return tripId;
    }

    public LocationDto getPickup() {
        return pickup;
    }
}

