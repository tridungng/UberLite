package com.uberlite.priceestimation.api;

import com.uberlite.common.dto.PriceEstimateRequestDto;
import com.uberlite.common.dto.PriceQuoteDto;
import com.uberlite.priceestimation.domain.PriceEstimationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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

    @PostMapping(
            path = "/price-estimates",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PriceQuoteDto> estimate(@Valid @RequestBody PriceEstimateRequestDto request) {
        return ResponseEntity.ok(priceEstimationService.estimatePrice(request));
    }
}
