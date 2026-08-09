package com.uberlite.priceestimation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "route-service")
public interface RouteServiceClient {
    @GetMapping("/route/estimate")
    RouteEstimate estimate(@RequestParam double lat1,
                           @RequestParam double lon1,
                           @RequestParam double lat2,
                           @RequestParam double lon2);

    class RouteEstimate {
        public double straightDistanceKm;
        public double detourFactor;
    }
}
