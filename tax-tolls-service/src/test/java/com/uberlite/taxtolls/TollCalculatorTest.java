package com.uberlite.taxtolls;

import com.uberlite.common.dto.RouteDto;
import com.uberlite.taxtolls.domain.TaxTollLookup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TollCalculatorTest {
    @Test
    void toll_applies_above_threshold() {
        TaxTollLookup lookup = new TaxTollLookup(null, null);
        double amount = lookup.estimateToll(new RouteDto(21_000L, List.of()));
        Assertions.assertEquals(2.5, amount, 0.001);
    }

    @Test
    void toll_zero_below_threshold() {
        TaxTollLookup lookup = new TaxTollLookup(null, null);
        double amount = lookup.estimateToll(new RouteDto(5_000L, List.of()));
        Assertions.assertEquals(0.0, amount, 0.001);
    }
}
