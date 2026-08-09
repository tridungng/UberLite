package com.uberlite.taxtolls.domain;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

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

    /** Simple toll estimate placeholder: if distanceKm > threshold => flat toll else 0.0 */
    public double estimateTollByDistance(double distanceKm) {
        double thresholdKm = 20.0;
        double flatToll = 2.50;
        return distanceKm > thresholdKm ? flatToll : 0.0;
    }
}
