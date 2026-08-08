package com.uberlite.surgepricing.domain;

import com.uberlite.surgepricing.client.DriverDiscoveryClient;
import com.uberlite.surgepricing.repository.SurgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrator for surge pricing operations.
 * Coordinates between driver discovery, pending request counts, and multiplier computation.
 */
@Service
public class SurgeComputationService {
    private static final Logger logger = LoggerFactory.getLogger(SurgeComputationService.class);
    private final SurgeRepository repository;
    private final DriverDiscoveryClient driverDiscoveryClient;
    private final SurgeMultiplierService multiplierService;

    public SurgeComputationService(
            SurgeRepository repository,
            DriverDiscoveryClient driverDiscoveryClient,
            SurgeMultiplierService multiplierService) {
        this.repository = repository;
        this.driverDiscoveryClient = driverDiscoveryClient;
        this.multiplierService = multiplierService;
    }

    /**
     * Get the current surge multiplier for an H3 cell.
     * If cached and fresh, return cached value.
     * Otherwise, query driver-discovery for active drivers, compute, cache, and return.
     * <p>
     * Fallback: if driver-discovery is unavailable, activeDrivers defaults to 0,
     * which is treated as 1 in computeMultiplier (no surge, multiplier = pending/1).
     *
     * @param h3Cell H3 cell identifier
     * @return surge multiplier [1.0, 3.0]
     */
    public double getMultiplier(String h3Cell) {
        // Try cache first
        Double cached = repository.getCachedMultiplier(h3Cell);
        if (cached != null) {
            logger.info("Returning cached multiplier {} for cell {}", cached, h3Cell);
            return cached;
        }

        // Compute on-demand
        long pendingRequests = repository.getPendingRequests(h3Cell);
        long activeDrivers = countActiveDrivers(h3Cell);
        double multiplier = multiplierService.computeMultiplier(pendingRequests, activeDrivers);

        // Cache result
        long now = System.currentTimeMillis();
        repository.cacheMultiplier(h3Cell, multiplier, now);

        logger.info("Computed multiplier {} for cell {} (pending={}, drivers={})", 
                   multiplier, h3Cell, pendingRequests, activeDrivers);
        return multiplier;
    }

    /**
     * Increment the pending request count for a cell.
     */
    public void incrementPendingRequest(String h3Cell) {
        long count = repository.incrementPendingRequests(h3Cell);
        logger.info("Incremented pending requests for cell {} to {}", h3Cell, count);
    }

    /**
     * Decrement the pending request count for a cell.
     */
    public void decrementPendingRequest(String h3Cell) {
        long count = repository.decrementPendingRequests(h3Cell);
        logger.info("Decremented pending requests for cell {} to {}", h3Cell, count);
    }

    /**
     * Query driver-discovery-service for active drivers in a cell.
     * If the service is unreachable, the fallback returns an empty list,
     * and activeDrivers count will be 0 (treated as 1 in computeMultiplier).
     */
    private long countActiveDrivers(String h3Cell) {
        try {
            var drivers = driverDiscoveryClient.getDriversInCell(h3Cell, 0, 100);
            long count = drivers.size();
            logger.debug("Found {} active drivers in cell {}", count, h3Cell);
            return count;
        } catch (Exception e) {
            logger.warn("Failed to query driver-discovery-service for cell {}; treating as no drivers available", h3Cell, e);
            return 0L;
        }
    }
}
