package com.uberlite.discountspromotions.domain;

public class NewRiderTripCountRule implements DiscountRule {
    private final int threshold;

    public NewRiderTripCountRule(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean evaluate(DiscountContext ctx) {
        return ctx.getRiderTripCount() < threshold;
    }
}
