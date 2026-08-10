package com.uberlite.priceestimation.domain;

/**
 * The raw values gathered from the five downstream services, before the formula is applied.
 *
 * <p>Keeping this separate from the Feign clients means {@link PricingCalculator} is a pure function
 * of its inputs and can be unit-tested with zero mocking.
 *
 * @param distanceKm       {@code d} — trip distance in kilometres
 * @param estimatedMinutes {@code t} — estimated trip duration in minutes
 * @param surgeMultiplier  {@code s} — surge multiplier for the pickup cell
 * @param tollAmount       {@code cm} — miscellaneous charges (tolls)
 * @param discountPct      {@code η} — discount as a fraction in [0,1]
 * @param taxRate          {@code T} — tax rate as a fraction
 */
public record PricingInputs(
        double distanceKm,
        double estimatedMinutes,
        double surgeMultiplier,
        double tollAmount,
        double discountPct,
        double taxRate) {

    public PricingInputs {
        requireNonNegative(distanceKm, "distanceKm");
        requireNonNegative(estimatedMinutes, "estimatedMinutes");
        requireNonNegative(tollAmount, "tollAmount");
        requireNonNegative(taxRate, "taxRate");
        if (surgeMultiplier < 1.0) {
            throw new IllegalArgumentException("surgeMultiplier must be >= 1.0 but was " + surgeMultiplier);
        }
        if (discountPct < 0.0 || discountPct > 1.0) {
            throw new IllegalArgumentException("discountPct must be within [0,1] but was " + discountPct);
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (value < 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite value >= 0 but was " + value);
        }
    }
}

