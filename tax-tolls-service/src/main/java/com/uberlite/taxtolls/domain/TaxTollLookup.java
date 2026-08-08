package com.uberlite.taxtolls.domain;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class TaxTollLookup {
    private final Map<String, Double> taxRates = new HashMap<>();
    private final Map<String, Double> tollsByRegion = new HashMap<>();

    public TaxTollLookup() {
        // Initialize with sample data
        taxRates.put("CA", 0.0725);
        taxRates.put("NY", 0.0800);
        taxRates.put("TX", 0.0625);
        taxRates.put("WA", 0.1025);
        
        tollsByRegion.put("CA", 2.50);
        tollsByRegion.put("NY", 5.00);
        tollsByRegion.put("TX", 0.0);
        tollsByRegion.put("WA", 1.75);
    }

    public TaxTollInfo lookupByRegion(String region) {
        double taxRate = taxRates.getOrDefault(region, 0.0);
        double tollAmount = tollsByRegion.getOrDefault(region, 0.0);
        return new TaxTollInfo(region, taxRate, tollAmount);
    }
}
