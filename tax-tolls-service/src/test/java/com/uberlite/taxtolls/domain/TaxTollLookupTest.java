package com.uberlite.taxtolls.domain;

import com.uberlite.taxtolls.repository.TaxRateRepository;
import com.uberlite.taxtolls.repository.TollSegmentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

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
        when(taxRateRepository.findById("CA"))
                .thenReturn(Optional.of(new com.uberlite.taxtolls.repository.entity.TaxRateEntity("CA", new BigDecimal("0.0725"))));
        when(tollSegmentRepository.findById("CA-DEFAULT"))
                .thenReturn(Optional.of(new com.uberlite.taxtolls.repository.entity.TollSegmentEntity("CA-DEFAULT", new BigDecimal("2.50"))));

        TaxTollInfo info = lookup.lookupByRegion("CA");

        assertEquals("CA", info.region);
        assertEquals(0.0725, info.taxRate);
        assertEquals(2.50, info.tollAmount);
    }

    @Test
    void lookupByRegionDefaultsForUnknownRegion() {
        when(taxRateRepository.findById("XX")).thenReturn(Optional.empty());
        when(tollSegmentRepository.findById("XX-DEFAULT")).thenReturn(Optional.empty());

        TaxTollInfo info = lookup.lookupByRegion("XX");

        assertEquals("XX", info.region);
        assertEquals(0.0, info.taxRate);
        assertEquals(0.0, info.tollAmount);
    }
}
