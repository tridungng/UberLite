package com.uberlite.surgepricing.client;

import com.uberlite.common.dto.DriverCandidateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client to driver-discovery-service.
 * Used to query active driver counts per H3 cell.
 */
@FeignClient(name = "driver-discovery-service", fallback = DriverDiscoveryClientFallback.class)
public interface DriverDiscoveryClient {
    /**
     * Get drivers in the given H3 cell (k-ring = 0).
     *
     * @param h3Cell central H3 cell id
     * @return list of active drivers in that cell
     */
    @GetMapping("/drivers/nearby-by-cell")
    List<DriverCandidateDto> getDriversInCell(
            @RequestParam String h3Cell,
            @RequestParam(defaultValue = "0") int kRing,
            @RequestParam(defaultValue = "100") int limit);
}
