package com.uberlite.matching.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable matching knobs, bound from {@code matching.*} in {@code application.yml}.
 *
 * <p>The issue specifies the search radius and candidate limit as configurable; they are market
 * levers (a dense city wants 1km/20 candidates, a rural region 10km/5) and must be changeable
 * without a rebuild.
 */
@Validated
@ConfigurationProperties(prefix = "matching")
public class MatchingProperties {

    /** Radius passed to driver-discovery-service {@code /drivers/nearby}. */
    @DecimalMin(value = "1.0", message = "matching.radius-meters must be >= 1")
    private double radiusMeters = 3000;

    /**
     * Max candidates to rank. Bounded because every candidate costs one route-service round trip,
     * so this is directly the fan-out of a rider-facing synchronous call.
     */
    @Min(value = 1, message = "matching.candidate-limit must be >= 1")
    @Max(value = 50, message = "matching.candidate-limit must be <= 50 to bound route-service fan-out")
    private int candidateLimit = 10;

    /**
     * Average vehicle speed in metres per second, used for the local straight-line pickup ETA.
     *
     * <p>Default 8.33 m/s == 30 km/h, a typical urban average. This service deliberately does NOT
     * call time-estimation-service: the ETA here only has to <em>rank</em> candidates, and a
     * monotonic function of distance ranks identically to a traffic-aware one while costing N fewer
     * network calls. The rider-facing ETA is TES's job.
     */
    @DecimalMin(value = "0.1", message = "matching.average-speed-mps must be > 0")
    private double averageSpeedMps = 8.33;

    /**
     * Straight-line -> road-distance correction. Route Service only returns a {@code detourFactor}
     * when given an observed distance, which we don't have at match time; without this the pickup
     * ETA is systematically optimistic. Mirrors {@code pricing.default-detour-factor}.
     */
    @DecimalMin(value = "1.0", message = "matching.default-detour-factor must be >= 1.0")
    private double defaultDetourFactor = 1.3;

    public double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        this.candidateLimit = candidateLimit;
    }

    public double getAverageSpeedMps() {
        return averageSpeedMps;
    }

    public void setAverageSpeedMps(double averageSpeedMps) {
        this.averageSpeedMps = averageSpeedMps;
    }

    public double getDefaultDetourFactor() {
        return defaultDetourFactor;
    }

    public void setDefaultDetourFactor(double defaultDetourFactor) {
        this.defaultDetourFactor = defaultDetourFactor;
    }
}

