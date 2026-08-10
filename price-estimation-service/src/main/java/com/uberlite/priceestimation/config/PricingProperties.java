package com.uberlite.priceestimation.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable pricing constants, bound from {@code pricing.*} in {@code application.yml}.
 *
 * <p>Deliberately not hardcoded in {@code PricingCalculator}: {@code cd}/{@code ct} are business
 * levers that change per market and per experiment, and must be settable without a rebuild.
 */
@Validated
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    /** {@code cd} in the paper's formula — cost per kilometre. */
    @DecimalMin(value = "0.0", message = "pricing.cost-per-km must be >= 0")
    private double costPerKm = 1.5;

    /** {@code ct} in the paper's formula — cost per minute. */
    @DecimalMin(value = "0.0", message = "pricing.cost-per-minute must be >= 0")
    private double costPerMinute = 0.3;

    /**
     * Fallback detour factor used when Route Service returns a null {@code detourFactor} (it only
     * computes one when the caller supplies an observed distance, which we cannot at quote time).
     * Must be >= 1.0: a road route is never shorter than the straight line.
     */
    @DecimalMin(value = "1.0", message = "pricing.default-detour-factor must be >= 1.0")
    private double defaultDetourFactor = 1.3;

    /**
     * Region used for the tax lookup. The MVP is single-region (ARCHITECTURE.md Sec. 9), so this is
     * config rather than derived from geography. An H3 cell is NOT a region id — passing one would
     * always miss the {@code tax_rates} table and silently yield a 0% tax rate.
     */
    @NotBlank(message = "pricing.region-id must not be blank")
    private String regionId = "default";

    /** ISO-4217 code echoed back on the quote so callers never have to assume a currency. */
    @NotBlank(message = "pricing.currency must not be blank")
    private String currency = "USD";

    public double getCostPerKm() {
        return costPerKm;
    }

    public void setCostPerKm(double costPerKm) {
        this.costPerKm = costPerKm;
    }

    public double getCostPerMinute() {
        return costPerMinute;
    }

    public void setCostPerMinute(double costPerMinute) {
        this.costPerMinute = costPerMinute;
    }

    public double getDefaultDetourFactor() {
        return defaultDetourFactor;
    }

    public void setDefaultDetourFactor(double defaultDetourFactor) {
        this.defaultDetourFactor = defaultDetourFactor;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}

