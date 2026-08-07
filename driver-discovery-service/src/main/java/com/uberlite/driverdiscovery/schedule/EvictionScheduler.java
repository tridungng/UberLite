package com.uberlite.driverdiscovery.schedule;

import com.uberlite.driverdiscovery.service.DriverStoreService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

/**
 * Scheduler that periodically evicts stale drivers from the driver store.
 * <p>
 * Designed to be lightweight: it delegates eviction logic to DriverStoreService
 * and only triggers on a fixed interval.
 */
@Component
public class EvictionScheduler {
    private final DriverStoreService store;
    private static final long STALE_SECONDS = Duration.ofMinutes(2).getSeconds();

    /**
     * Create a scheduler bound to the provided DriverStoreService.
     *
     * @param store service used to perform eviction operations
     */
    public EvictionScheduler(DriverStoreService store) {
        this.store = store;
    }

    /**
     * Trigger eviction of drivers that have not been seen recently.
     * <p>
     * This method is scheduled to run periodically; it forwards the eviction
     * threshold to the store service and ignores the returned list.
     */
    @Scheduled(fixedDelay = 30_000)
    public void evict() {
        store.evictStaleAndReturnRemoved(STALE_SECONDS);
    }
}
