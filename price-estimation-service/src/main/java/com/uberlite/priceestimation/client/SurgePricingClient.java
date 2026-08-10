package com.uberlite.priceestimation.client;

import com.uberlite.common.dto.SurgeMultiplierDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Surge Pricing Service (ARCHITECTURE.md Sec. 2, "SPS").
 * Contract: {@code GET /surge/{h3Cell}} returning {@link SurgeMultiplierDto}.
 */
@FeignClient(name = "surge-pricing-service", contextId = "surgePricingClient")
public interface SurgePricingClient {

    @GetMapping("/surge/{h3Cell}")
    SurgeMultiplierDto getMultiplier(@PathVariable("h3Cell") String h3Cell);
}
