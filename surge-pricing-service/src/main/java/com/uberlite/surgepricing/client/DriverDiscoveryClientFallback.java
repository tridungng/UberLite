package com.uberlite.surgepricing.client;

import com.uberlite.common.dto.DriverCandidateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fallback for driver-discovery-service calls.
 * If the service is unreachable, return an empty list so surge pricing
 * degrades to baseline (multiplier = 1.0) rather than failing the request.
 */
@Component
public class DriverDiscoveryClientFallback implements DriverDiscoveryClient {
    private static final Logger logger = LoggerFactory.getLogger(DriverDiscoveryClientFallback.class);

    @Override
    public List<DriverCandidateDto> getDriversInCell(String h3Cell, int kRing, int limit) {
        logger.warn("driver-discovery-service unavailable for cell {}; falling back to empty driver list", h3Cell);
        return Collections.emptyList();
    }
}
