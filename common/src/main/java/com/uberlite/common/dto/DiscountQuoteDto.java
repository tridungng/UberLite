package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@code POST /discounts/evaluate}.
 * {@code discountPct} is a fraction in [0,1] — 0.2 means "20% off".
 */
public class DiscountQuoteDto {
    private final double discountPct;

    @JsonCreator
    public DiscountQuoteDto(@JsonProperty("discountPct") double discountPct) {
        this.discountPct = discountPct;
    }

    public double getDiscountPct() {
        return discountPct;
    }
}

