package com.uberlite.routeservice.api;

import com.uberlite.common.dto.RouteEstimateDto;
import com.uberlite.routeservice.domain.RouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the Route Service.
 *
 * <p>Contract: {@code GET /route/estimate} returning {@link RouteEstimateDto}. The response type is
 * the shared DTO from {@code common} — the very class price-estimation-service and matching-service
 * deserialize into — so producer and consumer cannot drift apart silently.
 */
@RestController
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * Estimate the distance between two points.
     *
     * @param actualDistanceKm optional real driven distance. When absent {@code detourFactor} is
     *     {@code null} rather than a made-up default — see {@link RouteEstimateDto}, where a 0
     *     would collapse the trip distance and produce a free ride.
     */
    @GetMapping("/route/estimate")
    public RouteEstimateDto estimate(
            @RequestParam double lat1,
            @RequestParam double lon1,
            @RequestParam double lat2,
            @RequestParam double lon2,
            @RequestParam(required = false) Double actualDistanceKm) {

        double straightDistanceKm = routeService.haversineKm(lat1, lon1, lat2, lon2);
        Double detourFactor = actualDistanceKm == null
                ? null
                : routeService.detourFactor(straightDistanceKm, actualDistanceKm);

        return new RouteEstimateDto(straightDistanceKm, detourFactor);
    }
}
