package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class PriceQuoteDto {
    private final double amount;
    private final String currency;
    private final Map<String, Object> breakdown;

    @JsonCreator
    public PriceQuoteDto(@JsonProperty("amount") double amount,
                         @JsonProperty("currency") String currency,
                         @JsonProperty("breakdown") Map<String, Object> breakdown) {
        this.amount = amount;
        this.currency = currency;
        this.breakdown = breakdown;
    }

    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Map<String, Object> getBreakdown() { return breakdown; }
}
