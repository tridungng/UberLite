package com.uberlite.priceestimation.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every intermediate value produced while evaluating the price formula, plus the final amount.
 *
 * <p>The issue calls this out explicitly: a quote nobody can explain is a support ticket. Each step
 * of {@code p = ((cd*d + ct*t) * s + cm) * (1 - η) * (1 + T)} is retained so a reader can reproduce
 * the arithmetic without re-running the service.
 */
public record PriceBreakdown(
        PricingInputs inputs,
        double costPerKm,
        double costPerMinute,
        BigDecimal distanceCost,
        BigDecimal timeCost,
        BigDecimal baseFare,
        BigDecimal surgedFare,
        BigDecimal fareWithTolls,
        BigDecimal discountAmount,
        BigDecimal fareAfterDiscount,
        BigDecimal taxAmount,
        BigDecimal total) {

    /** Ordered map for the {@code breakdown} field of the API response. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        // Downstream inputs.
        map.put("distanceKm", inputs.distanceKm());
        map.put("estimatedMinutes", inputs.estimatedMinutes());
        map.put("surgeMultiplier", inputs.surgeMultiplier());
        map.put("tollAmount", inputs.tollAmount());
        map.put("discountPct", inputs.discountPct());
        map.put("taxRate", inputs.taxRate());
        // Configured constants, so a quote can be reproduced from the breakdown alone.
        map.put("costPerKm", costPerKm);
        map.put("costPerMinute", costPerMinute);
        // Formula intermediates, in evaluation order.
        map.put("distanceCost", distanceCost);
        map.put("timeCost", timeCost);
        map.put("baseFare", baseFare);
        map.put("surgedFare", surgedFare);
        map.put("fareWithTolls", fareWithTolls);
        map.put("discountAmount", discountAmount);
        map.put("fareAfterDiscount", fareAfterDiscount);
        map.put("taxAmount", taxAmount);
        map.put("total", total);
        return map;
    }
}

