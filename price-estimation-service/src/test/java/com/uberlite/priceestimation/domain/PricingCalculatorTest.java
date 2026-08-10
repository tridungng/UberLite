// package com.uberlite.priceestimation.domain;
//
// import org.junit.jupiter.api.Test;
// import static org.junit.jupiter.api.Assertions.assertEquals;
//
// class PricingCalculatorTest {
//    private final PricingCalculator calculator = new PricingCalculator();
//
//    @Test
//    void calculatePriceReturnsValidEstimate() {
//        PriceEstimate estimate = calculator.calculatePrice(
//            "trip-1", 5.0, 15.0, 1.0, 0.0, 0.0
//        );
//
//        assertEquals("trip-1", estimate.tripId);
//        assertEquals(2.0, estimate.baseFare);
//        assertEquals(7.5, estimate.distanceFare);
//        assertEquals(4.5, estimate.timeFare);
//        assertEquals(0.0, estimate.discountAmount);
//    }
//
//    @Test
//    void calculatePriceWithSurge() {
//        PriceEstimate estimate = calculator.calculatePrice(
//            "trip-2", 10.0, 20.0, 1.5, 2.0, 0.0
//        );
//
//        assertEquals("trip-2", estimate.tripId);
//        assertEquals(true, estimate.surgeFare > 0);
//    }
//
//    @Test
//    void calculatePriceWithDiscount() {
//        PriceEstimate estimate = calculator.calculatePrice(
//            "trip-3", 5.0, 15.0, 1.0, 0.0, 0.2
//        );
//
//        assertEquals("trip-3", estimate.tripId);
//        assertEquals(true, estimate.discountAmount > 0);
//    }
// }
