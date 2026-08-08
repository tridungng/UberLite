package com.uberlite.discountspromotions.api;

import com.uberlite.discountspromotions.domain.DiscountInfo;
import com.uberlite.discountspromotions.domain.DiscountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiscountsController {
    private final DiscountService discountService;

    public DiscountsController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @GetMapping("/discount/lookup")
    public ResponseEntity<DiscountInfo> lookup(
            @RequestParam String riderId,
            @RequestParam(required = false) String promoCode) {
        DiscountInfo discount = discountService.lookupDiscount(riderId, promoCode);
        return ResponseEntity.ok(discount);
    }
}
