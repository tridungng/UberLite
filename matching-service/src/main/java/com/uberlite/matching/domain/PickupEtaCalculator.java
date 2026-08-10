package com.uberlite.matching.domain;

import java.time.Duration;

/**
 * Converts a straight-line pickup distance into a pickup ETA.
 *
 * <p>Pure function, no Spring, no I/O — the ranking rule is the one piece of real logic in this
 * service and is unit-tested in isolation.
 *
 * <p>Paper Sec. 4.1 swap-out point: a real implementation would call time-estimation-service per
 * candidate for a traffic-aware ETA. For <em>ranking</em> that is not needed — this is a monotonic
 * function of distance, so it produces the same ordering as any distance-monotonic ETA model, at
 * zero extra network cost.
 */
public final class PickupEtaCalculator {

    private final double averageSpeedMps;
    private final double defaultDetourFactor;

    public PickupEtaCalculator(double averageSpeedMps, double defaultDetourFactor) {
        if (averageSpeedMps <= 0) {
            throw new IllegalArgumentException("averageSpeedMps must be > 0 but was " + averageSpeedMps);
        }
        if (defaultDetourFactor < 1.0) {
            throw new IllegalArgumentException(
                    "defaultDetourFactor must be >= 1.0 but was " + defaultDetourFactor);
        }
        this.averageSpeedMps = averageSpeedMps;
        this.defaultDetourFactor = defaultDetourFactor;
    }

    /**
     * @param straightDistanceKm haversine distance from driver to pickup, as returned by Route Service
     * @param detourFactor Route Service's detour factor, nullable — it only computes one when given an
     *     observed distance, which we don't have at match time. Falls back to the configured default
     *     rather than 1.0, so the ETA is not systematically optimistic.
     * @return whole-second pickup ETA, never negative
     */
    public long etaSeconds(double straightDistanceKm, Double detourFactor) {
        if (straightDistanceKm < 0 || !Double.isFinite(straightDistanceKm)) {
            throw new IllegalArgumentException("straightDistanceKm must be finite and >= 0");
        }
        double factor = (detourFactor == null || !Double.isFinite(detourFactor) || detourFactor < 1.0)
                ? defaultDetourFactor
                : detourFactor;
        double metres = straightDistanceKm * 1000.0 * factor;
        return Math.round(metres / averageSpeedMps);
    }

    public Duration eta(double straightDistanceKm, Double detourFactor) {
        return Duration.ofSeconds(etaSeconds(straightDistanceKm, detourFactor));
    }
}

