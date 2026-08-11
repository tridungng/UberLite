package com.uberlite.routeservice.domain;

import com.uberlite.routeservice.domain.RouteService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteServiceTest {
    private final RouteService svc = new RouteService();

    @Test
    void testHaversineAtEquator() {
        double d = svc.haversineKm(0.0, 0.0, 0.0, 1.0);
        // ~111.32 km per degree at equator
        assertTrue(Math.abs(d - 111.32) < 1.0, "distance per degree should be ~111.32km");
    }

    @Test
    void testDetourFactor() {
        double f = svc.detourFactor(100.0, 120.0);
        assertEquals(1.2, f, 1e-9);
    }
}
