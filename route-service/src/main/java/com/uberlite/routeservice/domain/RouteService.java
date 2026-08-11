package com.uberlite.routeservice.domain;

import org.springframework.stereotype.Service;

/**
 * Route Service (ARCHITECTURE.md Sec. 2, "RS").
 *
 * <p>MVP simplification: no real road network. Distance is the great-circle (haversine) distance
 * between the two points; a caller that knows the real driven distance can ask for the detour
 * factor relating the two. Swapping in OSRM/Google (ARCHITECTURE.md Sec. 9) replaces this class
 * only, because the HTTP contract is expressed in terms of distance rather than how the distance
 * was obtained.
 */
@Service
public class RouteService {

    /** IUGG mean Earth radius, in kilometres. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * Great-circle distance between two WGS-84 points, in kilometres.
     */
    public double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Ratio of a real driven distance to the straight-line distance between the same two points.
     *
     * @return {@link Double#POSITIVE_INFINITY} when {@code straightKm} is zero or negative — the
     *     two points coincide, so no finite factor relates them. Callers must not use that as a
     *     multiplier.
     */
    public double detourFactor(double straightKm, double actualKm) {
        if (straightKm <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return actualKm / straightKm;
    }
}
