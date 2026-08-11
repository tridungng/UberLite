package com.uberlite.timeestimation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Tunables for the Time Estimation Service, bound from the {@code time-estimation.*} prefix.
 *
 * <p>Follows the same {@code config/*Properties} pattern as matching-service and
 * price-estimation-service: no magic number lives inline in the domain classes.
 */
@ConfigurationProperties(prefix = "time-estimation")
public class TimeEstimationProperties {

    /** Baseline trip duration in minutes before the heat map multiplier is applied. */
    private double baseMinutes = 10.0;

    /** JSON document mapping a {@code "<lat>,<lon>"} cell key to a traffic multiplier. */
    private Resource heatmapSeed;

    public double getBaseMinutes() {
        return baseMinutes;
    }

    public void setBaseMinutes(double baseMinutes) {
        this.baseMinutes = baseMinutes;
    }

    public Resource getHeatmapSeed() {
        return heatmapSeed;
    }

    public void setHeatmapSeed(Resource heatmapSeed) {
        this.heatmapSeed = heatmapSeed;
    }
}

