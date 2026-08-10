package com.uberlite.priceestimation.domain;

import com.uberlite.priceestimation.config.PricingProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Implements the paper's price formula (ARCHITECTURE.md Sec. 8):
 *
 * <pre>
 *   p = ((cd * d + ct * t) * s + cm) * (1 - eta) * (1 + T)
 * </pre>
 *
 * <p><b>Sign convention for eta.</b> The paper writes eta as "a discount rate" that multiplies the
 * price, which is ambiguous: taken literally, eta = 0.2 would charge 20% <i>of</i> the fare rather
 * than take 20% <i>off</i> it. We interpret {@code discountPct} as a reduction and apply
 * {@code (1 - discountPct)}, matching the Discounts &amp; Promotions Service whose rules read
 * "first 3 rides: 20% off". This is a deliberate reading of an ambiguous formula.
 *
 * <p><b>Why BigDecimal.</b> This produces money. Binary floating point cannot represent values such
 * as 0.1 exactly, so a {@code double} pipeline accumulates error and rounds inconsistently at the
 * half cent. All arithmetic is done in {@link BigDecimal} and rounded, once, with
 * {@link RoundingMode#HALF_UP}.
 */
@Component
public class PricingCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final PricingProperties properties;

    public PricingCalculator(PricingProperties properties) {
        this.properties = properties;
    }

    public PriceBreakdown calculate(PricingInputs inputs) {
        BigDecimal cd = BigDecimal.valueOf(properties.getCostPerKm());
        BigDecimal ct = BigDecimal.valueOf(properties.getCostPerMinute());

        BigDecimal distanceCost = cd.multiply(BigDecimal.valueOf(inputs.distanceKm()), PRECISION);
        BigDecimal timeCost = ct.multiply(BigDecimal.valueOf(inputs.estimatedMinutes()), PRECISION);
        BigDecimal baseFare = distanceCost.add(timeCost, PRECISION);

        BigDecimal surgedFare = baseFare.multiply(BigDecimal.valueOf(inputs.surgeMultiplier()), PRECISION);
        BigDecimal fareWithTolls = surgedFare.add(BigDecimal.valueOf(inputs.tollAmount()), PRECISION);

        BigDecimal discountAmount = fareWithTolls.multiply(BigDecimal.valueOf(inputs.discountPct()), PRECISION);
        BigDecimal fareAfterDiscount = fareWithTolls.subtract(discountAmount, PRECISION);

        BigDecimal taxAmount = fareAfterDiscount.multiply(BigDecimal.valueOf(inputs.taxRate()), PRECISION);
        BigDecimal total = fareAfterDiscount.add(taxAmount, PRECISION);

        return new PriceBreakdown(
                inputs,
                properties.getCostPerKm(),
                properties.getCostPerMinute(),
                money(distanceCost),
                money(timeCost),
                money(baseFare),
                money(surgedFare),
                money(fareWithTolls),
                money(discountAmount),
                money(fareAfterDiscount),
                money(taxAmount),
                money(total));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}

