package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Forecasting Service response for {@code GET /forecast/{h3Cell}?hourOfDay=} (paper Sec. 5).
 *
 * <p>Lives in {@code common} rather than in the service module because it is the contract
 * Surge Pricing v2 will consume when it starts blending forecast demand with the live
 * pending-request counter — see {@code DemandForecaster} for the plug-in point.
 */
public class DemandForecastDto {

    private final String h3Cell;
    private final int hourOfDay;
    private final double predictedDemand;

    @JsonCreator
    public DemandForecastDto(
            @JsonProperty("h3Cell") String h3Cell,
            @JsonProperty("hourOfDay") int hourOfDay,
            @JsonProperty("predictedDemand") double predictedDemand) {
        this.h3Cell = h3Cell;
        this.hourOfDay = hourOfDay;
        this.predictedDemand = predictedDemand;
    }

    public String getH3Cell() {
        return h3Cell;
    }

    public int getHourOfDay() {
        return hourOfDay;
    }

    public double getPredictedDemand() {
        return predictedDemand;
    }
}

