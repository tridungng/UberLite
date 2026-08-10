package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of {@code GET /route/estimate} on the Route Service.
 *
 * <p>{@code detourFactor} is nullable: the Route Service only computes it when the caller supplies
 * {@code actualDistanceKm}. Callers must decide on a fallback rather than assuming 0 — a 0 detour
 * factor collapses the trip distance to zero and would silently produce a free ride.
 */
public class RouteEstimateDto {
    private final double straightDistanceKm;
    private final Double detourFactor;

    @JsonCreator
    public RouteEstimateDto(
            @JsonProperty("straightDistanceKm") double straightDistanceKm,
            @JsonProperty("detourFactor") Double detourFactor) {
        this.straightDistanceKm = straightDistanceKm;
        this.detourFactor = detourFactor;
    }

    public double getStraightDistanceKm() {
        return straightDistanceKm;
    }

    public Double getDetourFactor() {
        return detourFactor;
    }
}

