package com.uberlite.priceestimation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "surge-pricing-service")
public interface SurgePricingClient {
    @GetMapping("/surge/multiplier")
    SurgeResponse getMultiplier(@RequestParam String h3Cell);
}

class SurgeResponse {
    public double multiplier;
}
