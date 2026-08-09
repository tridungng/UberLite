package com.uberlite.priceestimation.api;

public class PriceEstimateRequest {
    public String riderId;
    public int riderTripCount;
    public LocationDto pickup;
    public LocationDto dropoff;

    public static class LocationDto {
        public double lat;
        public double lon;
    }
}
