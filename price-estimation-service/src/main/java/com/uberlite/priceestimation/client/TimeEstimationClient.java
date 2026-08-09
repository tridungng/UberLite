package com.uberlite.priceestimation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "time-estimation-service")
public interface TimeEstimationClient {
    @GetMapping("/time/estimate")
    TimeEstimate estimate(@RequestParam String h3Cell, @RequestParam double distanceKm);

    class TimeEstimate {
        public double estimatedMinutes;
    }
}
