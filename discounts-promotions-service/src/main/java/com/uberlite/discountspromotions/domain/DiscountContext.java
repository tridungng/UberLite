package com.uberlite.discountspromotions.domain;

public class DiscountContext {
    private final String riderId;
    private final int riderTripCount;

    public DiscountContext(String riderId, int riderTripCount) {
        this.riderId = riderId;
        this.riderTripCount = riderTripCount;
    }

    public String getRiderId() { return riderId; }
    public int getRiderTripCount() { return riderTripCount; }
}
