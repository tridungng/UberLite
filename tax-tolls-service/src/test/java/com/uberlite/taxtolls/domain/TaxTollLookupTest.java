package com.uberlite.taxtolls.domain;

import com.uberlite.taxtolls.repository.TaxRateRepository;
import com.uberlite.taxtolls.repository.TollSegmentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class TaxTollLookupTest {
    @Mock
    private TaxRateRepository taxRateRepository;

    @Mock
    private TollSegmentRepository tollSegmentRepository;

    @InjectMocks
    private TaxTollLookup lookup;

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
