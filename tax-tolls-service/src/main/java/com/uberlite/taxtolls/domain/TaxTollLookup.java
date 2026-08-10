package com.uberlite.taxtolls.domain;

import com.uberlite.common.dto.RouteDto;
import org.springframework.stereotype.Service;

import com.uberlite.taxtolls.repository.TaxRateRepository;
import com.uberlite.taxtolls.repository.TollSegmentRepository;
import com.uberlite.taxtolls.repository.entity.TaxRateEntity;
import com.uberlite.taxtolls.repository.entity.TollSegmentEntity;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxTollLookup {
    private final TaxRateRepository taxRateRepository;
    private final TollSegmentRepository tollSegmentRepository;

    public TaxTollLookup(TaxRateRepository taxRateRepository, TollSegmentRepository tollSegmentRepository) {
        this.taxRateRepository = taxRateRepository;
        this.tollSegmentRepository = tollSegmentRepository;
    }

    @Transactional(readOnly = true)
    public TaxTollInfo lookupByRegion(String region) {
        TaxRateEntity tax = taxRateRepository.findById(region).orElse(null);
        TollSegmentEntity toll = tollSegmentRepository.findById(region + "-DEFAULT").orElse(null);

        double taxRate = tax == null ? 0.0 : tax.getRate().doubleValue();
        double tollAmount = toll == null ? 0.0 : toll.getAmount().doubleValue();
        return new TaxTollInfo(region, taxRate, tollAmount);
    }

    /**
     * MVP placeholder: if route distance exceeds 20 km, return a flat toll.
     * Real toll-segment lookups can replace this without changing the endpoint contract.
     */
    public double estimateToll(RouteDto route) {
        long thresholdMeters = 20_000L;
        double flatToll = 2.50;
        return route.getDistanceMeters() > thresholdMeters ? flatToll : 0.0;
    }
}
