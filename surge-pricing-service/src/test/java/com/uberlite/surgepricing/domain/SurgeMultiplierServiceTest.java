package com.uberlite.surgepricing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SurgeMultiplierService unit tests")
class SurgeMultiplierServiceTest {
    private final SurgeMultiplierService service = new SurgeMultiplierService();

    @Test
    @DisplayName("clamp returns value unchanged if within bounds [1.0, 3.0]")
    void testClampWithinBounds() {
        assertEquals(1.5, service.clamp(1.5), 0.001);
        assertEquals(2.0, service.clamp(2.0), 0.001);
        assertEquals(1.0, service.clamp(1.0), 0.001);
        assertEquals(3.0, service.clamp(3.0), 0.001);
    }

    @Test
    @DisplayName("clamp returns minimum 1.0 for values below lower bound")
    void testClampBelowMin() {
        assertEquals(1.0, service.clamp(0.5), 0.001);
        assertEquals(1.0, service.clamp(0.0), 0.001);
        assertEquals(1.0, service.clamp(-1.0), 0.001);
    }

    @Test
    @DisplayName("clamp returns maximum 3.0 for values above upper bound")
    void testClampAboveMax() {
        assertEquals(3.0, service.clamp(4.0), 0.001);
        assertEquals(3.0, service.clamp(10.0), 0.001);
        assertEquals(3.0, service.clamp(100.0), 0.001);
    }

    @Test
    @DisplayName("computeMultiplier with no pending requests returns 1.0 (baseline)")
    void testComputeMultiplierNoPending() {
        double result = service.computeMultiplier(0, 10);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    @DisplayName("computeMultiplier with equal pending and drivers returns 1.0")
    void testComputeMultiplierBalanced() {
        double result = service.computeMultiplier(10, 10);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    @DisplayName("computeMultiplier with more pending than drivers returns higher multiplier")
    void testComputeMultiplierHighDemand() {
        double result = service.computeMultiplier(30, 10);
        assertEquals(3.0, result, 0.001); // 30/10 = 3.0, clamped to max 3.0
    }

    @Test
    @DisplayName("computeMultiplier with moderate demand")
    void testComputeMultiplierModerate() {
        double result = service.computeMultiplier(15, 10);
        assertEquals(1.5, result, 0.001); // 15/10 = 1.5
    }

    @Test
    @DisplayName("computeMultiplier with zero active drivers and some pending returns max multiplier")
    void testComputeMultiplierZeroDrivers() {
        double result = service.computeMultiplier(10, 0);
        assertEquals(3.0, result, 0.001); // 10/1 = 10.0, clamped to 3.0
    }

    @Test
    @DisplayName("computeMultiplier with zero active drivers and high pending returns max multiplier")
    void testComputeMultiplierZeroDriversHighPending() {
        double result = service.computeMultiplier(50, 0);
        assertEquals(3.0, result, 0.001); // 50/1 = 50.0, clamped to 3.0
    }

    @Test
    @DisplayName("computeMultiplier with very small pending count")
    void testComputeMultiplierSmallPending() {
        double result = service.computeMultiplier(1, 100);
        assertEquals(1.0, result, 0.001); // 1/100 = 0.01, clamped to 1.0
    }
}
