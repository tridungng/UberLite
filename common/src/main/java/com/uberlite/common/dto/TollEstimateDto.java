package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code POST /tolls/estimate} on the Tax &amp; Tolls Service. */
public class TollEstimateDto {
    private final double amount;

    @JsonCreator
    public TollEstimateDto(@JsonProperty("amount") double amount) {
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }
}

