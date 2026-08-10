package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body of {@code POST /matches} on the Matching Service (ARCHITECTURE.md Sec. 4, "MS").
 *
 * <p>Lives in {@code common} because Trip Service is the caller — it drives the
 * {@code ACCEPTED_BY_RIDER -> DRIVER_PROPOSED} transition and must not duplicate this shape.
 *
 * <p><b>{@code excludedDriverIds} and statelessness.</b> Matching still remembers nothing between
 * calls: Trip Service owns the retry budget (k=3, ARCHITECTURE.md Sec. 3) and the durable
 * declined-driver list on the trip row. But the exclusions must travel <em>with</em> the request,
 * because Matching is greedy-nearest and deterministic: given the same driver pool it returns the
 * same driver every time, so a retry that did not carry the exclusions would re-propose the driver
 * who just declined, forever. Passing them keeps Matching stateless while letting it pick the best
 * <em>eligible</em> driver rather than handing back an answer the caller must throw away.
 */
public class MatchRequestDto {

    @NotBlank(message = "tripId must not be blank")
    private final String tripId;

    @NotNull(message = "pickup must not be null")
    @Valid
    private final LocationDto pickup;

    /** Drivers who already declined this trip. Never null; absent in JSON means "none". */
    private final List<String> excludedDriverIds;

    @JsonCreator
    public MatchRequestDto(@JsonProperty("tripId") String tripId,
                           @JsonProperty("pickup") LocationDto pickup,
                           @JsonProperty("excludedDriverIds") List<String> excludedDriverIds) {
        this.tripId = tripId;
        this.pickup = pickup;
        this.excludedDriverIds = excludedDriverIds == null ? List.of() : List.copyOf(excludedDriverIds);
    }

    /** First-attempt convenience: nobody has declined yet. */
    public MatchRequestDto(String tripId, LocationDto pickup) {
        this(tripId, pickup, List.of());
    }

    public String getTripId() {
        return tripId;
    }

    public LocationDto getPickup() {
        return pickup;
    }

    public List<String> getExcludedDriverIds() {
        return excludedDriverIds;
    }
}
