package com.uberlite.matching.client;

import com.uberlite.common.dto.RouteEstimateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Route Service (ARCHITECTURE.md Sec. 2, "RS"). Contract: {@code GET /route/estimate}.
 *
 * <p>Used per candidate to get the driver -> pickup distance (ARCHITECTURE.md Sec. 4:
 * "Matching -> Route Service (per candidate, pickup ETA)").
 */
@FeignClient(name = "route-service", contextId = "routeServiceClient")
public interface RouteServiceClient {

    @GetMapping("/route/estimate")
    RouteEstimateDto estimate(
            @RequestParam("lat1") double lat1,
            @RequestParam("lon1") double lon1,
            @RequestParam("lat2") double lat2,
            @RequestParam("lon2") double lon2);
}

