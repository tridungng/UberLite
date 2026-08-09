package com.uberlite.discountspromotions.api;

import com.uberlite.discountspromotions.domain.DiscountEvaluator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DiscountsController {
    private final DiscountEvaluator evaluator;

    public DiscountsController(DiscountEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @PostMapping("/discounts/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@RequestBody Map<String, Object> req) {
        String riderId = (String) req.get("riderId");
        int riderTripCount = 0;
        if (req.get("riderTripCount") instanceof Number) {
            riderTripCount = ((Number) req.get("riderTripCount")).intValue();
        }
        double discountPct = evaluator.evaluate(riderId, riderTripCount);
        return ResponseEntity.ok(Map.of("discountPct", discountPct));
    }
}
