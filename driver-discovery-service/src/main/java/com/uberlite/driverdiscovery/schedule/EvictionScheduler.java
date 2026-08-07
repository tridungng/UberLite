package com.uberlite.driverdiscovery.schedule;

import com.uberlite.driverdiscovery.service.DriverStoreService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EvictionScheduler {
    private final DriverStoreService store;
    private static final long STALE_SECONDS = Duration.ofMinutes(2).getSeconds();

    public EvictionScheduler(DriverStoreService store) { this.store = store; }

    @Scheduled(fixedDelay = 30_000)
    public void evict() {
        store.evictStaleAndReturnRemoved(STALE_SECONDS);
    }
}
