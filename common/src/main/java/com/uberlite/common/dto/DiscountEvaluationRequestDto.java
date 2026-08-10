package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /discounts/evaluate} on the Discounts &amp; Promotions Service.
 * Shared contract between Price Estimation Service and Discounts &amp; Promotions Service.
 */
public class DiscountEvaluationRequestDto {
    private final String riderId;
    private final int riderTripCount;

    @JsonCreator
    public DiscountEvaluationRequestDto(
            @JsonProperty("riderId") String riderId,
            @JsonProperty("riderTripCount") int riderTripCount) {
        this.riderId = riderId;
        this.riderTripCount = riderTripCount;
    }

    public String getRiderId() {
        return riderId;
    }

    public int getRiderTripCount() {
        return riderTripCount;
    }
}

