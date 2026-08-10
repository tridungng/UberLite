package com.uberlite.tripservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Surge Pricing Service pending-request counter.
 *
 * <p>This is the demand half of the surge signal: Trip Service is the only component that knows how
 * many riders are actually waiting in a cell, so it increments when a trip enters the matching
 * pipeline and decrements when it leaves. Without these calls SPS computes surge from supply alone.
 */
@FeignClient(name = "surge-pricing-service", contextId = "surgePricingClient")
public interface SurgePricingClient {

    @PostMapping("/surge/{h3Cell}/pending-request")
    void incrementPendingRequest(@PathVariable("h3Cell") String h3Cell);

    @DeleteMapping("/surge/{h3Cell}/pending-request")
    void decrementPendingRequest(@PathVariable("h3Cell") String h3Cell);
}

