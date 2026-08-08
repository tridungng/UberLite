package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Surge pricing multiplier for an H3 cell.
 * Part of the Price Estimation flow (paper Sec. 4.1).
 */
public class SurgeMultiplierDto {
    private final String h3Cell;
    private final double multiplier;
    private final long updatedAtMs;

    @JsonCreator
    public SurgeMultiplierDto(
            @JsonProperty("h3Cell") String h3Cell,
            @JsonProperty("multiplier") double multiplier,
            @JsonProperty("updatedAtMs") long updatedAtMs) {
        this.h3Cell = h3Cell;
        this.multiplier = multiplier;
        this.updatedAtMs = updatedAtMs;
    }

    public String getH3Cell() {
        return h3Cell;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }
}
