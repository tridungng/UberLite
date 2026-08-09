package com.uberlite.priceestimation.api;

import java.util.Map;

public class PriceQuoteDto {
    public double amount;
    public Map<String, Object> breakdown;

    public PriceQuoteDto() {}

    public PriceQuoteDto(double amount, Map<String, Object> breakdown) {
        this.amount = amount;
        this.breakdown = breakdown;
    }
}
