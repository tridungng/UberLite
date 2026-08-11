package com.uberlite.timeestimation.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uberlite.timeestimation.config.TimeEstimationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatmapServiceTest {

    private HeatmapService service;

    @BeforeEach
    void setUp() {
        service = new HeatmapService(properties("heatmap-seed.json"), new ObjectMapper());
    }

    private static TimeEstimationProperties properties(String classpathSeed) {
        TimeEstimationProperties properties = new TimeEstimationProperties();
        properties.setBaseMinutes(10.0);
        properties.setHeatmapSeed(new ClassPathResource(classpathSeed));
        return properties;
    }

    @Test
    void seededCellIsSlowerThanAnUnseededOne() {
        double seeded = service.estimateMinutes(1.2, 1.3);   // floor -> "1,1" -> multiplier 3.0
        double unseeded = service.estimateMinutes(80.0, 80.0);

        assertTrue(seeded > unseeded, "a seeded (congested) cell must estimate longer than a default one");
    }

    @Test
    void unseededCellUsesTheBaseDurationUnchanged() {
        assertEquals(10.0, service.estimateMinutes(80.0, 80.0), 1e-9);
    }

    @Test
    void multiplierIsAppliedToTheConfiguredBaseDuration() {
        assertEquals(3.0, service.multiplierFor(1.2, 1.3), 1e-9);
        assertEquals(30.0, service.estimateMinutes(1.2, 1.3), 1e-9);
    }

    @Test
    void negativeCoordinatesFloorDownwardsRatherThanTowardsZero() {
        // -0.5 must key to cell "-1,-1", not "0,0". Truncating instead of flooring would silently
        // hand the southern/western hemisphere a neighbouring cell's traffic.
        assertEquals(1.0, service.multiplierFor(-0.5, -0.5), 1e-9);
    }

    @Test
    void aMissingSeedFileDegradesToNoSlowdownInsteadOfFailingToStart() {
        HeatmapService withoutSeed =
                new HeatmapService(properties("no-such-heatmap.json"), new ObjectMapper());

        assertEquals(10.0, withoutSeed.estimateMinutes(1.2, 1.3), 1e-9);
    }
}
