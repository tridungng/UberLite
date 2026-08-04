package com.uberlite.common.geo;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.GeoCoord;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thin wrapper around H3. Exposes only what services need.
 */
public final class H3Util {
    public static final int DEFAULT_RESOLUTION = 8;
    private static final H3Core h3;

    static {
        try {
            h3 = H3Core.newInstance();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private H3Util() {}

    public static long latLngToCell(double lat, double lon, int resolution) {
        return h3.latLngToCell(lat, lon, resolution);
    }

    public static long latLngToCell(double lat, double lon) {
        return latLngToCell(lat, lon, DEFAULT_RESOLUTION);
    }

    public static List<Long> gridDisk(long cell, int k) {
        Set<Long> ring = h3.kRing(cell, k);
        return ring.stream().collect(Collectors.toList());
    }

    public static GeoCoord cellToLatLng(long cell) {
        return h3.cellToLatLng(cell);
    }
}
