package com.uberlite.matching.client;

import com.uberlite.common.dto.DriverCandidateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Driver Discovery Service (ARCHITECTURE.md Sec. 2, "DRS"). Contract: {@code GET /drivers/nearby}.
 *
 * <p>Deliberately has no {@code fallback}: an empty list from a fallback is indistinguishable from
 * "no drivers in this area", which would turn a DRS outage into a 404 and make Trip Service burn
 * its retry budget against a dead dependency. A connection failure must surface as a 502 instead —
 * see {@code ApiExceptionHandler}.
 */
@FeignClient(name = "driver-discovery-service", contextId = "driverDiscoveryClient")
public interface DriverDiscoveryClient {

    @GetMapping("/drivers/nearby")
    List<DriverCandidateDto> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam("radiusMeters") double radiusMeters,
            @RequestParam("limit") int limit);
}
