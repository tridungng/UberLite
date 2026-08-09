package com.uberlite.discountspromotions.domain;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class DiscountService {
    // Legacy in-memory support retained for backward compatibility
    private final Map<String, Double> activeDiscounts = new HashMap<>();

    public DiscountService() {
        // Initialize with sample promo rules
        activeDiscounts.put("FIRST_RIDE", 0.20); // 20% off first ride
        activeDiscounts.put("REFERRAL", 0.15);    // 15% off referral
    }

    public DiscountInfo lookupDiscount(String riderId, String promoCode) {
        double discountRate = 0.0;
        if (promoCode != null && activeDiscounts.containsKey(promoCode)) {
            discountRate = activeDiscounts.get(promoCode);
        }
        return new DiscountInfo(riderId, discountRate, promoCode);
    }
}
