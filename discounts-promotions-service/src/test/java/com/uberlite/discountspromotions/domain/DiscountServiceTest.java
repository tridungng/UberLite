package com.uberlite.discountspromotions.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountServiceTest {
    private final DiscountService service = new DiscountService();

    @Test
    void lookupDiscountReturnsFlatRateForPromo() {
        DiscountInfo info = service.lookupDiscount("rider-1", "FIRST_RIDE");
        
        assertEquals("rider-1", info.riderId);
        assertEquals(0.20, info.discountPercentage);
        assertEquals("FIRST_RIDE", info.promoCode);
    }

    @Test
    void lookupDiscountReturnsZeroForUnknownPromo() {
        DiscountInfo info = service.lookupDiscount("rider-2", "UNKNOWN");
        
        assertEquals("rider-2", info.riderId);
        assertEquals(0.0, info.discountPercentage);
    }
}
