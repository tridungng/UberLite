package com.uberlite.timeestimation;

import com.uberlite.timeestimation.service.HeatmapService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HeatmapServiceTest {
    private final HeatmapService svc = new HeatmapService();

    @Test
    public void seededCellProducesLargerEstimate() {
        double seeded = svc.estimateMinutes(1.2, 1.3); // floor -> 1,1 -> multiplier 3.0
        double defaultCell = svc.estimateMinutes(99.0, 99.0);
        assertTrue(seeded > defaultCell, "seeded cell should produce larger estimate than default");
    }
}
