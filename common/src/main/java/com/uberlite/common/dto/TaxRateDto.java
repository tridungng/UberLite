package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code GET /tax/{regionId}} on the Tax &amp; Tolls Service. */
public class TaxRateDto {
    private final String regionId;
    private final double rate;

    @JsonCreator
    public TaxRateDto(@JsonProperty("regionId") String regionId, @JsonProperty("rate") double rate) {
        this.regionId = regionId;
        this.rate = rate;
    }

    public String getRegionId() {
        return regionId;
    }

    public double getRate() {
        return rate;
    }
}

