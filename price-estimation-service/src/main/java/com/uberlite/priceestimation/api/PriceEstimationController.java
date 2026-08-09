package com.uberlite.priceestimation.api;

import com.uberlite.priceestimation.service.PriceEstimationService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceEstimationController {
    private final PriceEstimationService priceEstimationService;

    public PriceEstimationController(PriceEstimationService priceEstimationService) {
        this.priceEstimationService = priceEstimationService;
    }

    @PostMapping("/price-estimates")
    public ResponseEntity<PriceQuoteDto> estimate(@RequestBody PriceEstimateRequest req) {
        PriceQuoteDto quote = priceEstimationService.estimatePrice(req);
        return ResponseEntity.ok(quote);
    }
}
