package com.uberlite.discountspromotions.api;

import com.uberlite.common.dto.DiscountEvaluationRequestDto;
import com.uberlite.common.dto.DiscountQuoteDto;
import com.uberlite.discountspromotions.domain.DiscountEvaluator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiscountsController {
    private final DiscountEvaluator evaluator;

    public DiscountsController(DiscountEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    // Request/response shapes live in `common` — Price Estimation Service consumes this endpoint,
    // so the contract must not be duplicated per module.
    @PostMapping("/discounts/evaluate")
    public ResponseEntity<DiscountQuoteDto> evaluate(@RequestBody DiscountEvaluationRequestDto req) {
        double discountPct = evaluator.evaluate(req.getRiderId(), req.getRiderTripCount());
        return ResponseEntity.ok(new DiscountQuoteDto(discountPct));
    }
}
