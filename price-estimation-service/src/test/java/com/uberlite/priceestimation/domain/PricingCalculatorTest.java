package com.uberlite.priceestimation.domain;

import com.uberlite.priceestimation.config.PricingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Formula math in isolation — no Spring, no mocks, no I/O.
 * Reference: ARCHITECTURE.md Sec. 8, p = ((cd*d + ct*t) * s + cm) * (1 - discountPct) * (1 + taxRate)
 */
class PricingCalculatorTest {

    private static PricingCalculator calculatorWith(double costPerKm, double costPerMinute) {
        PricingProperties properties = new PricingProperties();
        properties.setCostPerKm(costPerKm);
        properties.setCostPerMinute(costPerMinute);
        return new PricingCalculator(properties);
    }

    @Test
    @DisplayName("applies every term of the paper's formula in order")
    void appliesFullFormula() {
        PricingCalculator calculator = calculatorWith(1.0, 0.5);

        // d=11, t=20, s=1.5, cm=2.5, discount=20%, tax=8%
        //   base            = 1.0*11 + 0.5*20        = 21.00
        //   after surge     = 21.00 * 1.5            = 31.50
        //   after tolls     = 31.50 + 2.50           = 34.00
        //   after discount  = 34.00 * (1 - 0.20)     = 27.20
        //   after tax       = 27.20 * (1 + 0.08)     = 29.376 -> 29.38
        PriceBreakdown breakdown = calculator.calculate(
                new PricingInputs(11.0, 20.0, 1.5, 2.5, 0.20, 0.08));

        assertThat(breakdown.distanceCost()).isEqualByComparingTo("11.00");
        assertThat(breakdown.timeCost()).isEqualByComparingTo("10.00");
        assertThat(breakdown.baseFare()).isEqualByComparingTo("21.00");
        assertThat(breakdown.surgedFare()).isEqualByComparingTo("31.50");
        assertThat(breakdown.fareWithTolls()).isEqualByComparingTo("34.00");
        assertThat(breakdown.discountAmount()).isEqualByComparingTo("6.80");
        assertThat(breakdown.fareAfterDiscount()).isEqualByComparingTo("27.20");
        assertThat(breakdown.taxAmount()).isEqualByComparingTo("2.18");
        assertThat(breakdown.total()).isEqualByComparingTo("29.38");
    }

    @Test
    @DisplayName("neutral surge, no tolls, no discount and no tax reduce to cd*d + ct*t")
    void neutralInputsReduceToBaseFare() {
        PricingCalculator calculator = calculatorWith(2.0, 0.25);

        PriceBreakdown breakdown = calculator.calculate(
                new PricingInputs(10.0, 40.0, 1.0, 0.0, 0.0, 0.0));

        assertThat(breakdown.total()).isEqualByComparingTo("30.00"); // 20.00 + 10.00
        assertThat(breakdown.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(breakdown.taxAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a 100% discount still charges tolls-inclusive zero, never a negative fare")
    void fullDiscountNeverGoesNegative() {
        PricingCalculator calculator = calculatorWith(1.5, 0.3);

        PriceBreakdown breakdown = calculator.calculate(
                new PricingInputs(12.0, 30.0, 2.0, 5.0, 1.0, 0.2));

        assertThat(breakdown.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("tolls are added after surge, so surge never multiplies the toll")
    void surgeDoesNotMultiplyTolls() {
        PricingCalculator calculator = calculatorWith(1.0, 0.0);

        PriceBreakdown withToll = calculator.calculate(new PricingInputs(10.0, 0.0, 3.0, 10.0, 0.0, 0.0));

        // 10*1.0 = 10, *3 = 30, + 10 toll = 40 (not (10 + 10) * 3 = 60)
        assertThat(withToll.total()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("money is rounded half-up to two decimals exactly once, at the end")
    void roundsHalfUpToCents() {
        PricingCalculator calculator = calculatorWith(0.1, 0.1);

        // 0.1*0.1 + 0.1*0.1 = 0.02 exactly; naive double math yields 0.020000000000000004
        PriceBreakdown breakdown = calculator.calculate(new PricingInputs(0.1, 0.1, 1.0, 0.0, 0.0, 0.0));

        assertThat(breakdown.total()).isEqualTo(new BigDecimal("0.02"));
        assertThat(breakdown.total().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects downstream values that cannot produce a sane price")
    void rejectsImplausibleInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PricingInputs(-1.0, 10.0, 1.0, 0.0, 0.0, 0.0))
                .withMessageContaining("distanceKm");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PricingInputs(1.0, 10.0, 0.5, 0.0, 0.0, 0.0))
                .withMessageContaining("surgeMultiplier");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PricingInputs(1.0, 10.0, 1.0, 0.0, 1.5, 0.0))
                .withMessageContaining("discountPct");
    }

    @Test
    @DisplayName("breakdown exposes the configured constants so a quote is reproducible")
    void breakdownIncludesConfiguredConstants() {
        PricingCalculator calculator = calculatorWith(1.75, 0.45);

        var map = calculator.calculate(new PricingInputs(5.0, 10.0, 1.0, 0.0, 0.0, 0.0)).toMap();

        assertThat(map).containsEntry("costPerKm", 1.75).containsEntry("costPerMinute", 0.45);
    }
}

