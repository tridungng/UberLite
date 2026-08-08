package com.uberlite.taxtolls.domain;

public class TaxTollInfo {
    public String region;
    public double taxRate;
    public double tollAmount;

    public TaxTollInfo(String region, double taxRate, double tollAmount) {
        this.region = region;
        this.taxRate = taxRate;
        this.tollAmount = tollAmount;
    }
}
