package com.uberlite.routeservice.service;

public class RouteService {
    private static final double EARTH_RADIUS_KM = 6371.0088;

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

    public double detourFactor(double straightKm, double actualKm) {
        if (straightKm <= 0) return Double.POSITIVE_INFINITY;
        return actualKm / straightKm;
    }
}
