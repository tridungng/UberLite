package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of {@code GET /time/estimate} on the Time Estimation Service.
 *
 * <p>The wire field is {@code minutes} (see {@code TimeEstimationController}); {@link #getSeconds()}
 * is a derived convenience so callers reasoning in seconds don't each redo the conversion.
 */
public class TimeEstimateDto {
    private final double minutes;

    @JsonCreator
    public TimeEstimateDto(@JsonProperty("minutes") double minutes) {
        this.minutes = minutes;
    }

    public double getMinutes() { return minutes; }

    public long getSeconds() { return Math.round(minutes * 60.0); }
}
