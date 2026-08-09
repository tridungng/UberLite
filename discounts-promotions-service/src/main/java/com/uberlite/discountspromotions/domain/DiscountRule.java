package com.uberlite.discountspromotions.domain;

public interface DiscountRule {
    boolean evaluate(DiscountContext ctx);
}
