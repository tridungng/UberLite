package com.uberlite.taxtolls.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxTollLookupTest {
    private final TaxTollLookup lookup = new TaxTollLookup();

    @Test
    void lookupByRegionReturnsTaxAndToll() {
        TaxTollInfo info = lookup.lookupByRegion("CA");
        
        assertEquals("CA", info.region);
        assertEquals(0.0725, info.taxRate);
        assertEquals(2.50, info.tollAmount);
    }

    @Test
    void lookupByRegionDefaultsForUnknownRegion() {
        TaxTollInfo info = lookup.lookupByRegion("XX");
        
        assertEquals("XX", info.region);
        assertEquals(0.0, info.taxRate);
        assertEquals(0.0, info.tollAmount);
    }
}
