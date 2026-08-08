package com.uberlite.priceestimation.domain;

import org.springframework.stereotype.Service;

@Service
public class PricingCalculator {
    private static final double COST_PER_KM = 1.5;
    private static final double COST_PER_MINUTE = 0.3;
    private static final double BASE_FARE = 2.0;
    private static final double TAX_RATE = 0.08;

    public PriceEstimate calculatePrice(String tripId, double distanceKm, double estimatedMinutes,
                                        double surgeMultiplier, double tollAmount, 
                                        double discountRate) {
        double distanceFare = distanceKm * COST_PER_KM;
        double timeFare = estimatedMinutes * COST_PER_MINUTE;
        double subtotal = BASE_FARE + distanceFare + timeFare;
        double surgeFare = subtotal * (surgeMultiplier - 1.0);
        double fareAfterSurge = subtotal * surgeMultiplier;
        double fareAfterToll = fareAfterSurge + tollAmount;
        double discountAmount = fareAfterToll * discountRate;
        double fareAfterDiscount = fareAfterToll * (1.0 - discountRate);
        double taxAmount = fareAfterDiscount * TAX_RATE;
        double totalFare = fareAfterDiscount + taxAmount;

        return new PriceEstimate(
            tripId,
            BASE_FARE,
            distanceFare,
            timeFare,
            surgeFare,
            tollAmount,
            discountAmount,
            taxAmount,
            totalFare
        );
    }
}
