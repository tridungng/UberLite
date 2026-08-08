package com.uberlite.priceestimation.domain;

public class PriceEstimate {
    public String tripId;
    public double baseFare;
    public double distanceFare;
    public double timeFare;
    public double surgeFare;
    public double tollFare;
    public double discountAmount;
    public double taxAmount;
    public double totalFare;

    public PriceEstimate(String tripId, double baseFare, double distanceFare, double timeFare,
                        double surgeFare, double tollFare, double discountAmount, 
                        double taxAmount, double totalFare) {
        this.tripId = tripId;
        this.baseFare = baseFare;
        this.distanceFare = distanceFare;
        this.timeFare = timeFare;
        this.surgeFare = surgeFare;
        this.tollFare = tollFare;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
        this.totalFare = totalFare;
    }
}
