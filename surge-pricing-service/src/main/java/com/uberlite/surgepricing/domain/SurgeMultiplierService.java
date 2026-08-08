package com.uberlite.surgepricing.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Business logic for surge pricing multiplier computation.
 * <p>
 * Paper reference: Sec. 4.1 "Surge Pricing Service (SPS)"
 * Formula: multiplier = clamp(pendingRequests / max(activeDrivers, 1), 1.0, 3.0)
 */
@Service
public class SurgeMultiplierService {
    private static final Logger logger = LoggerFactory.getLogger(SurgeMultiplierService.class);
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 3.0;

    /**
     * Clamp a value within the surge pricing bounds.
     *
     * @param value the raw surge ratio (pending / active)
     * @return value clamped to [1.0, 3.0]
     */
    public double clamp(double value) {
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
    }

    /**
     * Compute the surge multiplier for a given ratio of pending requests to active drivers.
     *
     * @param pendingRequests count of pending requests in this H3 cell
     * @param activeDrivers   count of active drivers in this H3 cell (fallback: 1)
     * @return surge multiplier [1.0, 3.0]
     */
    public double computeMultiplier(long pendingRequests, long activeDrivers) {
        // Avoid divide-by-zero: if no active drivers, treat as 1 (no surge)
        long denominator = Math.max(activeDrivers, 1L);
        double ratio = (double) pendingRequests / denominator;
        return clamp(ratio);
    }
}
