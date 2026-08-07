package com.uberlite.common.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class H3UtilTest {
    @Test
    public void latLngToCellIsStable() {
        double lat = 37.775938728915946;
        double lon = -122.41795063018799;
        String cell1 = H3Util.latLngToCell(lat, lon, 8);
        String cell2 = H3Util.latLngToCell(lat, lon, 8);
        assertEquals(cell1, cell2);
    }

    @Test
    public void gridDiskNeighborsCountForK1() {
        double lat = 37.775938728915946;
        double lon = -122.41795063018799;
        String cell = H3Util.latLngToCell(lat, lon, 8);
        List<String> disk = H3Util.gridDisk(cell, 1);
        // For a hex cell with a full neighborhood, k=1 gives 7 cells (self + 6 neighbors)
        assertTrue(disk.size() >= 7);
        assertTrue(disk.contains(cell));
    }
}
