package com.uberlite.matching.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PickupEtaCalculatorTest {

    /** 10 m/s, no detour inflation — keeps the arithmetic checkable by hand. */
    private final PickupEtaCalculator calculator = new PickupEtaCalculator(10.0, 1.0);

    @Test
    @DisplayName("distance / speed, in whole seconds")
    void computesEtaFromDistanceAndSpeed() {
        assertThat(calculator.etaSeconds(1.0, 1.0)).isEqualTo(100); // 1000 m / 10 m/s
        assertThat(calculator.etaSeconds(0.0, 1.0)).isZero();
    }

    @Test
    @DisplayName("a null detourFactor falls back to the configured default, not 1.0")
    void nullDetourFactorUsesDefault() {
        PickupEtaCalculator withDetour = new PickupEtaCalculator(10.0, 1.5);

        // 1 km * 1.5 / 10 m/s = 150s, not the 100s a naive 1.0 fallback would give.
        assertThat(withDetour.etaSeconds(1.0, null)).isEqualTo(150);
    }

    @Test
    @DisplayName("a nonsensical detourFactor (< 1.0) is ignored — a road route is never shorter")
    void rejectsSubUnitDetourFactor() {
        PickupEtaCalculator withDetour = new PickupEtaCalculator(10.0, 1.5);

        assertThat(withDetour.etaSeconds(1.0, 0.5)).isEqualTo(150);
    }

    @Test
    @DisplayName("ETA is monotonic in distance, which is all the ranking relies on")
    void etaIsMonotonicInDistance() {
        assertThat(calculator.etaSeconds(1.0, 1.0))
                .isLessThan(calculator.etaSeconds(2.0, 1.0));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new PickupEtaCalculator(0.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PickupEtaCalculator(10.0, 0.9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.etaSeconds(-1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

