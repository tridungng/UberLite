package com.uberlite.priceestimation.domain;

import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

@Service
public class PricingCalculator {
    private final double cd;
    private final double ct;

    public PricingCalculator(@Value("${price.cd:1.5}") double cd, @Value("${price.ct:0.3}") double ct) {
        this.cd = cd;
        this.ct = ct;
    }

    /**
     * Implements p = ((cd*d + ct*t) * s + cm) * (1 - discountPct) * (1 + taxRate)
     * Note: paper writes η as discount rate; here discountPct is a fraction (0.2 for 20%) and
     * we apply (1 - discountPct) to reduce the price.
     */
    public double calculateUsingFormula(
            double distanceKm,
            double estimatedMinutes,
            double surgeMultiplier,
            double tollAmount,
            double discountPct,
            double taxRate) {
        double dPart = cd * distanceKm;
        double tPart = ct * estimatedMinutes;
        double base = dPart + tPart;
        double afterSurge = base * surgeMultiplier;
        double afterTolls = afterSurge + tollAmount;
        double afterDiscount = afterTolls * (1.0 - discountPct);
        double afterTax = afterDiscount * (1.0 + taxRate);
        return roundTwo(afterTax);
    }

    private double roundTwo(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
