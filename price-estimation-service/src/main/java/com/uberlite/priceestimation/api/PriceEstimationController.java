package com.uberlite.priceestimation.api;

import com.uberlite.priceestimation.domain.PriceEstimate;
import com.uberlite.priceestimation.domain.PricingCalculator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceEstimationController {
    private final PricingCalculator pricingCalculator;

    public PriceEstimationController(PricingCalculator pricingCalculator) {
        this.pricingCalculator = pricingCalculator;
    }

    @PostMapping("/price/estimate")
    public ResponseEntity<PriceEstimate> estimate(
            @RequestParam String tripId,
            @RequestParam double distanceKm,
            @RequestParam double estimatedMinutes,
            @RequestParam(defaultValue = "1.0") double surgeMultiplier,
            @RequestParam(defaultValue = "0.0") double tollAmount,
            @RequestParam(defaultValue = "0.0") double discountRate) {
        
        PriceEstimate estimate = pricingCalculator.calculatePrice(
            tripId, distanceKm, estimatedMinutes, surgeMultiplier, tollAmount, discountRate
        );
        return ResponseEntity.ok(estimate);
    }
}
